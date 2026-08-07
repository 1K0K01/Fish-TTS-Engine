package com.example.fishtts

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/**
 * 합성된 PCM을 캐시합니다. 저장 위치는 우선순위대로 결정됩니다.
 *
 * 1) 사용자가 [linkFolder]로 직접 고른 폴더(SAF) — 연동돼 있으면 항상 최우선
 * 2) (기본값) 다운로드 폴더 안의 `Download/FishTTSCache/` — 사용자가 아무것도
 *    설정하지 않아도 안드로이드 10(API 29) 이상에서는 권한 요청 없이 자동으로
 *    여기에 저장됩니다. 앱을 지워도 남아 있습니다.
 * 3) 그마저 안 되는 아주 오래된 기기(API 28 이하)에서는 앱 내부 캐시로 대체됩니다.
 *    (이 경우엔 앱 삭제 시 함께 사라집니다.)
 */
class PcmCache(private val context: Context) {

    data class Entry(val key: String)

    data class Meta(
        val sampleRate: Int,
        val channels: Int,
        val bits: Int
    ) {
        fun encoding(): Int {
            return if (bits == 16) AudioFormat.ENCODING_PCM_16BIT else AudioFormat.ENCODING_PCM_8BIT
        }
    }

    inner class Writer internal constructor(
        private val key: String,
        private val out: OutputStream
    ) {
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
            val json = JSONObject().apply {
                put("sampleRate", meta.sampleRate)
                put("channels", meta.channels)
                put("bits", meta.bits)
            }
            backend().openWrite("$key.meta").use { it.write(json.toString().toByteArray()) }
        }

        fun cancel() {
            try {
                if (!closed) { out.close(); closed = true }
            } catch (_: Exception) {
            }
            backend().delete("$key.pcm")
        }
    }

    // ===== 저장소 백엔드 추상화 =====

    private interface Backend {
        val label: String
        fun exists(name: String): Boolean
        fun length(name: String): Long
        fun openRead(name: String): InputStream?
        fun openWrite(name: String): OutputStream
        fun delete(name: String)
        fun listNames(): List<String>
    }

    private class InternalBackend(private val dir: File) : Backend {
        override val label = "앱 내부 저장소"
        init { dir.mkdirs() }
        override fun exists(name: String) = File(dir, name).exists()
        override fun length(name: String) = File(dir, name).length()
        override fun openRead(name: String): InputStream? {
            val f = File(dir, name)
            return if (f.exists()) f.inputStream() else null
        }
        override fun openWrite(name: String): OutputStream = FileOutputStream(File(dir, name))
        override fun delete(name: String) { File(dir, name).delete() }
        override fun listNames(): List<String> = dir.listFiles()?.map { it.name } ?: emptyList()
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private inner class MediaStoreBackend : Backend {
        override val label = "다운로드 폴더 (Download/FishTTSCache)"
        private val relativePath = "Download/FishTTSCache/"
        private val collection: Uri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        private val resolver get() = context.contentResolver

        private fun findUri(name: String): Uri? {
            val projection = arrayOf(MediaStore.Downloads._ID)
            val selection = "${MediaStore.Downloads.DISPLAY_NAME}=? AND ${MediaStore.Downloads.RELATIVE_PATH}=?"
            val args = arrayOf(name, relativePath)
            resolver.query(collection, projection, selection, args, null)?.use { c ->
                if (c.moveToFirst()) {
                    return ContentUris.withAppendedId(collection, c.getLong(0))
                }
            }
            return null
        }

        override fun exists(name: String) = findUri(name) != null

        override fun length(name: String): Long {
            val uri = findUri(name) ?: return 0L
            resolver.query(uri, arrayOf(MediaStore.Downloads.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getLong(0)
            }
            return 0L
        }

        override fun openRead(name: String): InputStream? {
            val uri = findUri(name) ?: return null
            return resolver.openInputStream(uri)
        }

        override fun openWrite(name: String): OutputStream {
            findUri(name)?.let { resolver.delete(it, null, null) }
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            }
            val uri = resolver.insert(collection, values)
                ?: throw IOException("다운로드 폴더에 캐시 파일을 만들 수 없습니다")
            return resolver.openOutputStream(uri)
                ?: throw IOException("캐시 파일을 열 수 없습니다")
        }

        override fun delete(name: String) {
            findUri(name)?.let { resolver.delete(it, null, null) }
        }

        override fun listNames(): List<String> {
            val names = mutableListOf<String>()
            val projection = arrayOf(MediaStore.Downloads.DISPLAY_NAME)
            val selection = "${MediaStore.Downloads.RELATIVE_PATH}=?"
            val args = arrayOf(relativePath)
            resolver.query(collection, projection, selection, args, null)?.use { c ->
                val idx = c.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                while (c.moveToNext()) names.add(c.getString(idx))
            }
            return names
        }
    }

    companion object {
        private const val PREFS_NAME = "fish_tts_cache_prefs"
        private const val KEY_TREE_URI = "cache_tree_uri"
        private const val SUBDIR_NAME = "fish_tts_pcm"
        private const val URI_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val internalDir = File(context.cacheDir, SUBDIR_NAME)

    // ===== 사용자 지정 폴더(SAF) 연동 =====

    fun isLinked(): Boolean = linkedTreeUri() != null

    fun linkedFolderLabel(): String? {
        val uri = linkedTreeUri() ?: return null
        return DocumentFile.fromTreeUri(context, uri)?.name
    }

    fun linkedTreeUri(): Uri? {
        val raw = prefs.getString(KEY_TREE_URI, null) ?: return null
        return try {
            val uri = Uri.parse(raw)
            val stillGranted = context.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
            if (stillGranted) uri else null
        } catch (e: Exception) {
            null
        }
    }

    fun linkFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, URI_FLAGS)
        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun unlinkFolder() {
        val uri = linkedTreeUri()
        if (uri != null) {
            try {
                context.contentResolver.releasePersistableUriPermission(uri, URI_FLAGS)
            } catch (_: Exception) {
            }
        }
        prefs.edit().remove(KEY_TREE_URI).apply()
    }

    /** 지금 실제로 쓰이고 있는 저장 위치 설명 (설정 화면 표시용) */
    fun currentStorageLabel(): String = backend().label

    private fun safDocDir(): DocumentFile? {
        val treeUri = linkedTreeUri() ?: return null
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return null
        val existing = root.findFile(SUBDIR_NAME)
        if (existing != null && existing.isDirectory) return existing
        return root.createDirectory(SUBDIR_NAME)
    }

    private fun backend(): Backend {
        val saf = safDocDir()
        if (saf != null) return RealSafBackend(context, saf)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStoreBackend()
        } else {
            InternalBackend(internalDir)
        }
    }

    private class RealSafBackend(
        private val context: Context,
        private val dir: DocumentFile
    ) : Backend {
        override val label = dir.name ?: "연동된 폴더"
        override fun exists(name: String) = dir.findFile(name) != null
        override fun length(name: String) = dir.findFile(name)?.length() ?: 0L
        override fun openRead(name: String): InputStream? {
            val doc = dir.findFile(name) ?: return null
            return context.contentResolver.openInputStream(doc.uri)
        }
        override fun openWrite(name: String): OutputStream {
            dir.findFile(name)?.delete()
            val doc = dir.createFile("application/octet-stream", name)
                ?: throw IOException("연동된 폴더에 캐시 파일을 만들 수 없습니다")
            return context.contentResolver.openOutputStream(doc.uri)
                ?: throw IOException("연동된 폴더의 캐시 파일을 열 수 없습니다")
        }
        override fun delete(name: String) { dir.findFile(name)?.delete() }
        override fun listNames(): List<String> = dir.listFiles().mapNotNull { it.name }
    }

    // ===== 캐시 키 =====

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
            text, voiceModelId, ttsModel, speed.toString(), pitch.toString(),
            sampleRate.toString(), format
        ).joinToString("|")
        return sha256(raw)
    }

    // ===== 조회/쓰기 =====

    fun get(key: String): Entry? {
        val b = backend()
        return if (b.exists("$key.pcm") && b.length("$key.pcm") > 0 && b.exists("$key.meta")) {
            Entry(key)
        } else {
            null
        }
    }

    fun readMeta(entry: Entry): Meta? {
        val text = backend().openRead("${entry.key}.meta")?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: return null

        return try {
            val json = JSONObject(text)
            Meta(
                sampleRate = json.optInt("sampleRate", 44100),
                channels = json.optInt("channels", 1),
                bits = json.optInt("bits", 16)
            )
        } catch (e: Exception) {
            null
        }
    }

    fun openPcmInputStream(entry: Entry): InputStream? = backend().openRead("${entry.key}.pcm")

    fun beginWrite(key: String): Writer = Writer(key, backend().openWrite("$key.pcm"))

    fun delete(entry: Entry) {
        backend().delete("${entry.key}.pcm")
        backend().delete("${entry.key}.meta")
    }

    fun clear() {
        val b = backend()
        b.listNames().forEach { b.delete(it) }
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
