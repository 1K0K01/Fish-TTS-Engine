package com.example.fishtts

import android.content.Context
import android.media.AudioFormat
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class PcmCache(context: Context) {

    data class Entry(
        val pcm: File,
        val meta: File
    )

    data class Meta(
        val sampleRate: Int,
        val channels: Int,
        val bits: Int
    ) {
        fun encoding(): Int {
            return if (bits == 16) {
                AudioFormat.ENCODING_PCM_16BIT
            } else {
                AudioFormat.ENCODING_PCM_8BIT
            }
        }
    }

    class Writer internal constructor(
        private val pcmTemp: File,
        private val metaFile: File
    ) {
        private val out = FileOutputStream(pcmTemp)
        private var closed = false

        fun write(buffer: ByteArray, offset: Int, length: Int) {
            out.write(buffer, offset, length)
        }

        fun finish(meta: Meta) {
            if (!closed) {
                out.flush()
                out.close()
                closed = true
            }

            val json = JSONObject()
            json.put("sampleRate", meta.sampleRate)
            json.put("channels", meta.channels)
            json.put("bits", meta.bits)

            metaFile.writeText(json.toString())

            val finalFile = File(pcmTemp.parentFile, pcmTemp.nameWithoutExtension + ".pcm")
            if (finalFile.exists()) {
                finalFile.delete()
            }

            pcmTemp.renameTo(finalFile)
        }

        fun cancel() {
            try {
                if (!closed) {
                    out.close()
                    closed = true
                }
            } catch (_: Exception) {
            }

            pcmTemp.delete()
        }
    }

    private val dir = File(context.cacheDir, "fish_tts_pcm").apply {
        mkdirs()
    }

    fun keyFor(
        text: String,
        voiceModelId: String,
        ttsModel: String,
        speed: Float,
        pitch: Float,
        sampleRate: Int,
        format: String
    ): String {
        val raw = listOf(
            text,
            voiceModelId,
            ttsModel,
            speed.toString(),
            pitch.toString(),
            sampleRate.toString(),
            format
        ).joinToString("|")

        return sha256(raw)
    }

    fun get(key: String): Entry? {
        val pcm = File(dir, "$key.pcm")
        val meta = File(dir, "$key.meta")

        return if (pcm.exists() && pcm.length() > 0 && meta.exists()) {
            Entry(pcm, meta)
        } else {
            null
        }
    }

    fun readMeta(entry: Entry): Meta? {
        return try {
            val json = JSONObject(entry.meta.readText())
            Meta(
                sampleRate = json.optInt("sampleRate", 44100),
                channels = json.optInt("channels", 1),
                bits = json.optInt("bits", 16)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun beginWrite(key: String): Writer {
        val temp = File(dir, "$key.tmp")
        if (temp.exists()) {
            temp.delete()
        }

        val meta = File(dir, "$key.meta")
        return Writer(temp, meta)
    }

    fun delete(entry: Entry) {
        entry.pcm.delete()
        entry.meta.delete()
    }

    fun clear() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
