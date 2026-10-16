package com.cktscoredemo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.json.JSONTokener
import android.view.ViewGroup
import android.content.ClipboardManager
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import java.util.Locale
import kotlin.math.abs
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // Permissions handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request runtime permissions for Camera, Audio & Notifications
        val permissions = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }

        val prefs = getSharedPreferences("ckt_prefs", Context.MODE_PRIVATE)
        val defaultUrl = prefs.getString("server_url", "file:///android_asset/web/overlay.html") ?: "file:///android_asset/web/overlay.html"

        setContent {
            val darkColors = darkColorScheme(
                primary = Color(0xFF087CFF),
                background = Color(0xFF061226),
                surface = Color(0xFF0B1B32),
                onPrimary = Color.White,
                onBackground = Color(0xFFF3F7FF),
                onSurface = Color(0xFFF3F7FF)
            )

            MaterialTheme(colorScheme = darkColors) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CktAppScreen(
                        initialUrl = defaultUrl,
                        onSaveUrl = { newUrl ->
                            prefs.edit().putString("server_url", newUrl).apply()
                        }
                    )
                }
            }
        }
    }
}

enum class ConnectionStatus {
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class AppMode {
    CONTROL,        // Web Control Panel
    CAMERA_STREAM,  // Live Phone Camera with Mixed Scoreboard Overlay
    OVERLAY_ONLY    // Clean Scoreboard Overlay View
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CktAppScreen(
    initialUrl: String,
    onSaveUrl: (String) -> Unit
) {
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var inputUrlText by remember { mutableStateOf(initialUrl) }
    var connectionStatus by remember { mutableStateOf(ConnectionStatus.CONNECTING) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var activeMode by remember { mutableStateOf(AppMode.CONTROL) }
    var isFullscreen by remember { mutableStateOf(false) }

    val overlayUrl = remember(currentUrl) {
        val baseUrl = currentUrl.trimEnd('/')
        if (baseUrl.startsWith("file://")) {
            "file:///android_asset/web/overlay.html"
        } else {
            "$baseUrl/overlay.html"
        }
    }

    BackHandler(enabled = (activeMode != AppMode.CONTROL) || (webViewRef?.canGoBack() == true)) {
        if (activeMode != AppMode.CONTROL) {
            activeMode = AppMode.CONTROL
        } else {
            webViewRef?.goBack()
        }
    }

    if (activeMode == AppMode.CAMERA_STREAM) {
        // CAMERA STREAM + OVERLAY MIXING MODE
        CameraStreamOverlayScreen(
            overlayUrl = overlayUrl,
            onBackToControl = { activeMode = AppMode.CONTROL },
            onSwitchToOverlayOnly = { activeMode = AppMode.OVERLAY_ONLY }
        )
    } else {
        // STANDARD OPERATOR / OVERLAY WEBVIEW MODE
        Column(modifier = Modifier.fillMaxSize()) {
            // Top App Bar
            if (!isFullscreen) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF071529))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Status and Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showUrlDialog = true }
                    ) {
                        val statusColor = when (connectionStatus) {
                            ConnectionStatus.CONNECTED -> Color(0xFF00E59B)
                            ConnectionStatus.CONNECTING -> Color(0xFFFFC107)
                            ConnectionStatus.ERROR -> Color(0xFFFF4E61)
                        }
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(statusColor)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(
                                text = "CKTScore Live",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = currentUrl,
                                fontSize = 10.sp,
                                color = Color(0xFFA6BAD4),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Mode & Action Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 📹 CAMERA + OVERLAY BUTTON
                        Button(
                            onClick = { activeMode = AppMode.CAMERA_STREAM },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A86B)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("📹 Live Cam", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Toggle Control vs Overlay Only
                        Button(
                            onClick = {
                                val baseUrl = currentUrl.trimEnd('/')
                                if (activeMode == AppMode.CONTROL) {
                                    activeMode = AppMode.OVERLAY_ONLY
                                    webViewRef?.loadUrl(overlayUrl)
                                } else {
                                    activeMode = AppMode.CONTROL
                                    val target = if (baseUrl.startsWith("file://")) {
                                        "file:///android_asset/web/index.html"
                                    } else {
                                        "$baseUrl/"
                                    }
                                    webViewRef?.loadUrl(target)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (activeMode == AppMode.OVERLAY_ONLY) Color(0xFF5B1725) else Color(0xFF102641)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (activeMode == AppMode.CONTROL) "Overlay" else "Control",
                                fontSize = 11.sp,
                                color = Color.White
                            )
                        }

                        // Server Settings Button
                        Button(
                            onClick = {
                                inputUrlText = currentUrl
                                showUrlDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF102641)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text("Server", fontSize = 11.sp, color = Color.White)
                        }

                        // Fullscreen Toggle
                        Button(
                            onClick = { isFullscreen = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF102641)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text("⛶", fontSize = 12.sp, color = Color.White)
                        }

                        // Reload Button
                        Button(
                            onClick = {
                                connectionStatus = ConnectionStatus.CONNECTING
                                errorMessage = null
                                webViewRef?.reload()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087CFF)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text("↻", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Loading indicator
            if (isLoading && progress < 1f) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.5.dp),
                    color = Color(0xFF00C7FF),
                    trackColor = Color(0xFF0B1B32)
                )
            }

            // Main WebView area with Error Overlay
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )

                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                textZoom = 100
                                mediaPlaybackRequiresUserGesture = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                cacheMode = WebSettings.LOAD_DEFAULT
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress / 100f
                                    if (newProgress >= 100) {
                                        isLoading = false
                                    }
                                }

                                override fun onPermissionRequest(request: PermissionRequest?) {
                                    request?.grant(request.resources)
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    return true
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    isLoading = true
                                    errorMessage = null
                                    connectionStatus = ConnectionStatus.CONNECTING
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    if (errorMessage == null) {
                                        connectionStatus = ConnectionStatus.CONNECTED
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        connectionStatus = ConnectionStatus.ERROR
                                        errorMessage = error?.description?.toString() ?: "Failed to connect"
                                    }
                                }
                            }

                            webViewRef = this
                            loadUrl(currentUrl)
                        }
                    },
                    update = { webView ->
                        webViewRef = webView
                    }
                )

                // Floating Restore Button when in fullscreen mode
                if (isFullscreen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xCC071529))
                            .clickable { isFullscreen = false }
                            .padding(8.dp)
                    ) {
                        Text("⚙", fontSize = 14.sp, color = Color.White)
                    }
                }

                // Connection Error Fallback Screen
                if (connectionStatus == ConnectionStatus.ERROR) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF061226).copy(alpha = 0.95f))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1B32)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "⚠️ Server Connection Error",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color(0xFFFF4E61)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Cannot reach $currentUrl",
                                    fontSize = 12.sp,
                                    color = Color(0xFFA6BAD4)
                                )
                                if (errorMessage != null) {
                                    Text(
                                        text = errorMessage!!,
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFC107),
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))

                                Button(
                                    onClick = {
                                        connectionStatus = ConnectionStatus.CONNECTING
                                        errorMessage = null
                                        webViewRef?.loadUrl(currentUrl)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087CFF)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Retry Connection")
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        val offlineUrl = "file:///android_asset/web/index.html"
                                        currentUrl = offlineUrl
                                        onSaveUrl(offlineUrl)
                                        connectionStatus = ConnectionStatus.CONNECTING
                                        errorMessage = null
                                        webViewRef?.loadUrl(offlineUrl)
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Use Offline Bundled UI", color = Color(0xFF00C7FF))
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedButton(
                                    onClick = {
                                        inputUrlText = currentUrl
                                        showUrlDialog = true
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Change Server IP / URL", color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Server Configuration Dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            title = {
                Text(
                    text = "Configure Server URL",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter your CKTScore server address (or tunnel):",
                        fontSize = 12.sp,
                        color = Color(0xFFA6BAD4)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = inputUrlText,
                        onValueChange = { inputUrlText = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. http://192.168.1.9:3000") }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Quick Presets:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00C7FF)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetChip(label = "Wi-Fi (192.168.1.9:3000)") {
                            inputUrlText = "http://192.168.1.9:3000"
                        }
                        PresetChip(label = "Bundled Offline UI") {
                            inputUrlText = "file:///android_asset/web/index.html"
                        }
                        PresetChip(label = "Emulator (10.0.2.2)") {
                            inputUrlText = "http://10.0.2.2:3000"
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        var trimmed = inputUrlText.trim()
                        if (trimmed.isNotEmpty()) {
                            // Auto-heal missing schemes and ports
                            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://") && !trimmed.startsWith("file://")) {
                                trimmed = if (trimmed.contains(".loca.lt") || trimmed.contains("ngrok") || trimmed.contains(".app") || trimmed.contains(".dev")) {
                                    "https://$trimmed"
                                } else {
                                    "http://$trimmed"
                                }
                            }
                            val hostPart = trimmed.substringAfter("://").substringBefore("/")
                            if (!hostPart.contains(":") && !trimmed.contains(".loca.lt") && !trimmed.contains("ngrok") && !trimmed.startsWith("file://")) {
                                val pathPart = trimmed.substringAfter("://").substringAfter("/", "")
                                trimmed = if (pathPart.isNotEmpty()) {
                                    trimmed.substringBefore("://") + "://" + hostPart + ":3000/" + pathPart
                                } else {
                                    trimmed.substringBefore("://") + "://" + hostPart + ":3000"
                                }
                            }

                            currentUrl = trimmed
                            onSaveUrl(trimmed)
                            connectionStatus = ConnectionStatus.CONNECTING
                            errorMessage = null
                            webViewRef?.loadUrl(trimmed)
                        }
                        showUrlDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087CFF))
                ) {
                    Text("Connect")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUrlDialog = false }) {
                    Text("Cancel", color = Color(0xFFA6BAD4))
                }
            },
            containerColor = Color(0xFF0B1B32)
        )
    }
}

/**
 * Format and sanitize YouTube RTMP/RTMPS streaming endpoint.
 * Guarantees explicit port 443 for RTMPS and handles key appending cleanly.
 */
fun formatYouTubeRtmpEndpoint(serverUrl: String, streamKey: String): String {
    var base = serverUrl.trim()
    var cleanKey = streamKey.trim()

    // If user pasted full rtmp/rtmps URL into the key field
    if (cleanKey.startsWith("rtmp://", ignoreCase = true) || cleanKey.startsWith("rtmps://", ignoreCase = true)) {
        base = cleanKey
        cleanKey = ""
    }

    if (base.contains("youtube.com", ignoreCase = true)) {
        base = base.replace("rtmp://", "rtmps://", ignoreCase = true)
            .replace("a.rtmp.youtube.com", "a.rtmps.youtube.com", ignoreCase = true)
            .replace("b.rtmp.youtube.com", "b.rtmps.youtube.com", ignoreCase = true)

        // Ensure port 443 is explicitly included for RTMPS if no port is specified
        if (base.startsWith("rtmps://", ignoreCase = true)) {
            val withoutScheme = base.substring(8)
            val slashIdx = withoutScheme.indexOf('/')
            val hostPart = if (slashIdx != -1) withoutScheme.substring(0, slashIdx) else withoutScheme
            val pathPart = if (slashIdx != -1) withoutScheme.substring(slashIdx) else ""

            if (!hostPart.contains(':')) {
                base = "rtmps://$hostPart:443$pathPart"
            }
        }
    }

    if (!base.startsWith("rtmp://", ignoreCase = true) && !base.startsWith("rtmps://", ignoreCase = true)) {
        base = "rtmps://$base"
    }

    base = base.trimEnd('/')
    cleanKey = cleanKey.trimStart('/')

    return if (cleanKey.isNotEmpty()) {
        "$base/$cleanKey"
    } else {
        base
    }
}

/**
 * Opens YouTube Live Studio in Chrome Custom Tabs (or default browser).
 * Google requires Custom Tabs for Google/YouTube Sign-In to prevent the
 * "This browser or app may not be secure" error on Android WebViews.
 */
fun launchYouTubeStudio(context: Context) {
    val studioUrl = "https://studio.youtube.com/livestreaming"
    try {
        val customTabsIntent = CustomTabsIntent.Builder()
            .setShowTitle(true)
            .build()
        customTabsIntent.launchUrl(context, Uri.parse(studioUrl))
    } catch (_: Exception) {
        val browserIntent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(studioUrl)).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(browserIntent)
    }
}

/**
 * CAMERA STREAM OVERLAY SCREEN WITH YOUTUBE LIVE INTEGRATION
 * Combines hardware-accelerated CameraX video preview (background)
 * with transparent CricHeroes live scoreboard overlay (foreground),
 * and streams the mixed video + overlay directly to YouTube Live via RTMP.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CameraStreamOverlayScreen(
    overlayUrl: String,
    onBackToControl: () -> Unit,
    onSwitchToOverlayOnly: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val activity = context as? Activity

    // Lock screen to 16:9 Landscape & Immersive Fullscreen for clean YouTube broadcast
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val window = activity?.window
        if (window != null) {
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (window != null) {
                val insetsController = WindowInsetsControllerCompat(window, window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val streamPrefs = remember { context.getSharedPreferences("ckt_stream_prefs", Context.MODE_PRIVATE) }
    val streamState by RtmpStreamService.streamState.collectAsState()

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var isTorchOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    var showControls by remember { mutableStateOf(true) }

    // Camera Zoom States
    var currentZoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(8f) }

    // Stream Setup & Dialog States
    var showStreamSetupDialog by remember { mutableStateOf(false) }
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    var streamKey by remember { mutableStateOf(streamPrefs.getString("yt_stream_key", "") ?: "") }
    var autoDetectedKeyNotice by remember { mutableStateOf<String?>(null) }
    
    val defaultRtmpEndpoint = "rtmps://a.rtmps.youtube.com:443/live2"
    var initialRtmpUrl = streamPrefs.getString("yt_rtmp_url", defaultRtmpEndpoint) ?: defaultRtmpEndpoint
    if (initialRtmpUrl.contains("youtube.com", ignoreCase = true)) {
        if (initialRtmpUrl.startsWith("rtmp://", ignoreCase = true)) {
            initialRtmpUrl = initialRtmpUrl.replace("rtmp://", "rtmps://", ignoreCase = true)
                .replace("a.rtmp.youtube.com", "a.rtmps.youtube.com", ignoreCase = true)
        }
        if (initialRtmpUrl.startsWith("rtmps://", ignoreCase = true) && !initialRtmpUrl.substring(8).contains(":")) {
            val slashIdx = initialRtmpUrl.indexOf('/', startIndex = 8)
            initialRtmpUrl = if (slashIdx != -1) {
                initialRtmpUrl.substring(0, slashIdx) + ":443" + initialRtmpUrl.substring(slashIdx)
            } else {
                "$initialRtmpUrl:443"
            }
        }
    }
    var rtmpServerUrl by remember { mutableStateOf(initialRtmpUrl) }
    var selectedQuality by remember { mutableStateOf(streamPrefs.getString("yt_quality", "720p") ?: "720p") }
    var isMicAudioEnabled by remember { mutableStateOf(streamPrefs.getBoolean("yt_audio_enabled", true)) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var streamKeyValidationError by remember { mutableStateOf<String?>(null) }

    // Automatically capture stream key when returning from YouTube Studio in Chrome Custom Tabs
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                if (!text.isNullOrEmpty() && text.length in 12..60 && !text.startsWith("http", ignoreCase = true) && text != streamKey) {
                    streamKey = text
                    streamKeyValidationError = null
                    autoDetectedKeyNotice = "Stream Key auto-detected and applied!"
                    streamPrefs.edit().putString("yt_stream_key", text).apply()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // MediaProjection screen recording launcher for RTMP streaming
    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val (w, h, bitrate) = when (selectedQuality) {
                "1080p" -> Triple(1920, 1080, 4500 * 1024)
                "480p" -> Triple(854, 480, 1200 * 1024)
                else -> Triple(1280, 720, 2500 * 1024)
            }

            val fullUrl = formatYouTubeRtmpEndpoint(rtmpServerUrl, streamKey)

            RtmpStreamService.start(
                context = context,
                resultCode = result.resultCode,
                data = result.data!!,
                endpoint = fullUrl,
                width = w,
                height = h,
                fps = 30,
                bitrate = bitrate,
                enableAudio = isMicAudioEnabled
            )
        }
    }

    // Bind CameraX preview
    LaunchedEffect(lensFacing, previewView) {
        val pv = previewView ?: return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(pv.surfaceProvider)
            }
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build()

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
                // restore torch state if supported
                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                    camera?.cameraControl?.enableTorch(isTorchOn)
                }
                // Observe hardware zoom range & current ratio
                camera?.cameraInfo?.zoomState?.observe(lifecycleOwner) { zoomState ->
                    if (zoomState != null) {
                        currentZoomRatio = zoomState.zoomRatio
                        minZoomRatio = zoomState.minZoomRatio
                        maxZoomRatio = zoomState.maxZoomRatio
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // LAYER 1: LIVE HARDWARE CAMERA PREVIEW WITH PINCH-TO-ZOOM
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(camera, minZoomRatio, maxZoomRatio) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1.0f && camera != null) {
                            val target = (currentZoomRatio * zoomChange).coerceIn(minZoomRatio, maxZoomRatio)
                            currentZoomRatio = target
                            camera?.cameraControl?.setZoomRatio(target)
                        }
                    }
                }
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        previewView = this
                    }
                },
                update = { pv ->
                    previewView = pv
                }
            )
        }

        // LAYER 2: TRANSPARENT CRICKET SCOREBOARD OVERLAY (Foreground)
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(0x00000000) // Transparent background
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
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }
                    webViewClient = WebViewClient()
                    loadUrl(overlayUrl)
                }
            }
        )

        // Error message banner if streaming fails
        if (streamState.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 50.dp, start = 12.dp, end = 12.dp)
                    .align(Alignment.TopCenter)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE8B0000)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "⚠️ ${streamState.errorMessage}",
                            fontSize = 11.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x44FFFFFF))
                                .clickable {
                                    RtmpStreamService.stop(context)
                                    RtmpStreamService.clearError()
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Dismiss", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // LAYER 3: FLOATING CAMERA & STREAM CONTROLS (Top Bar)
        if (showControls) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Exit to Control Panel
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xCC071529))
                        .clickable { onBackToControl() }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("← Scorer", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                // Streaming & Camera Action Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 🔴 YOUTUBE LIVE STREAMING BUTTON / STATUS PILL
                    if (streamState.isStreaming) {
                        // LIVE STATUS PILL (Click to stop)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE50914))
                                .clickable { showStopConfirmDialog = true }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = "LIVE ${formatDuration(streamState.durationSeconds)} (${streamState.bitrateKbps}k)",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (streamState.isConnecting) {
                        // CONNECTING STATE
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFD97706))
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "⏳ Connecting...",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // GO LIVE BUTTON
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE50914))
                                .clickable {
                                    streamKeyValidationError = null
                                    showStreamSetupDialog = true
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🔴", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Stream to YouTube",
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Flip Camera (Back / Front)
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xCC071529))
                            .clickable {
                                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                    CameraSelector.LENS_FACING_FRONT
                                } else {
                                    CameraSelector.LENS_FACING_BACK
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Text("🔄", fontSize = 13.sp)
                    }

                    // YouTube Studio Live Control / Sign In Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC071529))
                            .clickable { launchYouTubeStudio(context) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📺", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Studio", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Quick Zoom Cycle Pill (1x -> 2x -> 3x -> 1x)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xCC071529))
                            .clickable {
                                val next = when {
                                    currentZoomRatio < 1.8f -> 2.0f
                                    currentZoomRatio < 2.8f -> 3.0f
                                    else -> 1.0f
                                }.coerceIn(minZoomRatio, maxZoomRatio)
                                currentZoomRatio = next
                                camera?.cameraControl?.setZoomRatio(next)
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = String.format(Locale.US, "🔍 %.1fx", currentZoomRatio),
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Torch / Flashlight
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (isTorchOn) Color(0xFFFFC107) else Color(0xCC071529))
                            .clickable {
                                if (camera?.cameraInfo?.hasFlashUnit() == true) {
                                    val newState = !isTorchOn
                                    isTorchOn = newState
                                    camera?.cameraControl?.enableTorch(newState)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Text(if (isTorchOn) "⚡" else "💡", fontSize = 13.sp)
                    }

                    // Hide Toolbar for Clean Stream View
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xCC071529))
                            .clickable { showControls = false }
                            .padding(8.dp)
                    ) {
                        Text("⛶", fontSize = 13.sp, color = Color.White)
                    }
                }
            }
        } else {
            // Restore Controls floating pill + Small Live Indicator if streaming
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (streamState.isStreaming) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xCCE50914))
                            .padding(horizontal = 7.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🔴 LIVE ${formatDuration(streamState.durationSeconds)}",
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xAA071529))
                        .clickable { showControls = true }
                        .padding(8.dp)
                ) {
                    Text("⚙", fontSize = 13.sp, color = Color.White)
                }
            }
        }

        // LAYER 4: FLOATING VERTICAL CAMERA ZOOM CONTROLLER (Right Side for quick thumb control)
        if (showControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xDD071529)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0x33FFFFFF))
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Current Zoom Level Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF087CFF))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = String.format(Locale.US, "%.1fx", currentZoomRatio),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        // Zoom In (+)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable {
                                    val next = (currentZoomRatio + 0.5f).coerceIn(minZoomRatio, maxZoomRatio)
                                    currentZoomRatio = next
                                    camera?.cameraControl?.setZoomRatio(next)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // Zoom Presets: 4x, 2x, 1x
                        val presets = listOf(4.0f, 2.0f, 1.0f)
                        for (preset in presets) {
                            if (preset <= maxZoomRatio) {
                                val isSelected = abs(currentZoomRatio - preset) < 0.25f
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color(0xFFFFC107) else Color(0x22FFFFFF))
                                        .clickable {
                                            val target = preset.coerceIn(minZoomRatio, maxZoomRatio)
                                            currentZoomRatio = target
                                            camera?.cameraControl?.setZoomRatio(target)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${preset.toInt()}x",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF071529) else Color.White
                                    )
                                }
                            }
                        }

                        // Zoom Out (-)
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(0x33FFFFFF))
                                .clickable {
                                    val prev = (currentZoomRatio - 0.5f).coerceIn(minZoomRatio, maxZoomRatio)
                                    currentZoomRatio = prev
                                    camera?.cameraControl?.setZoomRatio(prev)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // YOUTUBE LIVE STREAMING CONFIGURATION DIALOG
    // ==========================================
    if (showStreamSetupDialog) {
        AlertDialog(
            onDismissRequest = { showStreamSetupDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "🔴 YouTube Live Broadcast", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Broadcast your live camera feed mixed with CricHeroes live score overlay in real-time.",
                        fontSize = 12.sp,
                        color = Color(0xFFA6BAD4)
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    // YOUTUBE SIGN-IN & STUDIO CARD
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                launchYouTubeStudio(context)
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFFF0000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("▶", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Sign In with YouTube / Open Studio",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "Opens secure browser to log in & copy Stream Key",
                                    color = Color(0xFFA6BAD4),
                                    fontSize = 10.sp
                                )
                            }
                            Text("➔", color = Color(0xFF00C7FF), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (autoDetectedKeyNotice != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF065F46))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "✅ $autoDetectedKeyNotice",
                                fontSize = 11.sp,
                                color = Color(0xFF6EE7B7),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // YouTube Stream Key Input with Auto-Paste
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "YouTube Stream Key *",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF00C7FF)
                        )
                        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        val clipText = clipboardManager?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                        if (!clipText.isNullOrEmpty() && clipText != streamKey) {
                            Text(
                                text = "📋 Paste Copied Key",
                                fontSize = 10.sp,
                                color = Color(0xFF00E59B),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        streamKey = clipText
                                        streamKeyValidationError = null
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = streamKey,
                        onValueChange = {
                            streamKey = it
                            streamKeyValidationError = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. abcd-1234-wxyz-5678", fontSize = 12.sp) },
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            Text(
                                text = if (isKeyVisible) "Hide" else "Show",
                                fontSize = 11.sp,
                                color = Color(0xFF00C7FF),
                                modifier = Modifier
                                    .clickable { isKeyVisible = !isKeyVisible }
                                    .padding(8.dp)
                            )
                        },
                        isError = streamKeyValidationError != null
                    )
                    if (streamKeyValidationError != null) {
                        Text(
                            text = streamKeyValidationError!!,
                            fontSize = 11.sp,
                            color = Color(0xFFFF4E61),
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // RTMP Server URL with Presets
                    Text(
                        text = "RTMP Server Endpoint",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFA6BAD4)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val isPrimary = rtmpServerUrl.contains("a.rtmps")
                        val isBackup = rtmpServerUrl.contains("b.rtmps")
                        val isLegacy = rtmpServerUrl.startsWith("rtmp://")

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isPrimary) Color(0xFF00C7FF) else Color(0x22FFFFFF))
                                .clickable { rtmpServerUrl = "rtmps://a.rtmps.youtube.com:443/live2" }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("Primary 443", fontSize = 10.sp, color = if (isPrimary) Color(0xFF071529) else Color.White, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isBackup) Color(0xFF00C7FF) else Color(0x22FFFFFF))
                                .clickable { rtmpServerUrl = "rtmps://b.rtmps.youtube.com:443/live2?backup=1" }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("Backup 443", fontSize = 10.sp, color = if (isBackup) Color(0xFF071529) else Color.White, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isLegacy) Color(0xFF00C7FF) else Color(0x22FFFFFF))
                                .clickable { rtmpServerUrl = "rtmp://a.rtmp.youtube.com:1935/live2" }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("Legacy 1935", fontSize = 10.sp, color = if (isLegacy) Color(0xFF071529) else Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = rtmpServerUrl,
                        onValueChange = { rtmpServerUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("rtmps://a.rtmps.youtube.com:443/live2", fontSize = 12.sp) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stream Quality Selector
                    Text(
                        text = "Stream Quality / Resolution",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF00C7FF)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        QualityOptionChip(
                            label = "720p HD",
                            subtext = "2.5M",
                            isSelected = selectedQuality == "720p"
                        ) {
                            selectedQuality = "720p"
                        }
                        QualityOptionChip(
                            label = "1080p FHD",
                            subtext = "4.5M",
                            isSelected = selectedQuality == "1080p"
                        ) {
                            selectedQuality = "1080p"
                        }
                        QualityOptionChip(
                            label = "480p SD",
                            subtext = "1.2M",
                            isSelected = selectedQuality == "480p"
                        ) {
                            selectedQuality = "480p"
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Commentary Mic Audio Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Commentary Microphone",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Include commentary audio in live stream",
                                fontSize = 10.sp,
                                color = Color(0xFFA6BAD4)
                            )
                        }
                        Switch(
                            checked = isMicAudioEnabled,
                            onCheckedChange = { isMicAudioEnabled = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF00C7FF)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Instructions tip
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF071529))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = "💡 Tip: Tap 'Sign In with YouTube / Open Studio' above to log in, view live stream controls, and auto-paste your stream key.",
                            fontSize = 10.sp,
                            color = Color(0xFFA6BAD4),
                            lineHeight = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmedKey = streamKey.trim()
                        if (trimmedKey.isEmpty()) {
                            streamKeyValidationError = "Please enter your YouTube Stream Key"
                            return@Button
                        }

                        // Persist user stream settings
                        streamPrefs.edit()
                            .putString("yt_stream_key", trimmedKey)
                            .putString("yt_rtmp_url", rtmpServerUrl.trim())
                            .putString("yt_quality", selectedQuality)
                            .putBoolean("yt_audio_enabled", isMicAudioEnabled)
                            .apply()

                        showStreamSetupDialog = false

                        // Request Android MediaProjection screen capture permission
                        val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
                        mediaProjectionLauncher.launch(captureIntent)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("🔴 Start YouTube Stream", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStreamSetupDialog = false }) {
                    Text("Cancel", color = Color(0xFFA6BAD4))
                }
            },
            containerColor = Color(0xFF0B1B32)
        )
    }

    // ==========================================
    // STOP STREAM CONFIRMATION DIALOG
    // ==========================================
    if (showStopConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showStopConfirmDialog = false },
            title = {
                Text(text = "End YouTube Broadcast?", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    text = "This will disconnect the live stream from YouTube. Viewers will see the stream end.",
                    fontSize = 13.sp,
                    color = Color(0xFFA6BAD4)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showStopConfirmDialog = false
                        RtmpStreamService.stop(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                ) {
                    Text("Stop Stream", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showStopConfirmDialog = false }) {
                    Text("Keep Live", color = Color.White)
                }
            },
            containerColor = Color(0xFF0B1B32)
        )
    }
}

@Composable
fun QualityOptionChip(
    label: String,
    subtext: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (isSelected) Color(0xFF087CFF) else Color(0xFF071529))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = Color.White
            )
            Text(
                text = subtext,
                fontSize = 9.sp,
                color = if (isSelected) Color.White.copy(alpha = 0.8f) else Color(0xFFA6BAD4)
            )
        }
    }
}

fun formatDuration(seconds: Long): String {
    val hours = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        String.format("%02d:%02d:%02d", hours, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}

@Composable
fun PresetChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF10243F))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF00C7FF))
    }
}
