package com.example.fishtts

import org.json.JSONArray
import org.json.JSONObject

/**
 * 연결 설정·보이스 목록을 파일 하나(JSON)로 내보내고 불러옵니다.
 * API 키는 민감 정보라 백업 파일에 포함하지 않습니다 — 복원 후에는
 * API 키를 다시 입력해야 합니다.
 * PCM 캐시와 달리 자동으로 저장하지 않고, 사용자가 "백업" 버튼을 눌렀을 때만
 * 동작합니다.
 */
object SettingsBackup {

    fun export(prefs: SecurePrefs): String {
        val json = JSONObject()
        json.put("endpoint", prefs.endpoint)
        json.put("ttsModel", prefs.ttsModel)
        json.put("localeTag", prefs.localeTag)
        json.put("sampleRate", prefs.sampleRate)
        json.put("format", prefs.format)
        json.put("maxChunkChars", prefs.maxChunkChars)
        json.put("extraBodyJson", prefs.extraBodyJson)
        json.put("cacheEnabled", prefs.cacheEnabled)
        json.put("defaultVoiceId", prefs.defaultVoiceId)

        val voicesArray = JSONArray()
        prefs.getVoices().forEach { voice -> voicesArray.put(voice.toJson()) }
        json.put("voices", voicesArray)

        return json.toString(2)
    }

    fun import(prefs: SecurePrefs, jsonText: String) {
        val json = JSONObject(jsonText)

        prefs.endpoint = json.optString("endpoint", prefs.endpoint)
        prefs.ttsModel = json.optString("ttsModel", prefs.ttsModel)
        prefs.localeTag = json.optString("localeTag", prefs.localeTag)
        prefs.sampleRate = json.optInt("sampleRate", prefs.sampleRate)
        prefs.format = json.optString("format", prefs.format)
        prefs.maxChunkChars = json.optInt("maxChunkChars", prefs.maxChunkChars)
        prefs.extraBodyJson = json.optString("extraBodyJson", prefs.extraBodyJson)
        prefs.cacheEnabled = json.optBoolean("cacheEnabled", prefs.cacheEnabled)

        val voicesJson = json.optJSONArray("voices")
        if (voicesJson != null && voicesJson.length() > 0) {
            val voices = mutableListOf<VoiceProfile>()
            for (i in 0 until voicesJson.length()) {
                val obj = voicesJson.optJSONObject(i) ?: continue
                voices.add(VoiceProfile.fromJson(obj))
            }
            if (voices.isNotEmpty()) {
                prefs.saveVoices(voices)
            }
        }

        val defaultId = json.optString("defaultVoiceId", "")
        if (defaultId.isNotBlank()) {
            prefs.setDefaultVoice(defaultId)
        }
    }
}
