package com.directorzone.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateMapOf

/** App settings. Backed by SharedPreferences, readable from Compose (snapshot state), so changes apply instantly. */
object Prefs {
    private val map = mutableStateMapOf<String, String>()
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp != null) return
        val s = ctx.getSharedPreferences("dz_prefs", Context.MODE_PRIVATE); sp = s
        s.all.forEach { (k, v) -> if (v != null) map[k] = v.toString() }
    }
    fun str(k: String, def: String): String = map[k] ?: def
    fun put(k: String, v: String) { map[k] = v; sp?.edit()?.putString(k, v)?.apply() }
    fun bool(k: String, def: Boolean): Boolean = map[k]?.let { it == "1" } ?: def
    fun put(k: String, v: Boolean) = put(k, if (v) "1" else "0")
    fun int(k: String, def: Int): Int = map[k]?.toIntOrNull() ?: def
    fun put(k: String, v: Int) = put(k, v.toString())

    // Appearance
    val theme get() = str("theme", "obsidian")
    val compact get() = str("density", "comfortable") == "compact"
    /** 0 = off, 1 = standard, 2 = high */
    val animQuality get() = int("anim", 2)
    val haptics get() = bool("haptics", true)
    // Playback
    val loop get() = bool("loop", false)
    /** preview (proxy) short side in px */
    val previewSide get() = int("proxy", 540)
    val previewFps get() = int("pfps", 30)
    // Editing
    val defaultClipMs get() = int("clipms", 3000)
    val snapping get() = bool("snap", true)
    val autoSave get() = bool("autosave", true)
    val defaultTransMs get() = int("transms", 600)
    // Export
    val exportSide get() = int("exside", 1080)
    val exportFps get() = int("exfps", 30)
    val exportHevc get() = bool("exhevc", false)
    val exportMbps get() = int("exmbps", 12)
}
