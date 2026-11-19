package com.directorzone

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.directorzone.catalog.*
import com.directorzone.data.*
import com.directorzone.engine.SoundLib
import com.directorzone.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * CI-only visual tour: drives the real UI state (tabs, screens, sheets) and logs a STEP marker after each screen has settled;
 * scripts/ui_tour.sh takes a real screenshot of the emulator at every marker. Only compiled into builds that include the dztest assets.
 */
object UiTour {
    private const val T = "UITOUR"
    fun available(ctx: Context) = try { ctx.assets.list("dztest")?.isNotEmpty() == true } catch (e: Exception) { false }

    fun run(ctx: Context, vm: EditorVM) {
        vm.viewModelScope.launch {
            try { script(ctx, vm) } catch (e: Throwable) { Log.e(T, "tour crash", e) }
            Log.i(T, "DONE")
        }
    }

    private suspend fun step(name: String, settle: Long = 1400, hold: Long = 3200) { delay(settle); Log.i(T, "STEP $name"); delay(hold) }

    private suspend fun script(ctx: Context, vm: EditorVM) {
        val work = File(ctx.filesDir, "dztest").apply { mkdirs() }
        for (n in ctx.assets.list("dztest")!!) ctx.assets.open("dztest/$n").use { i -> File(work, n).outputStream().use { o -> i.copyTo(o) } }
        val media = File(ctx.filesDir, "media").apply { mkdirs() }
        // the clips live in the app's media folder like real imports, so Assets shows them
        fun clip(n: String): Clip { val f = File(media, "m_tour_$n.mp4"); File(work, "$n.mp4").copyTo(f, true); return Clip(uri = Uri.fromFile(f).toString(), srcDurMs = 5000, outMs = 5000) }
        val a = clip("A"); val b = clip("B"); val c = clip("C")
        val tone = File(media, "a_tour_tone.m4a").also { File(work, "tone.m4a").copyTo(it, true) }
        val whoosh = SoundLib.byName("Soft Whoosh")!!; val bed = SoundLib.byName("Chill Groove")!!

        step("00_home_empty", 800)
        vm.openProject(Project(
            name = "Cinematic VLOG_04", ratio = "16:9",
            clips = listOf(a, b.copy(transition = "wipel", transitionMs = 800, filter = "cinema"), c.copy(transition = "flash", effect = "haze")),
            texts = listOf(TextItem(text = "HIGHLAND PASS", startMs = 400, durMs = 4000, font = "anton", size = 0.11f, outlineW = 0.04f, anim = "pop")),
            sounds = listOf(SoundItem(name = "Tone", source = Uri.fromFile(tone).toString(), kind = "music", durMs = 15000, srcMs = 15000, volume = 0.6f),
                SoundItem(name = whoosh.name, source = whoosh.assetUri, startMs = 4500, durMs = whoosh.ms.toLong(), srcMs = whoosh.ms.toLong()),
                SoundItem(name = bed.name, source = bed.assetUri, kind = "music", startMs = 8000, durMs = bed.ms.toLong(), loop = true, srcMs = bed.ms.toLong())),
            autoSfx = false))
        vm.save(); delay(900)
        step("01_editor", 3500)
        vm.selectClip(b.id)
        step("02_editor_clip_selected", 1500)
        vm.seek(1200)
        vm.selText = vm.project.texts.first().id
        step("03_editor_text_selected", 1500)
        for ((i, s) in listOf(Sheet.Text, Sheet.Color, Sheet.Effects, Sheet.Transitions, Sheet.Audio, Sheet.Canvas, Sheet.More, Sheet.Phone, Sheet.Stickers, Sheet.Trim, Sheet.Speed, Sheet.Templates).withIndex()) {
            if (s == Sheet.Transitions) vm.selectClip(b.id)
            vm.sheetExpanded = false; vm.sheet = s
            step("1${(i + 10).toString().padStart(2, '0')}_sheet_${s.name.lowercase()}")
        }
        for ((n, t) in listOf("free_music" to 2, "free_sfx" to 3, "your_music" to 4)) { vm.audioTab = t; vm.sheetExpanded = false; vm.sheet = Sheet.Audio; step("13${t}_audio_$n", if (t < 4) 6000 else 1400) }
        vm.audioTab = 1
        vm.selectClip(a.id); vm.seek(1000)
        vm.updateClip(a.id) { it.copy(adjust = Adjust(grain = 1f)) }
        vm.sheet = null; vm.sheetExpanded = false
        step("140_grain_full", 7000)
        vm.updateClip(a.id) { it.copy(adjust = Adjust()) }
        vm.sheetExpanded = true; vm.sheet = Sheet.Color
        step("200_sheet_color_expanded")
        vm.sheet = Sheet.Text; step("201_sheet_text_expanded")
        vm.sheet = null; vm.sheetExpanded = false
        step("210_editor_back")
        vm.openExport()
        step("220_export")
        vm.showExport = false
        vm.closeEditor(); delay(600)
        for (t in Tab.values()) { vm.tab = t; step("30${t.ordinal}_tab_${t.name.lowercase()}") }
        vm.tab = Tab.Home
        vm.showSettings = true; step("310_settings")
        vm.showThemes = true; step("311_themes")
        vm.showThemes = false; vm.showSettings = false
        for (th in listOf("crimson", "emerald", "arctic")) { Prefs.put("theme", th); vm.tab = Tab.Home; step("32_home_$th", 900, 2600) }
        Prefs.put("theme", "midnight")
    }
}
