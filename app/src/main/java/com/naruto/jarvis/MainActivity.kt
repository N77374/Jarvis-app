package com.naruto.jarvis

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.naruto.jarvis.accessibility.JarvisAccessibilityService
import com.naruto.jarvis.core.*

class MainActivity : ComponentActivity(), JarvisStateListener, VoiceCommandPipeline.Listener {

    private lateinit var webView: WebView

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* mic just won't work until granted — no special handling needed */ }

    // Names you actually use -> real package IDs. Extend as needed.
    private val appPackages = mapOf(
        "whatsapp" to "com.whatsapp",
        "youtube" to "com.google.android.youtube",
        "maps" to "com.google.android.apps.maps",
        "gmail" to "com.google.android.gm",
        "camera" to "com.android.camera"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TtsEngine.init(this)
        JarvisStateManager.addListener(this)
        VoiceCommandPipeline.addListener(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true // required for the app's existing localStorage settings
            addJavascriptInterface(JarvisJsBridge(this@MainActivity), "AndroidBridge")
            loadUrl("file:///android_asset/www/index.html")
        }
        setContentView(webView)

        requestRuntimePermissions()
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        requestPermissions.launch(perms.toTypedArray())
    }

    // ---------- called by JarvisJsBridge (always already on UI thread here) ----------

    fun setWakeServiceEnabled(enabled: Boolean) {
        val intent = Intent(this, WakeWordService::class.java)
        if (enabled) startForegroundService(intent) else stopService(intent)
    }

    /** Returns true immediately if recording actually started (mic access ok). */
    fun startNativeRecording(): Boolean = VoiceCommandPipeline.startManual(this)

    fun stopNativeRecording() {
        VoiceCommandPipeline.stopManual()
    }

    fun speakNative(text: String) {
        TtsEngine.onDone = {
            TtsEngine.onDone = null
            runOnUiThread { webView.evaluateJavascript("window.onNativeSpeechDone && window.onNativeSpeechDone();", null) }
        }
        TtsEngine.speak(text)
    }

    fun launchAppByName(name: String): Boolean {
        val pkg = appPackages[name.lowercase()] ?: return false
        val service = JarvisAccessibilityService.instance
        return if (service != null) {
            service.launchApp(pkg)
        } else {
            // Accessibility not enabled yet — fall back to a plain launcher intent,
            // which doesn't need the accessibility permission at all.
            val launchIntent = packageManager.getLaunchIntentForPackage(pkg) ?: return false
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launchIntent)
            true
        }
    }

    fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun openOverlaySettings() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    fun showElementGrid() {
        val service = JarvisAccessibilityService.instance ?: return
        com.naruto.jarvis.accessibility.GridOverlayHolder.instance(this).show(service)
    }

    fun hideElementGrid() {
        com.naruto.jarvis.accessibility.GridOverlayHolder.instance(this).hide()
    }

    // ---------- JarvisStateListener: relay state changes into the web UI ----------

    override fun onStateChanged(newState: JarvisState) {
        runOnUiThread {
            val stateStr = if (newState == JarvisState.ACTIVE) "ACTIVE" else "SLEEP"
            webView.evaluateJavascript(
                "window.onNativeStateChange && window.onNativeStateChange('$stateStr');", null
            )
        }
    }

    // ---------- VoiceCommandPipeline.Listener: relay transcripts into the web UI ----------

    override fun onTranscript(text: String) {
        runOnUiThread {
            val escaped = org.json.JSONObject.quote(text) // safe JS string literal
            webView.evaluateJavascript(
                "window.onNativeTranscript && window.onNativeTranscript($escaped);", null
            )
        }
    }

    override fun onNoSpeechDetected() {
        runOnUiThread {
            webView.evaluateJavascript(
                "window.onNativeNoSpeech && window.onNativeNoSpeech();", null
            )
        }
    }

    override fun onDestroy() {
        TtsEngine.shutdown()
        super.onDestroy()
    }
}
