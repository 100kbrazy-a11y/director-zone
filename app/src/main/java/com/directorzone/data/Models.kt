package com.directorzone.data

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().take(8)

@Serializable
data class Adjust(
    val exposure: Float = 0f, val contrast: Float = 0f, val saturation: Float = 0f,
    val warmth: Float = 0f, val tint: Float = 0f, val highlights: Float = 0f, val shadows: Float = 0f,
    val vignette: Float = 0f, val grain: Float = 0f, val fade: Float = 0f, val hue: Float = 0f,
    val glow: Float = 0f, val chroma: Float = 0f,
)

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
    val updated: Long = System.currentTimeMillis(),
) {
    val durationMs: Long get() = clips.sumOf { it.lengthMs }
}

@Serializable
data class ExportOptions(val shortSide: Int = 1080, val fps: Int = 30, val bitrateMbps: Int = 12, val hevc: Boolean = false)
