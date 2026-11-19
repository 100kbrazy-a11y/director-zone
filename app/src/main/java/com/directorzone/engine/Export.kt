package com.directorzone.engine

import android.content.ContentValues
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.*
import com.directorzone.data.ExportOptions
import com.directorzone.data.Project
import java.io.File

class Exporter(private val ctx: Context) {
    private var transformer: Transformer? = null
    private val handler = Handler(Looper.getMainLooper())

    fun export(p: Project, o: ExportOptions, onProgress: (Float) -> Unit, onDone: (Result<File>) -> Unit) {
        val out = File(ctx.cacheDir, "exports").apply { mkdirs() }.resolve("DirectorZone_${System.currentTimeMillis()}.mp4")
        val comp = try { ProjectBuilder.build(ctx, p, o.shortSide, o.fps) } catch (e: Exception) { onDone(Result.failure(e)); return }
        val enc = DefaultEncoderFactory.Builder(ctx)
            .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(o.bitrateMbps * 1_000_000).build())
            .setEnableFallback(true).build()
        val t = Transformer.Builder(ctx)
            .setVideoMimeType(if (o.hevc) MimeTypes.VIDEO_H265 else MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .setEncoderFactory(enc)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    handler.removeCallbacksAndMessages(null); onProgress(1f); onDone(Result.success(out))
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    android.util.Log.e("DZ", "export failed code=${exportException.errorCode} ${exportException.errorCodeName}", exportException)
                    handler.removeCallbacksAndMessages(null); onDone(Result.failure(exportException))
                }
            }).build()
        transformer = t
        t.start(comp, out.absolutePath)
        val holder = ProgressHolder()
        val poll = object : Runnable {
            override fun run() {
                val st = t.getProgress(holder)
                if (st != Transformer.PROGRESS_STATE_NOT_STARTED && st != Transformer.PROGRESS_STATE_UNAVAILABLE) onProgress(holder.progress / 100f)
                handler.postDelayed(this, 250)
            }
        }
        handler.postDelayed(poll, 250)
    }

    fun cancel() { handler.removeCallbacksAndMessages(null); transformer?.cancel() }

    companion object {
        /** Copies the finished file into the gallery (Movies/Director Zone). */
        fun saveToGallery(ctx: Context, f: File): Uri? {
            val cv = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, f.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) { put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/Director Zone"); put(MediaStore.Video.Media.IS_PENDING, 1) }
            }
            val uri = ctx.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: return null
            ctx.contentResolver.openOutputStream(uri)?.use { os -> f.inputStream().use { it.copyTo(os) } }
            if (Build.VERSION.SDK_INT >= 29) { cv.clear(); cv.put(MediaStore.Video.Media.IS_PENDING, 0); ctx.contentResolver.update(uri, cv, null, null) }
            return uri
        }
    }
}

class VoiceRecorder(private val ctx: Context) {
    private var rec: MediaRecorder? = null
    var file: File? = null
        private set

    fun start(): Boolean {
        val f = File(ctx.filesDir, "voice_${System.currentTimeMillis()}.m4a")
        return try {
            val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(ctx) else @Suppress("DEPRECATION") MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44100); r.setAudioEncodingBitRate(128_000)
            r.setOutputFile(f.absolutePath)
            r.prepare(); r.start()
            rec = r; file = f; true
        } catch (e: Exception) { rec = null; false }
    }

    fun stop(): File? {
        val r = rec ?: return null
        rec = null
        return try { r.stop(); r.release(); file } catch (e: Exception) { try { r.release() } catch (_: Exception) {}; null }
    }
}
