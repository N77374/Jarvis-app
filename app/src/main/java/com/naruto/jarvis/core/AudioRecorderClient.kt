package com.naruto.jarvis.core

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * AudioRecorderClient
 * ---------------------------------------------------------------
 * Native equivalent of the web app's MediaRecorder-based command
 * capture. start() begins recording to a temp file; stop() finalizes
 * it and hands the File to the caller (normally passed straight into
 * AiBackendClient.transcribe()).
 */
class AudioRecorderClient(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null

    fun start(): Boolean {
        val file = File(context.cacheDir, "jarvis_command_${System.currentTimeMillis()}.m4a")
        outputFile = file

        return try {
            @Suppress("DEPRECATION")
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            mr.setAudioSource(MediaRecorder.AudioSource.MIC)
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mr.setOutputFile(file.absolutePath)
            mr.prepare()
            mr.start()
            recorder = mr
            true
        } catch (e: Exception) {
            false
        }
    }

    /** Stops recording and returns the finished audio file, or null on failure. */
    fun stop(): File? {
        return try {
            recorder?.apply {
                stop()
                release()
            }
            recorder = null
            outputFile
        } catch (e: Exception) {
            null
        }
    }

    /** Current mic input level (0 = silence, higher = louder) — used to auto-detect when the user has stopped talking. */
    fun currentAmplitude(): Int = try {
        recorder?.maxAmplitude ?: 0
    } catch (e: Exception) {
        0
    }
}
