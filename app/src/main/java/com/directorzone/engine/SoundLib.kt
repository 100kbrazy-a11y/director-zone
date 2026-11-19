package com.directorzone.engine

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SoundMeta(val name: String, val cat: String, val file: String, val ms: Int, val loop: Boolean) {
    val assetUri get() = "asset:///sfx/$file"
}

object SoundLib {
    var all: List<SoundMeta> = emptyList()
        private set
    val categories: List<String> get() = ORDER.filter { c -> all.any { it.cat == c } }

    fun load(ctx: Context) {
        if (all.isNotEmpty()) return
        val txt = ctx.assets.open("sfx/index.json").bufferedReader().use { it.readText() }
        val raw: List<SoundMeta> = Json { ignoreUnknownKeys = true }.decodeFromString(txt)
        all = raw.map { it.copy(cat = CAT[it.name] ?: it.cat) }
    }

    /** The 11 library categories shown in the Audio sheet. */
    val ORDER = listOf("Whoosh", "Impact", "Transition", "UI", "Riser", "Drop", "Glitch", "Chime", "Ambient", "Cinematic", "Beats")
    private val CAT: Map<String, String> = mapOf(
        "Soft Whoosh" to "Whoosh", "Fast Whoosh" to "Whoosh", "Air Whoosh" to "Whoosh", "Cinematic Whoosh" to "Whoosh",
        "Swipe Left" to "Transition", "Swipe Right" to "Transition", "Flip" to "Transition", "Spin" to "Transition", "Rewind" to "Transition", "Swish In" to "Transition",
        "Thud" to "Impact", "Hit" to "Impact", "Slam" to "Impact", "Punch" to "Impact",
        "Bass Drop" to "Drop", "Boom" to "Drop", "Power Down" to "Drop",
        "Riser Short" to "Riser", "Riser Long" to "Riser", "Reverse Cymbal" to "Riser",
        "Glass Hit" to "Cinematic", "Dreamy Swell" to "Cinematic", "Cinematic Pad" to "Cinematic",
        "Calm Pad" to "Ambient", "Dreamy Pad" to "Ambient", "Tech Pad" to "Ambient",
        "Shutter" to "UI", "Key Press" to "UI", "Mouse Click" to "UI", "Typewriter" to "UI",
    )

    fun byName(n: String) = all.firstOrNull { it.name == n }
}
