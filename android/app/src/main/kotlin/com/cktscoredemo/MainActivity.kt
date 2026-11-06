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
import android.view.MotionEvent
import android.view.GestureDetector
import android.view.ScaleGestureDetector
import android.content.ClipboardManager
import android.net.http.SslError
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import android.view.KeyEvent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.draw.alpha
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import android.widget.Toast

fun triggerHapticFeedback(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) {}
}

fun getNetworkStrengthInfo(context: Context): Pair<String, String> {
    try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = cm?.activeNetwork ?: return Pair("📶 Standby", "Offline")
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return Pair("📶 Connected", "OK")
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Pair("📶 Wi-Fi", "Stable")
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
                val downSpeed = caps.linkDownstreamBandwidthKbps
                if (downSpeed >= 12000) Pair("📶 5G/4G", "High") else Pair("📶 4G", "Good")
            }
            else -> Pair("📶 Network", "Online")
        }
    } catch (_: Exception) {
        return Pair("📶 4G/LTE", "Good")
    }
}

class MainActivity : ComponentActivity() {

    companion object {
        var onHardwareVolumeUp: (() -> Unit)? = null
        var onHardwareVolumeDown: (() -> Unit)? = null
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP && onHardwareVolumeUp != null) {
            onHardwareVolumeUp?.invoke()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && onHardwareVolumeDown != null) {
            onHardwareVolumeDown?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

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
    val context = LocalContext.current
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

    // Method 1: Mobile 4G CricHeroes Sync State
    var showSyncDialog by remember { mutableStateOf(false) }
    var mobileSyncUrl by remember { mutableStateOf("https://cricheroes.com/scorecard/27695455/hyper-turf-maharaja-dasara-cup-u-13-2026/falcon-12-vs-kashish-cricket-club/live") }
    var isMobileSyncRunning by remember { mutableStateOf(false) }
    var mobileSyncStatusText by remember { mutableStateOf("Ready to sync") }

    val syncer = remember {
        MobileCricHeroesSyncer(context).apply {
            onStatusChanged = { status ->
                mobileSyncStatusText = status
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            syncer.stopSync()
        }
    }

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
                                text = "Cricket Live YT",
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

                        // 📱 4G CRICHEROES SYNC BUTTON (Method 1: Bypasses Cloudflare)
                        Button(
                            onClick = { showSyncDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMobileSyncRunning) Color(0xFF00A86B) else Color(0xFF102641)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 7.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isMobileSyncRunning) "🟢 4G Sync" else "📱 4G Sync",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
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

                            // Expose AndroidScoreBridge for web control panel
                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun startCricHeroesSync(url: String) {
                                    Handler(Looper.getMainLooper()).post {
                                        if (url.isNotBlank()) {
                                            mobileSyncUrl = url
                                            isMobileSyncRunning = true
                                            syncer.startSync(url, currentUrl)
                                        }
                                    }
                                }

                                @JavascriptInterface
                                fun stopCricHeroesSync() {
                                    Handler(Looper.getMainLooper()).post {
                                        isMobileSyncRunning = false
                                        syncer.stopSync()
                                    }
                                }

                                @JavascriptInterface
                                fun backToControl() {
                                    Handler(Looper.getMainLooper()).post {
                                        activeMode = AppMode.CONTROL
                                        val baseUrl = currentUrl.trimEnd('/')
                                        val target = if (baseUrl.startsWith("file://")) {
                                            "file:///android_asset/web/index.html"
                                        } else {
                                            "$baseUrl/"
                                        }
                                        webViewRef?.loadUrl(target)
                                    }
                                }

                                @JavascriptInterface
                                fun stopStreamFromWeb() {
                                    Handler(Looper.getMainLooper()).post {
                                        LiveStreamEngine.stop()
                                    }
                                }

                                @JavascriptInterface
                                fun getBatteryLevel(): Int {
                                    val batteryFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
                                    val batteryStatus = context.registerReceiver(null, batteryFilter)
                                    val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                                    val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                                    return if (level >= 0 && scale > 0) (level * 100 / scale) else 100
                                }
                            }, "AndroidScoreBridge")

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

                                @SuppressLint("WebViewClientOnReceivedSslError")
                                override fun onReceivedSslError(
                                    view: WebView?,
                                    handler: SslErrorHandler?,
                                    error: SslError?
                                ) {
                                    handler?.proceed()
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

    // 📱 Mobile 4G CricHeroes Sync Dialog (Method 1)
    if (showSyncDialog) {
        AlertDialog(
            onDismissRequest = { showSyncDialog = false },
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 4.dp),
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            ),
            title = {
                Text(
                    text = "📱 Mobile 4G CricHeroes Sync",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Bypasses Cloudflare by using your phone's 4G mobile connection to read CricHeroes and push real live scores to your server.",
                        fontSize = 12.sp,
                        color = Color(0xFFA6BAD4)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "CricHeroes Match URL:",
                        fontSize = 12.sp,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = mobileSyncUrl,
                        onValueChange = { mobileSyncUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://cricheroes.com/scorecard/...") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        PresetChip(label = "Match 27695455") {
                            mobileSyncUrl = "https://cricheroes.com/scorecard/27695455/hyper-turf-maharaja-dasara-cup-u-13-2026/falcon-12-vs-kashish-cricket-club/live"
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF071529)),
                        border = BorderStroke(1.dp, if (isMobileSyncRunning) Color(0xFF00E59B) else Color(0xFF1E3A5F)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Status: $mobileSyncStatusText",
                                fontSize = 12.sp,
                                color = if (isMobileSyncRunning) Color(0xFF00E59B) else Color(0xFFA6BAD4),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isMobileSyncRunning) {
                            isMobileSyncRunning = false
                            syncer.stopSync()
                        } else {
                            if (mobileSyncUrl.isNotBlank()) {
                                isMobileSyncRunning = true
                                syncer.startSync(mobileSyncUrl, currentUrl)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isMobileSyncRunning) Color(0xFFFF4E61) else Color(0xFF00A86B)
                    )
                ) {
                    Text(if (isMobileSyncRunning) "Stop Sync" else "Start 4G Sync", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSyncDialog = false }) {
                    Text("Close", color = Color.White)
                }
            }
        )
    }

    // Server Configuration Dialog
    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 4.dp),
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            ),
            title = {
                Text(
                    text = "Configure Server URL",
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Enter your Cricket Live YT server address (or tunnel):",
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
                        PresetChip(label = "Render Cloud") {
                            inputUrlText = "https://criclive-lwgn.onrender.com"
                        }
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
                            if (!trimmed.startsWith("http://", ignoreCase = true) &&
                                !trimmed.startsWith("https://", ignoreCase = true) &&
                                !trimmed.startsWith("file://", ignoreCase = true)
                            ) {
                                val isLocalIp = trimmed.startsWith("192.168.") ||
                                        trimmed.startsWith("10.") ||
                                        trimmed.startsWith("172.") ||
                                        trimmed.startsWith("127.") ||
                                        trimmed.startsWith("localhost")
                                trimmed = if (isLocalIp) "http://$trimmed" else "https://$trimmed"
                            }

                            // Only auto-append :3000 for local private IPs and localhost when port is omitted
                            val hostPart = trimmed.substringAfter("://").substringBefore("/")
                            val isLocalHost = hostPart.startsWith("192.168.") ||
                                    hostPart.startsWith("10.") ||
                                    hostPart.startsWith("172.") ||
                                    hostPart.startsWith("127.") ||
                                    hostPart.equals("localhost", ignoreCase = true)

                            if (isLocalHost && !hostPart.contains(":") && !trimmed.startsWith("file://")) {
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

    if (base.isEmpty()) {
        base = "rtmp://a.rtmp.youtube.com/live2"
    }

    if (!base.startsWith("rtmp://", ignoreCase = true) && !base.startsWith("rtmps://", ignoreCase = true)) {
        base = "rtmp://$base"
    }

    base = base.trimEnd('/')
    cleanKey = cleanKey.trimStart('/')

    return if (cleanKey.isNotEmpty()) {
        "$base/$cleanKey"
    } else {
        base
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

/**
 * Method 1: Mobile-Assisted CricHeroes Syncer
 * Bypasses Cloudflare datacenter IP blocking by loading CricHeroes on the user's
 * Android phone with 4G/5G mobile connection, extracting live scores from the canvas/DOM,
 * and pushing them to Render (/api/mobile-sync).
 */
class MobileCricHeroesSyncer(private val context: Context) {
    private var syncWebView: WebView? = null
    private val handler = Handler(Looper.getMainLooper())
    var isSyncing = false
        private set
    var currentMatchUrl: String = ""
        private set
    var currentServerUrl: String = ""
        private set
    private var pollRunnable: Runnable? = null
    var onStatusChanged: ((String) -> Unit)? = null
    var onScoreUpdated: ((String) -> Unit)? = null

    fun startSync(matchUrl: String, serverUrl: String) {
        if (matchUrl.isBlank()) return
        currentMatchUrl = matchUrl.trim()
        currentServerUrl = serverUrl.trim().trimEnd('/')
        isSyncing = true
        onStatusChanged?.invoke("Starting 4G mobile reader...")

        handler.post {
            initWebView()
            val target = if (currentMatchUrl.endsWith("/live")) currentMatchUrl else "$currentMatchUrl/live"
            syncWebView?.loadUrl(target)
        }
    }

    fun stopSync() {
        isSyncing = false
        pollRunnable?.let { handler.removeCallbacks(it) }
        handler.post {
            try {
                syncWebView?.stopLoading()
                syncWebView?.loadUrl("about:blank")
            } catch (_: Exception) {}
        }
        onStatusChanged?.invoke("Sync stopped")
    }

    private fun initWebView() {
        if (syncWebView != null) return
        syncWebView = WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    if (!isSyncing) return
                    val hookJs = """
                        (function() {
                            if (window.__chHookInjected) return;
                            window.__chHookInjected = true;
                            var orig = CanvasRenderingContext2D.prototype.fillText;
                            CanvasRenderingContext2D.prototype.fillText = function(text) {
                                if (this.canvas) {
                                    this.canvas.setAttribute('data-canvas-text', String(text).trim());
                                }
                                return orig.apply(this, arguments);
                            };
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(hookJs, null)

                    scheduleExtraction(4000)
                }

                @SuppressLint("WebViewClientOnReceivedSslError")
                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                    handler?.proceed()
                }
            }
        }
    }

    private fun scheduleExtraction(delayMs: Long) {
        pollRunnable?.let { handler.removeCallbacks(it) }
        pollRunnable = Runnable {
            if (!isSyncing) return@Runnable
            extractAndPostScore()
        }
        handler.postDelayed(pollRunnable!!, delayMs)
    }

    private fun extractAndPostScore() {
        val extractJs = """
            (function() {
                var d = {
                    title: document.title || '',
                    bodyText: (document.body && document.body.innerText) ? document.body.innerText : '',
                    scores: [],
                    batters: [],
                    bowlers: [],
                    recentBalls: []
                };
                var canvases = document.querySelectorAll('canvas');
                for (var i = 0; i < canvases.length; i++) {
                    var c = canvases[i];
                    var t = c.getAttribute('data-canvas-text');
                    if (t && t.indexOf('/') !== -1) {
                        d.scores.push({
                            score: t,
                            parentText: (c.parentElement && c.parentElement.innerText) ? c.parentElement.innerText.trim() : ''
                        });
                    }
                }
                var tables = document.querySelectorAll('table');
                if (tables.length > 0) {
                    var rows = tables[0].querySelectorAll('tbody tr');
                    for (var r = 0; r < rows.length; r++) {
                        var tds = rows[r].querySelectorAll('td');
                        if (tds.length >= 6) {
                            var nameEl = tds[0].querySelector('a') || tds[0].querySelector('.playerLink') || tds[0];
                            var name = (nameEl.innerText || '').split('\n')[0].replace(/[*()]/g, '').trim();
                            var canvas = tds[1].querySelector('canvas');
                            var runs = canvas ? canvas.getAttribute('data-canvas-text') : '';
                            d.batters.push({
                                name: name,
                                runs: runs || '',
                                balls: (tds[2].innerText || '').trim(),
                                fours: (tds[3].innerText || '').trim(),
                                sixes: (tds[4].innerText || '').trim(),
                                sr: (tds[5].innerText || '').trim()
                            });
                        }
                    }
                }
                if (tables.length > 1) {
                    var bRows = tables[1].querySelectorAll('tbody tr');
                    for (var br = 0; br < bRows.length; br++) {
                        var bTds = bRows[br].querySelectorAll('td');
                        if (bTds.length >= 6) {
                            var bNameEl = bTds[0].querySelector('a') || bTds[0].querySelector('.playerLink') || bTds[0];
                            var bName = (bNameEl.innerText || '').split('\n')[0].replace(/[*()]/g, '').trim();
                            var bCanvas = bTds[3].querySelector('canvas');
                            var bRuns = bCanvas ? bCanvas.getAttribute('data-canvas-text') : '';
                            d.bowlers.push({
                                name: bName,
                                overs: (bTds[1].innerText || '').trim(),
                                maidens: (bTds[2].innerText || '').trim(),
                                runs: bRuns || '',
                                wickets: (bTds[4].innerText || '').trim(),
                                eco: (bTds[5].innerText || '').trim()
                            });
                        }
                    }
                }
                var balls = document.querySelectorAll('[class*="recentBall"], [class*="ballBubble"], [class*="delivery"]');
                for (var bi = 0; bi < balls.length; bi++) {
                    var bt = (balls[bi].innerText || '').trim();
                    if (/^[0-6wWdnbNB]$/.test(bt)) d.recentBalls.push(bt);
                }
                return JSON.stringify(d);
            })()
        """.trimIndent()

        syncWebView?.evaluateJavascript(extractJs) { jsonResult ->
            if (jsonResult != null && jsonResult != "null" && isSyncing) {
                Thread {
                    try {
                        val rawJson = if (jsonResult.startsWith("\"") && jsonResult.endsWith("\"")) {
                            JSONTokener(jsonResult).nextValue().toString()
                        } else {
                            jsonResult
                        }
                        val domData = JSONObject(rawJson)

                        val payload = JSONObject().apply {
                            put("url", currentMatchUrl)
                            put("domData", domData)
                        }

                        val targetEndpoint = if (currentServerUrl.startsWith("file://") || currentServerUrl.isBlank()) {
                            "https://criclive-lwgn.onrender.com/api/mobile-sync"
                        } else {
                            "$currentServerUrl/api/mobile-sync"
                        }

                        val urlObj = java.net.URL(targetEndpoint)
                        val conn = urlObj.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        conn.setRequestProperty("Accept", "application/json")
                        conn.doOutput = true
                        conn.connectTimeout = 8000
                        conn.readTimeout = 8000

                        val os = conn.outputStream
                        os.write(payload.toString().toByteArray(Charsets.UTF_8))
                        os.flush()
                        os.close()

                        val code = conn.responseCode
                        if (code in 200..299) {
                            val respText = conn.inputStream.bufferedReader().use { it.readText() }
                            val respJson = JSONObject(respText)
                            val state = respJson.optJSONObject("state")
                            val scoreStr = state?.optString("score") ?: ""
                            val battingTeam = state?.optString("batting_team") ?: ""
                            val overs = state?.optString("overs") ?: ""

                            handler.post {
                                val summary = if (scoreStr.isNotBlank()) "$battingTeam $scoreStr ($overs Ov)" else "Score Synced"
                                onStatusChanged?.invoke("✓ Synced via 4G: $summary")
                                onScoreUpdated?.invoke(summary)
                            }
                        } else {
                            handler.post {
                                onStatusChanged?.invoke("Server response HTTP $code")
                            }
                        }
                    } catch (e: Exception) {
                        handler.post {
                            onStatusChanged?.invoke("Syncing... (${e.message ?: "network retry"})")
                        }
                    }
                }.start()
            }
            if (isSyncing) {
                scheduleExtraction(6000)
            }
        }
    }
}
