package com.example.fishtts

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

object Mp3Decoder {

    data class DecodedPcm(
        val pcmFile: File,
        val sampleRate: Int,
        val channels: Int
    )

    fun decode(mp3File: File, outputPcm: File): DecodedPcm? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(mp3File.path)

            if (extractor.trackCount == 0) return null

            var trackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    trackIndex = i
                    format = f
                    break
                }
            }

            if (trackIndex < 0 || format == null) return null

            extractor.selectTrack(trackIndex)

            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            val decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            decoder.configure(format, null, null, 0)
            decoder.start()

            FileOutputStream(outputPcm).use { out ->
                val bufferInfo = MediaCodec.BufferInfo()
                var isEOS = false

                while (true) {
                    if (!isEOS) {
                        val inputIndex = decoder.dequeueInputBuffer(10000)
                        if (inputIndex >= 0) {
                            val inputBuffer = decoder.getInputBuffer(inputIndex) ?: break
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                decoder.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                decoder.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    var outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                    while (outputIndex >= 0) {
                        val outputBuffer = decoder.getOutputBuffer(outputIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            val pcm = ByteArray(bufferInfo.size)
                            outputBuffer.get(pcm)
                            out.write(pcm)
                        }
                        decoder.releaseOutputBuffer(outputIndex, false)
                        outputIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                    }

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
                }
            }

            decoder.stop()
            decoder.release()

            return DecodedPcm(outputPcm, sampleRate, channels)
        } catch (e: Exception) {
            return null
        } finally {
            extractor.release()
        }
    }
}
