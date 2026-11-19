package com.directorzone

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.lifecycle.viewModelScope
import com.directorzone.data.*
import com.directorzone.ui.EditorVM
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.resume

/**
 * Reproducible on-device playback test (run by CI on an emulator; see scripts/playback_test.sh).
 * Timeline: A (5s red) → B (5s green) → C (5s blue), plus a 15s tone. Every check grabs the REAL preview frame
 * (PixelCopy of the player surface) and compares it with what the timeline says should be on screen.
 */
object DzTest {
    private const val T = "DZTEST"
    private val results = ArrayList<String>()

    fun available(ctx: Context) = try { ctx.assets.list("dztest")?.isNotEmpty() == true } catch (e: Exception) { false }
    private fun log(s: String) { Log.i(T, s) }
    private fun check(name: String, ok: Boolean, detail: String) { results.add("${if (ok) "PASS" else "FAIL"} $name  $detail"); log("RESULT ${if (ok) "PASS" else "FAIL"} $name  $detail") }

    private class Rgb(val r: Int, val g: Int, val b: Int) { val lum get() = (0.299 * r + 0.587 * g + 0.114 * b).toInt(); override fun toString() = "rgb($r,$g,$b)" }
    private fun isRed(c: Rgb) = c.r > c.g + 40 && c.r > c.b + 40
    private fun isGreen(c: Rgb) = c.g > c.r + 30 && c.g > c.b + 30
    private fun isBlue(c: Rgb) = c.b > c.r + 30 && c.b > c.g + 30
    private fun isGray(c: Rgb) = Math.abs(c.r - c.g) < 14 && Math.abs(c.g - c.b) < 14

    private fun median(bmp: Bitmap): Rgb {
        val rs = ArrayList<Int>(); val gs = ArrayList<Int>(); val bs = ArrayList<Int>()
        for (y in (bmp.height * 0.42f).toInt() until (bmp.height * 0.58f).toInt() step maxOf(1, bmp.height / 120)) for (x in 4 until bmp.width step maxOf(1, bmp.width / 36)) {
            val p = bmp.getPixel(x, y); rs.add((p shr 16) and 255); gs.add((p shr 8) and 255); bs.add(p and 255)
        }
        rs.sort(); gs.sort(); bs.sort(); val m = rs.size / 2
        return Rgb(rs[m], gs[m], bs[m])
    }

    private var lastBarSec = -1f
    private fun barSec(b: Bitmap): Float {
        val vh = b.width * 9f / 16f; val y = (b.height / 2f + 0.44f * vh).toInt().coerceIn(0, b.height - 1); var white = 0; var vid = 0
        for (x in 0 until b.width step 2) { val c = b.getPixel(x, y); val r = (c shr 16) and 255; val g = (c shr 8) and 255; val bl = c and 255
            if (r + g + bl > 60) vid++; if (r > 230 && g > 230 && bl > 230) white++ }
        return if (vid == 0) -1f else 5f * white / vid
    }

    private suspend fun grab(vm: EditorVM, name: String, out: File, save: Boolean = true): Rgb? = suspendCancellableCoroutine { cont ->
        val sv = vm.engine.hostView?.videoSurfaceView as? SurfaceView
        if (sv == null || sv.width == 0 || sv.height == 0) { log("grab $name: no surface"); cont.resume(null); return@suspendCancellableCoroutine }
        val bmp = Bitmap.createBitmap(sv.width, sv.height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(sv, bmp, { r ->
                if (r == PixelCopy.SUCCESS) {
                    lastBarSec = barSec(bmp)
                    if (save) try { File(out, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) } } catch (_: Exception) {}
                    cont.resume(median(bmp))
                } else { log("grab $name: PixelCopy result=$r"); cont.resume(null) }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) { log("grab $name failed $e"); cont.resume(null) }
    }

    private suspend fun grabRetry(vm: EditorVM, name: String, out: File, pred: (Rgb) -> Boolean): Rgb? {
        var c: Rgb? = null
        for (i in 0 until 4) { c = grab(vm, name, out); if (c != null && pred(c)) return c; delay(250) }
        return c
    }

    private fun stamp(vm: EditorVM) = "timeline=${vm.engine.timelineMs} player=${vm.engine.positionNow()} state=${vm.engine.stateNow()} playing=${vm.engine.isPlaying}"

    private suspend fun settle(vm: EditorVM, maxMs: Long = 25000) {
        // wait for the (debounced) rebuild to finish and a frame to be on screen
        val t0 = System.currentTimeMillis()
        delay(600)
        while (System.currentTimeMillis() - t0 < maxMs && (vm.engine.building || vm.engine.player == null || !vm.engine.frameRendered)) delay(100)
        delay(500)
    }

    /** wait until the player is READY, not building, and (if a seek is in flight) a fresh frame was rendered */
    private suspend fun awaitFrame(vm: EditorVM, sig0: Int, maxMs: Long = 8000) {
        val t0 = System.currentTimeMillis()
        delay(250)
        while (System.currentTimeMillis() - t0 < maxMs && (vm.engine.building || vm.engine.stateNow() != "READY" || vm.engine.framesRenderedSignals == sig0)) delay(50)
        delay(350)
    }

    private suspend fun seekGrab(vm: EditorVM, name: String, ms: Long, out: File, waitMs: Long = 900): Rgb? {
        val sig0 = vm.engine.framesRenderedSignals
        vm.seek(ms); awaitFrame(vm, sig0)
        val c = grab(vm, name, out)
        log("SEEK $name target=$ms ${stamp(vm)} color=$c")
        return c
    }

    fun run(ctx: Context, vm: EditorVM) {
        vm.viewModelScope.launch {
            try { script(ctx, vm) } catch (e: Throwable) { log("SCRIPT CRASH $e"); Log.e(T, "crash", e) }
            log("SUMMARY\n" + results.joinToString("\n"))
            log("DONE")
        }
    }

    private suspend fun script(ctx: Context, vm: EditorVM) {
        val out = File(ctx.getExternalFilesDir(null), "dztest_out").apply { deleteRecursively(); mkdirs() }
        val work = File(ctx.filesDir, "dztest").apply { mkdirs() }
        File(work, "side.txt").delete()
        for (n in ctx.assets.list("dztest")!!) ctx.assets.open("dztest/$n").use { i -> File(work, n).outputStream().use { o -> i.copyTo(o) } }
        File(work, "side.txt").takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull()?.let { vm.engine.previewShortSide = it; log("PREVIEW short side = $it") }
        fun clip(n: String) = Clip(uri = Uri.fromFile(File(work, "$n.mp4")).toString(), srcDurMs = 5000, outMs = 5000)
        val a = clip("A"); val b = clip("B"); val c = clip("C")
        val tone = SoundItem(name = "tone", source = Uri.fromFile(File(work, "tone.m4a")).toString(), kind = "music", startMs = 0, durMs = 15000)
        vm.openProject(Project(name = "DZ test", ratio = "16:9", clips = listOf(a, b, c), sounds = listOf(tone), autoSfx = false))
        log("PROJECT duration=${vm.project.durationMs}")
        settle(vm)
        log("READY ${stamp(vm)} error=${vm.engine.error} frameRendered=${vm.engine.frameRendered}")
        val first = grabRetry(vm, "00_initial", out, ::isRed)
        check("T0 initial frame shows clip A", first != null && isRed(first), "$first")

        // baseline: how fast does the emulator render video at all? (single clip, no audio) vs the full project
        suspend fun rate(label: String, pr: Project) {
            vm.openProject(pr); settle(vm); val t0 = System.currentTimeMillis(); vm.seek(0); delay(600); vm.engine.play()
            val w0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - w0 < 4500) { delay(550); val c = grab(vm, "rate", out, false); log("RATE $label wall=${System.currentTimeMillis() - w0} tl=${vm.engine.timelineMs} videoSec=$lastBarSec") }
            vm.engine.pause(); delay(300)
        }
        rate("single-A-noaudio", Project(name = "r1", ratio = "16:9", clips = listOf(a), autoSfx = false))
        rate("ABC-noaudio", Project(name = "r2", ratio = "16:9", clips = listOf(a, b, c), autoSfx = false))
        vm.openProject(Project(name = "DZ test", ratio = "16:9", clips = listOf(a, b, c), sounds = listOf(tone), autoSfx = false)); settle(vm)
        vm.seek(0); delay(800)

        // T1 play A -> B -> C. The CI emulator renders video well below real time (software GL + emulated codecs, see RATE lines),
        // so instead of asserting wall-clock positions we assert the *sequence*: A shows first, then B, then C, with audio/timeline
        // continuing, no error, and a monotonic playhead.
        vm.engine.play()
        val t0 = System.currentTimeMillis()
        var seenA = -1L; var seenB = -1L; var seenC = -1L; var maxT = 0L; var monotonic = true; var last = -1L; var order = ""
        while (System.currentTimeMillis() - t0 < 75000 && vm.engine.error == null) {
            val t = vm.engine.timelineMs
            if (t < last - 50) monotonic = false
            last = t; maxT = maxOf(maxT, t)
            val pc = grab(vm, "probe", out, false)
            val k = pc?.let { if (isRed(it)) 'A' else if (isGreen(it)) 'B' else if (isBlue(it)) 'C' else '?' } ?: '-'
            if (k != '?' && k != '-' && order.lastOrNull() != k) order += k
            if (k == 'A' && seenA < 0) { seenA = t; grab(vm, "01_play_A", out) }
            if (k == 'B' && seenB < 0) { seenB = t; grab(vm, "02_play_B", out) }
            if (k == 'C' && seenC < 0) { seenC = t; grab(vm, "03_play_C", out) }
            log("PROBE tl=$t player=${vm.engine.positionNow()} state=${vm.engine.stateNow()} clip=$k videoSec=$lastBarSec")
            if (seenC >= 0 && t >= 14900) break
            if (!vm.engine.isPlaying && vm.engine.stateNow() == "READY" && t < 14900) vm.engine.play()   // user taps play again after a stall
            delay(300)
        }
        val wall = System.currentTimeMillis() - t0
        check("T1a play shows A first", seenA >= 0, "seenAt=$seenA")
        check("T1b play continues into B at the cut", seenB >= 0, "seenAt=$seenB (cut at 5000; video lag ${seenB - 5000}ms) order=$order")
        check("T1c play continues into C at the cut", seenC >= 0, "seenAt=$seenC (cut at 10000; video lag ${seenC - 10000}ms) order=$order")
        check("T1d playhead advanced monotonically to the end, no player error", maxT >= 14500 && monotonic && vm.engine.error == null && order.startsWith("ABC"), "maxT=$maxT wall=${wall}ms monotonic=$monotonic order=$order err=${vm.engine.error} ${stamp(vm)}")
        delay(500)

        // T2 pause at ~3s
        vm.seek(0); delay(800); vm.engine.play()
        val t2s = System.currentTimeMillis()
        while (vm.engine.timelineMs < 3000 && System.currentTimeMillis() - t2s < 15000) delay(25)
        vm.engine.pause(); delay(900)
        val p1 = grab(vm, "04_pause_3s_a", out); val tl1 = vm.engine.timelineMs
        delay(1200)
        val p2 = grab(vm, "05_pause_3s_b", out); val tl2 = vm.engine.timelineMs
        check("T2 pause holds frame (A) and playhead", p1 != null && p2 != null && isRed(p1) && Math.abs(p1.r - p2.r) < 6 && tl1 == tl2 && tl1 in 2900..3600, "tl=$tl1/$tl2 $p1 $p2 ${stamp(vm)}")

        // T3 drag to 8s -> B
        seekGrab(vm, "06_seek_8s", 8000, out); val t3 = grabRetry(vm, "06_seek_8s", out, ::isGreen)
        check("T3 seek 8s shows B", t3 != null && isGreen(t3), "$t3 ${stamp(vm)}")

        // T4 rapid scrubbing
        val sigR = vm.engine.framesRenderedSignals
        for (ms in listOf(1000L, 10000L, 4000L, 12000L, 6000L)) { vm.seek(ms); delay(220) }
        awaitFrame(vm, sigR)
        val t4 = grab(vm, "07_rapid_final_6s", out)
        check("T4 rapid seeks end on 6s (B)", t4 != null && isGreen(t4) && Math.abs(vm.engine.timelineMs - 6000) < 80, "${stamp(vm)} $t4")
        // sequential seeks with a capture after each
        val seq = listOf(1000L to ::isRed, 10200L to ::isBlue, 4000L to ::isRed, 12000L to ::isBlue, 6000L to ::isGreen)
        var okSeq = true; val det = StringBuilder()
        for ((i, pair) in seq.withIndex()) { val col = seekGrab(vm, "08_seq_$i", pair.first, out); val good = col != null && pair.second(col); okSeq = okSeq && good; det.append("${pair.first}:${if (good) "ok" else "BAD"}($col) ") }
        check("T4b each seek in sequence renders its frame", okSeq, det.toString())

        // T5 play after scrubbing
        vm.seek(6000); delay(700); vm.engine.play(); delay(1600)
        val t5 = grab(vm, "09_play_after_scrub", out); val pos5 = vm.engine.timelineMs
        check("T5 play resumes from the scrubbed position", pos5 in 7000..8500 && t5 != null && isGreen(t5), "${stamp(vm)} $t5")
        vm.engine.pause(); delay(500)

        // T6 select clip
        vm.seek(1000); delay(500)
        vm.selectClip(b.id); delay(900); grabRetry(vm, "10_select_B", out, ::isGreen)
        val t6 = grabRetry(vm, "10_select_B", out, ::isGreen)
        check("T6 selecting clip B moves the playhead into B and the preview follows", vm.engine.timelineMs in 5000..9999 && t6 != null && isGreen(t6), "${stamp(vm)} $t6")

        // T7 effect
        vm.updateClip(b.id) { it.copy(filter = "noir") }
        settle(vm); val t7 = seekGrab(vm, "11_filter_noir", 7000, out, 1200)
        check("T7 Noir filter on B is visible in the preview (grayscale)", t7 != null && isGray(t7), "$t7 ${stamp(vm)}")

        // T8 transition
        vm.updateClip(c.id) { it.copy(transition = "flash", transitionMs = 1000) }
        settle(vm); val t8 = seekGrab(vm, "12_transition_flash", 10200, out, 1200)
        check("T8 Flash transition brightens the first frames of C", t8 != null && t8.lum > 110, "$t8 ${stamp(vm)}")
        val t8b = seekGrab(vm, "13_after_transition", 13000, out, 1000)
        check("T8b C is back to normal after the transition, content time matches position", t8b != null && isBlue(t8b) && Math.abs(lastBarSec - 3.0f) < 0.8f, "$t8b sourceSec@13s=$lastBarSec (expect ~3.0)")

        // T9 speed
        vm.updateClip(c.id) { it.copy(speed = 2f) }
        settle(vm)
        val dur9 = vm.project.durationMs
        log("SPEED duration project=$dur9 engine=${vm.engine.durationMs} player=${vm.engine.player?.duration}")
        seekGrab(vm, "14_speed_2x", 12000, out, 1200); val t9 = grabRetry(vm, "14_speed_2x", out, ::isBlue); val bar9 = lastBarSec
        for (ms in listOf(10300L, 10800L, 11200L, 12000L, 12400L)) { val cc = seekGrab(vm, "15_speed_$ms", ms, out); log("SPEEDPOS $ms -> $cc") }
        check("T9 speed 2x → timeline 12.5s and player duration matches", dur9 == 12500L && (vm.engine.player?.duration ?: 0) in 12300..12700 && t9 != null && isBlue(t9) && Math.abs(bar9 - 4.0f) < 0.8f, "project=$dur9 player=${vm.engine.player?.duration} $t9 sourceSec@12s=$bar9 (expect ~4.0 at 2x)")
        vm.seek(10000); delay(700); vm.engine.play(); delay(1500); val p9 = vm.engine.timelineMs; vm.engine.pause()
        check("T9b playback at 2x finishes C sooner (timeline advances 1x, content 2x)", p9 in 11200..11900, "pos=$p9 ${stamp(vm)}")
        vm.updateClip(c.id) { it.copy(speed = 1f) }; settle(vm)

        // T10 export
        vm.exportOpts = ExportOptions(shortSide = 360, fps = 30, bitrateMbps = 8, hevc = false)
        val mainProj = vm.project
        suspend fun probeExport(label: String, pr: Project) {
            vm.openProject(pr); settle(vm); vm.startExport(); val s0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - s0 < 35000 && vm.export.file == null && vm.export.error == null) delay(500)
            log("EXPORTPROBE $label ok=${vm.export.file != null} err=${vm.export.error} took=${System.currentTimeMillis() - s0}ms")
            if (vm.export.file == null) vm.cancelExport()
        }
        probeExport("A-only-video", Project(name = "e1", ratio = "16:9", clips = listOf(a), autoSfx = false))
        probeExport("ABC-video", Project(name = "e2", ratio = "16:9", clips = listOf(a, b, c), autoSfx = false))
        probeExport("B-noir", Project(name = "e4", ratio = "16:9", clips = listOf(b.copy(filter = "noir")), autoSfx = false))
        probeExport("C-flash", Project(name = "e5", ratio = "16:9", clips = listOf(c.copy(transition = "flash", transitionMs = 1000)), autoSfx = false))
        probeExport("A+B-flash", Project(name = "e6", ratio = "16:9", clips = listOf(a, b.copy(transition = "flash", transitionMs = 1000)), autoSfx = false))
        probeExport("B-noir+in-flash", Project(name = "e7", ratio = "16:9", clips = listOf(b.copy(filter = "noir", transition = "flash", transitionMs = 1000)), autoSfx = false))
        probeExport("B+C-flash(out on B)", Project(name = "e8", ratio = "16:9", clips = listOf(b, c.copy(transition = "flash", transitionMs = 1000)), autoSfx = false))
        probeExport("A+B-noir+C", Project(name = "e9", ratio = "16:9", clips = listOf(a, b.copy(filter = "noir"), c), autoSfx = false))
        probeExport("A+B+C-flash", Project(name = "e10", ratio = "16:9", clips = listOf(a, b, c.copy(transition = "flash", transitionMs = 1000)), autoSfx = false))
        probeExport("main-without-tone", mainProj.copy(sounds = emptyList()))
        probeExport("A-with-tone", Project(name = "e3", ratio = "16:9", clips = listOf(a), sounds = listOf(tone.copy(durMs = 5000)), autoSfx = false))
        vm.openProject(mainProj); settle(vm)
        vm.startExport()
        val e0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - e0 < 300000 && vm.export.file == null && vm.export.error == null) { delay(500) }
        val f = vm.export.file
        log("EXPORT file=$f error=${vm.export.error} took=${System.currentTimeMillis() - e0}ms size=${f?.length()}")
        if (f != null) {
            val r = MediaMetadataRetriever(); r.setDataSource(f.absolutePath)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0
            val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH); val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            fun fr(ms: Long, n: String): Rgb? { val bm = r.getFrameAtTime(ms * 1000, MediaMetadataRetriever.OPTION_CLOSEST) ?: return null; File(out, "$n.png").outputStream().use { bm.compress(Bitmap.CompressFormat.PNG, 90, it) }; return median(bm) }
            val ea = fr(2500, "20_export_A"); val eb = fr(7000, "21_export_B"); val ec = fr(13000, "22_export_C"); val et = fr(10200, "23_export_transition")
            r.release()
            check("T10a export is ~15s with audio", d in 14500..15600 && hasAudio == "yes", "dur=$d ${w}x$h audio=$hasAudio size=${f.length() / 1024}KB")
            check("T10b exported frames match the preview (A red, B noir, C blue, flash at cut)", ea != null && isRed(ea) && eb != null && isGray(eb) && ec != null && isBlue(ec) && et != null && et.lum > 110, "A=$ea B=$eb C=$ec flash=$et")
        } else check("T10 export completed", false, "error=${vm.export.error}")
    }
}
