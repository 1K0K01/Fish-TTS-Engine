package com.example.fishtts

import android.media.AudioFormat
import java.io.InputStream

object WavParser {

    data class PcmInfo(
        val sampleRate: Int,
        val channels: Int,
        val encoding: Int
    )

    data class ParseResult(
        val info: PcmInfo,
        val stream: InputStream
    )

    fun isWavHeader(header: ByteArray): Boolean {
        if (header.size < 12) return false

        val riff = String(header, 0, 4, Charsets.US_ASCII)
        val wave = String(header, 8, 4, Charsets.US_ASCII)

        return riff == "RIFF" && wave == "WAVE"
    }

    fun parse(input: InputStream): ParseResult? {
        val riffHeader = readExactly(input, 12) ?: return null
        if (!isWavHeader(riffHeader)) return null

        var fmt: PcmInfo? = null

        while (true) {
            val chunkHeader = readExactly(input, 8) ?: return null

            val chunkId = String(chunkHeader, 0, 4, Charsets.US_ASCII)
            val chunkSize = uintLE(chunkHeader, 4)

            if (chunkId == "fmt ") {
                if (chunkSize > 4096L) return null

                val fmtBytes = readExactly(input, chunkSize.toInt()) ?: return null

                if (fmtBytes.size >= 16) {
                    val audioFormat = shortLE(fmtBytes, 0)
                    val channels = shortLE(fmtBytes, 2)
                    val sampleRate = intLE(fmtBytes, 4)
                    val bitsPerSample = shortLE(fmtBytes, 14)

                    if (
                        (audioFormat == 1 || audioFormat == 0xFFFE) &&
                        bitsPerSample == 16 &&
                        sampleRate > 0 &&
                        channels > 0
                    ) {
                        fmt = PcmInfo(
                            sampleRate = sampleRate,
                            channels = channels,
                            encoding = AudioFormat.ENCODING_PCM_16BIT
                        )
                    }
                }

                if (chunkSize % 2L == 1L) {
                    skipFully(input, 1)
                }
            } else if (chunkId == "data") {
                val info = fmt ?: return null

                val dataStream = if (chunkSize == 0L) {
                    input
                } else {
                    BoundedInputStream(input, chunkSize)
                }

                return ParseResult(info, dataStream)
            } else {
                skipFully(input, chunkSize)
                if (chunkSize % 2L == 1L) {
                    skipFully(input, 1)
                }
            }
        }
    }

    private fun readExactly(input: InputStream, length: Int): ByteArray? {
        val buffer = ByteArray(length)
        var offset = 0

        while (offset < length) {
            val read = input.read(buffer, offset, length - offset)
            if (read < 0) return null
            offset += read
        }

        return buffer
    }

    private fun skipFully(input: InputStream, length: Long) {
        var remaining = length

        while (remaining > 0) {
            val skipped = input.skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else {
                val b = input.read()
                if (b == -1) break
                remaining--
            }
        }
    }

    private fun intLE(b: ByteArray, offset: Int): Int {
        return (b[offset].toInt() and 0xff) or
                ((b[offset + 1].toInt() and 0xff) shl 8) or
                ((b[offset + 2].toInt() and 0xff) shl 16) or
                ((b[offset + 3].toInt() and 0xff) shl 24)
    }

    private fun shortLE(b: ByteArray, offset: Int): Int {
        return (b[offset].toInt() and 0xff) or
                ((b[offset + 1].toInt() and 0xff) shl 8)
    }

    private fun uintLE(b: ByteArray, offset: Int): Long {
        return (b[offset].toLong() and 0xff) or
                ((b[offset + 1].toLong() and 0xff) shl 8) or
                ((b[offset + 2].toLong() and 0xff) shl 16) or
                ((b[offset + 3].toLong() and 0xff) shl 24)
    }

    private class BoundedInputStream(
        private val source: InputStream,
        private var remaining: Long
    ) : InputStream() {

        override fun read(): Int {
            if (remaining <= 0) return -1

            val b = source.read()
            if (b != -1) remaining--

            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1

            val toRead = minOf(len.toLong(), remaining).toInt()
            val read = source.read(b, off, toRead)

            if (read > 0) {
                remaining -= read
            }

            return read
        }

        override fun close() {
            source.close()
        }
    }
}
