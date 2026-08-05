package com.naruto.jarvis.core

import android.content.Context
import com.naruto.jarvis.accessibility.GridOverlayHolder
import com.naruto.jarvis.accessibility.JarvisAccessibilityService
import org.json.JSONObject

/**
 * NativeCommandRouter
 * ---------------------------------------------------------------
 * Runs entirely independent of the WebView/Activity, so voice
 * commands given through the floating bubble (or wake-word) keep
 * working even if the main app screen isn't open or gets killed
 * in the background by Android.
 *
 * Narrates each step via TTS as it works — "Opening WhatsApp...",
 * "Done." — rather than staying silent until the very end.
 */
object NativeCommandRouter {

    private val WEATHER_CODES = mapOf(
        0 to "clear sky", 1 to "mostly clear", 2 to "partly cloudy", 3 to "overcast",
        45 to "foggy", 48 to "foggy", 51 to "light drizzle", 53 to "drizzle", 55 to "heavy drizzle",
        61 to "light rain", 63 to "rain", 65 to "heavy rain", 71 to "light snow", 73 to "snow",
        75 to "heavy snow", 80 to "rain showers", 81 to "rain showers", 82 to "heavy rain showers",
        95 to "thunderstorm", 96 to "thunderstorm with hail", 99 to "severe thunderstorm"
    )

    fun stripWakeWord(text: String): String =
        text.replace(Regex("^\\s*(hey\\s+)?jarvis[,:]?\\s*", RegexOption.IGNORE_CASE), "").trim()

    fun route(context: Context, rawText: String, onNarration: ((String) -> Unit)? = null) {
        val text = stripWakeWord(rawText).lowercase()
        val narrate: (String) -> Unit = { onNarration?.invoke(it) }

        when {
            text.contains("weather") -> weatherReply(context)

            text.contains("time") -> {
                val now = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                    .format(java.util.Date())
                TtsEngine.speak(now)
            }

            text.contains("date") -> {
                val now = java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault())
                    .format(java.util.Date())
                TtsEngine.speak(now)
            }

            text.contains("flashlight") || text.contains("torch") -> {
                val on = !(text.contains("off") || text.contains("turn off"))
                val ok = SystemControlManager.setFlashlight(context, on)
                TtsEngine.speak(if (ok) "Done." else "Couldn't control the flashlight.")
            }

            text.contains("volume up") || text.contains("increase volume") -> {
                SystemControlManager.adjustVolume(context, true)
                TtsEngine.speak("Done.")
            }

            text.contains("volume down") || text.contains("decrease volume") || text.contains("lower volume") -> {
                SystemControlManager.adjustVolume(context, false)
                TtsEngine.speak("Done.")
            }

            text.contains("mute") -> {
                SystemControlManager.setMuted(context, true)
                TtsEngine.speak("Muted.")
            }

            text.contains("unmute") -> {
                SystemControlManager.setMuted(context, false)
                TtsEngine.speak("Unmuted.")
            }

            text.contains("set volume to") -> {
                val pct = Regex("\\d+").find(text)?.value?.toIntOrNull()
                if (pct != null) {
                    val ok = SystemControlManager.setVolumePercent(context, pct)
                    TtsEngine.speak(if (ok) "Volume set to $pct percent." else "Couldn't set volume.")
                } else {
                    TtsEngine.speak("Tell me a percentage, like 'set volume to 50'.")
                }
            }

            text.contains("brightness") -> {
                val pct = Regex("\\d+").find(text)?.value?.toIntOrNull()
                if (!SystemControlManager.canWriteSettings(context)) {
                    TtsEngine.speak("I need the 'Modify system settings' permission first — check Jarvis settings.")
                } else if (pct != null) {
                    val ok = SystemControlManager.setBrightnessPercent(context, pct)
                    TtsEngine.speak(if (ok) "Brightness set to $pct percent." else "Couldn't set brightness.")
                } else {
                    TtsEngine.speak("Tell me a percentage, like 'set brightness to 70'.")
                }
            }

            text.contains("bluetooth") -> {
                val on = !(text.contains("off") || text.contains("turn off"))
                val ok = SystemControlManager.setBluetooth(on)
                TtsEngine.speak(if (ok) "Done." else "Couldn't control Bluetooth on this phone.")
            }

            text.contains("do not disturb") || text.contains("dnd") -> {
                val on = !(text.contains("off") || text.contains("turn off"))
                if (!SystemControlManager.canAccessNotificationPolicy(context)) {
                    TtsEngine.speak("I need Do Not Disturb access first — check Jarvis settings.")
                } else {
                    val ok = SystemControlManager.setDoNotDisturb(context, on)
                    TtsEngine.speak(if (ok) "Done." else "Couldn't change Do Not Disturb.")
                }
            }

            text.contains("wifi") || text.contains("wi-fi") -> {
                // Android blocks silent Wi-Fi toggling since Android 10 — opening the
                // quick panel is the honest, actually-possible version of this.
                SystemControlManager.openWifiPanel(context)
                TtsEngine.speak("Here's the Wi-Fi panel — one tap to switch it.")
            }

            text.startsWith("close ") -> {
                val service = JarvisAccessibilityService.instance
                if (service == null) {
                    TtsEngine.speak("Accessibility isn't enabled, so I can't close apps yet.")
                } else {
                    narrate("Closing…")
                    service.closeCurrentApp()
                    TtsEngine.speak("Done.")
                }
            }

            text.contains("scroll up") -> {
                JarvisAccessibilityService.instance?.scroll(false)
                TtsEngine.speak("Done.")
            }
            text.contains("scroll down") -> {
                JarvisAccessibilityService.instance?.scroll(true)
                TtsEngine.speak("Done.")
            }
            text.contains("screenshot") -> {
                val ok = JarvisAccessibilityService.instance?.takeScreenshotAction() ?: false
                TtsEngine.speak(if (ok) "Got it." else "Couldn't take a screenshot.")
            }
            text.contains("lock screen") || text.contains("lock the phone") || text.contains("lock my phone") -> {
                val ok = JarvisAccessibilityService.instance?.lockScreen() ?: false
                TtsEngine.speak(if (ok) "Locking up." else "Couldn't lock the screen.")
            }
            text.contains("go back") -> { JarvisAccessibilityService.instance?.goBack(); TtsEngine.speak("Done.") }
            text.contains("go home") || text == "home" -> { JarvisAccessibilityService.instance?.goHome(); TtsEngine.speak("Done.") }
            text.contains("recent apps") || text.contains("show recents") -> {
                JarvisAccessibilityService.instance?.openRecents(); TtsEngine.speak("Here you go.")
            }
            text.contains("notifications") -> {
                JarvisAccessibilityService.instance?.openNotifications(); TtsEngine.speak("Here you go.")
            }
            text.contains("quick settings") -> {
                val ok = JarvisAccessibilityService.instance?.openQuickSettings() ?: false
                TtsEngine.speak(if (ok) "Here you go." else "Not available on this Android version.")
            }
            text.contains("power menu") || text.contains("power dialog") -> {
                JarvisAccessibilityService.instance?.openPowerDialog(); TtsEngine.speak("Here you go.")
            }

            text.contains("auto rotate") || text.contains("auto-rotate") -> {
                val on = !(text.contains("off") || text.contains("turn off"))
                val ok = SystemControlManager.setAutoRotate(context, on)
                TtsEngine.speak(if (ok) "Done." else "I need the 'Modify system settings' permission first.")
            }

            text.contains("silent mode") -> {
                val ok = SystemControlManager.setRingerMode(context, "silent")
                TtsEngine.speak(if (ok) "Going silent." else "I need Do Not Disturb access first.")
            }
            text.contains("vibrate mode") -> {
                val ok = SystemControlManager.setRingerMode(context, "vibrate")
                TtsEngine.speak(if (ok) "Switched to vibrate." else "I need Do Not Disturb access first.")
            }
            text.contains("normal mode") || text.contains("ringer") -> {
                SystemControlManager.setRingerMode(context, "normal")
                TtsEngine.speak("Back to normal.")
            }

            text.startsWith("open ") -> {
                val appName = text.removePrefix("open ").trim()
                narrate("Opening $appName…")
                val pkg = AppRegistry.findPackage(context, appName)
                if (pkg == null) {
                    TtsEngine.speak("Couldn't find $appName installed.")
                    return
                }
                val service = JarvisAccessibilityService.instance
                val opened = if (service != null) {
                    service.launchApp(pkg)
                } else {
                    val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                    if (intent != null) {
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        true
                    } else false
                }
                TtsEngine.speak(if (opened) "Done." else "Couldn't open $appName.")
            }

            (text.startsWith("tap ") || text.startsWith("click ")) -> {
                val label = text.replace(Regex("^tap |^click "), "").trim()
                val service = JarvisAccessibilityService.instance
                if (service == null) {
                    TtsEngine.speak("Accessibility isn't enabled, so I can't tap things yet.")
                    return
                }
                narrate("Looking for $label…")
                val asIndex = label.toIntOrNull()
                val success = if (asIndex != null) {
                    service.clickByGridIndex(asIndex - 1)
                } else {
                    service.clickElement(label)
                }
                if (success) {
                    TtsEngine.speak("Done.")
                    GridOverlayHolder.instance(context).hide()
                } else {
                    GridOverlayHolder.instance(context).show(service)
                    TtsEngine.speak("I don't see that labeled — say a number.")
                }
            }

            else -> {
                narrate("Thinking…")
                AiBackendClient.sendPrompt(text) { reply ->
                    TtsEngine.speak(reply)
                }
            }
        }
    }

    private fun weatherReply(context: Context) {
        // Reuses the same free Open-Meteo APIs the web UI uses — no key needed.
        Thread {
            try {
                val prefs = context.getSharedPreferences("jarvis_prefs", Context.MODE_PRIVATE)
                val city = prefs.getString("city", "Ahmedabad") ?: "Ahmedabad"

                val geoJson = java.net.URL(
                    "https://geocoding-api.open-meteo.com/v1/search?name=${java.net.URLEncoder.encode(city, "UTF-8")}&count=1"
                ).readText()
                val geoResults = JSONObject(geoJson).optJSONArray("results")
                if (geoResults == null || geoResults.length() == 0) {
                    TtsEngine.speak("Couldn't find weather for $city.")
                    return@Thread
                }
                val place = geoResults.getJSONObject(0)
                val lat = place.getDouble("latitude")
                val lon = place.getDouble("longitude")
                val name = place.getString("name")

                val wJson = java.net.URL(
                    "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code"
                ).readText()
                val current = JSONObject(wJson).getJSONObject("current")
                val temp = current.getDouble("temperature_2m").toInt()
                val code = current.getInt("weather_code")
                val desc = WEATHER_CODES[code] ?: "normal"

                TtsEngine.speak("$name is $desc right now, $temp degrees celsius.")
            } catch (e: Exception) {
                TtsEngine.speak("Couldn't fetch the weather, check your internet.")
            }
        }.start()
    }
}
