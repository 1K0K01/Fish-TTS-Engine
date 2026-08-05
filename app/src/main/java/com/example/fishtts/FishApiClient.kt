package com.example.fishtts

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FishApiClient(private val prefs: SecurePrefs) {

    data class TtsParams(
        val text: String,
        val voiceModelId: String,
        val ttsModel: String,
        val speed: Float,
        val pitch: Float
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun createCall(params: TtsParams): okhttp3.Call {
        val root = JSONObject()
        root.put("text", params.text)
        root.put("reference_id", params.voiceModelId)
        root.put("format", "mp3")
        root.put("mp3_bitrate", 128)
        root.put("normalize", true)
        root.put("latency", "balanced")

        val prosody = JSONObject()
        prosody.put("speed", params.speed.toDouble())
        prosody.put("volume", 0)
        root.put("prosody", prosody)

        val extra = prefs.extraBodyJson.trim()
        if (extra.isNotEmpty()) {
            try {
                val extraJson = JSONObject(extra)
                val keys = extraJson.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    root.put(key, extraJson.get(key))
                }
            } catch (_: Exception) {}
        }

        val body = root.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(prefs.endpoint.trim().ifEmpty { DEFAULT_ENDPOINT })
            .header("Authorization", "Bearer ${prefs.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .header("model", params.ttsModel)
            .post(body)
            .build()

        return client.newCall(request)
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.fish.audio/v1/tts"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
