package com.example.fishtts.engine

import android.media.AudioFormat
import android.speech.tts.SynthesisCallback
import android.speech.tts.SynthesisRequest
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeechService
import android.speech.tts.Voice
import android.util.Log
import com.example.fishtts.FishApiClient
import com.example.fishtts.PcmCache
import com.example.fishtts.SecurePrefs
import com.example.fishtts.TextChunker
import com.example.fishtts.WavParser
import okhttp3.Call
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean

class FishTtsEngineService : TextToSpeechService() {

    companion object {
        private const val TAG = "FishTtsEngine"
        private const val AUDIO_BUFFER_SIZE = 8192
    }

    private lateinit var prefs: SecurePrefs
    private lateinit var api: FishApiClient
    private lateinit var cache: PcmCache

    private val stopped = AtomicBoolean(false)

    @Volatile
    private var activeCall: Call? = null

    private data class SynthState(
        var started: Boolean = false,
        var info: WavParser.PcmInfo? = null
    )

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

    override fun onIsLanguageAvailable(
        language: String,
        country: String,
        variant: String
    ): Int {
        return if (prefs.getVoices().isNotEmpty()) {
            TextToSpeech.LANG_AVAILABLE
        } else {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    override fun onLoadLanguage(): Int {
        return TextToSpeech.SUCCESS
    }

    override fun onGetDefaultVoiceNameFor(
        language: String,
        country: String,
        variant: String
    ): String? {
        val voices = prefs.getVoices()
        if (voices.isEmpty()) return null

        val matched = voices.firstOrNull {
            it.locale().language.equals(language, ignoreCase = true)
        }

        return matched?.name
            ?: prefs.defaultVoice()?.name
            ?: voices.first().name
    }

    override fun onGetVoices(): List<Voice> {
        return prefs.getVoices().map { profile ->
            Voice(
                profile.name,
                profile.locale(),
                Voice.QUALITY_NORMAL,
                Voice.LATENCY_VERY_HIGH,
                true,
                emptySet()
            )
        }
    }

    override fun onIsValidVoiceName(voiceName: String): Int {
        val valid = prefs.getVoices().any { it.name == voiceName }
        return if (valid) TextToSpeech.SUCCESS else TextToSpeech.ERROR
    }

    override fun onStop() {
        stopped.set(true)
        activeCall?.cancel()
    }

    override fun onSynthesizeText(
        request: SynthesisRequest,
        callback: SynthesisCallback
    ) {
        stopped.set(false)

        val text = request.getCharSequenceText()?.toString()?.trim().orEmpty()
        if (text.isEmpty()) {
            callback.done()
            return
        }

        val voices = prefs.getVoices()
        val requestedVoice = request.getVoiceName()

        val profile = voices.firstOrNull { it.name == requestedVoice }
            ?: prefs.defaultVoice()

        if (profile == null) {
            Log.e(TAG, "Voice profile not found")
            callback.error()
            return
        }

        if (prefs.apiKey.isBlank()) {
            Log.e(TAG, "API key is not configured")
            callback.error()
            return
        }

        if (profile.modelId.isBlank()) {
            Log.e(TAG, "Voice model id is empty")
            callback.error()
            return
        }

        if (prefs.ttsModel.isBlank()) {
            Log.e(TAG, "TTS model is empty")
            callback.error()
            return
        }

        val speed = (request.getSpeechRate() / 100f).coerceIn(0.5f, 2.0f)
        val pitch = (request.getPitch() / 100f).coerceIn(0.5f, 2.0f)

        val chunks = TextChunker.split(
            text = text,
            locale = profile.locale(),
            maxChars = prefs.maxChunkChars
        )

        val state = SynthState()

        try {
            for (chunk in chunks) {
                if (stopped.get() || callback.isCancelled()) {
                    callback.error()
                    return
                }

                val key = cache.keyFor(
                    text = chunk,
                    voiceModelId = profile.modelId,
                    ttsModel = prefs.ttsModel,
                    speed = speed,
                    pitch = pitch,
                    sampleRate = prefs.sampleRate,
                    format = prefs.format
                )

                val cached = if (prefs.cacheEnabled) cache.get(key) else null

                if (cached != null) {
                    val meta = cache.readMeta(cached)

                    if (meta != null) {
                        val info = WavParser.PcmInfo(
                            sampleRate = meta.sampleRate,
                            channels = meta.channels,
                            encoding = meta.encoding()
                        )

                        if (!startIfNeeded(info, state, callback)) {
                            callback.error()
                            return
                        }

                        if (!feedFile(cached.pcm, callback)) {
                            callback.error()
                            return
                        }

                        continue
                    } else {
                        cache.delete(cached)
                    }
                }

                val writer = if (prefs.cacheEnabled) {
                    cache.beginWrite(key)
                } else {
                    null
                }

                val success = fetchAndFeed(
                    chunk = chunk,
                    voiceModelId = profile.modelId,
                    ttsModel = prefs.ttsModel,
                    speed = speed,
                    pitch = pitch,
                    state = state,
                    callback = callback,
                    writer = writer
                )

                if (!success) {
                    writer?.cancel()
                    callback.error()
                    return
                }

                val info = state.info
                if (info == null) {
                    writer?.cancel()
                    callback.error()
                    return
                }

                writer?.finish(metaFrom(info))
            }

            callback.done()
        } catch (e: Exception) {
            Log.e(TAG, "Synthesis failed", e)
            callback.error()
        }
    }

    private fun expectedInfo(): WavParser.PcmInfo {
        return WavParser.PcmInfo(
            sampleRate = prefs.sampleRate,
            channels = 1,
            encoding = AudioFormat.ENCODING_PCM_16BIT
        )
    }

    private fun metaFrom(info: WavParser.PcmInfo): PcmCache.Meta {
        val bits = if (info.encoding == AudioFormat.ENCODING_PCM_16BIT) 16 else 8
        return PcmCache.Meta(
            sampleRate = info.sampleRate,
            channels = info.channels,
            bits = bits
        )
    }

    private fun startIfNeeded(
        info: WavParser.PcmInfo,
        state: SynthState,
        callback: SynthesisCallback
    ): Boolean {
        if (state.started) {
            val current = state.info ?: return false

            return current.sampleRate == info.sampleRate &&
                    current.channels == info.channels &&
                    current.encoding == info.encoding
        }

        val result = callback.start(
            info.sampleRate,
            info.encoding,
            info.channels
        )

        if (result != TextToSpeech.SUCCESS) {
            return false
        }

        state.started = true
        state.info = info

        return true
    }

    private fun feedFile(file: File, callback: SynthesisCallback): Boolean {
        FileInputStream(file).use { input ->
            val buffer = ByteArray(AUDIO_BUFFER_SIZE)

            while (true) {
                if (stopped.get() || callback.isCancelled()) {
                    return false
                }

                val read = input.read(buffer)
                if (read <= 0) break

                if (callback.audioAvailable(buffer, 0, read) != TextToSpeech.SUCCESS) {
                    return false
                }
            }
        }

        return true
    }

    private fun fetchAndFeed(
        chunk: String,
        voiceModelId: String,
        ttsModel: String,
        speed: Float,
        pitch: Float,
        state: SynthState,
        callback: SynthesisCallback,
        writer: PcmCache.Writer?
    ): Boolean {
        val params = FishApiClient.TtsParams(
            text = chunk,
            voiceModelId = voiceModelId,
            ttsModel = ttsModel,
            speed = speed,
            pitch = pitch,
            sampleRate = prefs.sampleRate,
            format = prefs.format
        )

        val call = api.createCall(params)
        activeCall = call

        return try {
            val response = call.execute()

            response.use { resp ->
                if (!resp.isSuccessful) {
                    val errorBody = try {
                        resp.body?.string()?.take(500)
                    } catch (e: Exception) {
                        null
                    }

                    Log.e(TAG, "Fish API HTTP ${resp.code}: $errorBody")
                    return@use false
                }

                val contentType = resp.header("Content-Type").orEmpty()
                if (contentType.contains("application/json", ignoreCase = true)) {
                    val json = try {
                        resp.body?.string()?.take(1000)
                    } catch (e: Exception) {
                        null
                    }

                    Log.e(TAG, "Fish API returned JSON instead of audio: $json")
                    return@use false
                }

                val body = resp.body ?: return@use false

                val input = BufferedInputStream(body.byteStream(), 16 * 1024)
                input.mark(4096)

                val head = ByteArray(12)
                val headRead = readAtLeast(input, head, 12)
                input.reset()

                val isWav = headRead == 12 && WavParser.isWavHeader(head)

                val info: WavParser.PcmInfo
                val dataStream: InputStream

                if (isWav) {
                    val parsed = WavParser.parse(input) ?: return@use false
                    info = parsed.info
                    dataStream = parsed.stream
                } else {
                    info = expectedInfo()
                    dataStream = input
                }

                if (!startIfNeeded(info, state, callback)) {
                    return@use false
                }

                val buffer = ByteArray(AUDIO_BUFFER_SIZE)
                var total = 0L

                while (true) {
                    if (stopped.get() || callback.isCancelled()) {
                        return@use false
                    }

                    val read = dataStream.read(buffer)
                    if (read <= 0) break

                    total += read
                    writer?.write(buffer, 0, read)

                    if (callback.audioAvailable(buffer, 0, read) != TextToSpeech.SUCCESS) {
                        return@use false
                    }
                }

                total > 0L
            }
        } catch (e: IOException) {
            if (!stopped.get()) {
                Log.e(TAG, "Network I/O failed", e)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected synthesis failure", e)
            false
        } finally {
            activeCall = null
        }
    }

    private fun readAtLeast(input: InputStream, buffer: ByteArray, length: Int): Int {
        var offset = 0

        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read < 0) break
            offset += read
        }

        return offset
    }
}
