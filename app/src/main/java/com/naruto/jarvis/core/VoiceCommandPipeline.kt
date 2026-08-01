package com.naruto.jarvis.core

import android.content.Context
import android.os.Handler
import android.os.Looper

/**
 * VoiceCommandPipeline
 * ---------------------------------------------------------------
 * One shared place for "record audio -> transcribe -> hand off text"
 * so both the tap-to-talk mic button (via MainActivity/JS bridge) and
 * the wake-word auto-capture (via WakeWordService, which has no UI of
 * its own) go through identical logic and notify the same listeners.
 */
object VoiceCommandPipeline {

    interface Listener {
        fun onTranscript(text: String)
        fun onNoSpeechDetected()
    }

    private val listeners = mutableListOf<Listener>()
    private var recorder: AudioRecorderClient? = null

    fun addListener(l: Listener) {
        if (!listeners.contains(l)) listeners.add(l)
    }

    fun removeListener(l: Listener) {
        listeners.remove(l)
    }

    /** Tap-to-talk: start now, caller stops later with stopManual(). */
    fun startManual(context: Context): Boolean {
        val rec = AudioRecorderClient(context)
        recorder = rec
        return rec.start()
    }

    fun stopManual() {
        val file = recorder?.stop()
        recorder = null
        if (file == null) {
            listeners.forEach { it.onNoSpeechDetected() }
            return
        }
        AiBackendClient.transcribe(file, "en") { text ->
            if (text.isBlank()) listeners.forEach { it.onNoSpeechDetected() }
            else listeners.forEach { it.onTranscript(text) }
        }
    }

    /**
     * Wake-word triggered: records for a fixed duration automatically,
     * then transcribes — used since there's no user tap to signal "done"
     * in hands-free mode.
     */
    fun startAutoCapture(context: Context, durationMs: Long) {
        val rec = AudioRecorderClient(context)
        recorder = rec
        if (!rec.start()) {
            listeners.forEach { it.onNoSpeechDetected() }
            return
        }
        Handler(Looper.getMainLooper()).postDelayed({
            val file = rec.stop()
            recorder = null
            if (file == null) {
                listeners.forEach { it.onNoSpeechDetected() }
                return@postDelayed
            }
            AiBackendClient.transcribe(file, "en") { text ->
                if (text.isBlank()) listeners.forEach { it.onNoSpeechDetected() }
                else listeners.forEach { it.onTranscript(text) }
            }
        }, durationMs)
    }
}
