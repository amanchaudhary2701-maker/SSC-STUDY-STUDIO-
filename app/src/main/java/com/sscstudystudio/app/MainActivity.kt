package com.sscstudystudio.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        setContentView(webView)

        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false

        webView.webViewClient = WebViewClient()
        webView.webChromeClient = WebChromeClient()
        webView.addJavascriptInterface(TimerBridge(), "AndroidTimerBridge")
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    inner class TimerBridge {
        @JavascriptInterface
        fun startFloatingDot(seconds: Int, subject: String, targetName: String) {
            runOnUiThread {
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(
                        this@MainActivity,
                        "Floating Dot ke liye 'Display over other apps' ON karein",
                        Toast.LENGTH_LONG
                    ).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                    return@runOnUiThread
                }

                val svc = Intent(this@MainActivity, FloatingTimerService::class.java).apply {
                    action = FloatingTimerService.ACTION_START
                    putExtra(FloatingTimerService.EXTRA_SECONDS, if (seconds > 0) seconds else 3600)
                    putExtra(FloatingTimerService.EXTRA_SUBJECT, subject)
                    putExtra(FloatingTimerService.EXTRA_TARGET, targetName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(svc)
                } else {
                    startService(svc)
                }
                Toast.makeText(
                    this@MainActivity,
                    "Floating Dot ON! Tap karke Timer / Transparent Dot toggle karein.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        @JavascriptInterface
        fun stopFloatingDot() {
            runOnUiThread {
                stopService(Intent(this@MainActivity, FloatingTimerService::class.java))
            }
        }
    }
}
