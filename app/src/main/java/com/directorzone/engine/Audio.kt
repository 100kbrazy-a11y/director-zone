package com.directorzone.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

/** Volume + linear fade in/out for 16-bit PCM. `totalMs` is the processed item's length. */
class GainProcessor(private val volume: Float, private val fadeInMs: Int, private val fadeOutMs: Int, private val totalMs: Long) : BaseAudioProcessor() {
    private var frames = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun onFlush() { frames = 0 }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val n = inputBuffer.remaining()
        if (n == 0) return
        val out = replaceOutputBuffer(n).order(ByteOrder.nativeOrder())
        val ch = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate.toFloat()
        val src = inputBuffer.order(ByteOrder.nativeOrder())
        while (src.remaining() >= 2 * ch) {
            val tMs = frames * 1000f / rate
            var g = volume
            if (fadeInMs > 0 && tMs < fadeInMs) g *= tMs / fadeInMs
            val left = totalMs - tMs
            if (fadeOutMs > 0 && left < fadeOutMs) g *= (left / fadeOutMs).coerceIn(0f, 1f)
            for (k in 0 until ch) {
                val v = (src.short * g).toInt().coerceIn(-32768, 32767)
                out.putShort(v.toShort())
            }
            frames++
        }
        out.flip()
    }
}

class ConstantSpeed(private val s: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = s
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}
