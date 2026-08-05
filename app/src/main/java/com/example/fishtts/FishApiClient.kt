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
        val pitch: Float,
        val sampleRate: Int,
        val format: String
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

        if (params.ttsModel.isNotBlank()) {
        }

        root.put("format", params.format)
        root.put("sample_rate", params.sampleRate)
        root.put("channels", 1)
        root.put("speed", params.speed.toDouble())

        val extra = prefs.extraBodyJson.trim()
        if (extra.isNotEmpty()) {
            try {
                val extraJson = JSONObject(extra)
                val keys = extraJson.keys()

                while (keys.hasNext()) {
                    val key = keys.next()
                    root.put(key, extraJson.get(key))
                }
            } catch (_: Exception) {
            }
        }

        val body = root.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url(prefs.endpoint.trim().ifEmpty { DEFAULT_ENDPOINT })
            .header("Authorization", "Bearer ${prefs.apiKey.trim()}")
            .header("Content-Type", "application/json")
            .header("model", params.ttsModel)
            .header("Accept", ACCEPT_HEADER)
            .post(body)
            .build()

        return client.newCall(request)
    }

    companion object {
        const val DEFAULT_ENDPOINT = "https://api.fish.audio/v1/tts"

        private val JSON_MEDIA_TYPE =
            "application/json; charset=utf-8".toMediaTypeOrNull()

        private const val ACCEPT_HEADER =
            "audio/pcm, audio/wav;q=0.9, application/octet-stream;q=0.8, */*;q=0.5"
    }
}
