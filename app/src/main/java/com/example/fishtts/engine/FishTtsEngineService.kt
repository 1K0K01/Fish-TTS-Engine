package com.example.fishtts.engine

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.example.fishtts.FishApiClient
import com.example.fishtts.Mp3Decoder
import com.example.fishtts.PcmCache
import com.example.fishtts.SecurePrefs
import com.example.fishtts.TextChunker
import okhttp3.Call
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

class FishTtsEngineService : TextToSpeechService() {

    companion object {
        private const val TAG = "FishTtsEngine"
        private const val BUFFER_SIZE = 8192
    }

    private lateinit var prefs: SecurePrefs
    private lateinit var api: FishApiClient
    private lateinit var cache: PcmCache

    private val stopped = AtomicBoolean(false)
    @Volatile private var activeCall: Call? = null

    override fun onCreate() {
        super.onCreate()
        prefs = SecurePrefs(this)
        api = FishApiClient(prefs)
        cache = PcmCache(this)
    }

    override fun onGetLanguage(): Array<String> {
        val locale = prefs.defaultVoice()?.locale() ?: prefs.defaultLocale()
        return arrayOf(locale.language, locale.country, locale.variant)
    }

    override fun onIsLanguageAvailable(language: String, country: String, variant: String): Int {
        return if (prefs.getVoices().isNotEmpty()) TextToSpeech.LANG_AVAILABLE else TextToSpeech.LANG_NOT_SUPPORTED
    }

    override fun onLoadLanguage(lang: String, country: String, variant: String): Int = TextToSpeech.SUCCESS

    override fun onGetDefaultVoiceNameFor(language: String, country: String, variant: String): String? {
        val voices = prefs.getVoices()
        return voices.firstOrNull { it.locale().language.equals(language, ignoreCase = true) }?.name
            ?: prefs.defaultVoice()?.name ?: voices.firstOrNull()?.name
    }

    override fun onGetVoices(): List<Voice> = prefs.getVoices().map {
        Voice(it.name, it.locale(), Voice.QUALITY_NORMAL, Voice.LATENCY_VERY_HIGH, true, emptySet())
    }

    override fun onIsValidVoiceName(voiceName: String): Int =
        if (prefs.getVoices().any { it.name == voiceName }) TextToSpeech.SUCCESS else TextToSpeech.ERROR

    override fun onStop() {
        stopped.set(true)
        activeCall?.cancel()
    }

    override fun onSynthesizeText(request: SynthesisRequest, callback: SynthesisCallback) {
        stopped.set(false)
        val text = request.getCharSequenceText()?.toString()?.trim().orEmpty()
        if (text.isEmpty()) { callback.done(); return }

        val voices = prefs.getVoices()
        val profile = voices.firstOrNull { it.name == request.voiceName } ?: prefs.defaultVoice()

        if (profile == null || prefs.apiKey.isBlank() || profile.modelId.isBlank() || prefs.ttsModel.isBlank()) {
            callback.error(); return
        }

        val speed = (request.speechRate / 100f).coerceIn(0.5f, 2.0f)
        val pitch = (request.pitch / 100f).coerceIn(0.5f, 2.0f)

        val chunks = TextChunker.split(text, profile.locale(), prefs.maxChunkChars)
        var started = false

        try {
            for (chunk in chunks) {
                if (stopped.get()) { callback.error(); return }

                val cacheKey = if (prefs.cacheEnabled) {
                    cache.keyFor(
                        text = chunk,
                        voiceModelId = profile.modelId,
                        ttsModel = prefs.ttsModel,
                        speed = speed,
                        pitch = pitch,
                        sampleRate = prefs.sampleRate,
                        format = prefs.format
                    )
                } else null

                // ---- 캐시 적중: 네트워크 호출 없이 바로 재생 ----
                val cachedEntry = cacheKey?.let { cache.get(it) }
                if (cachedEntry != null) {
                    val meta = cache.readMeta(cachedEntry)
                    val input = cache.openPcmInputStream(cachedEntry)

                    if (meta != null && input != null) {
                        if (!started) {
                            callback.start(meta.sampleRate, meta.encoding(), meta.channels)
                            started = true
                        }

                        var playbackFailed = false
                        input.use { stream ->
                            val buf = ByteArray(BUFFER_SIZE)
                            var read: Int
                            while (stream.read(buf).also { read = it } != -1) {
                                if (callback.audioAvailable(buf, 0, read) != TextToSpeech.SUCCESS) {
                                    playbackFailed = true
                                    break
                                }
                            }
                        }

                        if (playbackFailed) { callback.error(); return }
                        continue
                    }
                }

                // ---- 캐시 미스: 기존대로 API 호출 후 디코딩 ----
                val params = FishApiClient.TtsParams(text = chunk, voiceModelId = profile.modelId, ttsModel = prefs.ttsModel, speed = speed, pitch = pitch)
                val call = api.createCall(params)
                activeCall = call

                val response = call.execute()
                response.use { resp ->
                    if (!resp.isSuccessful) { callback.error(); return }

                    val mp3File = File.createTempFile("tts_", ".mp3", cacheDir)
                    FileOutputStream(mp3File).use { fos ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var read: Int
                        while (resp.body?.byteStream()?.read(buf).also { read = it ?: -1 } != -1) {
                            fos.write(buf, 0, read)
                        }
                    }

                    val pcmFile = File.createTempFile("tts_", ".pcm", cacheDir)
                    val decoded = Mp3Decoder.decode(mp3File, pcmFile)
                    mp3File.delete()

                    if (decoded == null || decoded.pcmFile.length() == 0L) {
                        pcmFile.delete()
                        callback.error()
                        return
                    }

                    if (!started) {
                        callback.start(decoded.sampleRate, AudioFormat.ENCODING_PCM_16BIT, decoded.channels)
                        started = true
                    }

                    var playbackFailed = false
                    FileInputStream(decoded.pcmFile).use { fis ->
                        val buf = ByteArray(BUFFER_SIZE)
                        var read: Int
                        while (fis.read(buf).also { read = it } != -1) {
                            if (callback.audioAvailable(buf, 0, read) != TextToSpeech.SUCCESS) {
                                playbackFailed = true
                                break
                            }
                        }
                    }

                    // 재생에 쓴 것과 별개로, 캐시가 켜져 있으면 결과를 저장해 둡니다.
                    if (!playbackFailed && cacheKey != null) {
                        try {
                            val writer = cache.beginWrite(cacheKey)
                            FileInputStream(decoded.pcmFile).use { fis ->
                                val buf = ByteArray(BUFFER_SIZE)
                                var read: Int
                                while (fis.read(buf).also { read = it } != -1) {
                                    writer.write(buf, 0, read)
                                }
                            }
                            writer.finish(PcmCache.Meta(decoded.sampleRate, decoded.channels, 16))
                        } catch (e: IOException) {
                            Log.e(TAG, "캐시 저장 실패", e)
                        }
                    }

                    decoded.pcmFile.delete()

                    if (playbackFailed) {
                        callback.error()
                        return
                    }
                }
            }
            callback.done()
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            callback.error()
        } finally {
            activeCall = null
        }
    }
}
