package com.naruto.jarvis

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.naruto.jarvis.accessibility.GridOverlayHolder
import com.naruto.jarvis.accessibility.JarvisAccessibilityService
import com.naruto.jarvis.core.*

class MainActivity : ComponentActivity(), JarvisStateListener, VoiceCommandPipeline.Listener {

    private lateinit var webView: WebView

    // ---------- silence-based auto-stop ----------
    private var silenceHandler: Handler? = null
    private var hasSpokenThisTurn = false
    private var silentMsAccum = 0
    private var totalMsAccum = 0
    private val POLL_MS = 200
    private val SPEECH_THRESHOLD = 2500     // mic amplitude above this counts as "talking"
    private val SILENCE_DURATION_MS = 1200  // how long of silence (after speech) before auto-stopping
    private val MAX_RECORD_MS = 15000       // hard cap regardless of silence detection

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* mic just won't work until granted — no special handling needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        TtsEngine.init(this)
        JarvisStateManager.addListener(this)
        VoiceCommandPipeline.addListener(this)

        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            addJavascriptInterface(JarvisJsBridge(this@MainActivity), "AndroidBridge")
            loadUrl("file:///android_asset/www/index.html")
        }
        setContentView(webView)

        requestRuntimePermissions()

        // Bubble on/off state is now native-authoritative (the bubble itself can turn
        // itself off via its ✕ button), so restore it here rather than trusting only
        // whatever JS had cached from localStorage last time.
        val bubbleWasOn = getSharedPreferences("jarvis_prefs", MODE_PRIVATE)
            .getBoolean("bubble_enabled", false)
        if (bubbleWasOn) setFloatingBubbleEnabled(true)
    }

    private fun requestRuntimePermissions() {
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        requestPermissions.launch(perms.toTypedArray())
    }

    // ---------- called by JarvisJsBridge ----------

    fun setWakeServiceEnabled(enabled: Boolean) {
        val intent = Intent(this, WakeWordService::class.java)
        if (enabled) startForegroundService(intent) else stopService(intent)
    }

    fun setFloatingBubbleEnabled(enabled: Boolean) {
        val intent = Intent(this, FloatingBubbleService::class.java)
        if (enabled) startForegroundService(intent) else stopService(intent)
        getSharedPreferences("jarvis_prefs", MODE_PRIVATE).edit()
            .putBoolean("bubble_enabled", enabled).apply()
    }

    fun saveCityForNativeUse(city: String) {
        getSharedPreferences("jarvis_prefs", MODE_PRIVATE).edit().putString("city", city).apply()
    }

    fun routeThroughNativeRouter(text: String) {
        NativeCommandRouter.route(this, text) { narration ->
            runOnUiThread {
                val escaped = org.json.JSONObject.quote(narration)
                webView.evaluateJavascript("window.onNativeNarration && window.onNativeNarration($escaped);", null)
            }
        }
    }

    /** Starts recording AND begins watching for silence to auto-stop it. */
    fun startNativeRecording(): Boolean {
        val started = VoiceCommandPipeline.startManual(this)
        if (started) startSilenceWatch()
        return started
    }

    /** Manual stop (user tapped Stop) — routes through the same path as auto-stop. */
    fun stopNativeRecording() {
        finishRecording()
    }

    fun speakNative(text: String) {
        TtsEngine.onDone = {
            TtsEngine.onDone = null
            runOnUiThread { webView.evaluateJavascript("window.onNativeSpeechDone && window.onNativeSpeechDone();", null) }
        }
        TtsEngine.speak(text)
    }

    /** Uses AppRegistry to find any installed app by spoken name — no pre-registration needed. */
    fun launchAppByName(name: String): Boolean {
        val pkg = AppRegistry.findPackage(this, name) ?: return false
        val service = JarvisAccessibilityService.instance
        return if (service != null) {
            service.launchApp(pkg)
        } else {
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
        GridOverlayHolder.instance(this).show(service)
    }

    fun hideElementGrid() {
        GridOverlayHolder.instance(this).hide()
    }

    // ---------- silence-watch implementation ----------

    private fun startSilenceWatch() {
        hasSpokenThisTurn = false
        silentMsAccum = 0
        totalMsAccum = 0
        silenceHandler = Handler(Looper.getMainLooper())

        val runnable = object : Runnable {
            override fun run() {
                val amp = VoiceCommandPipeline.currentAmplitude()
                totalMsAccum += POLL_MS

                if (amp > SPEECH_THRESHOLD) {
                    hasSpokenThisTurn = true
                    silentMsAccum = 0
                } else if (hasSpokenThisTurn) {
                    silentMsAccum += POLL_MS
                }

                val shouldStop = (hasSpokenThisTurn && silentMsAccum >= SILENCE_DURATION_MS) ||
                        totalMsAccum >= MAX_RECORD_MS

                if (shouldStop) {
                    finishRecording()
                } else {
                    silenceHandler?.postDelayed(this, POLL_MS.toLong())
                }
            }
        }
        silenceHandler?.postDelayed(runnable, POLL_MS.toLong())
    }

    private fun stopSilenceWatch() {
        silenceHandler?.removeCallbacksAndMessages(null)
        silenceHandler = null
    }

    /** Single path for both manual-tap-stop and auto-stop-on-silence. */
    private fun finishRecording() {
        stopSilenceWatch()
        runOnUiThread {
            webView.evaluateJavascript("window.onNativeRecordingStopped && window.onNativeRecordingStopped();", null)
        }
        VoiceCommandPipeline.stopManual()
    }

    // ---------- JarvisStateListener ----------

    override fun onStateChanged(newState: JarvisState) {
        runOnUiThread {
            val stateStr = if (newState == JarvisState.ACTIVE) "ACTIVE" else "SLEEP"
            webView.evaluateJavascript(
                "window.onNativeStateChange && window.onNativeStateChange('$stateStr');", null
            )
        }
    }

    // ---------- VoiceCommandPipeline.Listener ----------

    override fun onTranscript(text: String) {
        runOnUiThread {
            val escaped = org.json.JSONObject.quote(text)
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
        stopSilenceWatch()
        TtsEngine.shutdown()
        super.onDestroy()
    }
}
