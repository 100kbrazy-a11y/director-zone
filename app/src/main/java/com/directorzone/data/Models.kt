package com.directorzone.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

@Serializable
data class Adjust(
    val exposure: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f,
    val warmth: Float = 0f, val tint: Float = 0f, val highlights: Float = 0f, val shadows: Float = 0f,
    val vignette: Float = 0f, val grain: Float = 0f, val fade: Float = 0f, val hue: Float = 0f,
    val glow: Float = 0f, val chroma: Float = 0f, val sharpen: Float = 0f,
) {
    operator fun plus(o: Adjust) = Adjust(exposure + o.exposure, contrast + o.contrast, saturation + o.saturation, warmth + o.warmth, tint + o.tint, highlights + o.highlights, shadows + o.shadows,
        vignette + o.vignette, grain + o.grain, fade + o.fade, hue + o.hue, glow + o.glow, chroma + o.chroma, sharpen + o.sharpen)
    fun scaled(k: Float) = Adjust(exposure * k, contrast * k, saturation * k, warmth * k, tint * k, highlights * k, shadows * k, vignette * k, grain * k, fade * k, hue * k, glow * k, chroma * k, sharpen * k)
}

@Serializable
data class Clip(
    val id: String = newId(),
    val uri: String,
    val isImage: Boolean = false,
    val srcDurMs: Long,
    val inMs: Long = 0,
    val outMs: Long = srcDurMs,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val filter: String = "none",
    val filterAmount: Float = 1f,
    val adjust: Adjust = Adjust(),
    val transition: String = "none",   // transition INTO this clip from the previous one
    val transitionMs: Int = 600,
    val kenBurns: Boolean = false,
    val effect: String = "none",       // look preset from the Effects sheet, stacked on top of filter + adjust
    val effectAmount: Float = 0.7f,
) {
    /** Length on the timeline, after speed. */
    val lengthMs: Long get() = ((outMs - inMs) / speed).toLong().coerceAtLeast(100)
}

@Serializable
data class TextItem(
    val id: String = newId(),
    val text: String = "Your text",
    val startMs: Long = 0,
    val durMs: Long = 2500,
    val anim: String = "pop",
    val font: String = "bold",
    val color: String = "FFFFFF",
    val size: Float = 0.08f,        // fraction of canvas height
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val pill: Boolean = false,
    val pillColor: String = "000000",
    val shadow: Boolean = true,
    val sticker: Boolean = false,    // emoji sticker
    val rotation: Float = 0f,
    val align: String = "center",   // left | center | right
    val letterSp: Float = 0f,       // extra tracking, fraction of font size
    val lineSp: Float = 1.18f,      // line height multiplier
    val outlineW: Float = 0f,       // outline width, fraction of font size (0 = off)
    val outlineColor: String = "000000",
    val glow: Float = 0f,           // 0..1 coloured glow in the text colour
    val bgAlpha: Float = 0.92f,
)

@Serializable
data class SoundItem(
    val id: String = newId(),
    val name: String,
    val source: String,             // "asset:///sfx/x.wav" or a file:// / content:// uri
    val kind: String = "sfx",       // sfx | music | voice
    val startMs: Long = 0,
    val durMs: Long,
    val volume: Float = 1f,
    val fadeInMs: Int = 0,
    val fadeOutMs: Int = 0,
    val loop: Boolean = false,
    val inMs: Long = 0,             // trim: offset into the source file
    val srcMs: Long = 0,            // full source length (0 = same as durMs)
)

@Serializable
data class PhoneCfg(
    val enabled: Boolean = false,
    val bezel: String = "titanium",
    val anim: String = "float",
    val bg: String = "aurora",
    val scale: Float = 1f,
    val shadow: Boolean = true,
    val tempo: Float = 1f,
    val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f,   // static rotation offsets, degrees
    val motion: Float = 1f,         // movement intensity of the animation preset
    val depth: Float = 1f,          // phone thickness
    val glare: Float = 1f,          // screen reflection
    val light: Float = 1f,          // body lighting
    val offX: Float = 0f, val offY: Float = 0f,
)

@Serializable
data class Project(
    val id: String = newId(),
    val name: String = "Untitled",
    val clips: List<Clip> = emptyList(),
    val texts: List<TextItem> = emptyList(),
    val sounds: List<SoundItem> = emptyList(),
    val phone: PhoneCfg = PhoneCfg(),
    val ratio: String = "9:16",
    val fit: Boolean = false,       // fit (letterbox) instead of fill (crop)
    val autoSfx: Boolean = true,    // drop a matching whoosh on every transition
    val favorite: Boolean = false,
    val folder: String = "",
    val lockV: Boolean = false, val lockT: Boolean = false, val hideT: Boolean = false,
    val lockA: Boolean = false, val muteA: Boolean = false,
    val exports: Int = 0,
    val updated: Long = System.currentTimeMillis(),
) {
    val durationMs: Long get() = clips.sumOf { it.lengthMs }
}

@Serializable
data class ExportOptions(val shortSide: Int = 1080, val fps: Int = 30, val bitrateMbps: Int = 12, val hevc: Boolean = false)
