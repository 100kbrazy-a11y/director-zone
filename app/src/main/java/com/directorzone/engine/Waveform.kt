package com.directorzone.engine

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.File
import java.nio.ByteOrder
import kotlin.math.abs

/** Real audio peaks (one byte per [BIN_MS] of source audio), decoded with MediaCodec and cached on disk. */
object Waveform {
    const val BIN_MS = 50

    fun peaks(ctx: Context, uri: String): ByteArray {
        val cache = File(File(ctx.cacheDir, "waves").apply { mkdirs() }, "w_${uri.hashCode().toUInt()}_${BIN_MS}.bin")
        if (cache.exists()) return cache.readBytes()
        val out = try { decode(ctx, uri) } catch (e: Throwable) { ByteArray(0) }
        try { cache.writeBytes(out) } catch (_: Exception) {}
        return out
    }

    private fun decode(ctx: Context, uri: String): ByteArray {
        val ex = MediaExtractor()
        if (uri.startsWith("asset:///")) {
            val fd = ctx.assets.openFd(uri.removePrefix("asset:///")); ex.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length); fd.close()
        } else ex.setDataSource(ctx, Uri.parse(uri), null)
        var track = -1
        for (i in 0 until ex.trackCount) if (ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
        if (track < 0) { ex.release(); return ByteArray(0) }
        ex.selectTrack(track)
        val fmt = ex.getTrackFormat(track)
        val codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(fmt, null, null, 0); codec.start()
        var rate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE); var ch = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val peaks = ArrayList<Byte>()
        var binFrames = rate * BIN_MS / 1000; var inBin = 0; var maxv = 0
        val info = MediaCodec.BufferInfo()
        var eosIn = false; var eosOut = false
        while (!eosOut) {
            if (!eosIn) {
                val ii = codec.dequeueInputBuffer(5000)
                if (ii >= 0) {
                    val buf = codec.getInputBuffer(ii)!!
                    val n = ex.readSampleData(buf, 0)
                    if (n < 0) { codec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); eosIn = true }
                    else { codec.queueInputBuffer(ii, 0, n, ex.sampleTime, 0); ex.advance() }
                }
            }
            val oi = codec.dequeueOutputBuffer(info, 5000)
            if (oi >= 0) {
                val ob = codec.getOutputBuffer(oi)!!.order(ByteOrder.nativeOrder())
                val sb = ob.asShortBuffer()
                val frames = sb.remaining() / ch
                for (f in 0 until frames) {
                    var m = 0
                    for (c in 0 until ch) m = maxOf(m, abs(sb.get(f * ch + c).toInt()))
                    if (m > maxv) maxv = m
                    if (++inBin >= binFrames) { peaks.add((maxv * 255 / 32768).coerceIn(0, 255).toByte()); inBin = 0; maxv = 0 }
                }
                codec.releaseOutputBuffer(oi, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eosOut = true
            } else if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val nf = codec.outputFormat; rate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE); ch = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT); binFrames = rate * BIN_MS / 1000
            }
        }
        codec.stop(); codec.release(); ex.release()
        return peaks.toByteArray()
    }
}
