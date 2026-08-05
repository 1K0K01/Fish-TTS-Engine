package com.example.fishtts

import org.json.JSONObject
import java.util.Locale

data class VoiceProfile(
    val name: String,
    val modelId: String,
    val localeTag: String = "ko-KR"
) {
    fun locale(): Locale {
        val locale = Locale.forLanguageTag(localeTag)
        return if (locale.toLanguageTag() == "und") Locale.KOREAN else locale
    }

    fun toJson(): JSONObject {
        return JSONObject()
            .put("name", name)
            .put("modelId", modelId)
            .put("localeTag", localeTag)
    }

    companion object {
        fun fromJson(obj: JSONObject): VoiceProfile {
            return VoiceProfile(
                name = obj.optString("name", "Voice"),
                modelId = obj.optString("modelId", ""),
                localeTag = obj.optString("localeTag", "ko-KR")
            )
        }
    }
}
