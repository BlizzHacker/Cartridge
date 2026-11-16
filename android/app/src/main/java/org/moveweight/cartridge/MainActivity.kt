package org.moveweight.cartridge

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Must set content view BEFORE touching WindowInsetsController, or PhoneWindow.mDecor
        // is null on Android 11+ (API 30+) and throws NullPointerException on launch.
        setContentView(R.layout.activity_main)
        hideSystemUI()

        webView = findViewById(R.id.webview)
        setupWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(function(){ if (window.GP && window.GP.triggerUI) { window.GP.triggerUI('b'); return true; } return false; })()"
                ) { handled ->
                    if (handled != "true" && webView.canGoBack()) {
                        webView.goBack()
                    }
                }
            }
        })

        val prefs = getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
        val startUrl = prefs.getString("server_url", null)?.takeIf { it.isNotBlank() }
            ?: "file:///android_asset/web/index.html"

        webView.loadUrl(startUrl)
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        if (::webView.isInitialized) {
            webView.onResume()
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
                // Never crash if an OEM window manager rejects immersive flags during transition
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

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.evaluateJavascript(
                    "window.__CARTRIDGE_PLATFORM = 'android'; window.__CARTRIDGE_NATIVE = true; window.__CARTRIDGE_VERSION = '0.31.1'; " +
                    "if (document.title === 'Cartridge for Xbox') { document.title = 'Cartridge'; " +
                    "const h1 = document.querySelector('#view-setup h1'); if (h1) h1.textContent = 'Cartridge'; }",
                    null
                )
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
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        val rx = event.getAxisValue(MotionEvent.AXIS_Z)
        val ry = event.getAxisValue(MotionEvent.AXIS_RZ)
        val lt = event.getAxisValue(MotionEvent.AXIS_LTRIGGER)
        val rt = event.getAxisValue(MotionEvent.AXIS_RTRIGGER)

        if (::webView.isInitialized) {
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
        fun getVersion(): String = "0.31.1"

        @JavascriptInterface
        fun setInsecureCerts(enabled: Boolean) {
            val prefs = getSharedPreferences("cartridge_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("insecure_certs", enabled).apply()
        }

        @JavascriptInterface
        fun exitApp() {
            runOnUiThread { finish() }
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
