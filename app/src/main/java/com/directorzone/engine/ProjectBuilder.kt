package com.directorzone.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.Size
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TimestampAdjustment
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.directorzone.catalog.*
import com.directorzone.data.*
import com.google.common.collect.ImmutableList
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ProjectBuilder {
    fun canvasSize(ratioId: String, shortSide: Int): Size {
        val r = ratioById(ratioId)
        fun even(v: Float) = (v.roundToInt() / 2) * 2
        return if (r.w < r.h) Size(even(shortSide.toFloat()), even(shortSide * r.h.toFloat() / r.w))
        else Size(even(shortSide * r.w.toFloat() / r.h), even(shortSide.toFloat()))
    }

    private fun silence(ms: Long): EditedMediaItem = EditedMediaItem.Builder(
        MediaItem.Builder().setUri(Uri.parse("asset:///sfx/_silence.wav"))
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(ms).build()).build()
    ).setRemoveVideo(true).setDurationUs(1_000_000).build()

    fun build(ctx: Context, p: Project, shortSide: Int, fps: Int = 30): Composition {
        val canvas = canvasSize(p.ratio, shortSide)
        val items = ArrayList<EditedMediaItem>()
        p.clips.forEachIndexed { i, clip ->
            val clipSec = clip.lengthMs / 1000f
            val fx = ArrayList<Effect>()
            val audio = ArrayList<AudioProcessor>()
            if (!clip.isImage && clip.speed != 1f) {
                // TimestampAdjustment (not SpeedChangeEffect) is the speed effect CompositionPlayer supports for live preview
                val sp = clip.speed
                fx.add(TimestampAdjustment({ inUs, out -> out.onTimestamp((inUs / sp).toLong()) }, ConstantSpeed(sp)))
                audio.add(SpeedChangingAudioProcessor(ConstantSpeed(clip.speed)))
            }
            if (p.phone.enabled) {
                fx.add(Presentation.createForHeight(((canvas.height * 0.8f).toInt() / 2) * 2))
            } else {
                fx.add(Presentation.createForWidthAndHeight(canvas.width, canvas.height, if (p.fit) Presentation.LAYOUT_SCALE_TO_FIT else Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))
            }
            // skip the colour pass entirely when it would be an identity (keeps preview light)
            if (clip.filter != "none" || clip.adjust != Adjust() || clip.kenBurns || clip.isImage)
                fx.add(GradeEffect(filterById(clip.filter), clip.filterAmount, clip.adjust, clip.kenBurns || clip.isImage, clipSec))
            if (p.phone.enabled) fx.add(PhoneEffect(p.phone, canvas, clipSec))
            // transition halves
            val tIn = if (i > 0) transitionById(clip.transition) else TRANSITIONS[0]
            val next = p.clips.getOrNull(i + 1)
            val tOut = if (next != null) transitionById(next.transition) else TRANSITIONS[0]
            if (tIn.mode != 0) fx.add(TransitionEffect(tIn.mode, true, min(clip.transitionMs / 1000f, clipSec / 2.2f), clipSec))
            if (tOut.mode != 0 && next != null) fx.add(TransitionEffect(tOut.mode, false, min(next.transitionMs / 1000f, clipSec / 2.2f), clipSec))
            if (!clip.isImage) audio.add(GainProcessor(clip.volume, 0, 0, clip.lengthMs))

            val mi = if (clip.isImage) MediaItem.fromUri(Uri.parse(clip.uri))
            else MediaItem.Builder().setUri(Uri.parse(clip.uri))
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.inMs).setEndPositionMs(clip.outMs).build()).build()
            val eb = EditedMediaItem.Builder(mi).setEffects(Effects(audio, fx)).setFrameRate(fps)
            eb.setDurationUs(if (clip.isImage) (clip.outMs - clip.inMs) * 1000 else clip.srcDurMs * 1000)
            items.add(eb.build())
        }
        val sequences = ArrayList<EditedMediaItemSequence>()
        sequences.add(EditedMediaItemSequence.Builder(items).build())

        // ---- sound lanes
        val total = p.durationMs
        val sounds = ArrayList<SoundItem>(p.sounds)
        if (p.autoSfx) {
            var t = 0L
            p.clips.forEachIndexed { i, c ->
                if (i > 0) {
                    val tr = transitionById(c.transition)
                    val sn = tr.sound?.let { SoundLib.byName(it) }
                    if (sn != null) sounds.add(SoundItem(name = sn.name, source = sn.assetUri, startMs = max(0, t - 160), durMs = sn.ms.toLong(), volume = 0.8f))
                }
                t += c.lengthMs
            }
        }
        class Lane { var end = 0L; val parts = ArrayList<Pair<Long, EditedMediaItem>>() }
        val lanes = ArrayList<Lane>()
        for (s in sounds.sortedBy { it.startMs }) {
            if (s.startMs >= total) continue
            var pieces = listOf(s)
            if (s.loop) {
                val list = ArrayList<SoundItem>(); var at = s.startMs
                while (at < total && list.size < 60) { list.add(s.copy(startMs = at)); at += s.durMs }
                pieces = list
            }
            for (pc in pieces) {
                val len = min(pc.durMs, total - pc.startMs)
                if (len < 50) continue
                val lane = lanes.firstOrNull { it.end <= pc.startMs } ?: Lane().also { lanes.add(it) }
                val item = EditedMediaItem.Builder(
                    MediaItem.Builder().setUri(Uri.parse(pc.source))
                        .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0).setEndPositionMs(len).build()).build()
                ).setRemoveVideo(true).setDurationUs(pc.durMs * 1000)
                    .setEffects(Effects(listOf<AudioProcessor>(GainProcessor(pc.volume, pc.fadeInMs, pc.fadeOutMs, len)), ImmutableList.of<Effect>()))
                    .build()
                lane.parts.add((pc.startMs - lane.end) to item)
                lane.end = pc.startMs + len
            }
        }
        for (lane in lanes) {
            val b = EditedMediaItemSequence.Builder(ImmutableList.of<EditedMediaItem>())
            for ((gap, item) in lane.parts) {
                var rem = gap
                while (rem > 0) { val l = min(rem, 1000L); b.addItem(silence(l)); rem -= l }
                b.addItem(item)
            }
            sequences.add(b.build())
        }
        val vfx = ArrayList<Effect>()
        if (p.texts.isNotEmpty()) vfx.add(OverlayEffect(ImmutableList.of<androidx.media3.effect.TextureOverlay>(TextBitmapOverlay(p.texts, canvas.width, canvas.height))))
        return Composition.Builder(sequences)
            .setEffects(Effects(ImmutableList.of<AudioProcessor>(), vfx))
            .experimentalSetForceAudioTrack(true)
            .build()
    }
}
