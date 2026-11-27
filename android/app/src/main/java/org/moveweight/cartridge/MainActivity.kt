package org.moveweight.cartridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.moveweight.cartridge.data.RommApiClient
import org.moveweight.cartridge.model.RomItem
import org.moveweight.cartridge.model.ServerConfig
import org.moveweight.cartridge.model.ServerProfile
import org.moveweight.cartridge.ui.CartridgeLauncherApp

class MainActivity : AppCompatActivity() {

    private lateinit var composeView: ComposeView
    private lateinit var webView: WebView
    private var isInWebPlayer: Boolean = false
    private val _gamepadEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val gamepadEvents = _gamepadEvents.asSharedFlow()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyProvisioningIntent(intent)

        // Must set content view BEFORE touching WindowInsetsController, or PhoneWindow.mDecor
        // is null on Android 11+ (API 30+) and throws NullPointerException on launch.
        setContentView(R.layout.activity_main)

        composeView = findViewById(R.id.compose_view)
        webView = findViewById(R.id.webview)
        setupWebView()

        composeView.setContent {
            CartridgeLauncherApp(
                gamepadEvents = gamepadEvents,
                onOpenBuiltInWebPlayer = { config, rom ->
                    openBuiltInWebPlayer(config, rom)
                },
                onOpenStreamPlayer = { config, rom ->
                    openStreamPlayer(config, rom)
                }
            )
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isInWebPlayer) {
                    closeBuiltInWebPlayer()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        applyProvisioningIntent(intent)
    }

    /**
     * Allows zero-touch provisioning of local/remote RomM & ROMarr servers via ADB intent extras.
     */
    private fun applyProvisioningIntent(intent: Intent?) {
        intent ?: return
        val localUrl = intent.getStringExtra("local_url")?.trim()?.trimEnd('/') ?: ""
        val remoteUrl = intent.getStringExtra("remote_url")?.trim()?.trimEnd('/') ?: ""
        val serverUrl = intent.getStringExtra("server_url")?.trim()?.trimEnd('/') ?: ""
        val streamUrl = intent.getStringExtra("stream_url")?.trim()?.trimEnd('/') ?: ""
        val token = intent.getStringExtra("auth_token")?.trim() ?: ""
        val basicAuth = intent.getStringExtra("basic_auth")?.trim() ?: ""
        val username = intent.getStringExtra("username")?.trim() ?: ""
        val serverName = intent.getStringExtra("server_name")?.trim() ?: ""
        val profilesJson = intent.getStringExtra("profiles_json")?.trim() ?: ""

        if (profilesJson.isNotBlank()) {
            val prefs = getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("server_profiles_json", profilesJson).apply()
        }

        if (localUrl.isNotBlank() || remoteUrl.isNotBlank() || serverUrl.isNotBlank() || streamUrl.isNotBlank()) {
            val current = RommApiClient.loadSavedConfig(this)
            val updated = current.copy(
                url = serverUrl.ifBlank { localUrl }.ifBlank { remoteUrl }.ifBlank { current.url },
                localUrl = localUrl.ifBlank { current.localUrl },
                remoteUrl = remoteUrl.ifBlank { current.remoteUrl },
                streamUrl = streamUrl.ifBlank { current.streamUrl },
                activeServerName = serverName.ifBlank { current.activeServerName },
                token = token.ifBlank { current.token },
                basicAuth = basicAuth.ifBlank { current.basicAuth },
                username = username.ifBlank { current.username },
                authMode = when {
                    token.isNotBlank() -> "token"
                    basicAuth.isNotBlank() -> "password"
                    else -> current.authMode
                }
            )
            RommApiClient.saveConfig(this, updated)
            RommApiClient.upsertProfileFromConfig(this, updated)
        }
    }

    private fun openStreamPlayer(config: ServerConfig, rom: RomItem) {
        isInWebPlayer = true
        composeView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        hideSystemUI()
        val streamBase = config.effectiveStreamUrl
        val qStream = Uri.encode(streamBase)
        val qPlat = Uri.encode(rom.platformSlug)
        val qRom = Uri.encode(rom.fileName)
        val qTitle = Uri.encode(rom.name)
        webView.loadUrl("file:///android_asset/stream_player.html?stream=$qStream&platform=$qPlat&rom=$qRom&title=$qTitle")
    }

    private fun openBuiltInWebPlayer(config: ServerConfig, rom: RomItem?) {
        isInWebPlayer = true
        composeView.visibility = View.GONE
        webView.visibility = View.VISIBLE
        hideSystemUI()
        val baseUrl = config.effectiveUrl
        val targetUrl = when {
            baseUrl.isNotBlank() && rom != null -> "$baseUrl/rom/${rom.id}"
            baseUrl.isNotBlank() -> baseUrl
            else -> "file:///android_asset/web/index.html"
        }
        webView.loadUrl(targetUrl)
    }

    private fun closeBuiltInWebPlayer() {
        isInWebPlayer = false
        webView.loadUrl("about:blank")
        webView.visibility = View.GONE
        composeView.visibility = View.VISIBLE
        showSystemUI()
    }

    override fun onResume() {
        super.onResume()
        if (isInWebPlayer) {
            hideSystemUI()
            if (::webView.isInitialized) {
                webView.onResume()
            }
        }
    }

    override fun onPause() {
        if (::webView.isInitialized) {
            webView.onPause()
        }
        super.onPause()
    }

    private fun hideSystemUI() {
        window?.decorView?.post {
            try {
                WindowCompat.setDecorFitsSystemWindows(window, false)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } catch (_: Throwable) {
            }
        }
    }

    private fun showSystemUI() {
        window?.decorView?.post {
            try {
                WindowCompat.setDecorFitsSystemWindows(window, true)
                val controller = WindowInsetsControllerCompat(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            } catch (_: Throwable) {
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true

        webView.addJavascriptInterface(CartridgeNativeBridge(), "CartridgeNative")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return false
                if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file://")) {
                    return false
                }
                if (url.startsWith("intent://") || url.startsWith("cartridge://")) {
                    handleCustomIntent(url)
                    return true
                }
                return false
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                val prefs = getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
                if (prefs.getBoolean("insecure_certs", false)) {
                    handler?.proceed()
                } else {
                    super.onReceivedSslError(view, handler, error)
                }
            }
        }

        webView.webChromeClient = WebChromeClient()
    }

    private fun handleCustomIntent(url: String) {
        try {
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Could not open external player: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode
        val action = event.action
        val jsKey = mapKeyCodeToJsKey(keyCode)
        if (jsKey != null && (action == KeyEvent.ACTION_DOWN || action == KeyEvent.ACTION_UP)) {
            val isDown = (action == KeyEvent.ACTION_DOWN)
            if (!isInWebPlayer) {
                if (isDown && event.repeatCount == 0 && jsKey in setOf("l1", "r1", "l2", "r2", "x", "y")) {
                    _gamepadEvents.tryEmit(jsKey)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
            if (isDown && event.repeatCount == 0) {
                webView.evaluateJavascript(
                    "if (window.GP && window.GP.triggerUI) window.GP.triggerUI('$jsKey');",
                    null
                )
            }
            webView.evaluateJavascript(
                "if (window.dispatchEvent) { window.dispatchEvent(new CustomEvent('cartridge_gamepad_key', { detail: { key: '$jsKey', down: $isDown } })); }",
                null
            )
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (isInWebPlayer && ::webView.isInitialized) {
            val lx = event.getAxisValue(MotionEvent.AXIS_X)
            val ly = event.getAxisValue(MotionEvent.AXIS_Y)
            val rx = event.getAxisValue(MotionEvent.AXIS_Z)
            val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
            val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
            val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)

            webView.evaluateJavascript(
                "if (window.dispatchEvent) { window.dispatchEvent(new CustomEvent('cartridge_gamepad_axes', { detail: { lx: $lx, ly: $ly, rx: $rx, ry: $ry, lt: $lt, rt: $rt } })); }",
                null
            )
        }
        return super.dispatchGenericMotionEvent(event)
    }

    private fun mapKeyCodeToJsKey(keyCode: Int): String? {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "a"
            KeyEvent.KEYCODE_BUTTON_B -> "b"
            KeyEvent.KEYCODE_BUTTON_X -> "x"
            KeyEvent.KEYCODE_BUTTON_Y -> "y"
            KeyEvent.KEYCODE_BUTTON_L1 -> "l1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "r1"
            KeyEvent.KEYCODE_BUTTON_L2 -> "l2"
            KeyEvent.KEYCODE_BUTTON_R2 -> "r2"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "l3"
            KeyEvent.KEYCODE_BUTTON_THUMBR -> "r3"
            KeyEvent.KEYCODE_BUTTON_START -> "start"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "select"
            KeyEvent.KEYCODE_DPAD_UP -> "up"
            KeyEvent.KEYCODE_DPAD_DOWN -> "down"
            KeyEvent.KEYCODE_DPAD_LEFT -> "left"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
            else -> null
        }
    }

    inner class CartridgeNativeBridge {
        @JavascriptInterface
        fun getPlatform(): String = "android"

        @JavascriptInterface
        fun getVersion(): String = "0.33.0"

        @JavascriptInterface
        fun setInsecureCerts(enabled: Boolean) {
            val prefs = getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("insecure_certs", enabled).apply()
        }

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread {
                if (isInWebPlayer) {
                    closeBuiltInWebPlayer()
                } else {
                    finish()
                }
            }
        }

        @JavascriptInterface
        fun launchExternalEmulator(packageName: String, uriString: String, mimeType: String?): Boolean {
            return try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(uriString), mimeType ?: "application/octet-stream")
                    setPackage(packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(intent)
                true
            } catch (_: Exception) {
                false
            }
        }

        @JavascriptInterface
        fun saveServerUrl(url: String) {
            val prefs = getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
            prefs.edit().putString("server_url", url).apply()
        }
    }
}
