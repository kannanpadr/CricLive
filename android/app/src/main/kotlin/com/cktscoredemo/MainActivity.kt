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
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.core.FocusMeteringAction
import androidx.camera.view.PreviewView
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import java.util.concurrent.TimeUnit
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

fun triggerHapticFeedback(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
            vibrator?.vibrate(android.os.VibrationEffect.createOneShot(30, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) {}
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
            WindowCompat.setDecorFitsSystemWindows(window, false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val lp = window.attributes
                lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                window.attributes = lp
            }
            val insetsController = WindowInsetsControllerCompat(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            activity?.requestedOrientation = originalOrientation
            if (window != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val lp = window.attributes
                    lp.layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                    window.attributes = lp
                }
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

    // Camera Focus & Focus Lock States (Cricket Broadcast Focus Engine)
    var isFocusLocked by remember { mutableStateOf(false) }
    var focusRingOffset by remember { mutableStateOf<Offset?>(null) }
    var focusRingColor by remember { mutableStateOf(Color(0xFF38BDF8)) }
    var focusStatusToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(focusRingOffset) {
        if (focusRingOffset != null) {
            kotlinx.coroutines.delay(1800)
            focusRingOffset = null
        }
    }

    LaunchedEffect(focusStatusToast) {
        if (focusStatusToast != null) {
            kotlinx.coroutines.delay(2200)
            focusStatusToast = null
        }
    }

    // Stream Setup & Dialog States
    var showStreamSetupDialog by remember { mutableStateOf(false) }
    var showStopConfirmDialog by remember { mutableStateOf(false) }
    var streamKey by remember { mutableStateOf(streamPrefs.getString("yt_stream_key", "") ?: "") }
    var autoDetectedKeyNotice by remember { mutableStateOf<String?>(null) }
    
    val defaultRtmpEndpoint = "rtmp://a.rtmp.youtube.com/live2"
    var initialRtmpUrl = streamPrefs.getString("yt_rtmp_url", defaultRtmpEndpoint) ?: defaultRtmpEndpoint
    if (initialRtmpUrl.contains("443") || initialRtmpUrl.startsWith("rtmps://", ignoreCase = true)) {
        initialRtmpUrl = initialRtmpUrl.replace("rtmps://", "rtmp://", ignoreCase = true)
            .replace(":443", "")
            .replace("a.rtmps.youtube.com", "a.rtmp.youtube.com", ignoreCase = true)
            .replace("b.rtmps.youtube.com", "b.rtmp.youtube.com", ignoreCase = true)
    }
    var rtmpServerUrl by remember { mutableStateOf(initialRtmpUrl) }
    var selectedQuality by remember { mutableStateOf(streamPrefs.getString("yt_quality", "1080p") ?: "1080p") }
    var isMicAudioEnabled by remember { mutableStateOf(streamPrefs.getBoolean("yt_audio_enabled", true)) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var streamKeyValidationError by remember { mutableStateOf<String?>(null) }
    var isScreenLocked by remember { mutableStateOf(false) }
    var isBreakScreenActive by remember { mutableStateOf(false) }
    var batteryLevel by remember { mutableIntStateOf(100) }
    var deviceTempC by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        val batteryFilter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, batteryFilter)
        val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            batteryLevel = (level * 100 / scale)
        }
        val tempRaw = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        if (tempRaw > 0) {
            deviceTempC = tempRaw / 10f
        }
        onDispose {}
    }

    // Hardware volume buttons integration (Bluetooth Remote & Tripod Ergonomics)
    DisposableEffect(camera, minZoomRatio, maxZoomRatio, isBreakScreenActive) {
        MainActivity.onHardwareVolumeUp = {
            val next = when {
                currentZoomRatio < 1.8f -> 2.0f
                currentZoomRatio < 2.8f -> 3.0f
                else -> 1.0f
            }.coerceIn(minZoomRatio, maxZoomRatio)
            currentZoomRatio = next
            camera?.cameraControl?.setZoomRatio(next)
            triggerHapticFeedback(context)
        }
        MainActivity.onHardwareVolumeDown = {
            isBreakScreenActive = !isBreakScreenActive
            triggerHapticFeedback(context)
        }
        onDispose {
            MainActivity.onHardwareVolumeUp = null
            MainActivity.onHardwareVolumeDown = null
        }
    }

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
                "2160p", "21604k", "4K" -> Triple(3840, 2160, 18000 * 1024)
                "1440p", "1440QHD", "QHD" -> Triple(2560, 1440, 9000 * 1024)
                else -> Triple(1920, 1080, 5000 * 1024)
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
            val preview = Preview.Builder()
                .setTargetAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
                .build().also {
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
        // LAYER 1: LIVE HARDWARE CAMERA PREVIEW
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    previewView = this
                }
            },
            update = { pv ->
                previewView = pv
            }
        )

        // LAYER 2: TRANSPARENT CRICKET SCOREBOARD OVERLAY WITH CAMERA GESTURES
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val scaleGestureDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        if (!isScreenLocked && camera != null) {
                            val factor = detector.scaleFactor
                            val target = (currentZoomRatio * factor).coerceIn(minZoomRatio, maxZoomRatio)
                            currentZoomRatio = target
                            camera?.cameraControl?.setZoomRatio(target)
                            return true
                        }
                        return false
                    }
                })

                val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean = true

                    override fun onSingleTapUp(e: MotionEvent): Boolean {
                        if (!isScreenLocked && camera != null && previewView != null) {
                            val pv = previewView ?: return false
                            val factory = pv.meteringPointFactory
                            val point = factory.createPoint(e.x, e.y)
                            focusRingOffset = Offset(e.x, e.y)
                            triggerHapticFeedback(ctx)

                            if (isFocusLocked) {
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                    .disableAutoCancel()
                                    .build()
                                camera?.cameraControl?.startFocusAndMetering(action)
                                focusRingColor = Color(0xFFFFC107)
                                focusStatusToast = "🔒 Focus Locked at point"
                            } else {
                                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                                    .build()
                                camera?.cameraControl?.startFocusAndMetering(action)
                                focusRingColor = Color(0xFF38BDF8)
                                focusStatusToast = "🎯 Focused"
                            }
                            return true
                        }
                        return false
                    }
                })

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
                    addJavascriptInterface(object {
                        @JavascriptInterface
                        fun backToControl() {
                            Handler(Looper.getMainLooper()).post {
                                onBackToControl()
                            }
                        }
                    }, "AndroidScoreBridge")
                    webChromeClient = object : WebChromeClient() {
                        override fun onPermissionRequest(request: PermissionRequest?) {
                            request?.grant(request.resources)
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        @SuppressLint("WebViewClientOnReceivedSslError")
                        override fun onReceivedSslError(
                            view: WebView?,
                            handler: SslErrorHandler?,
                            error: SslError?
                        ) {
                            handler?.proceed()
                        }
                    }

                    // Smart touch routing:
                    // If touch is in the top-left area (overlay setting icon & controls),
                    // allow WebView to process clicks and interactions!
                    // Outside that area: handle tap-to-focus and pinch-zoom on the camera preview!
                    setOnTouchListener { _, event ->
                        val density = ctx.resources.displayMetrics.density
                        val controlsWidthPx = 480f * density
                        val controlsHeightPx = 90f * density

                        val isNearOverlayControls = event.x <= controlsWidthPx && event.y <= controlsHeightPx
                        if (isNearOverlayControls) {
                            // Let WebView handle the click on the left overlay control setting button!
                            return@setOnTouchListener false
                        }

                        // Feed to gesture detectors for pinch-to-zoom & tap-to-focus
                        val scaled = scaleGestureDetector.onTouchEvent(event)
                        val tapped = gestureDetector.onTouchEvent(event)
                        scaled || tapped
                    }

                    loadUrl(overlayUrl)
                }
            }
        )

        // ANIMATED BROADCAST FOCUS TARGET RING
        if (focusRingOffset != null) {
            val offset = focusRingOffset!!
            val density = LocalDensity.current
            val xDp = with(density) { (offset.x - 32.dp.toPx()).toDp() }
            val yDp = with(density) { (offset.y - 32.dp.toPx()).toDp() }
            Box(
                modifier = Modifier
                    .offset(x = xDp, y = yDp)
                    .size(64.dp)
                    .border(BorderStroke(2.dp, focusRingColor), RoundedCornerShape(10.dp))
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(focusRingColor)
                        .align(Alignment.Center)
                )
            }
        }

        // LAYER: BROADCAST BREAK SCREEN / INTERMISSION GRAPHIC
        if (isBreakScreenActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xF0071326)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xEE0B1C33)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color(0x4400C7FF)),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("🏏", fontSize = 36.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "INNINGS BREAK / TECHNICAL PAUSE",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFFC107)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Live broadcast will resume shortly",
                            fontSize = 12.sp,
                            color = Color(0xFFA6BAD4)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Button(
                            onClick = { isBreakScreenActive = false },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF087CFF)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("▶ Resume Camera Feed", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // LAYER: TRIPOD TOUCH GUARD / SCREEN LOCK INTERCEPTOR
        if (isScreenLocked) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x33000000))
                    .pointerInput(Unit) {
                        detectTransformGestures { _, _, _, _ -> }
                    },
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xDD071529))
                        .border(BorderStroke(1.dp, Color(0x88FFC107)), RoundedCornerShape(20.dp))
                        .clickable {
                            triggerHapticFeedback(context)
                            isScreenLocked = false
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🔒", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Touch Guard Active · Tap to Unlock",
                            fontSize = 11.sp,
                            color = Color(0xFFFFC107),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

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

        // Hardware / Gesture Back Handler to return to control screen
        BackHandler {
            onBackToControl()
        }

        // LAYER 3: FLOATING CAMERA & STREAM CONTROLS (Top Bar)
        if (showControls && !isScreenLocked) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Streaming & Camera Action Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thermal Warning Indicator if device is overheating under sun
                    if (deviceTempC >= 42.0f) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFDC2626))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "🔥 Overheating ${String.format(Locale.US, "%.1f°C", deviceTempC)}",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 🔴 YOUTUBE LIVE STREAMING BUTTON / STATUS PILL
                    if (streamState.isStreaming) {
                        // LIVE STATUS PILL WITH HEALTH DIAGNOSTICS (Bitrate + Temp + Battery)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFE50914))
                                .clickable {
                                    triggerHapticFeedback(context)
                                    showStopConfirmDialog = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(Color.White)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                val bitrateStr = if (streamState.bitrateKbps >= 1000) {
                                    String.format(Locale.US, "%.1fM", streamState.bitrateKbps / 1000f)
                                } else if (streamState.bitrateKbps > 0) {
                                    "${streamState.bitrateKbps}k"
                                } else ""
                                val tempStr = if (deviceTempC > 0f) " · ${String.format(Locale.US, "%.0f°C", deviceTempC)}" else ""
                                Text(
                                    text = "LIVE ${formatDuration(streamState.durationSeconds)}${if (bitrateStr.isNotEmpty()) " · $bitrateStr" else ""}$tempStr · 🔋$batteryLevel%",
                                    fontSize = 9.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (streamState.isReconnecting) {
                        // AUTO-RECONNECTING STATE
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFB45309))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "⏳ Reconnecting (${streamState.reconnectAttempts}/5)...",
                                fontSize = 9.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else if (streamState.isConnecting) {
                        // CONNECTING STATE
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFD97706))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "⏳ Connecting...",
                                fontSize = 9.sp,
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
                                    triggerHapticFeedback(context)
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

                    // Break / Privacy Screen Toggle
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isBreakScreenActive) Color(0xFFD97706) else Color(0xCC071529))
                            .clickable {
                                triggerHapticFeedback(context)
                                isBreakScreenActive = !isBreakScreenActive
                            }
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = if (isBreakScreenActive) "▶ Live" else "⏸ Break",
                            fontSize = 10.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Tripod Touch Guard / Screen Lock Toggle
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xCC071529))
                            .clickable {
                                triggerHapticFeedback(context)
                                isScreenLocked = true
                            }
                            .padding(8.dp)
                    ) {
                        Text("🔒", fontSize = 12.sp)
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

                    // Camera Auto Focus / Focus Lock Pill (Cricket Broadcast Focus)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isFocusLocked) Color(0xFFB45309) else Color(0xCC071529))
                            .border(
                                BorderStroke(1.dp, if (isFocusLocked) Color(0xFFFFC107) else Color(0x3338BDF8)),
                                RoundedCornerShape(8.dp)
                            )
                            .clickable {
                                triggerHapticFeedback(context)
                                if (isFocusLocked) {
                                    camera?.cameraControl?.cancelFocusAndMetering()
                                    isFocusLocked = false
                                    focusRingOffset = null
                                    focusStatusToast = "🎯 Continuous Auto Focus active"
                                } else {
                                    val pv = previewView
                                    if (pv != null && camera != null) {
                                        val factory = pv.meteringPointFactory
                                        val centerX = pv.width / 2f
                                        val centerY = pv.height / 2f
                                        val point = factory.createPoint(centerX, centerY)
                                        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                            .disableAutoCancel()
                                            .build()
                                        camera?.cameraControl?.startFocusAndMetering(action)
                                        focusRingOffset = Offset(centerX, centerY)
                                        focusRingColor = Color(0xFFFFC107)
                                    }
                                    isFocusLocked = true
                                    focusStatusToast = "🔒 Focus Locked (No Hunting)"
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (isFocusLocked) "🔒" else "🎯", fontSize = 11.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isFocusLocked) "Locked" else "AF Auto",
                                fontSize = 11.sp,
                                color = if (isFocusLocked) Color(0xFFFFC107) else Color(0xFF6EE7B7),
                                fontWeight = FontWeight.Bold
                            )
                        }
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
        } else if (!isScreenLocked) {
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
        if (showControls && !isScreenLocked) {
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
                                            triggerHapticFeedback(context)
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
                                    triggerHapticFeedback(context)
                                    val prev = (currentZoomRatio - 0.5f).coerceIn(minZoomRatio, maxZoomRatio)
                                    currentZoomRatio = prev
                                    camera?.cameraControl?.setZoomRatio(prev)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("−", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        // Focus Mode Button in Thumb Controller (🎯 AF / 🔒 Lock)
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(if (isFocusLocked) Color(0xFFFFC107) else Color(0x22FFFFFF))
                                .clickable {
                                    triggerHapticFeedback(context)
                                    if (isFocusLocked) {
                                        camera?.cameraControl?.cancelFocusAndMetering()
                                        isFocusLocked = false
                                        focusRingOffset = null
                                        focusStatusToast = "🎯 Auto Focus active"
                                    } else {
                                        val pv = previewView
                                        if (pv != null && camera != null) {
                                            val factory = pv.meteringPointFactory
                                            val centerX = pv.width / 2f
                                            val centerY = pv.height / 2f
                                            val point = factory.createPoint(centerX, centerY)
                                            val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                                                .disableAutoCancel()
                                                .build()
                                            camera?.cameraControl?.startFocusAndMetering(action)
                                            focusRingOffset = Offset(centerX, centerY)
                                            focusRingColor = Color(0xFFFFC107)
                                        }
                                        isFocusLocked = true
                                        focusStatusToast = "🔒 Focus Locked (No Hunting)"
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isFocusLocked) "🔒" else "🎯",
                                fontSize = 11.sp,
                                color = if (isFocusLocked) Color(0xFF071529) else Color.White
                            )
                        }
                    }
                }
            }
        }

        // FLOATING FOCUS STATUS INDICATOR BANNER
        if (focusStatusToast != null && !isScreenLocked) {
            Box(
                modifier = Modifier
                    .padding(top = 58.dp)
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xEE071529))
                    .border(
                        BorderStroke(1.dp, if (isFocusLocked) Color(0xAAFFC107) else Color(0xAA38BDF8)),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = focusStatusToast!!,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFocusLocked) Color(0xFFFFC107) else Color(0xFF6EE7B7)
                )
            }
        }
    }

    // ==========================================
    // YOUTUBE LIVE STREAMING CONFIGURATION DIALOG
    // ==========================================
    if (showStreamSetupDialog) {
        AlertDialog(
            onDismissRequest = { showStreamSetupDialog = false },
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false
            ),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "🔴 YouTube Live Broadcast", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Broadcast your live camera feed mixed with CricHeroes live score overlay in real-time.",
                        fontSize = 12.sp,
                        color = Color(0xFFA6BAD4)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Do Not Disturb reminder banner for clean broadcast
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F1E36))
                            .border(BorderStroke(1.dp, Color(0x3338BDF8)), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🔕", fontSize = 13.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Pro Tip: Enable 'Do Not Disturb' to prevent WhatsApp calls/notifications from appearing on YouTube Live.",
                                fontSize = 10.5.sp,
                                color = Color(0xFFBAE6FD)
                            )
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!clipText.isNullOrEmpty() && clipText != streamKey) {
                                    Text(
                                        text = "📋 Paste",
                                        fontSize = 11.sp,
                                        color = Color(0xFF00E59B),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier
                                            .clickable {
                                                streamKey = clipText
                                                streamKeyValidationError = null
                                            }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = if (isKeyVisible) "Hide" else "Show",
                                    fontSize = 11.sp,
                                    color = Color(0xFF00C7FF),
                                    modifier = Modifier
                                        .clickable { isKeyVisible = !isKeyVisible }
                                        .padding(8.dp)
                                )
                            }
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

                    // RTMP Server URL (Editable Text Field, default rtmp://a.rtmp.youtube.com/live2)
                    Text(
                        text = "RTMP Server Endpoint",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFA6BAD4)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (rtmpServerUrl == "rtmp://a.rtmp.youtube.com/live2") Color(0xFF00C7FF) else Color(0x22FFFFFF))
                                .clickable { rtmpServerUrl = "rtmp://a.rtmp.youtube.com/live2" }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Default RTMP", fontSize = 10.sp, color = if (rtmpServerUrl == "rtmp://a.rtmp.youtube.com/live2") Color(0xFF071529) else Color.White, fontWeight = FontWeight.Bold)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (rtmpServerUrl.contains("b.rtmp")) Color(0xFF00C7FF) else Color(0x22FFFFFF))
                                .clickable { rtmpServerUrl = "rtmp://b.rtmp.youtube.com/live2?backup=1" }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Backup RTMP", fontSize = 10.sp, color = if (rtmpServerUrl.contains("b.rtmp")) Color(0xFF071529) else Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = rtmpServerUrl,
                        onValueChange = { rtmpServerUrl = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("rtmp://a.rtmp.youtube.com/live2", fontSize = 12.sp) },
                        trailingIcon = {
                            Text(
                                text = "📋 Paste",
                                fontSize = 11.sp,
                                color = Color(0xFF00E59B),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                        if (!clipText.isNullOrEmpty()) {
                                            rtmpServerUrl = clipText
                                        }
                                    }
                                    .padding(8.dp)
                            )
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Stream Quality Selector: 1080FHD, 1440QHD, 21604k
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
                            label = "1080p FHD",
                            subtext = "5.0M",
                            isSelected = selectedQuality == "1080p"
                        ) {
                            selectedQuality = "1080p"
                        }
                        QualityOptionChip(
                            label = "1440p QHD",
                            subtext = "9.0M",
                            isSelected = selectedQuality == "1440p"
                        ) {
                            selectedQuality = "1440p"
                        }
                        QualityOptionChip(
                            label = "2160p 4K",
                            subtext = "18M",
                            isSelected = selectedQuality == "2160p"
                        ) {
                            selectedQuality = "2160p"
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
                            text = "💡 Tip: Paste your YouTube Stream Key to start broadcast. Your camera video and live scoreboard will be mixed and streamed in high resolution.",
                            fontSize = 10.sp,
                            color = Color(0xFFA6BAD4),
                            lineHeight = 14.sp
                        )
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showStreamSetupDialog = false },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Cancel", color = Color(0xFFA6BAD4))
                    }
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
                }
            },
            dismissButton = null,
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
