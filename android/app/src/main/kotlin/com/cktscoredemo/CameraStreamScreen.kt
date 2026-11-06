package com.cktscoredemo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.SurfaceHolder
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pedro.library.view.OpenGlView
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

private val PanelBg = Color(0xE6071529)
private val ChipBg = Color(0x99071529)
private val Accent = Color(0xFF38BDF8)
private val LiveRed = Color(0xFFE50914)
private val Amber = Color(0xFFF59E0B)
private val Good = Color(0xFF10B981)
private val Muted = Color(0xFFA6BAD4)
private val ZOOM_LADDER = listOf(0.6f, 1f, 1.5f, 2f, 3f, 5f, 10f, 20f, 30f)

/**
 * LIVE CAMERA BROADCAST SCREEN
 *
 * The centre of the screen is an exact 16:9 preview of what YouTube receives: camera + scoreboard,
 * composited on the GPU. Operator controls sit in the side rails (outside the picture on modern
 * phones) and are never part of the broadcast.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun CameraStreamOverlayScreen(
    overlayUrl: String,
    onBackToControl: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onSwitchToOverlayOnly: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()

    // Fixed landscape, immersive, screen always on while this screen is open.
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
            WindowInsetsControllerCompat(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                hide(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    window.attributes = lp
                }
                WindowInsetsControllerCompat(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val prefs = remember { context.getSharedPreferences("ckt_stream_prefs", Context.MODE_PRIVATE) }
    val streamState by LiveStreamEngine.state.collectAsState()

    // ---- Saved broadcast settings ----
    val defaultRtmpEndpoint = "rtmp://a.rtmp.youtube.com/live2"
    var streamKey by remember { mutableStateOf(prefs.getString("yt_stream_key", "") ?: "") }
    var rtmpServerUrl by remember {
        var url = prefs.getString("yt_rtmp_url", defaultRtmpEndpoint) ?: defaultRtmpEndpoint
        if (url.contains("443") || url.startsWith("rtmps://", ignoreCase = true)) {
            url = url.replace("rtmps://", "rtmp://", ignoreCase = true)
                .replace(":443", "")
                .replace("a.rtmps.youtube.com", "a.rtmp.youtube.com", ignoreCase = true)
                .replace("b.rtmps.youtube.com", "b.rtmp.youtube.com", ignoreCase = true)
        }
        mutableStateOf(url)
    }
    var selectedQuality by remember {
        val saved = prefs.getString("yt_quality", "1080p") ?: "1080p"
        mutableStateOf(if (saved in StreamProfile.QUALITIES) saved else "1080p")
    }
    var selectedFps by remember { mutableIntStateOf(prefs.getInt("yt_fps", 30)) }
    var isMicAudioEnabled by remember { mutableStateOf(prefs.getBoolean("yt_audio_enabled", true)) }
    var isSteadyMode by remember { mutableStateOf(prefs.getBoolean("yt_steady_mode", false)) }

    // ---- Camera / UI state ----
    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasMicPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var surfaceReady by remember { mutableStateOf(false) }
    var overlayWebView by remember { mutableStateOf<WebView?>(null) }

    var currentZoom by remember { mutableFloatStateOf(1f) }
    var zoomMin by remember { mutableFloatStateOf(1f) }
    var zoomMax by remember { mutableFloatStateOf(1f) }
    var isTorchOn by remember { mutableStateOf(false) }
    var isFarFocusLocked by remember { mutableStateOf(false) }
    var exposureIndex by remember { mutableIntStateOf(0) }
    var exposureRange by remember { mutableStateOf(0..0) }
    var evStep by remember { mutableFloatStateOf(1f / 3f) }

    var isOverlayVisible by remember { mutableStateOf(true) }
    var isBreakActive by remember { mutableStateOf(false) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showCameraTools by remember { mutableStateOf(false) }
    var showStopConfirm by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var streamKeyError by remember { mutableStateOf<String?>(null) }
    var autoDetectedKeyNotice by remember { mutableStateOf<String?>(null) }

    var focusRingOffset by remember { mutableStateOf<Offset?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    var batteryLevel by remember { mutableIntStateOf(100) }
    var deviceTempC by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(focusRingOffset) { if (focusRingOffset != null) { delay(1500); focusRingOffset = null } }
    LaunchedEffect(hint) { if (hint != null) { delay(2200); hint = null } }
    LaunchedEffect(streamState.infoMessage) {
        if (streamState.infoMessage != null) { delay(5000); LiveStreamEngine.clearInfo() }
    }

    // Battery & temperature, refreshed every 30s (long broadcasts heat phones).
    LaunchedEffect(Unit) {
        while (isActive) {
            val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = status?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = status?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) batteryLevel = level * 100 / scale
            val temp = status?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (temp > 0) deviceTempC = temp / 10f
            delay(30_000)
        }
    }

    // ---- Permissions ----
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result[Manifest.permission.CAMERA]?.let { hasCameraPermission = it }
        result[Manifest.permission.RECORD_AUDIO]?.let { hasMicPermission = it }
    }
    LaunchedEffect(Unit) {
        val missing = buildList {
            if (!hasCameraPermission) add(Manifest.permission.CAMERA)
            if (!hasMicPermission) add(Manifest.permission.RECORD_AUDIO)
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())
    }

    // ---- Camera preview lifecycle ----
    val requestedProfile = remember(selectedQuality, selectedFps) { StreamProfile.of(selectedQuality, selectedFps) }
    val isLandscape = LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    LaunchedEffect(surfaceReady, hasCameraPermission, requestedProfile, isLandscape) {
        // Wait for the landscape switch so the camera opens with the correct rotation.
        if (surfaceReady && isLandscape && hasCameraPermission && !LiveStreamEngine.state.value.isActive) {
            LiveStreamEngine.startPreview(context, requestedProfile, isSteadyMode)
        }
    }

    // Refresh hardware ranges and re-apply manual controls whenever the camera (re)opens.
    LaunchedEffect(streamState.cameraGeneration) {
        if (streamState.cameraGeneration == 0) return@LaunchedEffect
        val zr = LiveStreamEngine.zoomRange()
        zoomMin = zr.start
        zoomMax = zr.endInclusive
        currentZoom = currentZoom.coerceIn(zoomMin, zoomMax)
        LiveStreamEngine.setZoom(currentZoom)
        exposureRange = LiveStreamEngine.exposureRange()
        evStep = LiveStreamEngine.exposureStepEv()
        exposureIndex = exposureIndex.coerceIn(exposureRange.first, exposureRange.last)
        LiveStreamEngine.setExposure(exposureIndex)
        if (isTorchOn) isTorchOn = LiveStreamEngine.setTorch(true)
        if (isFarFocusLocked) LiveStreamEngine.setFarFocusLock(true)
    }

    // ---- Scoreboard → video compositing ----
    LaunchedEffect(isOverlayVisible, isBreakActive, streamState.previewGeneration) {
        LiveStreamEngine.setOverlayEnabled(isOverlayVisible || isBreakActive)
    }
    LaunchedEffect(overlayWebView, isOverlayVisible, isBreakActive, streamState.previewGeneration, streamState.activeProfile) {
        val profile = streamState.activeProfile ?: return@LaunchedEffect
        if (isBreakActive) {
            val slate = renderBreakSlate(minOf(profile.width, 1920), minOf(profile.height, 1080))
            while (isActive && !LiveStreamEngine.canAcceptOverlayFrame()) delay(40)
            LiveStreamEngine.pushOverlayFrame(slate)
            return@LaunchedEffect
        }
        if (!isOverlayVisible) return@LaunchedEffect
        val webView = overlayWebView ?: return@LaunchedEffect
        val maxWidth = minOf(profile.width, 2560)
        while (isActive) {
            var cost = 0L
            if (LiveStreamEngine.canAcceptOverlayFrame()) {
                val t0 = SystemClock.uptimeMillis()
                snapshotViewForStream(webView, maxWidth)?.let { LiveStreamEngine.pushOverlayFrame(it) }
                cost = SystemClock.uptimeMillis() - t0
            }
            // ~8 fps for the scoreboard, backing off automatically on slower devices.
            delay((cost * 4).coerceIn(120L, 600L))
        }
    }

    // ---- Smooth broadcast-style zoom ----
    var zoomJob by remember { mutableStateOf<Job?>(null) }
    fun animateZoomTo(target: Float) {
        val to = target.coerceIn(zoomMin, zoomMax)
        val from = currentZoom.coerceAtLeast(0.1f)
        zoomJob?.cancel()
        zoomJob = scope.launch {
            val steps = 18
            for (i in 1..steps) {
                val f = i / steps.toFloat()
                val eased = f * f * (3 - 2 * f)
                val z = exp(ln(from) + (ln(to) - ln(from)) * eased)
                currentZoom = z
                LiveStreamEngine.setZoom(z)
                delay(20)
            }
        }
    }
    fun zoomStep(up: Boolean) {
        val ladder = ZOOM_LADDER.filter { it in zoomMin..zoomMax }.ifEmpty { listOf(zoomMin, zoomMax) }
        val next = if (up) ladder.firstOrNull { it > currentZoom + 0.05f } ?: zoomMax
        else ladder.lastOrNull { it < currentZoom - 0.05f } ?: zoomMin
        animateZoomTo(next)
    }

    // ---- Hardware buttons: Vol+ zoom in (wraps to 1x), Vol− toggles break slate ----
    DisposableEffect(Unit) {
        MainActivity.onHardwareVolumeUp = {
            if (currentZoom >= zoomMax - 0.05f) animateZoomTo(1f) else zoomStep(true)
            triggerHapticFeedback(context)
        }
        MainActivity.onHardwareVolumeDown = {
            isBreakActive = !isBreakActive
            triggerHapticFeedback(context)
        }
        onDispose {
            MainActivity.onHardwareVolumeUp = null
            MainActivity.onHardwareVolumeDown = null
        }
    }

    // Pick up a stream key copied in YouTube Studio when returning to the app.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                if (!text.isNullOrEmpty() && text.length in 12..60 && !text.startsWith("http", true) &&
                    !text.contains(' ') && text != streamKey
                ) {
                    streamKey = text
                    streamKeyError = null
                    autoDetectedKeyNotice = "Stream key detected from clipboard and saved."
                    prefs.edit().putString("yt_stream_key", text).apply()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose { LiveStreamEngine.detach() }
    }

    fun saveSettings() {
        prefs.edit()
            .putString("yt_stream_key", streamKey.trim())
            .putString("yt_rtmp_url", rtmpServerUrl.trim())
            .putString("yt_quality", selectedQuality)
            .putInt("yt_fps", selectedFps)
            .putBoolean("yt_audio_enabled", isMicAudioEnabled)
            .putBoolean("yt_steady_mode", isSteadyMode)
            .apply()
    }

    fun goLive() {
        if (streamKey.isBlank()) {
            streamKeyError = "Paste your YouTube stream key to go live."
            showSettings = true
            return
        }
        if (!hasCameraPermission) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
            return
        }
        saveSettings()
        triggerHapticFeedback(context)
        LiveStreamEngine.goLive(
            context,
            formatYouTubeRtmpEndpoint(rtmpServerUrl, streamKey),
            audioEnabled = isMicAudioEnabled && hasMicPermission,
            hasMicPermission = hasMicPermission
        )
        if (!hasMicPermission) hint = "No microphone permission — streaming without sound"
    }

    BackHandler {
        when {
            isScreenLocked -> hint = "Screen is locked — press and hold the lock badge to unlock"
            showCameraTools -> showCameraTools = false
            streamState.isActive -> showLeaveConfirm = true
            else -> onBackToControl()
        }
    }

    // =====================================================================================
    // LAYOUT
    // =====================================================================================
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        // ---------------- 16:9 PROGRAM PREVIEW (exactly what viewers see) ----------------
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
        ) {
            // Scoreboard source. It sits *under* the camera surface, so it is never shown directly;
            // it is snapshotted and composited into the video so preview == broadcast.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(0x00000000)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            textZoom = 100
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        addJavascriptInterface(object {
                            @JavascriptInterface
                            fun backToControl() {
                                Handler(Looper.getMainLooper()).post { onBackToControl() }
                            }
                        }, "AndroidScoreBridge")
                        webChromeClient = object : WebChromeClient() {
                            override fun onPermissionRequest(request: PermissionRequest?) {
                                request?.grant(request.resources)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            @SuppressLint("WebViewClientOnReceivedSslError")
                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.proceed()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                view?.let { MobileSyncBus.replayLatest(it) }
                            }
                        }
                        MobileSyncBus.register(this)
                        loadUrl(overlayUrl)
                        overlayWebView = this
                    }
                }
            )

            // Camera + composited scoreboard, rendered by the encoder's GL pipeline.
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    OpenGlView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        keepScreenOn = true
                        LiveStreamEngine.attach(this)
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {}
                            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                                surfaceReady = true
                            }
                            override fun surfaceDestroyed(holder: SurfaceHolder) {
                                surfaceReady = false
                                LiveStreamEngine.onSurfaceDestroyed()
                            }
                        })

                        val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                            override fun onScale(detector: ScaleGestureDetector): Boolean {
                                if (isScreenLocked) return false
                                zoomJob?.cancel()
                                val target = (currentZoom * detector.scaleFactor).coerceIn(zoomMin, zoomMax)
                                currentZoom = target
                                LiveStreamEngine.setZoom(target)
                                return true
                            }
                        })
                        val tapDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onDown(e: MotionEvent): Boolean = true
                            override fun onSingleTapUp(e: MotionEvent): Boolean {
                                if (isScreenLocked) return false
                                if (showCameraTools) {
                                    showCameraTools = false
                                    return true
                                }
                                if (isFarFocusLocked) {
                                    isFarFocusLocked = false
                                    LiveStreamEngine.setFarFocusLock(false)
                                }
                                LiveStreamEngine.tapToFocus(e)
                                focusRingOffset = Offset(e.x, e.y)
                                hint = "Focus & exposure set on this spot"
                                triggerHapticFeedback(ctx)
                                return true
                            }
                            override fun onDoubleTap(e: MotionEvent): Boolean {
                                if (isScreenLocked) return false
                                animateZoomTo(if (currentZoom > 1.05f) 1f else 2f)
                                return true
                            }
                        })
                        setOnTouchListener { _, event ->
                            scaleDetector.onTouchEvent(event)
                            if (!scaleDetector.isInProgress) tapDetector.onTouchEvent(event)
                            true
                        }
                    }
                }
            )

            // Focus ring (operator only)
            focusRingOffset?.let { offset ->
                val density = LocalDensity.current
                val xDp = with(density) { offset.x.toDp() } - 34.dp
                val yDp = with(density) { offset.y.toDp() } - 34.dp
                Box(
                    modifier = Modifier
                        .offset(x = xDp, y = yDp)
                        .size(68.dp)
                        .border(BorderStroke(2.dp, Color(0xFFFFC107)), RoundedCornerShape(12.dp))
                )
            }

            if (!hasCameraPermission) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Camera access is needed to broadcast", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087CFF))
                    ) { Text("Allow camera & microphone") }
                }
            }

            // ---- Status strip (top-left of the picture) ----
            StatusStrip(
                state = streamState,
                batteryLevel = batteryLevel,
                deviceTempC = deviceTempC,
                isBreakActive = isBreakActive,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
            )

            // ---- Banners (top-centre) ----
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 48.dp, start = 12.dp, end = 12.dp)
                    .widthIn(max = 520.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                streamState.errorMessage?.let { msg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xF08B0000))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(msg, fontSize = 12.sp, color = Color.White, modifier = Modifier.weight(1f))
                        if (streamState.canRetry && !streamState.isActive) {
                            SmallPillButton("Retry", Color(0x55FFFFFF)) {
                                LiveStreamEngine.clearError()
                                goLive()
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        SmallPillButton("Dismiss", Color(0x33FFFFFF)) { LiveStreamEngine.clearError() }
                    }
                }
                if (streamState.isReconnecting) {
                    Banner("Network dropped — reconnecting (attempt ${streamState.reconnectAttempts} of ${LiveStreamEngine.MAX_RETRIES})…", Color(0xF0B45309))
                }
                streamState.infoMessage?.let { Banner(it, Color(0xF0075985)) }
                hint?.let { Banner(it, PanelBg) }
            }

            // ---- Camera tools panel ----
            if (showCameraTools && !isScreenLocked) {
                CameraToolsPanel(
                    modifier = Modifier.align(Alignment.Center),
                    zoom = currentZoom,
                    zoomMin = zoomMin,
                    zoomMax = zoomMax,
                    onZoomPreset = { animateZoomTo(it) },
                    torchSupported = LiveStreamEngine.isTorchSupported(),
                    isTorchOn = isTorchOn,
                    onTorch = { on -> isTorchOn = LiveStreamEngine.setTorch(on) && on },
                    isFarFocusLocked = isFarFocusLocked,
                    onFocusMode = { far ->
                        isFarFocusLocked = far
                        LiveStreamEngine.setFarFocusLock(far)
                        hint = if (far) "Focus locked far (∞) — ideal for full-ground shots" else "Continuous auto-focus"
                    },
                    exposureIndex = exposureIndex,
                    exposureRange = exposureRange,
                    evStep = evStep,
                    onExposure = { idx ->
                        exposureIndex = idx.coerceIn(exposureRange.first, exposureRange.last)
                        LiveStreamEngine.setExposure(exposureIndex)
                    },
                    isSteadyMode = isSteadyMode,
                    onSteadyMode = { on ->
                        isSteadyMode = on
                        prefs.edit().putBoolean("yt_steady_mode", on).apply()
                        LiveStreamEngine.setStabilization(on)
                    },
                    onClose = { showCameraTools = false }
                )
            }
        }

        // ---------------- LEFT RAIL: production controls ----------------
        if (!isScreenLocked) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RailButton("‹", "Back") {
                    if (streamState.isActive) showLeaveConfirm = true else onBackToControl()
                }
                RailButton(if (isOverlayVisible) "▣" else "▢", if (isOverlayVisible) "Score ON" else "Score OFF", active = isOverlayVisible) {
                    triggerHapticFeedback(context)
                    isOverlayVisible = !isOverlayVisible
                }
                RailButton("⏸", if (isBreakActive) "On break" else "Break", active = isBreakActive, activeColor = Color(0xFFD97706)) {
                    triggerHapticFeedback(context)
                    isBreakActive = !isBreakActive
                }
                val micOn = isMicAudioEnabled && !streamState.isMicMuted && hasMicPermission
                RailButton(if (micOn) "🎙" else "🔇", if (micOn) "Mic ON" else "Mic OFF", active = !micOn, activeColor = Color(0xFFB91C1C)) {
                    triggerHapticFeedback(context)
                    if (!hasMicPermission) {
                        permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                    } else if (streamState.isActive) {
                        LiveStreamEngine.setMicMuted(!streamState.isMicMuted)
                    } else {
                        val turnOn = !micOn
                        isMicAudioEnabled = turnOn
                        LiveStreamEngine.setMicMuted(!turnOn)
                        prefs.edit().putBoolean("yt_audio_enabled", turnOn).apply()
                    }
                }
                RailButton("🔒", "Lock") {
                    triggerHapticFeedback(context)
                    showCameraTools = false
                    isScreenLocked = true
                }
            }

            // ---------------- RIGHT RAIL: camera + go live ----------------
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(end = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                RailButton("⚙", "Setup") { showSettings = true }
                RailButton("◎", "Camera", active = showCameraTools) { showCameraTools = !showCameraTools }

                GoLiveButton(state = streamState) {
                    when {
                        streamState.isStreaming || streamState.isReconnecting -> showStopConfirm = true
                        streamState.isConnecting -> LiveStreamEngine.stop()
                        else -> goLive()
                    }
                }

                Column(
                    modifier = Modifier
                        .width(64.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0x26FFFFFF)),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ZoomStepButton("+") { zoomStep(true) }
                    Text(
                        text = String.format(Locale.US, "%.1fx", currentZoom),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFFFC107),
                        modifier = Modifier
                            .clickable { animateZoomTo(1f) }
                            .padding(vertical = 2.dp)
                    )
                    ZoomStepButton("−") { zoomStep(false) }
                }
            }
        }

        // ---------------- TOUCH GUARD ----------------
        if (isScreenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(PanelBg)
                        .border(BorderStroke(1.dp, Color(0x88FFC107)), RoundedCornerShape(22.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { hint = "Press and hold to unlock" },
                                onLongPress = {
                                    triggerHapticFeedback(context)
                                    isScreenLocked = false
                                }
                            )
                        }
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔒", fontSize = 14.sp)
                    Spacer(Modifier.width(8.dp))
                    Text("Locked · press & hold to unlock", fontSize = 12.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // =====================================================================================
    // DIALOGS
    // =====================================================================================
    if (showSettings) {
        val isLive = streamState.isActive
        AlertDialog(
            onDismissRequest = { showSettings = false },
            modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 640.dp),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = Color(0xFF0B1B32),
            title = { Text("YouTube Live setup", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 17.sp) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    autoDetectedKeyNotice?.let {
                        Banner("✅ $it", Color(0xFF065F46))
                        Spacer(Modifier.height(10.dp))
                    }

                    SectionLabel("Stream key")
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                    OutlinedTextField(
                        value = streamKey,
                        onValueChange = { streamKey = it; streamKeyError = null },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("xxxx-xxxx-xxxx-xxxx-xxxx", fontSize = 13.sp) },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!clipText.isNullOrEmpty() && clipText != streamKey) {
                                    Text(
                                        "Paste", fontSize = 12.sp, color = Good, fontWeight = FontWeight.Bold,
                                        modifier = Modifier.clickable { streamKey = clipText; streamKeyError = null }.padding(8.dp)
                                    )
                                }
                                Text(
                                    if (isKeyVisible) "Hide" else "Show", fontSize = 12.sp, color = Accent,
                                    modifier = Modifier.clickable { isKeyVisible = !isKeyVisible }.padding(8.dp)
                                )
                            }
                        },
                        isError = streamKeyError != null
                    )
                    streamKeyError?.let { Text(it, fontSize = 12.sp, color = Color(0xFFFF4E61), modifier = Modifier.padding(top = 4.dp)) }
                    Text(
                        "YouTube Studio → Go live → Stream → copy the stream key. It's saved on this phone for next time.",
                        fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(14.dp))
                    SectionLabel("Video quality")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(
                            "720p" to "Weak network",
                            "1080p" to "Recommended",
                            "1440p" to "Strong Wi-Fi/5G",
                            "2160p" to "4K · very strong"
                        ).forEach { (q, note) ->
                            ChoiceChip(
                                title = if (q == "2160p") "4K" else q,
                                subtitle = note,
                                selected = selectedQuality == q,
                                enabled = !isLive,
                                modifier = Modifier.weight(1f)
                            ) { selectedQuality = q }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("Frame rate")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        ChoiceChip("30 fps", "Most reliable", selectedFps == 30, !isLive, Modifier.weight(1f)) { selectedFps = 30 }
                        ChoiceChip("60 fps", "Smooth action, more data", selectedFps == 60, !isLive, Modifier.weight(1f)) { selectedFps = 60 }
                    }
                    val preview = StreamProfile.of(selectedQuality, selectedFps)
                    Text(
                        if (isLive) "Stop the broadcast to change quality."
                        else "${preview.label} at ${preview.bitrateLabel} upload. Bitrate lowers automatically if the network struggles.",
                        fontSize = 11.sp, color = Muted, modifier = Modifier.padding(top = 6.dp)
                    )

                    Spacer(Modifier.height(14.dp))
                    SettingSwitch(
                        title = "Commentary microphone",
                        subtitle = "Uses the phone's stereo camcorder mic",
                        checked = isMicAudioEnabled,
                        onChange = { isMicAudioEnabled = it; LiveStreamEngine.setMicMuted(!it) }
                    )
                    SettingSwitch(
                        title = "Steady mode (handheld)",
                        subtitle = "Electronic stabilization; crops slightly. Leave off on a tripod.",
                        checked = isSteadyMode,
                        onChange = { isSteadyMode = it; LiveStreamEngine.setStabilization(it) }
                    )

                    Spacer(Modifier.height(10.dp))
                    SectionLabel("RTMP server")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallPillButton("Primary", if (rtmpServerUrl == defaultRtmpEndpoint) Color(0xFF087CFF) else Color(0x22FFFFFF)) {
                            rtmpServerUrl = defaultRtmpEndpoint
                        }
                        SmallPillButton("Backup", if (rtmpServerUrl.contains("b.rtmp")) Color(0xFF087CFF) else Color(0x22FFFFFF)) {
                            rtmpServerUrl = "rtmp://b.rtmp.youtube.com/live2?backup=1"
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = rtmpServerUrl,
                        onValueChange = { rtmpServerUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text(defaultRtmpEndpoint, fontSize = 13.sp) }
                    )

                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tip: turn on Do Not Disturb and plug in a charger for long matches. Only the camera and scoreboard are broadcast — never these controls or your notifications.",
                        fontSize = 11.sp, color = Muted, lineHeight = 15.sp
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = {
                        saveSettings()
                        showSettings = false
                    }) { Text("Save", color = Color.White) }
                    if (!isLive) {
                        Button(
                            onClick = {
                                if (streamKey.isBlank()) {
                                    streamKeyError = "Paste your YouTube stream key to go live."
                                } else {
                                    showSettings = false
                                    goLive()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = LiveRed)
                        ) { Text("Go live", fontWeight = FontWeight.Bold, color = Color.White) }
                    }
                }
            },
            dismissButton = null
        )
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            containerColor = Color(0xFF0B1B32),
            title = { Text("End the broadcast?", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Viewers will see the stream end. You've been live for ${formatDuration(streamState.durationSeconds)}.", fontSize = 13.sp, color = Muted) },
            confirmButton = {
                Button(
                    onClick = { showStopConfirm = false; LiveStreamEngine.stop() },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveRed)
                ) { Text("End broadcast", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStopConfirm = false }) { Text("Stay live", color = Color.White) }
            }
        )
    }

    if (showLeaveConfirm) {
        AlertDialog(
            onDismissRequest = { showLeaveConfirm = false },
            containerColor = Color(0xFF0B1B32),
            title = { Text("You're live", fontWeight = FontWeight.Bold, color = Color.White) },
            text = { Text("Leaving the camera screen ends the broadcast. Stay here to keep streaming.", fontSize = 13.sp, color = Muted) },
            confirmButton = {
                Button(
                    onClick = {
                        showLeaveConfirm = false
                        LiveStreamEngine.stop()
                        onBackToControl()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = LiveRed)
                ) { Text("End & leave", color = Color.White) }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLeaveConfirm = false }) { Text("Stay live", color = Color.White) }
            }
        )
    }
}

// =========================================================================================
// Components
// =========================================================================================

@Composable
private fun StatusStrip(
    state: LiveStreamEngine.State,
    batteryLevel: Int,
    deviceTempC: Float,
    isBreakActive: Boolean,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        val (label, color) = when {
            state.isStreaming -> "● LIVE  ${formatDuration(state.durationSeconds)}" to LiveRed
            state.isReconnecting -> "RECONNECTING" to Amber
            state.isConnecting -> "CONNECTING…" to Amber
            state.isPreviewing -> "READY" to Good
            else -> "STARTING CAMERA" to Color(0xFF475569)
        }
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 10.dp, vertical = 5.dp)
        )
        state.activeProfile?.let { InfoChip(it.label, Accent) }
        if (state.isStreaming) {
            val maxKbps = (state.activeProfile?.bitrate ?: 1) / 1000f
            val ratio = state.bitrateKbps / maxKbps
            val health = when {
                ratio >= 0.7f -> Good
                ratio >= 0.4f -> Amber
                else -> Color(0xFFFF4E61)
            }
            InfoChip(
                if (state.bitrateKbps >= 1000) String.format(Locale.US, "%.1f Mbps", state.bitrateKbps / 1000f) else "${state.bitrateKbps} kbps",
                health
            )
        }
        if (isBreakActive) InfoChip("BREAK SLATE ON AIR", Color(0xFFFFC107))
        if (state.isMicMuted) InfoChip("MIC OFF", Color(0xFFFF4E61))
        if (state.isStabilized) InfoChip("STEADY", Muted)
        InfoChip("🔋 $batteryLevel%", if (batteryLevel <= 20) Color(0xFFFF4E61) else Color(0xFFE2E8F0))
        if (deviceTempC >= 41f) InfoChip(String.format(Locale.US, "🌡 %.0f°C", deviceTempC), if (deviceTempC >= 44f) Color(0xFFFF4E61) else Amber)
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
    Text(
        text,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(ChipBg).padding(horizontal = 8.dp, vertical = 5.dp)
    )
}

@Composable
private fun Banner(text: String, background: Color) {
    Text(
        text,
        fontSize = 12.sp,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(background).padding(horizontal = 14.dp, vertical = 7.dp)
    )
}

@Composable
private fun RailButton(
    icon: String,
    label: String,
    active: Boolean = false,
    activeColor: Color = Color(0xFF0284C7),
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) activeColor else Color(0x26FFFFFF))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 18.sp, color = Color.White)
        Spacer(Modifier.height(2.dp))
        Text(label, fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun GoLiveButton(state: LiveStreamEngine.State, onClick: () -> Unit) {
    val isLive = state.isStreaming || state.isReconnecting
    val bg = when {
        isLive -> Color(0xFF1F2937)
        state.isConnecting -> Amber
        else -> LiveRed
    }
    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(bg)
            .border(BorderStroke(3.dp, if (isLive) LiveRed else Color.White), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                isLive -> {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(3.dp)).background(LiveRed))
                    Spacer(Modifier.height(4.dp))
                    Text("END", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                state.isConnecting -> {
                    Text("…", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("CANCEL", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
                else -> {
                    Text("GO", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("LIVE", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ZoomStepButton(symbol: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().height(40.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(symbol, fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SmallPillButton(text: String, background: Color, onClick: () -> Unit) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Accent, modifier = Modifier.padding(bottom = 6.dp))
}

@Composable
private fun ChoiceChip(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF087CFF) else Color(0xFF071529))
            .border(BorderStroke(1.dp, if (selected) Color(0xFF60A5FA) else Color(0x22FFFFFF)), RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled || selected) 1f else 0.45f)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
        Text(subtitle, fontSize = 9.5.sp, color = if (selected) Color(0xDDFFFFFF) else Muted, maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
            Text(subtitle, fontSize = 11.sp, color = Muted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF087CFF))
        )
    }
}

@Composable
private fun CameraToolsPanel(
    modifier: Modifier,
    zoom: Float,
    zoomMin: Float,
    zoomMax: Float,
    onZoomPreset: (Float) -> Unit,
    torchSupported: Boolean,
    isTorchOn: Boolean,
    onTorch: (Boolean) -> Unit,
    isFarFocusLocked: Boolean,
    onFocusMode: (Boolean) -> Unit,
    exposureIndex: Int,
    exposureRange: IntRange,
    evStep: Float,
    onExposure: (Int) -> Unit,
    isSteadyMode: Boolean,
    onSteadyMode: (Boolean) -> Unit,
    onClose: () -> Unit
) {
    Card(
        modifier = modifier.widthIn(max = 380.dp).padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = PanelBg),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0x3338BDF8))
    ) {
        Column(
            modifier = Modifier.padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Camera", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(1f))
                SmallPillButton("Done", Color(0xFF087CFF), onClose)
            }

            SectionLabel("Zoom  ·  pinch, double-tap or Vol+")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ZOOM_LADDER.filter { it in zoomMin..zoomMax && it != 1.5f }.take(6).forEach { z ->
                    val selected = abs(zoom - z) < 0.1f
                    Text(
                        if (z < 1f) String.format(Locale.US, "%.1fx", z) else "${z.toInt()}x",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color(0xFF071529) else Color.White,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFFFFC107) else Color(0x26FFFFFF))
                            .clickable { onZoomPreset(z) }
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                    )
                }
            }

            SectionLabel("Focus  ·  tap the picture to focus on a spot")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                ChoiceChip("Auto", "Follows the action", !isFarFocusLocked, true, Modifier.weight(1f)) { onFocusMode(false) }
                ChoiceChip("Far lock ∞", "Whole ground sharp, no hunting", isFarFocusLocked, true, Modifier.weight(1f)) { onFocusMode(true) }
            }

            if (exposureRange.last > exposureRange.first) {
                val tap = (0.33f / evStep.coerceAtLeast(0.01f)).let { kotlin.math.round(it).toInt() }.coerceAtLeast(1)
                SectionLabel("Brightness")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallPillButton("−", Color(0x26FFFFFF)) { onExposure(exposureIndex - tap) }
                    Text(
                        String.format(Locale.US, "%+.1f EV", exposureIndex * evStep),
                        fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFC107),
                        modifier = Modifier.width(72.dp), textAlign = TextAlign.Center
                    )
                    SmallPillButton("+", Color(0x26FFFFFF)) { onExposure(exposureIndex + tap) }
                    SmallPillButton("Reset", Color(0x26FFFFFF)) { onExposure(0) }
                }
            }

            if (torchSupported) {
                SettingSwitch("Torch", "Phone flashlight, for dusk or indoor nets", isTorchOn, onTorch)
            }
            SettingSwitch("Steady mode", "For handheld shots; crops slightly", isSteadyMode, onSteadyMode)
        }
    }
}
