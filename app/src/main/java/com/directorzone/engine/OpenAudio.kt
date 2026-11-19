package com.directorzone.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free-to-use music and sound effects from Openverse (https://openverse.org): Creative Commons / public-domain audio aggregated from
 * Jamendo, Freesound, Wikimedia Commons and others. Only licences that allow commercial use and modification are requested.
 * Tracks are downloaded into the app's media folder, so they work offline, in the timeline and in the exported video.
 */
object OpenAudio {
    data class Track(
        val id: String, val title: String, val creator: String, val license: String, val licenseVersion: String,
        val url: String, val durationMs: Long, val sourceUrl: String, val provider: String,
    ) {
        val licenseLabel get() = when (license.lowercase()) { "cc0" -> "CC0"; "pdm" -> "Public domain"; else -> "CC ${license.uppercase()} $licenseVersion".trim() }
        val credit get() = "\"$title\" by $creator, $licenseLabel" + if (sourceUrl.isNotBlank()) " ($sourceUrl)" else ""
        val needsCredit get() = license.lowercase() != "cc0" && license.lowercase() != "pdm"
    }

    class Page(val tracks: List<Track>, val hasMore: Boolean)

    private const val UA = "DirectorZone/1.0 (Android video editor)"

    suspend fun search(query: String, sfx: Boolean, page: Int): Page = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.ifBlank { if (sfx) "sound" else "music" }, "UTF-8")
        val cat = if (sfx) "sound_effect" else "music"
        val u = "https://api.openverse.org/v1/audio/?q=$q&category=$cat&license_type=commercial,modification&mature=false&page_size=20&page=$page"
        val c = (URL(u).openConnection() as HttpURLConnection).apply { connectTimeout = 12000; readTimeout = 15000; setRequestProperty("User-Agent", UA); setRequestProperty("Accept", "application/json") }
        try {
            if (c.responseCode !in 200..299) throw java.io.IOException("Library returned ${c.responseCode}")
            val j = JSONObject(c.inputStream.bufferedReader().readText())
            val arr = j.optJSONArray("results")
            val out = ArrayList<Track>()
            if (arr != null) for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("url", ""); if (!url.startsWith("http")) continue
                out.add(Track(
                    id = o.optString("id"), title = o.optString("title", "Untitled").ifBlank { "Untitled" }, creator = o.optString("creator", "Unknown").takeUnless { it == "null" || it.isBlank() } ?: "Unknown",
                    license = o.optString("license", ""), licenseVersion = o.optString("license_version", ""), url = url,
                    durationMs = if (o.isNull("duration")) 0 else o.optLong("duration", 0), sourceUrl = o.optString("foreign_landing_url", ""), provider = o.optString("provider", ""),
                ))
            }
            Page(out, j.optInt("page_count", 1) > page)
        } finally { c.disconnect() }
    }

    suspend fun download(ctx: Context, t: Track): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "media").apply { mkdirs() }
        val ext = t.url.substringBefore('?').substringAfterLast('.', "mp3").lowercase().takeIf { it.length in 2..4 } ?: "mp3"
        val out = File(dir, "open_${t.id.take(12)}.$ext")
        if (out.exists() && out.length() > 1024) return@withContext out
        val tmp = File(dir, out.name + ".part")
        var conn = URL(t.url).openConnection() as HttpURLConnection
        var hops = 0
        while (true) {
            conn.instanceFollowRedirects = false; conn.connectTimeout = 15000; conn.readTimeout = 30000; conn.setRequestProperty("User-Agent", UA)
            val code = conn.responseCode
            if (code in 300..399 && hops < 5) { val next = conn.getHeaderField("Location") ?: throw java.io.IOException("Bad redirect"); conn.disconnect(); conn = URL(URL(t.url), next).openConnection() as HttpURLConnection; hops++; continue }
            if (code !in 200..299) throw java.io.IOException("Download failed ($code)")
            break
        }
        try { conn.inputStream.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } } finally { conn.disconnect() }
        if (tmp.length() < 1024) { tmp.delete(); throw java.io.IOException("Empty download") }
        tmp.renameTo(out); out
    }
}
