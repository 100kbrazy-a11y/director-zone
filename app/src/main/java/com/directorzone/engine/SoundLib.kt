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
    val categories: List<String> get() = all.map { it.cat }.distinct()

    fun load(ctx: Context) {
        if (all.isNotEmpty()) return
        val txt = ctx.assets.open("sfx/index.json").bufferedReader().use { it.readText() }
        all = Json { ignoreUnknownKeys = true }.decodeFromString(txt)
    }

    fun byName(n: String) = all.firstOrNull { it.name == n }
}
