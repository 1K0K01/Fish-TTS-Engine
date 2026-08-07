package com.example.fishtts

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import java.util.Locale

class SecurePrefs(context: Context) {

    companion object {
        private const val PREF_NAME = "fish_tts_secure"
        private const val FALLBACK_PREF_NAME = "fish_tts_plain"

        private const val KEY_API_KEY = "api_key"
        private const val KEY_ENDPOINT = "endpoint"
        private const val KEY_TTS_MODEL = "tts_model"
        private const val KEY_DEFAULT_VOICE_ID = "default_voice_id"
        private const val KEY_VOICE_PROFILES = "voice_profiles"

        private const val KEY_VOICE_NAME = "voice_name"
        private const val KEY_LOCALE_TAG = "locale_tag"

        private const val KEY_SAMPLE_RATE = "sample_rate"
        private const val KEY_FORMAT = "format"
        private const val KEY_MAX_CHUNK = "max_chunk"
        private const val KEY_EXTRA_BODY_JSON = "extra_body_json"
        private const val KEY_CACHE_ENABLED = "cache_enabled"

        const val DEFAULT_TTS_MODEL = "s2.1-pro-free"

        const val DEFAULT_VOICE_1_ID = "9f5bf37aaa1048dd871a74356dd67f24"
        const val DEFAULT_VOICE_2_ID = "371503658994498498c6e5d2ebc072bb"
        const val DEFAULT_VOICE_3_ID = "b547f56c9a6f4241987f822a670fa79a"
        const val DEFAULT_VOICE_4_ID = "191f6bbd3526405ca6c445e531c7b8b0"
        const val DEFAULT_VOICE_5_ID = "02b292cfae574c82bfdbff1d8603bc63"
        const val DEFAULT_VOICE_6_ID = "ff2945cbfd274c85b440bd39d8cb3729"
        const val DEFAULT_VOICE_7_ID = "048a61e0b1d647e1a8507e39ba15348a"
        const val DEFAULT_VOICE_8_ID = "16a1d3f92d2c4d428c79e3b5fa5721ad"
        const val DEFAULT_VOICE_9_ID = "3d7308e5fd334bd58f5c0f52a9afb337"
        const val DEFAULT_VOICE_10_ID = "5915eba6792645509ca30d00e5d3bc99"
    }

    private val prefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (t: Throwable) {
        context.getSharedPreferences(FALLBACK_PREF_NAME, Context.MODE_PRIVATE)
    }

    private val builtInVoices = listOf(
        VoiceProfile(name = "차분한 목소리, 남성 A", modelId = DEFAULT_VOICE_1_ID, localeTag = "ko-KR"),
        VoiceProfile(name = "내성적인 목소리, 남성 B", modelId = DEFAULT_VOICE_2_ID, localeTag = "ko-KR"),
        VoiceProfile(name = "진중한 내레이션, 중년 남성 A", modelId = DEFAULT_VOICE_3_ID, localeTag = "ko-KR"),
        VoiceProfile(name = "중후한 목소리, 중년 남성 B", modelId = DEFAULT_VOICE_4_ID, localeTag = "ko-KR"),
        VoiceProfile(name = "권위있는 목소리, 중년 남성 C", modelId = DEFAULT_VOICE_5_ID, localeTag = "ko-KR"),
        VoiceProfile(name = "부드러운 내레이션, 여성 A", modelId = DEFAULT_VOICE_6_ID, localeTag = "ko-KR"),
        VoiceProfile(name = "절제된 내레이션, 여성 B", modelId = DEFAULT_VOICE_7_ID, localeTag = "ko-KR"),
        VoiceProfile(name = "또렷한 목소리, 여성 C", modelId = DEFAULT_VOICE_8_ID, localeTag = "ko-KR"),
        VoiceProfile(name = "권위있는 목소리, 중년 여성 A", modelId = DEFAULT_VOICE_9_ID, localeTag = "ko-KR"),
        VoiceProfile(name = "침착한 목소리, 중년 여성 B", modelId = DEFAULT_VOICE_10_ID, localeTag = "ko-KR")
    )

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value).apply()
        }

    var endpoint: String
        get() = prefs.getString(KEY_ENDPOINT, FishApiClient.DEFAULT_ENDPOINT)
            ?: FishApiClient.DEFAULT_ENDPOINT
        set(value) {
            prefs.edit().putString(KEY_ENDPOINT, value).apply()
        }

    var ttsModel: String
        get() = prefs.getString(KEY_TTS_MODEL, DEFAULT_TTS_MODEL) ?: DEFAULT_TTS_MODEL
        set(value) {
            prefs.edit().putString(KEY_TTS_MODEL, value).apply()
        }

    var defaultVoiceId: String
        get() = prefs.getString(KEY_DEFAULT_VOICE_ID, DEFAULT_VOICE_1_ID) ?: DEFAULT_VOICE_1_ID
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_VOICE_ID, value).apply()
        }

    var localeTag: String
        get() = prefs.getString(KEY_LOCALE_TAG, "ko-KR") ?: "ko-KR"
        set(value) {
            prefs.edit().putString(KEY_LOCALE_TAG, value).apply()
        }

    var sampleRate: Int
        get() = prefs.getInt(KEY_SAMPLE_RATE, 44100)
        set(value) {
            prefs.edit().putInt(KEY_SAMPLE_RATE, value).apply()
        }

    var format: String
        get() = prefs.getString(KEY_FORMAT, "mp3") ?: "mp3"
        set(value) {
            prefs.edit().putString(KEY_FORMAT, value).apply()
        }

    var maxChunkChars: Int
        get() = prefs.getInt(KEY_MAX_CHUNK, 500)
        set(value) {
            prefs.edit().putInt(KEY_MAX_CHUNK, value).apply()
        }

    var extraBodyJson: String
        get() = prefs.getString(KEY_EXTRA_BODY_JSON, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_EXTRA_BODY_JSON, value).apply()
        }

    var cacheEnabled: Boolean
        get() = prefs.getBoolean(KEY_CACHE_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_CACHE_ENABLED, value).apply()
        }

    fun getVoices(): List<VoiceProfile> {
        val json = prefs.getString(KEY_VOICE_PROFILES, null)

        if (json == null) {
            val migrated = mutableListOf<VoiceProfile>()

            val oldId = prefs.getString(KEY_DEFAULT_VOICE_ID, "") ?: ""
            val oldName = prefs.getString(KEY_VOICE_NAME, "") ?: ""
            val oldLocale = prefs.getString(KEY_LOCALE_TAG, "ko-KR") ?: "ko-KR"

            if (oldId.isNotBlank() && builtInVoices.none { it.modelId == oldId }) {
                migrated.add(
                    VoiceProfile(
                        name = oldName.ifEmpty { "Custom Voice" },
                        modelId = oldId,
                        localeTag = oldLocale
                    )
                )
            }

            migrated.addAll(builtInVoices)
            saveVoices(migrated)
            return migrated
        }

        return parseVoices(json)
    }

    private fun parseVoices(json: String): List<VoiceProfile> {
        return try {
            val array = JSONArray(json)
            val list = mutableListOf<VoiceProfile>()

            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val voice = VoiceProfile.fromJson(obj)

                if (voice.modelId.isNotBlank()) {
                    list.add(voice)
                }
            }

            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveVoices(list: List<VoiceProfile>) {
        val array = JSONArray()
        list.forEach { voice ->
            array.put(voice.toJson())
        }

        prefs.edit()
            .putString(KEY_VOICE_PROFILES, array.toString())
            .apply()

        val currentDefault = prefs.getString(KEY_DEFAULT_VOICE_ID, null)

        if (currentDefault == null || list.none { it.modelId == currentDefault }) {
            defaultVoiceId = list.firstOrNull()?.modelId ?: ""
        }
    }

    fun addVoice(name: String, modelId: String, localeTag: String): Boolean {
        val id = modelId.trim()
        if (id.isEmpty()) return false

        val voices = getVoices().toMutableList()

        if (voices.any { it.modelId == id }) {
            return false
        }

        val baseName = name.trim().ifEmpty { "Voice ${voices.size + 1}" }
        var finalName = baseName
        var suffix = 2

        while (voices.any { it.name == finalName }) {
            finalName = "$baseName ($suffix)"
            suffix++
        }

        val locale = localeTag.trim().ifEmpty { "ko-KR" }

        voices.add(
            VoiceProfile(
                name = finalName,
                modelId = id,
                localeTag = locale
            )
        )

        saveVoices(voices)

        if (defaultVoiceId.isBlank()) {
            defaultVoiceId = id
        }

        return true
    }

    fun deleteVoice(modelId: String) {
        val voices = getVoices().filterNot { it.modelId == modelId }
        saveVoices(voices)
    }

    fun setDefaultVoice(modelId: String) {
        val voices = getVoices()
        if (voices.any { it.modelId == modelId }) {
            defaultVoiceId = modelId
        }
    }

    fun defaultVoice(): VoiceProfile? {
        val voices = getVoices()

        return voices.firstOrNull { it.modelId == defaultVoiceId }
            ?: voices.firstOrNull()
    }

    fun defaultLocale(): Locale {
        val tag = localeTag.trim()
        if (tag.isEmpty()) return Locale.KOREAN

        val locale = Locale.forLanguageTag(tag)
        return if (locale.toLanguageTag() == "und") Locale.KOREAN else locale
    }

    fun voiceProfiles(): List<VoiceProfile> = getVoices()
}
