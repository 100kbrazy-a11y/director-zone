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

enum class Tab(val label: String) { Home("Home"), Projects("Projects"), Assets("Assets"), Templates("Templates"), Profile("Profile") }
/** Every bottom sheet of the editor. The global toolbar opens the first eight; the rest are reached from the clip menu or More. */
enum class Sheet { Media, Audio, Text, Effects, Color, Transitions, Canvas, More, Stickers, Phone, Trim, Speed, Volume, ClipMore, Templates }
class MediaFile(val file: File, val uri: String, val isImage: Boolean, val isAudio: Boolean, val bytes: Long, val usedIn: Int)
class Storage(val media: Long = 0, val cache: Long = 0, val projects: Long = 0, val free: Long = 0)
enum class Pending { New, Phone, Template }

class ExportUi(val progress: Float = 0f, val running: Boolean = false, val file: File? = null, val error: String? = null)

class EditorVM(app: Application) : AndroidViewModel(app) {
    private val ctx get() = getApplication<Application>()

    var inEditor by mutableStateOf(false); private set
    var project by mutableStateOf(Project()); private set
    var recents by mutableStateOf<List<Project>>(emptyList()); private set
    var selClip by mutableStateOf<String?>(null)
    var selText by mutableStateOf<String?>(null)
    var selSound by mutableStateOf<String?>(null)
    var sheet by mutableStateOf<Sheet?>(null)
    var sheetExpanded by mutableStateOf(false)
    var tab by mutableStateOf(Tab.Home)
    var showSettings by mutableStateOf(false)
    var projectFilter by mutableStateOf("All")
    var search by mutableStateOf("")
    var showThemes by mutableStateOf(false)
    var showExport by mutableStateOf(false)
    var pending by mutableStateOf(Pending.New)
    var pendingTemplate by mutableStateOf<TemplateDef?>(null)
    var assets by mutableStateOf<List<MediaFile>>(emptyList()); private set
    var storage by mutableStateOf(Storage()); private set
    val waves = mutableStateMapOf<String, ByteArray>()
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

    val strips = mutableStateMapOf<String, List<ImageBitmap>>()
    init { SoundLib.load(ctx); TextPainter.assets = ctx.assets; refreshRecents() }

    /** A handful of real frames across the source, used as the clip's filmstrip on the timeline. */
    fun loadStrip(c: Clip) {
        if (strips.containsKey(c.uri) || c.isImage) return
        strips[c.uri] = emptyList()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val r = MediaMetadataRetriever(); r.setDataSource(ctx, Uri.parse(c.uri))
                val n = 10
                val out = (0 until n).mapNotNull { i ->
                    val b = r.getFrameAtTime((c.srcDurMs * (i + 0.5) / n * 1000).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    b?.let { Bitmap.createScaledBitmap(it, 140, (140f * it.height / it.width).toInt().coerceAtLeast(1), true).asImageBitmap() }
                }
                r.release()
                withContext(Dispatchers.Main) { strips[c.uri] = out }
            } catch (_: Exception) {}
        }
    }

    fun refreshRecents() { viewModelScope.launch { recents = withContext(Dispatchers.IO) { Store.list(ctx) } } }

    // ---------- project lifecycle
    fun newProject(): Project { openProject(Project(name = "Project ${recents.size + 1}")); return project }
    fun openProject(p: Project) {
        project = p; undo.clear(); redo.clear(); canUndo = false; canRedo = false
        selClip = p.clips.firstOrNull()?.id; selText = null; selSound = null; sheet = null; sheetExpanded = false; showExport = false; inEditor = true; engine.pause(); engine.seek(0); engine.load(p, 0)
        export = ExportUi()
        p.clips.forEach { loadThumb(it); loadStrip(it); loadWave(it.uri) }
        p.sounds.forEach { loadWave(it.source) }
    }
    fun closeEditor() { saveJob?.cancel(); save(); inEditor = false; sheet = null; showExport = false; engine.pause(); engine.release(); viewModelScope.launch { kotlinx.coroutines.delay(300); refreshRecents() } }
    fun save() { if (project.clips.isNotEmpty() || project.texts.isNotEmpty()) viewModelScope.launch(Dispatchers.IO) { Store.save(ctx, project) } }
    fun deleteProject(id: String) { viewModelScope.launch { withContext(Dispatchers.IO) { Store.delete(ctx, id) }; refreshRecents() } }
    private fun updateStored(p: Project, f: (Project) -> Project) { viewModelScope.launch { withContext(Dispatchers.IO) { Store.save(ctx, f(p)) }; refreshRecents() } }
    fun renameProject(p: Project, name: String) = updateStored(p) { it.copy(name = name.trim().ifEmpty { it.name }) }
    fun toggleFavorite(p: Project) = updateStored(p) { it.copy(favorite = !it.favorite) }
    fun moveToFolder(p: Project, folder: String) = updateStored(p) { it.copy(folder = folder.trim()) }
    fun duplicateProject(p: Project) = updateStored(p) { it.copy(id = newId(), name = it.name + " copy", favorite = false) }
    fun renameCurrent(name: String) { project = project.copy(name = name.trim().ifEmpty { project.name }); save() }
    fun lastProject(): Project? = recents.firstOrNull()

    // ---------- editing core with undo
    fun edit(f: (Project) -> Project) {
        val now = System.currentTimeMillis()
        if (now - lastPush > 700) { undo.add(project); if (undo.size > 60) undo.removeAt(0); redo.clear() }
        lastPush = now
        project = f(project)
        canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
        engine.load(project)
        if (Prefs.autoSave) { saveJob?.cancel(); saveJob = viewModelScope.launch { kotlinx.coroutines.delay(1500); save() } }
    }
    private var saveJob: kotlinx.coroutines.Job? = null
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
    fun setTrim(id: String, inMs: Long, outMs: Long) = updateClip(id) { it.copy(inMs = inMs, outMs = outMs) }
    fun setImageLen(id: String, lenMs: Long) = updateClip(id) { it.copy(inMs = 0, outMs = lenMs, srcDurMs = maxOf(it.srcDurMs, lenMs)) }
    fun clipStartMs(id: String) = clipStart(id)
    fun setTransition(id: String, tid: String) = updateClip(id) { c -> c.copy(transition = tid, transitionMs = if (c.transition == "none" && tid != "none") Prefs.defaultTransMs else c.transitionMs) }
    /** plays through the cut into the selected clip, a moment before it */
    fun previewTransition() {
        val c = clip ?: return
        val idx = project.clips.indexOfFirst { it.id == c.id }
        if (idx <= 0) return
        val st = clipStart(c.id)
        seek((st - 900).coerceAtLeast(0)); engine.play()
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
    fun duplicateText() {
        val t = text ?: return
        val d = t.copy(id = newId(), startMs = t.startMs + t.durMs, x = (t.x + 0.03f).coerceAtMost(0.95f), y = (t.y + 0.03f).coerceAtMost(0.95f))
        edit { it.copy(texts = it.texts + d) }; selText = d.id
    }
    fun splitText() {
        val t = text ?: return
        val local = posMs - t.startMs
        if (local < 200 || local > t.durMs - 200) { toast = "Move the playhead inside the item"; return }
        val a = t.copy(durMs = local); val b = t.copy(id = newId(), startMs = posMs, durMs = t.durMs - local)
        edit { p -> p.copy(texts = p.texts.flatMap { if (it.id == t.id) listOf(a, b) else listOf(it) }) }; selText = b.id
    }
    fun moveText(id: String, startMs: Long) = updateText(id) { it.copy(startMs = startMs.coerceAtLeast(0)) }
    fun trimText(id: String, startMs: Long, durMs: Long) = updateText(id) { it.copy(startMs = startMs.coerceAtLeast(0), durMs = durMs.coerceAtLeast(300)) }

    // ---------- sounds
    fun addSound(m: SoundMeta) {
        val isBed = m.loop
        val s = SoundItem(name = m.name, source = m.assetUri, kind = if (isBed) "music" else "sfx", startMs = posMs, durMs = m.ms.toLong(), volume = if (isBed) 0.45f else 0.9f, loop = isBed, fadeOutMs = if (isBed) 800 else 0, srcMs = m.ms.toLong())
        edit { it.copy(sounds = it.sounds + s) }
        selSound = s.id; selText = null
        loadWave(s.source)
        Prefs.put("recent", (listOf(m.name) + Prefs.str("recent", "").split("|").filter { it.isNotEmpty() && it != m.name }).take(12).joinToString("|"))
    }
    fun addAudioFile(f: File, name: String, kind: String) {
        viewModelScope.launch {
            val d = withContext(Dispatchers.IO) { durationOf(Uri.fromFile(f).toString()) }
            val s = SoundItem(name = name, source = Uri.fromFile(f).toString(), kind = kind, startMs = posMs, durMs = d.coerceAtLeast(300), srcMs = d.coerceAtLeast(300))
            edit { it.copy(sounds = it.sounds + s) }
            selSound = s.id; selText = null
            loadWave(s.source)
        }
    }
    fun deleteSound() { val s = sound ?: return; edit { p -> p.copy(sounds = p.sounds.filter { it.id != s.id }) }; selSound = null }
    fun duplicateSound() {
        val s = sound ?: return
        val d = s.copy(id = newId(), startMs = s.startMs + s.durMs)
        edit { it.copy(sounds = it.sounds + d) }; selSound = d.id
    }
    fun splitSound() {
        val s = sound ?: return
        val local = posMs - s.startMs
        if (s.loop || local < 200 || local > s.durMs - 200) { toast = if (s.loop) "Loops can't be split — trim instead" else "Move the playhead inside the item"; return }
        val src = if (s.srcMs > 0) s.srcMs else s.durMs
        val a = s.copy(durMs = local, fadeOutMs = 0, srcMs = src)
        val b = s.copy(id = newId(), startMs = posMs, inMs = s.inMs + local, durMs = s.durMs - local, fadeInMs = 0, srcMs = src)
        edit { p -> p.copy(sounds = p.sounds.flatMap { if (it.id == s.id) listOf(a, b) else listOf(it) }) }; selSound = b.id
    }
    fun moveSound(id: String, startMs: Long) = updateSound(id) { it.copy(startMs = startMs.coerceAtLeast(0)) }
    /** trims the sound. [left] moves its in-point (keeping the sound's tail in place), otherwise its end. */
    fun trimSound(id: String, left: Boolean, deltaMs: Long) = updateSound(id) { s ->
        val src = if (s.srcMs > 0) s.srcMs else s.durMs
        if (left) {
            val d = deltaMs.coerceIn(-s.inMs, s.durMs - 200)
            s.copy(startMs = s.startMs + d, inMs = s.inMs + d, durMs = s.durMs - d, srcMs = src)
        } else {
            val max = if (s.loop) 600_000L else src - s.inMs
            s.copy(durMs = (s.durMs + deltaMs).coerceIn(200, max), srcMs = src)
        }
    }
    fun loadWave(uri: String) {
        if (waves.containsKey(uri)) return
        viewModelScope.launch(Dispatchers.IO) { val pk = Waveform.peaks(ctx, uri); withContext(Dispatchers.Main) { waves[uri] = pk } }
    }

    // ---------- media import
    private fun durationOf(uri: String): Long = try {
        val r = MediaMetadataRetriever(); r.setDataSource(ctx, Uri.parse(uri))
        val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L; r.release(); d
    } catch (e: Exception) { 0L }

    fun loadThumb(c: Clip) = loadThumbUri(c.uri, c.isImage, c.inMs)
    fun loadThumbUri(uri: String, isImage: Boolean, atMs: Long = 0) {
        if (thumbs.containsKey(uri)) return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp: Bitmap? = if (isImage) android.graphics.BitmapFactory.decodeFile(Uri.parse(uri).path, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })?.let { Bitmap.createScaledBitmap(it, 320, (320f * it.height / it.width).toInt().coerceAtLeast(1), true) }
                else { val r = MediaMetadataRetriever(); r.setDataSource(ctx, Uri.parse(uri)); val b = r.getFrameAtTime(atMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC); r.release(); b?.let { Bitmap.createScaledBitmap(it, 320, (320f * it.height / it.width).toInt().coerceAtLeast(1), true) } }
                if (bmp != null) withContext(Dispatchers.Main) { thumbs[uri] = bmp.asImageBitmap() }
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
                        if (img) Clip(uri = furi, isImage = true, srcDurMs = Prefs.defaultClipMs.toLong(), outMs = Prefs.defaultClipMs.toLong())
                        else { val d = durationOf(furi); if (d <= 0) null else Clip(uri = furi, srcDurMs = d, outMs = d) }
                    } catch (e: Exception) { null }
                }
            }
            importing = false
            if (added.isEmpty()) { toast = "Couldn't read that media"; return@launch }
            edit { it.copy(clips = it.clips + added) }
            added.forEach { loadThumb(it); loadStrip(it); if (!it.isImage) loadWave(it.uri) }
            selClip = added.first().id
            then?.invoke(added)
        }
    }

    var downloading by mutableStateOf<String?>(null)

    /** Downloads a free-to-use track from the open library and drops it on the timeline at the playhead. */
    fun addOpenTrack(t: OpenAudio.Track) {
        if (downloading != null) { toast = "Another download is running"; return }
        downloading = t.id
        viewModelScope.launch {
            try {
                val f = OpenAudio.download(ctx, t)
                val src = Uri.fromFile(f).toString()
                val d = withContext(Dispatchers.IO) { durationOf(src) }.let { if (it > 0) it else t.durationMs }.coerceAtLeast(300)
                val s = SoundItem(name = t.title, source = src, kind = "music", startMs = posMs, durMs = d, srcMs = d, volume = 0.6f, credit = if (t.needsCredit) t.credit else "")
                edit { it.copy(sounds = it.sounds + s) }
                selSound = s.id; selText = null
                loadWave(s.source)
                toast = "Added at playhead"
            } catch (e: Exception) { toast = "Download failed. Check your connection." }
            finally { downloading = null }
        }
    }

    /** Songs picked from the phone's music/files (any app that exposes audio through the system picker). */
    fun importAudios(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var at = posMs
            var last: SoundItem? = null
            for (u in uris) {
                val name = withContext(Dispatchers.IO) {
                    try { ctx.contentResolver.query(u, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null } } catch (e: Exception) { null }
                }?.substringBeforeLast('.')?.ifBlank { null } ?: "My audio"
                val f = withContext(Dispatchers.IO) {
                    val out = File(File(ctx.filesDir, "media").apply { mkdirs() }, "a_${System.nanoTime()}.m4a")
                    try { ctx.contentResolver.openInputStream(u)?.use { i -> out.outputStream().use { o -> i.copyTo(o) } }; out } catch (e: Exception) { null }
                } ?: continue
                val d = withContext(Dispatchers.IO) { durationOf(Uri.fromFile(f).toString()) }.coerceAtLeast(300)
                val s = SoundItem(name = name, source = Uri.fromFile(f).toString(), kind = "music", startMs = at, durMs = d, srcMs = d, volume = 0.7f)
                edit { it.copy(sounds = it.sounds + s) }
                loadWave(s.source); at += d; last = s
            }
            last?.let { selSound = it.id; selText = null; toast = if (uris.size > 1) "${uris.size} songs added" else "Added at playhead" }
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
                c.copy(inMs = 0, outMs = if (c.isImage) len else len, filter = t.filter, filterAmount = 1f, transition = if (i == 0) "none" else t.transition, transitionMs = Prefs.defaultTransMs)
            }
            val total = clips.sumOf { it.lengthMs }
            val texts = if (t.title != null) listOf(TextItem(text = t.title, startMs = 300, durMs = minOf(2600L, total - 300), anim = t.titleAnim, size = 0.085f, y = if (t.phone) 0.1f else 0.5f, font = titleFont(t.category), pill = false)) else emptyList()
            val sounds = ArrayList<SoundItem>()
            t.music?.let { n -> SoundLib.byName(n)?.let { m -> sounds.add(SoundItem(name = m.name, source = m.assetUri, kind = "music", startMs = 0, durMs = m.ms.toLong(), volume = 0.4f, loop = true, fadeOutMs = 900, srcMs = m.ms.toLong())) } }
            t.sfx.forEachIndexed { i, n -> SoundLib.byName(n)?.let { m -> sounds.add(SoundItem(name = m.name, source = m.assetUri, startMs = 250L + i * 450L, durMs = m.ms.toLong(), volume = 0.9f, srcMs = m.ms.toLong())) } }
            p.copy(clips = clips, texts = texts, sounds = sounds, ratio = t.ratio, fit = false,
                phone = PhoneCfg(enabled = t.phone, bezel = t.bezel, anim = t.anim, bg = t.bg))
        }
        engine.seek(0)
        project.sounds.forEach { loadWave(it.source) }
    }
    private fun titleFont(cat: String) = when (cat) {
        "Cinematic" -> "playfair"; "Gaming" -> "orbitron"; "Fashion" -> "abril"; "Retro" -> "mono"; "Vlog" -> "caveat"; "Social" -> "poppins"
        "Promotional" -> "anton"; "Product" -> "montserrat"; "Minimal" -> "light"; "Music" -> "bebas"; "Travel" -> "pacifico"; else -> "black"
    }

    // ---------- starting projects from Home / Templates / Assets
    /** Called with the gallery result: creates the project, imports the media and applies whatever was asked for. */
    fun startFromPicker(uris: List<Uri>) {
        val kind = pending; val tpl = pendingTemplate
        pending = Pending.New; pendingTemplate = null
        if (uris.isEmpty()) return
        newProject()
        importMedia(uris) {
            when {
                kind == Pending.Template && tpl != null -> applyTemplate(tpl)
                kind == Pending.Phone -> edit { it.copy(phone = it.phone.copy(enabled = true)) }
            }
        }
    }
    fun addExistingMedia(f: MediaFile) {
        val d = if (f.isImage) Prefs.defaultClipMs.toLong() else durationOf(f.uri)
        if (d <= 0) { toast = "Couldn't read that media"; return }
        val c = Clip(uri = f.uri, isImage = f.isImage, srcDurMs = d, outMs = d)
        edit { it.copy(clips = it.clips + c) }; loadThumb(c); loadStrip(c); if (!c.isImage) loadWave(c.uri); selClip = c.id
    }

    // ---------- assets / storage
    private fun dirSize(f: File): Long = if (f.isFile) f.length() else (f.listFiles() ?: emptyArray()).sumOf { dirSize(it) }
    fun refreshAssets() {
        viewModelScope.launch {
            val (list, st) = withContext(Dispatchers.IO) {
                val all = Store.list(ctx) + (if (inEditor) listOf(project) else emptyList())
                val used = HashMap<String, MutableSet<String>>()
                all.forEach { pr ->
                    pr.clips.forEach { used.getOrPut(it.uri) { HashSet() }.add(pr.id) }
                    pr.sounds.forEach { used.getOrPut(it.source) { HashSet() }.add(pr.id) }
                }
                val dir = File(ctx.filesDir, "media")
                val files = (dir.listFiles() ?: emptyArray()).sortedByDescending { it.lastModified() }.map { f ->
                    val u = Uri.fromFile(f).toString()
                    MediaFile(f, u, f.name.endsWith(".jpg"), f.name.startsWith("a_") || f.name.startsWith("voice"), f.length(), used[u]?.size ?: 0)
                }
                val sf = android.os.StatFs(ctx.filesDir.absolutePath)
                files to Storage(dirSize(dir), dirSize(ctx.cacheDir), dirSize(File(ctx.filesDir, "projects")), sf.availableBytes)
            }
            assets = list; storage = st
        }
    }
    fun clearCache() {
        if (export.running) { toast = "Wait for the export to finish"; return }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            waves.clear(); toast = "Cache cleared"; refreshAssets()
        }
    }
    fun removeUnusedMedia() {
        viewModelScope.launch {
            val n = withContext(Dispatchers.IO) { var k = 0; assets.filter { it.usedIn == 0 }.forEach { if (it.file.delete()) k++ }; k }
            toast = if (n == 0) "No unused media" else "Removed $n unused file${if (n == 1) "" else "s"}"
            refreshAssets()
        }
    }
    fun deleteMedia(f: MediaFile) { viewModelScope.launch { withContext(Dispatchers.IO) { f.file.delete() }; refreshAssets() } }

    // ---------- export
    fun openExport() {
        engine.pause()
        exportOpts = ExportOptions(Prefs.exportSide, Prefs.exportFps, Prefs.exportMbps, Prefs.exportHevc)
        export = ExportUi(); showExport = true
    }
    /** true when this device's encoder can produce [shortSide] for the project's aspect ratio */
    fun encodeSupported(shortSide: Int, hevc: Boolean): Boolean = try {
        val r = ratioById(project.ratio); val long = shortSide * maxOf(r.w, r.h) / minOf(r.w, r.h)
        val mime = if (hevc) "video/hevc" else "video/avc"
        android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { ci ->
            ci.isEncoder && ci.supportedTypes.any { it.equals(mime, true) } &&
                ci.getCapabilitiesForType(mime).videoCapabilities.let { vc -> vc.isSizeSupported(long, shortSide) || vc.isSizeSupported(shortSide, long) }
        }
    } catch (e: Exception) { true }
    fun hevcSupported(): Boolean = try {
        android.media.MediaCodecList(android.media.MediaCodecList.REGULAR_CODECS).codecInfos.any { it.isEncoder && it.supportedTypes.any { t -> t.equals("video/hevc", true) } }
    } catch (e: Exception) { false }


    fun startExport() {
        if (project.clips.isEmpty()) return
        val resumeAt = engine.timelineMs
        // free the preview decoders/GL while the encoder runs (low-end devices have few codec instances)
        engine.release()
        export = ExportUi(0f, true)
        exporter.export(project, exportOpts, { pr -> export = ExportUi(pr, true) }) { r ->
            r.onSuccess { export = ExportUi(1f, false, it); project = project.copy(exports = project.exports + 1); save() }.onFailure { export = ExportUi(0f, false, null, it.message ?: "Export failed") }
            engine.load(project, 0); engine.seek(resumeAt)
        }
    }
    fun cancelExport() { exporter.cancel(); export = ExportUi(); engine.load(project, 0) }
    fun saveExport() {
        val f = export.file ?: return
        viewModelScope.launch { val u = withContext(Dispatchers.IO) { Exporter.saveToGallery(ctx, f) }; toast = if (u != null) "Saved to Movies/Director Zone" else "Couldn't save" }
    }
}
