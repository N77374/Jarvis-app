package com.naruto.jarvis.core

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import ai.picovoice.porcupine.PorcupineManager
import ai.picovoice.porcupine.PorcupineManagerCallback
import com.naruto.jarvis.BuildConfig

/**
 * WakeWordService
 * ---------------------------------------------------------------
 * Runs as a foreground service so Android doesn't kill it. Hosts
 * Porcupine — a small on-device wake-word engine (NOT cloud STT,
 * so it's cheap on battery/data and can run 24/7 in SLEEP state).
 *
 * You define two custom wake phrases in the free Picovoice Console
 * (console.picovoice.ai):
 *   - "Jarvis are you there"  -> jarvis_wake.ppn
 *   - "Jarvis go sleep"       -> jarvis_sleep.ppn
 * Drop the resulting .ppn files into app/src/main/assets/.
 *
 * NOTE: In the hybrid app, command ROUTING (what "open X" or "what's
 * the weather" should do) lives in the existing JS (handleCommand in
 * app.js) — this service's job ends at "capture audio and hand text
 * over" via VoiceCommandPipeline. MainActivity relays that text into
 * the WebView so the same JS logic you already built handles it,
 * exactly like a tap-to-talk transcript would.
 */
class WakeWordService : Service() {

    private var porcupineManager: PorcupineManager? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        startWakeWordListening()
    }

    private fun startWakeWordListening() {
        val callback = PorcupineManagerCallback { keywordIndex ->
            when (keywordIndex) {
                0 -> { // "Jarvis are you there"
                    if (JarvisStateManager.currentState == JarvisState.SLEEP) {
                        JarvisStateManager.wake()
                        TtsEngine.onDone = {
                            TtsEngine.onDone = null
                            // Hands-free: no tap to signal "done speaking", so
                            // auto-capture the user's next sentence for 6s.
                            VoiceCommandPipeline.startAutoCapture(applicationContext, 6000)
                        }
                        TtsEngine.speak("I am online and ready, sir.")
                    }
                }
                1 -> { // "Jarvis go sleep" — works even mid-conversation, independent of cloud STT
                    JarvisStateManager.sleep()
                    TtsEngine.speak("Going into standby mode.")
                }
            }
        }

        porcupineManager = PorcupineManager.Builder()
            .setAccessKey(BuildConfig.PICOVOICE_ACCESS_KEY) // free tier key from Picovoice Console, via build.gradle.kts
            .setKeywordPaths(arrayOf("jarvis_wake.ppn", "jarvis_sleep.ppn"))
            .setSensitivities(floatArrayOf(0.6f, 0.6f))
            .build(applicationContext, callback)

        porcupineManager?.start()
    }

    private fun buildNotification(): Notification {
        val channelId = "jarvis_wakeword_channel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Jarvis listening", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis")
            .setContentText("Standing by — say \"Jarvis are you there\"")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        porcupineManager?.stop()
        porcupineManager?.delete()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 42
    }
}
