package com.naruto.jarvis

import android.webkit.JavascriptInterface
import com.naruto.jarvis.accessibility.JarvisAccessibilityService
import com.naruto.jarvis.core.WakeWordService

/**
 * JarvisJsBridge
 * ---------------------------------------------------------------
 * Every method here is callable from your EXISTING app.js as
 * `AndroidBridge.methodName(...)`. This is the entire hybrid
 * connection point — the web UI keeps doing everything it already
 * does; it just calls out here for the things only native code can
 * actually do (device control, reliable background wake-word).
 *
 * IMPORTANT: methods here run on a background thread (WebView's JS
 * bridge thread), NOT the UI thread — that's why each one posts back
 * through MainActivity's runOnUiThread where it touches UI/WebView.
 */
class JarvisJsBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun toggleWake(enabled: Boolean) {
        activity.runOnUiThread { activity.setWakeServiceEnabled(enabled) }
    }

    @JavascriptInterface
    fun startRecording(): Boolean = activity.startNativeRecording()

    @JavascriptInterface
    fun stopRecording() {
        activity.runOnUiThread { activity.stopNativeRecording() }
    }

    @JavascriptInterface
    fun speak(text: String) {
        activity.runOnUiThread { activity.speakNative(text) }
    }

    @JavascriptInterface
    fun openApp(name: String): Boolean = activity.launchAppByName(name)

    @JavascriptInterface
    fun tapElement(label: String): Boolean {
        val service = JarvisAccessibilityService.instance ?: return false
        val asIndex = label.toIntOrNull()
        return if (asIndex != null) {
            service.clickByGridIndex(asIndex - 1)
        } else {
            service.clickElement(label)
        }
    }

    /** Called by JS when tapElement(label) returns false for a non-numeric label —
     *  draws numbered badges over every clickable element so the user can say "tap 4". */
    @JavascriptInterface
    fun showGrid() {
        activity.runOnUiThread { activity.showElementGrid() }
    }

    @JavascriptInterface
    fun hideGrid() {
        activity.runOnUiThread { activity.hideElementGrid() }
    }

    @JavascriptInterface
    fun isAccessibilityEnabled(): Boolean = JarvisAccessibilityService.instance != null

    @JavascriptInterface
    fun getDefaultProxyUrl(): String = com.naruto.jarvis.BuildConfig.PROXY_BASE_URL

    @JavascriptInterface
    fun openAccessibilitySettings() {
        activity.runOnUiThread { activity.openAccessibilitySettings() }
    }

    @JavascriptInterface
    fun openOverlaySettings() {
        activity.runOnUiThread { activity.openOverlaySettings() }
    }
}
