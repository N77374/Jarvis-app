package com.naruto.jarvis.core

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.UUID

object TtsEngine {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pendingQueue = mutableListOf<String>()

    var onUtterance: ((String) -> Unit)? = null // hook for UI to log what was said
    var onDone: (() -> Unit)? = null            // hook fired when speech finishes

    fun init(context: Context) {
        if (tts != null) return
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
                tts?.setPitch(0.9f)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) { onDone?.invoke() }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) { onDone?.invoke() }
                })
                ready = true
                pendingQueue.forEach { speakNow(it) }
                pendingQueue.clear()
            }
        }
    }

    fun speak(text: String) {
        onUtterance?.invoke(text)
        if (ready) speakNow(text) else pendingQueue.add(text)
    }

    private fun speakNow(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
