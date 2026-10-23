package com.cktscoredemo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class RtmpStreamService : Service(), ConnectChecker {

    data class StreamState(
        val isStreaming: Boolean = false,
        val isConnecting: Boolean = false,
        val isReconnecting: Boolean = false,
        val reconnectAttempts: Int = 0,
        val durationSeconds: Long = 0L,
        val bitrateKbps: Long = 0L,
        val errorMessage: String? = null,
        val endpointUrl: String = ""
    )

    companion object {
        const val ACTION_START = "com.cktscoredemo.ACTION_START"
        const val ACTION_STOP = "com.cktscoredemo.ACTION_STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA_INTENT = "extra_data_intent"
        const val EXTRA_ENDPOINT = "extra_endpoint"
        const val EXTRA_WIDTH = "extra_width"
        const val EXTRA_HEIGHT = "extra_height"
        const val EXTRA_FPS = "extra_fps"
        const val EXTRA_BITRATE = "extra_bitrate"
        const val EXTRA_AUDIO = "extra_audio"

        private const val NOTIFICATION_ID = 8801
        private const val CHANNEL_ID = "ckt_live_stream_channel"
        private const val MAX_RECONNECT_ATTEMPTS = 5

        private val _streamState = MutableStateFlow(StreamState())
        val streamState: StateFlow<StreamState> = _streamState.asStateFlow()

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            endpoint: String,
            width: Int = 1280,
            height: Int = 720,
            fps: Int = 30,
            bitrate: Int = 2500 * 1024,
            enableAudio: Boolean = true
        ) {
            val intent = Intent(context, RtmpStreamService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA_INTENT, data)
                putExtra(EXTRA_ENDPOINT, endpoint)
                putExtra(EXTRA_WIDTH, width)
                putExtra(EXTRA_HEIGHT, height)
                putExtra(EXTRA_FPS, fps)
                putExtra(EXTRA_BITRATE, bitrate)
                putExtra(EXTRA_AUDIO, enableAudio)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, RtmpStreamService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun clearError() {
            _streamState.value = _streamState.value.copy(errorMessage = null)
        }
    }

    private var rtmpDisplay: RtmpDisplay? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var durationSeconds = 0L
    private var reconnectAttempts = 0
    private var userRequestedStop = false
    private var currentEndpoint: String = ""
    private var currentBitrate: Int = 2500 * 1024
    private var originalTargetBitrate: Int = 2500 * 1024
    private var isAdaptiveBitrateEnabled: Boolean = true
    private var lowBitrateCount: Int = 0

    private val timerRunnable = object : Runnable {
        override fun run() {
            if (_streamState.value.isStreaming) {
                durationSeconds++
                _streamState.value = _streamState.value.copy(durationSeconds = durationSeconds)
                updateNotification()
                mainHandler.postDelayed(this, 1000)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Initialize RtmpDisplay with OpenGL enabled for smooth orientation and surface scaling
        rtmpDisplay = RtmpDisplay(baseContext, true, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_STOP) {
            userRequestedStop = true
            stopStreaming()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            userRequestedStop = false
            reconnectAttempts = 0
            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
            val dataIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_DATA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_DATA_INTENT)
            }
            val endpoint = intent.getStringExtra(EXTRA_ENDPOINT) ?: ""
            val width = intent.getIntExtra(EXTRA_WIDTH, 1280)
            val height = intent.getIntExtra(EXTRA_HEIGHT, 720)
            val fps = intent.getIntExtra(EXTRA_FPS, 30)
            val bitrate = intent.getIntExtra(EXTRA_BITRATE, 2500 * 1024)
            val enableAudio = intent.getBooleanExtra(EXTRA_AUDIO, true)
            currentBitrate = bitrate
            originalTargetBitrate = bitrate
            lowBitrateCount = 0

            // Start foreground immediately
            val notification = buildNotification("Preparing YouTube Live Stream...")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }

            if (resultCode != 0 && dataIntent != null && endpoint.isNotBlank()) {
                startStreaming(resultCode, dataIntent, endpoint, width, height, fps, bitrate, enableAudio)
            } else {
                _streamState.value = _streamState.value.copy(
                    isConnecting = false,
                    isStreaming = false,
                    errorMessage = "Missing permission or invalid streaming URL"
                )
                stopStreaming()
            }
        }

        return START_NOT_STICKY
    }

    private fun startStreaming(
        resultCode: Int,
        data: Intent,
        endpoint: String,
        width: Int,
        height: Int,
        fps: Int,
        bitrate: Int,
        enableAudio: Boolean
    ) {
        // Stop any active session and create a fresh instance
        try {
            if (rtmpDisplay?.isStreaming == true) {
                rtmpDisplay?.stopStream()
            }
        } catch (_: Exception) {}
        rtmpDisplay = RtmpDisplay(baseContext, true, this)
        val display = rtmpDisplay ?: return

        var targetEndpoint = endpoint.trim()
        if (targetEndpoint.isEmpty()) {
            targetEndpoint = "rtmp://a.rtmp.youtube.com/live2"
        }
        currentEndpoint = targetEndpoint

        _streamState.value = StreamState(
            isConnecting = true,
            isStreaming = false,
            endpointUrl = targetEndpoint,
            errorMessage = null
        )

        try {
            display.setIntentResult(resultCode, data)

            val displayMetrics = resources.displayMetrics
            val dpi = displayMetrics.densityDpi.coerceAtLeast(160)

            // Enforce standard 16:9 widescreen landscape for YouTube Live broadcasts
            val streamWidth = maxOf(width, height)
            val streamHeight = minOf(width, height)

            // prepareVideo(width, height, fps, bitrate, rotation, dpi)
            val videoPrepared = display.prepareVideo(
                streamWidth,
                streamHeight,
                fps,
                bitrate,
                0,
                dpi
            )

            val audioPrepared = if (enableAudio) {
                val prepared = display.prepareAudio(128 * 1024, 44100, true, false, false)
                if (!prepared) {
                    display.prepareAudio()
                } else {
                    true
                }
            } else {
                display.disableAudio()
                true
            }

            if (videoPrepared && audioPrepared) {
                display.startStream(targetEndpoint)
            } else {
                _streamState.value = _streamState.value.copy(
                    isConnecting = false,
                    isStreaming = false,
                    errorMessage = "Hardware encoder initialization failed (Video: $videoPrepared, Audio: $audioPrepared)"
                )
                stopStreaming()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _streamState.value = _streamState.value.copy(
                isConnecting = false,
                isStreaming = false,
                errorMessage = e.localizedMessage ?: "Encoder exception"
            )
            stopStreaming()
        }
    }

    private fun stopStreaming() {
        mainHandler.removeCallbacks(timerRunnable)
        try {
            if (rtmpDisplay?.isStreaming == true) {
                rtmpDisplay?.stopStream()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        rtmpDisplay = null
        durationSeconds = 0L
        reconnectAttempts = 0
        _streamState.value = _streamState.value.copy(
            isStreaming = false,
            isConnecting = false,
            isReconnecting = false,
            reconnectAttempts = 0,
            bitrateKbps = 0L
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ConnectChecker Callbacks
    override fun onConnectionStarted(url: String) {
        mainHandler.post {
            _streamState.value = _streamState.value.copy(
                isConnecting = true,
                errorMessage = null
            )
            updateNotification()
        }
    }

    override fun onConnectionSuccess() {
        mainHandler.post {
            reconnectAttempts = 0
            _streamState.value = _streamState.value.copy(
                isConnecting = false,
                isStreaming = true,
                isReconnecting = false,
                reconnectAttempts = 0,
                errorMessage = null
            )
            mainHandler.post(timerRunnable)
            updateNotification()
        }
    }

    private fun handleConnectionLoss(reason: String) {
        if (!userRequestedStop && reconnectAttempts < MAX_RECONNECT_ATTEMPTS && rtmpDisplay != null) {
            reconnectAttempts++
            val delayMs = (2000L * reconnectAttempts).coerceAtMost(8000L)
            _streamState.value = _streamState.value.copy(
                isConnecting = true,
                isReconnecting = true,
                reconnectAttempts = reconnectAttempts,
                errorMessage = "Network drop. Reconnecting in ${delayMs / 1000}s (Attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)..."
            )
            updateNotification()
            mainHandler.postDelayed({
                if (!userRequestedStop && rtmpDisplay != null) {
                    try {
                        if (rtmpDisplay?.isStreaming == true) {
                            rtmpDisplay?.stopStream()
                        }
                        rtmpDisplay?.startStream(currentEndpoint)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }, delayMs)
        } else {
            mainHandler.removeCallbacks(timerRunnable)
            val friendlyReason = when {
                reason.contains("Unimplemented message type", ignoreCase = true) || reason.contains("null", ignoreCase = true) ->
                    "Server closed stream. YouTube Live requires an active broadcast in YouTube Studio."
                else -> reason
            }
            _streamState.value = _streamState.value.copy(
                isConnecting = false,
                isStreaming = false,
                isReconnecting = false,
                errorMessage = if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
                    "Stream disconnected after $MAX_RECONNECT_ATTEMPTS reconnect attempts ($friendlyReason)"
                } else {
                    "Connection Failed: $friendlyReason"
                }
            )
            stopStreaming()
        }
    }

    override fun onConnectionFailed(reason: String) {
        mainHandler.post {
            handleConnectionLoss(reason)
        }
    }

    override fun onNewBitrate(bitrate: Long) {
        mainHandler.post {
            val kbps = bitrate / 1000
            _streamState.value = _streamState.value.copy(
                bitrateKbps = kbps
            )
            // Adaptive Bitrate: If bandwidth drops under 40% of target for 4 consecutive reports, step down
            if (isAdaptiveBitrateEnabled && _streamState.value.isStreaming && rtmpDisplay != null) {
                val targetKbps = (currentBitrate / 1024).toLong()
                if (kbps in 1 until (targetKbps * 4 / 10)) {
                    lowBitrateCount++
                    if (lowBitrateCount >= 4) {
                        val steppedDown = (currentBitrate * 0.7).toInt().coerceAtLeast(1500 * 1024)
                        if (steppedDown < currentBitrate) {
                            currentBitrate = steppedDown
                            try {
                                rtmpDisplay?.setVideoBitrateOnFly(steppedDown)
                            } catch (_: Exception) {}
                        }
                        lowBitrateCount = 0
                    }
                } else if (kbps > (targetKbps * 8 / 10)) {
                    lowBitrateCount = 0
                }
            }
        }
    }

    override fun onDisconnect() {
        mainHandler.post {
            handleConnectionLoss("Network disconnected")
        }
    }

    override fun onAuthError() {
        mainHandler.post {
            mainHandler.removeCallbacks(timerRunnable)
            _streamState.value = _streamState.value.copy(
                isConnecting = false,
                isStreaming = false,
                errorMessage = "YouTube RTMP Auth Error. Please check your YouTube Stream Key."
            )
            stopStreaming()
        }
    }

    override fun onAuthSuccess() {
        // Authenticated with RTMP server
    }

    // Notification Management
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CKTScore YouTube Live Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground service notification for active YouTube RTMP live stream"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, RtmpStreamService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("🔴 CKTScore — Live on YouTube")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_media_pause, "Stop Stream", stopPendingIntent)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val timeFormatted = formatDuration(durationSeconds)
        val text = if (_streamState.value.isStreaming) {
            "Broadcasting live ($timeFormatted • ${_streamState.value.bitrateKbps} kbps)"
        } else if (_streamState.value.isConnecting) {
            "Connecting to YouTube Live..."
        } else {
            "Live stream ready"
        }
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }
    }
}
