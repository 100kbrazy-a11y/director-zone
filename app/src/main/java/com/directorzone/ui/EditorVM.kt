package com.directorzone.ui

import android.app.Application
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.directorzone.catalog.*
import com.directorzone.data.*
import com.directorzone.engine.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Tool(val label: String) { Clip("Clip"), Filters("Filters"), Adjust("Adjust"), Transitions("Transitions"), Phone("Phone"), Text("Text"), Stickers("Stickers"), Sound("Sound"), Canvas("Canvas") }

class ExportUi(val progress: Float = 0f, val running: Boolean = false, val file: File? = null, val error: String? = null)

class EditorVM(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    var inEditor by mutableStateOf(false); private set
    var project by mutableStateOf(Project()); private set
    var recents by mutableStateOf<List<Project>>(emptyList()); private set
    var selClip by mutableStateOf<String?>(null)
    var selText by mutableStateOf<String?>(null)
    var selSound by mutableStateOf<String?>(null)
    var tool by mutableStateOf<Tool?>(null)
    /** The one preview player + the one authoritative playhead. */
    val engine = PreviewEngine(app, viewModelScope)
    val posMs: Long get() = engine.timelineMs
    val playing: Boolean get() = engine.isPlaying
    var importing by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var export by mutableStateOf(ExportUi())
    var exportOpts by mutableStateOf(ExportOptions())
    val thumbs = mutableStateMapOf<String, ImageBitmap>()
    private val exporter by lazy { Exporter(ctx) }

    private val undo = ArrayList<Project>(); private val redo = ArrayList<Project>(); private var lastPush = 0L
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set

    init { SoundLib.load(ctx); refreshRecents() }

    fun refreshRecents() { viewModelScope.launch { recents = withContext(Dispatchers.IO) { Store.list(ctx) } } }

    // ---------- project lifecycle
    fun newProject(): Project { openProject(Project(name = "Project ${recents.size + 1}")); return project }
    fun openProject(p: Project) {
        project = p; undo.clear(); redo.clear(); canUndo = false; canRedo = false
        selClip = p.clips.firstOrNull()?.id; selText = null; selSound = null; tool = null; inEditor = true; engine.pause(); engine.seek(0); engine.load(p, 0)
        export = ExportUi()
        p.clips.forEach { loadThumb(it) }
    }
    fun closeEditor() { save(); inEditor = false; engine.pause(); engine.release(); refreshRecents() }
    fun save() { if (project.clips.isNotEmpty() || project.texts.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { Store.save(ctx, project) } }
    fun deleteProject(id: String) { viewModelScope.launch { withContext(Dispatchers.IO) { Store.delete(ctx, id) }; refreshRecents() } }

    // ---------- editing core with undo
    fun edit(f: (Project) -> Project) {
        val now = System.currentTimeMillis()
        if (now - lastPush > 700) { undo.add(project); if (undo.size > 60) undo.removeAt(0); redo.clear() }
        lastPush = now
        project = f(project)
        canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
        engine.load(project)
    }
    fun undo() { if (undo.isEmpty()) return; redo.add(project); project = undo.removeAt(undo.size - 1); fixSel(); engine.load(project); canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty(); lastPush = 0 }
    fun redo() { if (redo.isEmpty()) return; undo.add(project); project = redo.removeAt(redo.size - 1); fixSel(); engine.load(project); canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty(); lastPush = 0 }
    private fun fixSel() {
        if (project.clips.none { it.id == selClip }) selClip = project.clips.firstOrNull()?.id
        if (project.texts.none { it.id == selText }) selText = null
        if (project.sounds.none { it.id == selSound }) selSound = null
    }

    val clip get() = project.clips.firstOrNull { it.id == selClip }
    val text get() = project.texts.firstOrNull { it.id == selText }
    val sound get() = project.sounds.firstOrNull { it.id == selSound }

    fun seek(ms: Long) = engine.seek(ms)
    fun togglePlay() = engine.toggle()

    /** Selecting a clip also makes the preview show it: playhead jumps into the clip if it isn't already inside. */
    fun selectClip(id: String) {
        selClip = id; selText = null; selSound = null
        val st = clipStart(id); val c = project.clips.firstOrNull { it.id == id } ?: return
        if (posMs < st || posMs >= st + c.lengthMs) seek(st + 1)
    }

    fun updateClip(id: String, f: (Clip) -> Clip) = edit { p -> p.copy(clips = p.clips.map { if (it.id == id) f(it) else it }) }
    fun updateSelClip(f: (Clip) -> Clip) { selClip?.let { updateClip(it, f) } }
    fun updateText(id: String, f: (TextItem) -> TextItem) = edit { p -> p.copy(texts = p.texts.map { if (it.id == id) f(it) else it }) }
    fun updateSound(id: String, f: (SoundItem) -> SoundItem) = edit { p -> p.copy(sounds = p.sounds.map { if (it.id == id) f(it) else it }) }
    fun allClips(f: (Clip) -> Clip) = edit { p -> p.copy(clips = p.clips.map(f)) }

    // ---------- clip ops
    private fun clipStart(id: String): Long { var t = 0L; for (c in project.clips) { if (c.id == id) return t; t += c.lengthMs }; return 0 }

    fun splitAtPlayhead() {
        val c = clip ?: return
        if (c.isImage) return
        val local = posMs - clipStart(c.id)
        if (local < 300 || local > c.lengthMs - 300) { toast = "Move the playhead inside the clip"; return }
        val srcAt = c.inMs + (local * c.speed).toLong()
        val a = c.copy(outMs = srcAt)
        val b = c.copy(id = newId(), inMs = srcAt, transition = "none")
        edit { p -> p.copy(clips = p.clips.flatMap { if (it.id == c.id) listOf(a, b) else listOf(it) }) }
        selClip = b.id
    }
    fun duplicateClip() {
        val c = clip ?: return
        val d = c.copy(id = newId(), transition = "none")
        edit { p -> p.copy(clips = p.clips.flatMap { if (it.id == c.id) listOf(it, d) else listOf(it) }) }
        selClip = d.id
    }
    fun deleteClip() {
        val c = clip ?: return
        edit { p -> p.copy(clips = p.clips.filter { it.id != c.id }) }
        selClip = project.clips.firstOrNull()?.id
        if (posMs > project.durationMs) engine.seek(project.durationMs)
    }
    fun moveClip(dir: Int) {
        val c = clip ?: return
        edit { p ->
            val l = p.clips.toMutableList(); val i = l.indexOfFirst { it.id == c.id }; val j = i + dir
            if (i < 0 || j < 0 || j >= l.size) p else { l.removeAt(i); l.add(j, c); p.copy(clips = l) }
        }
    }

    // ---------- text / sticker
    fun addText(sticker: Boolean = false, emoji: String = "✨") {
        val t = if (sticker) TextItem(text = emoji, startMs = posMs, durMs = 3000, anim = "pop", sticker = true, size = 0.13f, y = 0.35f)
        else TextItem(text = "Your text", startMs = posMs, durMs = 2800, anim = "pop", color = "FFFFFF")
        edit { it.copy(texts = it.texts + t) }
        selText = t.id; selSound = null
    }
    fun deleteText() { val t = text ?: return; edit { p -> p.copy(texts = p.texts.filter { it.id != t.id }) }; selText = null }

    // ---------- sounds
    fun addSound(m: SoundMeta) {
        val isBed = m.cat == "Ambience" || m.cat == "Beats"
        val s = SoundItem(name = m.name, source = m.assetUri, kind = if (isBed) "music" else "sfx", startMs = posMs, durMs = m.ms.toLong(), volume = if (isBed) 0.45f else 0.9f, loop = isBed, fadeOutMs = if (isBed) 800 else 0)
        edit { it.copy(sounds = it.sounds + s) }
        selSound = s.id; selText = null
    }
    fun addAudioFile(f: File, name: String, kind: String) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { durationOf(Uri.fromFile(f).toString()) }
            val s = SoundItem(name = name, source = Uri.fromFile(f).toString(), kind = kind, startMs = if (kind == "voice") posMs else posMs, durMs = d.coerceAtLeast(300))
            edit { it.copy(sounds = it.sounds + s) }
            selSound = s.id; selText = null
        }
    }
    fun deleteSound() { val s = sound ?: return; edit { p -> p.copy(sounds = p.sounds.filter { it.id != s.id }) }; selSound = null }

    // ---------- media import
    private fun durationOf(uri: String): Long = try {
        val r = MediaMetadataRetriever(); r.setDataSource(ctx, Uri.parse(uri))
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L; r.release(); d
    } catch (e: Exception) { 0L }

    fun loadThumb(c: Clip) {
        if (thumbs.containsKey(c.uri)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp: Bitmap? = if (c.isImage) android.graphics.BitmapFactory.decodeFile(Uri.parse(c.uri).path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 8 })
                else { val r = MediaMetadataRetriever(); r.setDataSource(ctx, Uri.parse(c.uri)); val b = r.getFrameAtTime(c.inMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC); r.release(); b?.let { Bitmap.createScaledBitmap(it, 160, (160f * it.height / it.width).toInt().coerceAtLeast(1), true) } }
                if (bmp != null) withContext(Dispatchers.Main) { thumbs[c.uri] = bmp.asImageBitmap() }
            } catch (_: Exception) {}
        }
    }

    /** Copies picked media into app storage (so projects keep working) and adds them as clips. */
    fun importMedia(uris: List<Uri>, then: ((List<Clip>) -> Unit)? = null) {
        if (uris.isEmpty()) return
        importing = true
        viewModelScope.launch {
            val added = withContext(Dispatchers.IO) {
                uris.mapNotNull { u ->
                    try {
                        val mime = ctx.contentResolver.getType(u) ?: ""
                        val img = mime.startsWith("image")
                        val ext = if (img) "jpg" else "mp4"
                        val f = File(File(ctx.filesDir, "media").apply { mkdirs() }, "m_${System.nanoTime()}.$ext")
                        ctx.contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                        val furi = Uri.fromFile(f).toString()
                        if (img) Clip(uri = furi, isImage = true, srcDurMs = 3000, outMs = 3000)
                        else { val d = durationOf(furi); if (d <= 0) null else Clip(uri = furi, srcDurMs = d, outMs = d) }
                    } catch (e: Exception) { null }
                }
            }
            importing = false
            if (added.isEmpty()) { toast = "Couldn't read that media"; return@launch }
            edit { it.copy(clips = it.clips + added) }
            added.forEach { loadThumb(it) }
            selClip = added.first().id
            then?.invoke(added)
        }
    }

    fun importAudio(uri: Uri) {
        viewModelScope.launch {
            val f = withContext(Dispatchers.IO) {
                val out = File(File(ctx.filesDir, "media").apply { mkdirs() }, "a_${System.nanoTime()}.m4a")
                ctx.contentResolver.openInputStream(uri)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }; out
            }
            addAudioFile(f, "My audio", "music")
        }
    }

    // ---------- templates
    fun applyTemplate(t: TemplateDef) {
        edit { p ->
            val clips = p.clips.mapIndexed { i, c ->
                val len = if (c.isImage) t.clipMs.coerceAtMost(4000) else minOf(c.srcDurMs, t.clipMs)
                c.copy(inMs = 0, outMs = if (c.isImage) len else len, filter = t.filter, filterAmount = 1f, transition = if (i == 0) "none" else t.transition, transitionMs = 600)
            }
            val total = clips.sumOf { it.lengthMs }
            val texts = if (t.title != null) listOf(TextItem(text = t.title, startMs = 300, durMs = minOf(2600L, total - 300), anim = t.titleAnim, size = 0.085f, y = if (t.phone) 0.1f else 0.5f, font = "black", pill = false)) else emptyList()
            val sounds = ArrayList<SoundItem>()
            t.music?.let { n -> SoundLib.byName(n)?.let { m -> sounds.add(SoundItem(name = m.name, source = m.assetUri, kind = "music", startMs = 0, durMs = m.ms.toLong(), volume = 0.4f, loop = true, fadeOutMs = 900)) } }
            t.sfx.forEachIndexed { i, n -> SoundLib.byName(n)?.let { m -> sounds.add(SoundItem(name = m.name, source = m.assetUri, startMs = 250L + i * 450L, durMs = m.ms.toLong(), volume = 0.9f)) } }
            p.copy(clips = clips, texts = texts, sounds = sounds, ratio = t.ratio, fit = false,
                phone = PhoneCfg(enabled = t.phone, bezel = t.bezel, anim = t.anim, bg = t.bg))
        }
        engine.seek(0)
    }

    // ---------- export
    fun startExport() {
        if (project.clips.isEmpty()) return
        val resumeAt = engine.timelineMs
        // free the preview decoders/GL while the encoder runs (low-end devices have few codec instances)
        engine.release()
        export = ExportUi(0f, true)
        exporter.export(project, exportOpts, { pr -> export = ExportUi(pr, true) }) { r ->
            r.onSuccess { export = ExportUi(1f, false, it) }.onFailure { export = ExportUi(0f, false, null, it.message ?: "Export failed") }
            engine.load(project, 0); engine.seek(resumeAt)
        }
    }
    fun cancelExport() { exporter.cancel(); export = ExportUi(); engine.load(project, 0) }
    fun saveExport() {
        val f = export.file ?: return
        viewModelScope.launch { val u = withContext(Dispatchers.IO) { Exporter.saveToGallery(ctx, f) }; toast = if (u != null) "Saved to Movies/Director Zone" else "Couldn't save" }
    }
}
