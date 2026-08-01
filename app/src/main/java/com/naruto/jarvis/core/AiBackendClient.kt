package com.naruto.jarvis.core

import com.naruto.jarvis.BuildConfig
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * AiBackendClient
 * ---------------------------------------------------------------
 * Talks to the SAME Cloudflare Worker your web Jarvis already uses
 * (chat endpoint at "/", transcription at "/transcribe"). No changes
 * needed on the worker side — this just calls it from native code
 * instead of browser fetch().
 *
 * PROXY_BASE_URL comes from BuildConfig, sourced from local.properties
 * or a GitHub Actions secret (see build.gradle.kts) — never hardcode
 * your worker URL directly in source if you plan to make this repo
 * public.
 */
object AiBackendClient {

    private val client = OkHttpClient()
    private val baseUrl get() = BuildConfig.PROXY_BASE_URL.trimEnd('/')

    /** Text prompt -> personality-driven reply (same as web app's chat call) */
    fun sendPrompt(prompt: String, onReply: (String) -> Unit) {
        val json = JSONObject().put("prompt", prompt).toString()
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(baseUrl).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onReply("Could not reach the AI backend.")
            }
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string() ?: "{}"
                val reply = runCatching { JSONObject(text).optString("reply") }.getOrNull()
                onReply(reply?.takeIf { it.isNotBlank() } ?: "No reply from backend.")
            }
        })
    }

    /** Recorded audio file -> transcribed text (Whisper via worker's /transcribe) */
    fun transcribe(audioFile: File, langCode: String = "en", onResult: (String) -> Unit) {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "audio", audioFile.name, // e.g. "jarvis_command_....m4a" — keep the real name/extension
                audioFile.readBytes().toRequestBody("audio/mp4".toMediaType())
            )
            .addFormDataPart("lang", langCode)
            .build()

        val request = Request.Builder().url("$baseUrl/transcribe").post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onResult("")
            }
            override fun onResponse(call: Call, response: Response) {
                val text = response.body?.string() ?: "{}"
                val transcript = runCatching { JSONObject(text).optString("text") }.getOrNull()
                onResult(transcript ?: "")
            }
        })
    }
}
