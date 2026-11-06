package com.cktscoredemo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.camera2.CameraCharacteristics
import android.media.MediaCodecInfo
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Size
import android.view.MotionEvent
import android.view.View
import com.pedro.common.ConnectChecker
import com.pedro.encoder.input.gl.render.filters.`object`.ImageObjectFilterRender
import com.pedro.encoder.input.video.CameraCallbacks
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.util.BitrateAdapter
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Video settings for one broadcast. Bitrates follow YouTube's recommended H.264 ranges.
 */
data class StreamProfile(
    val quality: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int // bits per second
) {
    val label: String get() = "${height}p$fps"
    val bitrateLabel: String get() = String.format(java.util.Locale.US, "%.1f Mbps", bitrate / 1_000_000f)

    companion object {
        val QUALITIES = listOf("720p", "1080p", "1440p", "2160p")

        fun of(quality: String, fps: Int): StreamProfile {
            val smooth = fps >= 50
            val f = if (smooth) 60 else 30
            return when (quality) {
                "720p" -> StreamProfile("720p", 1280, 720, f, if (smooth) 5_000_000 else 3_500_000)
                "1440p" -> StreamProfile("1440p", 2560, 1440, f, if (smooth) 15_000_000 else 10_000_000)
                "2160p" -> StreamProfile("2160p", 3840, 2160, f, if (smooth) 30_000_000 else 20_000_000)
                else -> StreamProfile("1080p", 1920, 1080, f, if (smooth) 9_000_000 else 6_000_000)
            }
        }

        fun qualityForHeight(height: Int): String = when {
            height >= 2160 -> "2160p"
            height >= 1440 -> "1440p"
            height >= 1080 -> "1080p"
            else -> "720p"
        }
    }
}

/**
 * Direct camera → encoder → RTMP pipeline.
 *
 * The camera sensor feeds an OpenGL surface at full stream resolution. The scoreboard
 * overlay is composited on the GPU as a texture, so only the camera + scoreboard reach
 * YouTube — never the operator controls, dialogs or system notifications.
 */
object LiveStreamEngine : ConnectChecker {

    data class State(
        val isPreviewing: Boolean = false,
        val isStreaming: Boolean = false,
        val isConnecting: Boolean = false,
        val isReconnecting: Boolean = false,
        val reconnectAttempts: Int = 0,
        val durationSeconds: Long = 0L,
        val bitrateKbps: Long = 0L,
        val targetKbps: Long = 0L,
        val droppedFrames: Long = 0L,
        val activeProfile: StreamProfile? = null,
        val isMicMuted: Boolean = false,
        val isStabilized: Boolean = false,
        val previewGeneration: Int = 0,
        val cameraGeneration: Int = 0,
        val errorMessage: String? = null,
        val canRetry: Boolean = false,
        val infoMessage: String? = null
    ) {
        val isActive: Boolean get() = isStreaming || isConnecting || isReconnecting
    }

    const val MAX_RETRIES = 10
    private const val RETRY_DELAY_MS = 3000L
    private const val KEYFRAME_INTERVAL_SECONDS = 2

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private val main = Handler(Looper.getMainLooper())
    private var camera: RtmpCamera2? = null
    private var bitrateAdapter: BitrateAdapter? = null
    private var overlayFilter: ImageObjectFilterRender? = null
    private var overlayEnabled = false
    private var pendingOverlay: Bitmap? = null
    private var userStopping = false
    private var startedAtMs = 0L
    private var wantStabilization = false

    private inline fun update(block: State.() -> State) {
        _state.value = _state.value.block()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val s = _state.value
            if (!s.isActive) return
            val dropped = try { camera?.streamClient?.getDroppedVideoFrames() ?: 0L } catch (_: Exception) { 0L }
            update {
                copy(
                    durationSeconds = if (startedAtMs > 0) (SystemClock.elapsedRealtime() - startedAtMs) / 1000 else 0L,
                    droppedFrames = dropped
                )
            }
            main.postDelayed(this, 1000)
        }
    }

    // ---------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------

    fun attach(view: OpenGlView) {
        detach()
        view.setAspectRatioMode(AspectRatioMode.Adjust)
        camera = RtmpCamera2(view, this).also { cam ->
            cam.streamClient.setReTries(MAX_RETRIES)
            cam.setCameraCallbacks(object : CameraCallbacks {
                override fun onCameraChanged(facing: CameraHelper.Facing) {}
                override fun onCameraError(error: String) {
                    main.post { update { copy(errorMessage = "Camera error: $error", canRetry = false) } }
                }
                override fun onCameraOpened() {
                    main.post {
                        applyStabilization()
                        update { copy(cameraGeneration = cameraGeneration + 1) }
                    }
                }
                override fun onCameraDisconnected() {}
            })
        }
    }

    fun detach() {
        val cam = camera ?: return
        userStopping = true
        main.removeCallbacks(ticker)
        try { if (cam.isStreaming) cam.stopStream() } catch (_: Exception) {}
        try { if (cam.isOnPreview) cam.stopPreview() } catch (_: Exception) {}
        camera = null
        overlayFilter = null
        pendingOverlay = null
        bitrateAdapter = null
        update {
            State(
                isMicMuted = isMicMuted,
                errorMessage = if (isActive) "Broadcast ended because the camera screen was closed." else errorMessage
            )
        }
    }

    /** Called when the preview surface is destroyed (screen off, app sent to background). */
    fun onSurfaceDestroyed() {
        val cam = camera ?: return
        val wasLive = _state.value.isActive
        if (wasLive) {
            userStopping = true
            main.removeCallbacks(ticker)
            try { cam.stopStream() } catch (_: Exception) {}
        }
        try { if (cam.isOnPreview) cam.stopPreview() } catch (_: Exception) {}
        overlayFilter = null
        pendingOverlay = null
        update {
            copy(
                isPreviewing = false, isStreaming = false, isConnecting = false, isReconnecting = false,
                bitrateKbps = 0L,
                errorMessage = if (wasLive) "Broadcast stopped because the app left the screen. Keep the app open while live." else errorMessage,
                canRetry = wasLive
            )
        }
    }

    /**
     * Opens the back camera at the requested profile (falling back to what the sensor supports).
     * Never restarts the camera while live.
     */
    fun startPreview(context: Context, requested: StreamProfile, stabilization: Boolean): StreamProfile? {
        val cam = camera ?: return null
        if (_state.value.isActive) return _state.value.activeProfile
        wantStabilization = stabilization

        val effective = resolveSupportedProfile(cam, requested)
        val rotation = CameraHelper.getCameraOrientation(context)
        try {
            if (cam.isOnPreview) cam.stopPreview()
            cam.startPreview(CameraHelper.Facing.BACK, effective.width, effective.height, effective.fps, rotation)
        } catch (e: Exception) {
            update { copy(errorMessage = "Could not open camera: ${e.localizedMessage ?: e.javaClass.simpleName}", canRetry = false) }
            return null
        }

        overlayFilter = null
        pendingOverlay = null
        if (overlayEnabled) attachOverlayFilter()

        val notice = if (effective != requested) {
            "This camera can't do ${requested.label}; using ${effective.label}."
        } else null
        update {
            copy(
                isPreviewing = true,
                activeProfile = effective,
                targetKbps = effective.bitrate / 1000L,
                previewGeneration = previewGeneration + 1,
                infoMessage = notice
            )
        }
        return effective
    }

    private fun resolveSupportedProfile(cam: RtmpCamera2, requested: StreamProfile): StreamProfile {
        var width = requested.width
        var height = requested.height
        try {
            val sizes: List<Size> = cam.resolutionsBack
            if (sizes.isNotEmpty() && sizes.none { it.width == width && it.height == height }) {
                val best = sizes
                    .filter { it.width * 9 == it.height * 16 && it.width <= requested.width }
                    .maxByOrNull { it.width }
                    ?: Size(1280, 720)
                width = best.width
                height = best.height
            }
        } catch (_: Exception) {}

        var fps = requested.fps
        if (fps > 30) {
            val supports = try {
                cam.getSupportedFps(Size(width, height), CameraHelper.Facing.BACK).any { it.upper >= fps }
            } catch (_: Exception) { false }
            if (!supports) fps = 30
        }

        return if (width == requested.width && height == requested.height && fps == requested.fps) {
            requested
        } else {
            StreamProfile.of(StreamProfile.qualityForHeight(height), fps).copy(width = width, height = height)
        }
    }

    // ---------------------------------------------------------------------------------
    // Broadcasting
    // ---------------------------------------------------------------------------------

    fun goLive(context: Context, url: String, audioEnabled: Boolean, hasMicPermission: Boolean = true): Boolean {
        val cam = camera
        val profile = _state.value.activeProfile
        if (cam == null || profile == null || !_state.value.isPreviewing) {
            update { copy(errorMessage = "Camera is not ready yet. Wait a moment and try again.", canRetry = false) }
            return false
        }
        if (_state.value.isActive) return true

        userStopping = false
        val rotation = CameraHelper.getCameraOrientation(context)

        // High profile gives noticeably sharper video at the same bitrate; fall back if unsupported.
        var videoOk = try {
            cam.prepareVideo(
                profile.width, profile.height, profile.fps, profile.bitrate,
                KEYFRAME_INTERVAL_SECONDS, rotation,
                MediaCodecInfo.CodecProfileLevel.AVCProfileHigh, avcLevelFor(profile)
            )
        } catch (_: Exception) { false }
        if (!videoOk) {
            videoOk = try {
                cam.prepareVideo(profile.width, profile.height, profile.fps, profile.bitrate, KEYFRAME_INTERVAL_SECONDS, rotation)
            } catch (_: Exception) { false }
        }

        val audioOk = if (!hasMicPermission) {
            // The mic can't be opened without permission; send a video-only stream instead of failing.
            cam.streamClient.setOnlyVideo(true)
            true
        } else try {
            cam.streamClient.setOnlyVideo(false)
            cam.prepareAudio(MediaRecorder.AudioSource.CAMCORDER, 128_000, 48_000, true, false, false) ||
                cam.prepareAudio(128_000, 44_100, true, false, false) ||
                cam.prepareAudio()
        } catch (_: Exception) { false }

        if (!videoOk || !audioOk) {
            update {
                copy(
                    errorMessage = "Encoder could not start (video: $videoOk, audio: $audioOk). Try a lower quality.",
                    canRetry = false
                )
            }
            return false
        }

        if (hasMicPermission) {
            if (!audioEnabled || _state.value.isMicMuted) cam.disableAudio() else cam.enableAudio()
        }

        bitrateAdapter = BitrateAdapter { newBitrate ->
            try { camera?.setVideoBitrateOnFly(newBitrate) } catch (_: Exception) {}
            update { copy(targetKbps = newBitrate / 1000L) }
        }.apply { setMaxBitrate(profile.bitrate) }

        startedAtMs = 0L
        update {
            copy(
                isConnecting = true, isStreaming = false, isReconnecting = false,
                reconnectAttempts = 0, durationSeconds = 0L, bitrateKbps = 0L, droppedFrames = 0L,
                targetKbps = profile.bitrate / 1000L,
                isMicMuted = isMicMuted || !audioEnabled,
                errorMessage = null, canRetry = false
            )
        }
        cam.startStream(url)
        return true
    }

    fun stop() {
        val cam = camera
        userStopping = true
        main.removeCallbacks(ticker)
        try { if (cam?.isStreaming == true) cam.stopStream() } catch (_: Exception) {}
        bitrateAdapter = null
        update {
            copy(
                isStreaming = false, isConnecting = false, isReconnecting = false,
                reconnectAttempts = 0, bitrateKbps = 0L
            )
        }
    }

    fun clearError() = update { copy(errorMessage = null, canRetry = false) }
    fun clearInfo() = update { copy(infoMessage = null) }

    private fun avcLevelFor(p: StreamProfile): Int {
        val macroblocksPerSecond = (p.width / 16) * (p.height / 16) * p.fps
        return when {
            macroblocksPerSecond <= 245_760 -> MediaCodecInfo.CodecProfileLevel.AVCLevel41  // 1080p30
            macroblocksPerSecond <= 522_240 -> MediaCodecInfo.CodecProfileLevel.AVCLevel42  // 1080p60
            macroblocksPerSecond <= 589_824 -> MediaCodecInfo.CodecProfileLevel.AVCLevel5   // 1440p30
            macroblocksPerSecond <= 983_040 -> MediaCodecInfo.CodecProfileLevel.AVCLevel51  // 1440p60 / 4K30
            else -> MediaCodecInfo.CodecProfileLevel.AVCLevel52                              // 4K60
        }
    }

    // ---------------------------------------------------------------------------------
    // Camera controls
    // ---------------------------------------------------------------------------------

    fun zoomRange(): ClosedFloatingPointRange<Float> = try {
        val r = camera?.zoomRange
        if (r != null && r.upper > r.lower) r.lower..r.upper else 1f..1f
    } catch (_: Exception) { 1f..1f }

    fun currentZoom(): Float = try { camera?.zoom ?: 1f } catch (_: Exception) { 1f }

    fun setZoom(level: Float) {
        try {
            val r = zoomRange()
            camera?.setZoom(level.coerceIn(r.start, r.endInclusive))
        } catch (_: Exception) {}
    }

    fun tapToFocus(event: MotionEvent): Boolean = try { camera?.tapToFocus(event) ?: false } catch (_: Exception) { false }

    /** Locks focus at infinity — ideal for a wide cricket field so AF never hunts on passing players. */
    fun setFarFocusLock(locked: Boolean) {
        val cam = camera ?: return
        try {
            if (locked) {
                cam.disableAutoFocus()
                cam.setFocusDistance(0f)
            } else {
                cam.enableAutoFocus()
            }
        } catch (_: Exception) {}
    }

    fun isTorchSupported(): Boolean = try { camera?.isLanternSupported ?: false } catch (_: Exception) { false }

    fun setTorch(on: Boolean): Boolean = try {
        val cam = camera
        if (cam == null) false else {
            if (on) cam.enableLantern() else cam.disableLantern()
            true
        }
    } catch (_: Exception) { false }

    fun exposureRange(): IntRange = try {
        val cam = camera
        if (cam == null) 0..0 else cam.minExposure..cam.maxExposure
    } catch (_: Exception) { 0..0 }

    fun exposureStepEv(): Float = try {
        camera?.cameraCharacteristics?.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: (1f / 3f)
    } catch (_: Exception) { 1f / 3f }

    fun setExposure(index: Int) {
        try { camera?.setExposure(index) } catch (_: Exception) {}
    }

    fun setStabilization(enabled: Boolean) {
        wantStabilization = enabled
        applyStabilization()
    }

    private fun applyStabilization() {
        val cam = camera ?: return
        var stabilized: Boolean
        try {
            if (wantStabilization) {
                // Electronic stabilization for handheld shots (crops slightly). Don't combine with OIS.
                cam.disableOpticalVideoStabilization()
                stabilized = cam.enableVideoStabilization()
                if (!stabilized) stabilized = cam.enableOpticalVideoStabilization()
            } else {
                // Tripod: optical stabilization only, no crop.
                cam.disableVideoStabilization()
                stabilized = cam.enableOpticalVideoStabilization()
            }
        } catch (_: Exception) {
            stabilized = false
        }
        update { copy(isStabilized = stabilized && wantStabilization) }
    }

    fun setMicMuted(muted: Boolean) {
        val cam = camera
        try {
            if (cam != null && cam.isStreaming) {
                if (muted) cam.disableAudio() else cam.enableAudio()
            }
        } catch (_: Exception) {}
        update { copy(isMicMuted = muted) }
    }

    // ---------------------------------------------------------------------------------
    // Scoreboard overlay compositing
    // ---------------------------------------------------------------------------------

    fun setOverlayEnabled(enabled: Boolean) {
        if (enabled == overlayEnabled && (overlayFilter != null || !enabled)) return
        overlayEnabled = enabled
        val gl = camera?.glInterface ?: return
        if (enabled) {
            attachOverlayFilter()
        } else {
            try { gl.clearFilters() } catch (_: Exception) {}
            overlayFilter = null
            pendingOverlay = null
        }
    }

    private fun attachOverlayFilter() {
        val gl = camera?.glInterface ?: return
        val filter = ImageObjectFilterRender()
        try {
            // setFilter(0, …) throws on an empty filter list, so always clear + add.
            gl.clearFilters()
            gl.addFilter(filter)
            filter.setScale(100f, 100f)
            filter.setPosition(0f, 0f)
            overlayFilter = filter
            pendingOverlay = null
        } catch (_: Exception) {
            overlayFilter = null
        }
    }

    /** True when the GPU has consumed the previous overlay frame and is ready for a new one. */
    fun canAcceptOverlayFrame(): Boolean {
        if (overlayFilter == null) return false
        val prev = pendingOverlay
        return prev == null || prev.isRecycled
    }

    /**
     * Hands a full-frame (16:9, transparent where the camera should show) bitmap to the GPU.
     * Ownership transfers to the engine; the texture loader recycles it after upload.
     */
    fun pushOverlayFrame(frame: Bitmap): Boolean {
        val filter = overlayFilter
        if (filter == null || !canAcceptOverlayFrame()) {
            frame.recycle()
            return false
        }
        filter.setImage(frame)
        filter.setScale(100f, 100f)
        filter.setPosition(0f, 0f)
        pendingOverlay = frame
        return true
    }

    // ---------------------------------------------------------------------------------
    // ConnectChecker (may be invoked off the main thread)
    // ---------------------------------------------------------------------------------

    override fun onConnectionStarted(url: String) {
        main.post { if (!userStopping) update { copy(isConnecting = true) } }
    }

    override fun onConnectionSuccess() {
        main.post {
            if (userStopping) return@post
            if (startedAtMs == 0L) startedAtMs = SystemClock.elapsedRealtime()
            update {
                copy(
                    isConnecting = false, isStreaming = true, isReconnecting = false,
                    reconnectAttempts = 0, errorMessage = null, canRetry = false
                )
            }
            main.removeCallbacks(ticker)
            main.post(ticker)
        }
    }

    override fun onConnectionFailed(reason: String) {
        main.post {
            val cam = camera ?: return@post
            if (userStopping) return@post
            val retrying = try { cam.streamClient.reTry(RETRY_DELAY_MS, reason) } catch (_: Exception) { false }
            if (retrying) {
                update {
                    copy(
                        isStreaming = false, isConnecting = true, isReconnecting = true,
                        reconnectAttempts = reconnectAttempts + 1, bitrateKbps = 0L
                    )
                }
            } else {
                userStopping = true
                main.removeCallbacks(ticker)
                try { cam.stopStream() } catch (_: Exception) {}
                update {
                    copy(
                        isStreaming = false, isConnecting = false, isReconnecting = false, bitrateKbps = 0L,
                        errorMessage = friendlyReason(reason, reconnectAttempts),
                        canRetry = true
                    )
                }
            }
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        main.post {
            if (userStopping) return@post
            update { copy(bitrateKbps = bitrate / 1000L) }
            val congested = try { camera?.streamClient?.hasCongestion() ?: false } catch (_: Exception) { false }
            bitrateAdapter?.adaptBitrate(bitrate, congested)
        }
    }

    override fun onDisconnect() {
        main.post {
            if (_state.value.isReconnecting) return@post
            update { copy(isStreaming = false, isConnecting = false, bitrateKbps = 0L) }
        }
    }

    override fun onAuthError() {
        main.post {
            userStopping = true
            main.removeCallbacks(ticker)
            try { camera?.stopStream() } catch (_: Exception) {}
            update {
                copy(
                    isStreaming = false, isConnecting = false, isReconnecting = false,
                    errorMessage = "YouTube rejected the stream key. Copy it again from YouTube Studio.",
                    canRetry = false
                )
            }
        }
    }

    override fun onAuthSuccess() {}

    private fun friendlyReason(reason: String, attempts: Int): String {
        val base = when {
            reason.contains("Unimplemented message type", true) || reason.contains("null", true) ->
                "YouTube closed the connection. Check the broadcast is set up in YouTube Studio and the key is current."
            reason.contains("timeout", true) || reason.contains("Endpoint malformed", true) ->
                "Could not reach YouTube ($reason). Check the network or RTMP server address."
            else -> "Connection lost: $reason"
        }
        return if (attempts > 0) "$base (gave up after $attempts reconnect attempts)" else base
    }
}

/** Draws a view (with its transparent background) into a bitmap no wider than [maxWidth]. */
fun snapshotViewForStream(view: View, maxWidth: Int): Bitmap? {
    val w = view.width
    val h = view.height
    if (w <= 0 || h <= 0) return null
    return try {
        val scale = min(1f, maxWidth / w.toFloat())
        val outW = (w * scale).roundToInt().coerceAtLeast(1)
        val outH = (h * scale).roundToInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.scale(outW / w.toFloat(), outH / h.toFloat())
        view.draw(canvas)
        bmp
    } catch (_: Throwable) {
        null
    }
}

/** Full-frame "innings break" slate that replaces the camera picture on the stream. */
fun renderBreakSlate(width: Int = 1920, height: Int = 1080): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val bg = Paint().apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            0xFF06142A.toInt(), 0xFF0B2A4A.toInt(), Shader.TileMode.CLAMP
        )
    }
    c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bg)

    val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC107.toInt() }
    c.drawRect(width * 0.38f, height * 0.585f, width * 0.62f, height * 0.585f + height * 0.006f, accent)

    val title = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textSize = height * 0.085f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.06f
    }
    c.drawText("INNINGS BREAK", width / 2f, height * 0.52f, title)

    val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFA6BAD4.toInt()
        textSize = height * 0.04f
        textAlign = Paint.Align.CENTER
    }
    c.drawText("Live coverage will resume shortly", width / 2f, height * 0.67f, sub)
    return bmp
}
