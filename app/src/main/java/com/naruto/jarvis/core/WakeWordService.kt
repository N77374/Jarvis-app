package com.naruto.jarvis.core

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

/**
 * WakeWordService
 * ---------------------------------------------------------------
 * Runs as a foreground service, continuously listening for "Jarvis
 * are you there" / "Jarvis go sleep" using Vosk — a free, fully
 * offline speech engine. No account, no API key, no internet
 * connection required once the model is bundled in the app.
 *
 * REQUIRES: a Vosk model zip in app/src/main/assets/, named exactly
 * "model-en-us.zip" (see setup notes — download from
 * alphacephei.com/vosk/models, no sign-up needed, direct download).
 * Vosk unpacks it to internal storage the first time the app runs.
 */
class WakeWordService : Service() {

    private var model: Model? = null
    private var speechService: SpeechService? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        loadModelAndStart()
    }

    private fun loadModelAndStart() {
        StorageService.unpack(
            this, "model-en-us", "model",
            { unpackedModel ->
                model = unpackedModel
                startListening()
            },
            { exception ->
                // Model missing/corrupt — nothing we can recover from here;
                // the notification stays up but listening won't start.
            }
        )
    }

    private fun startListening() {
        val m = model ?: return
        val recognizer = Recognizer(m, 16000.0f)
        speechService = SpeechService(recognizer, 16000.0f)
        speechService?.startListening(object : RecognitionListener {

            override fun onPartialResult(hypothesis: String?) {}

            override fun onResult(hypothesis: String?) {
                handleHeardText(extractText(hypothesis))
            }

            override fun onFinalResult(hypothesis: String?) {
                handleHeardText(extractText(hypothesis))
            }

            override fun onError(exception: Exception?) {
                // Restart listening on error rather than dying silently.
                speechService?.stop()
                startListening()
            }

            override fun onTimeout() {
                startListening()
            }
        })
    }

    private fun extractText(hypothesisJson: String?): String {
        if (hypothesisJson.isNullOrBlank()) return ""
        return try {
            JSONObject(hypothesisJson).optString("text", "").lowercase()
        } catch (e: Exception) {
            ""
        }
    }

    private fun handleHeardText(heard: String) {
        if (heard.isBlank()) return
        when {
            heard.contains("are you there") -> {
                if (JarvisStateManager.currentState == JarvisState.SLEEP) {
                    JarvisStateManager.wake()
                    TtsEngine.onDone = {
                        TtsEngine.onDone = null
                        VoiceCommandPipeline.startAutoCapture(applicationContext, 6000)
                    }
                    TtsEngine.speak("I am online and ready, sir.")
                }
            }
            heard.contains("go sleep") || heard.contains("go to sleep") -> {
                JarvisStateManager.sleep()
                TtsEngine.speak("Going into standby mode.")
            }
        }
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
        speechService?.stop()
        speechService?.shutdown()
        model?.close()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIF_ID = 42
    }
}
