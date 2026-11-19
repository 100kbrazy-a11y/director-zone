package com.directorzone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.directorzone.catalog.*
import com.directorzone.data.*
import com.directorzone.engine.SoundLib
import com.directorzone.engine.SoundMeta
import com.directorzone.engine.TextPainter
import com.directorzone.engine.VoiceRecorder

private fun col(hex: String) = try { Color(("FF$hex").toLong(16)) } catch (e: Exception) { Color.White }

/** Non-lazy grid for the small fixed catalogues inside scrolling sheets. */
@Composable
fun <T> Grid(items: List<T>, cols: Int, gap: Dp = 8.dp, modifier: Modifier = Modifier, cell: @Composable (T) -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        items.chunked(cols).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                row.forEach { Box(Modifier.weight(1f)) { cell(it) } }
                repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun Hint(t: String, modifier: Modifier = Modifier) { Text(t, color = DZ.sub, fontSize = 14.sp, modifier = modifier.padding(horizontal = 18.dp, vertical = 14.dp)) }

@Composable
fun BigButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier = Modifier, filled: Boolean = true, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(if (filled) DZ.accent else DZ.card).border(1.dp, if (filled) Color.Transparent else DZ.line, RoundedCornerShape(50)).clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (icon != null) { Icon(icon, null, tint = if (filled) DZ.onAccent else DZ.text, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (filled) DZ.onAccent else DZ.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
    }
}

@Composable
fun SheetHost(vm: EditorVM, sheet: Sheet, pickMedia: () -> Unit) {
    val close = { vm.sheet = null }
    when (sheet) {
        Sheet.Media -> MediaSheet(vm, pickMedia, close)
        Sheet.Audio -> AudioSheet(vm, close)
        Sheet.Text -> TextSheet(vm, close)
        Sheet.Effects -> EffectsSheet(vm, close)
        Sheet.Color -> ColorSheet(vm, close)
        Sheet.Transitions -> TransitionsSheet(vm, close)
        Sheet.Canvas -> CanvasSheet(vm, close)
        Sheet.More -> MoreSheet(vm, pickMedia, close)
        Sheet.Stickers -> StickersSheet(vm, close)
        Sheet.Phone -> PhoneSheet(vm, close)
        Sheet.Trim -> TrimSheet(vm, close)
        Sheet.Speed -> SpeedSheet(vm, close)
        Sheet.Volume -> VolumeSheet(vm, close)
        Sheet.ClipMore -> ClipMoreSheet(vm, close)
        Sheet.Templates -> TemplatesSheet(vm, close)
    }
}

// =================================================================== Media
@Composable
fun MediaSheet(vm: EditorVM, pickMedia: () -> Unit, close: () -> Unit) {
    LaunchedEffect(Unit) { vm.refreshAssets() }
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Media", close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Add photos & videos", Icons.Rounded.AddPhotoAlternate, Modifier.fillMaxWidth()) { pickMedia() }
            val media = vm.assets.filter { !it.isAudio }
            SectionLabel("Already imported (${media.size})")
            if (media.isEmpty()) Text("Media you import is kept with the app so projects keep working. It will show up here.", color = DZ.sub, fontSize = 13.sp)
            else Grid(media, 3) { f ->
                LaunchedEffect(f.uri) { vm.loadThumbUri(f.uri, f.isImage) }
                Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(12.dp)).background(DZ.card).clickable { vm.addExistingMedia(f); vm.toast = "Added to timeline" }) {
                    vm.thumbs[f.uri]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                    Icon(if (f.isImage) Icons.Rounded.Image else Icons.Rounded.Videocam, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(16.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

// =================================================================== Audio
object Previewer {
    private var mp: MediaPlayer? = null
    var playing by mutableStateOf<String?>(null); private set
    fun toggle(ctx: android.content.Context, m: SoundMeta) {
        if (playing == m.name) { stop(); return }
        stop(); OnlinePreview.stop()
        try {
            val fd = ctx.assets.openFd("sfx/${m.file}")
            mp = MediaPlayer().apply { setDataSource(fd.fileDescriptor, fd.startOffset, fd.length); isLooping = false; prepare(); start(); setOnCompletionListener { stop() } }
            fd.close(); playing = m.name
        } catch (_: Exception) { playing = null }
    }
    fun stop() { try { mp?.stop(); mp?.release() } catch (_: Exception) {}; mp = null; playing = null }
}

@Composable
fun AudioSheet(vm: EditorVM, close: () -> Unit) {
    val ctx = LocalContext.current
    var tab by vm::audioTab
    var search by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var cat by remember { mutableStateOf("All") }
    val rec = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) vm.importAudio(u) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok && rec.start()) recording = true else vm.toast = "Microphone permission needed" }
    DisposableEffect(Unit) { onDispose { Previewer.stop(); if (recording) rec.stop() } }
    val sel = vm.sound
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Audio", close) {
            if (tab < 2 || tab == 5) IconButton(onClick = { search = !search; if (!search) query = "" }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Search, null, tint = if (search) DZ.accent else DZ.text) }
        }
        TextTabs(listOf("Music Library", "Sound Effects", "Free Music", "Free SFX", "Your Music", "Recent", "Voiceover"), tab) { tab = it; cat = "All" }
        if (search && tab < 2 || search && tab == 5) BasicTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(DZ.card).padding(12.dp),
            textStyle = TextStyle(color = DZ.text, fontSize = 15.sp), cursorBrush = SolidColor(DZ.accent), decorationBox = { if (query.isEmpty()) Text("Search sounds", color = DZ.sub, fontSize = 15.sp); it() })
        if (sel != null) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(DZ.card).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.GraphicEq, null, tint = DZ.accent2, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(sel.name, color = DZ.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 1)
                        if (sel.credit.isNotBlank()) Text(sel.credit, color = DZ.sub, fontSize = 10.sp, maxLines = 1)
                    }
                    IconButton(onClick = { vm.moveSound(sel.id, vm.posMs) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.PlaylistAdd, null, tint = DZ.text, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { vm.splitSound() }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.CallSplit, null, tint = DZ.text, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { vm.duplicateSound() }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.ContentCopy, null, tint = DZ.text, modifier = Modifier.size(20.dp)) }
                    IconButton(onClick = { vm.deleteSound() }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Delete, null, tint = DZ.danger, modifier = Modifier.size(20.dp)) }
                }
                AdjustRow("Volume", sel.volume, 0f..2f, { pct(it) + "%" }) { v -> vm.updateSound(sel.id) { it.copy(volume = v) } }
                if (vm.sheetExpanded) {
                    AdjustRow("Fade in", sel.fadeInMs / 1000f, 0f..3f, { fmtSecs((it * 1000).toLong()) }) { v -> vm.updateSound(sel.id) { it.copy(fadeInMs = (v * 1000).toInt()) } }
                    AdjustRow("Fade out", sel.fadeOutMs / 1000f, 0f..3f, { fmtSecs((it * 1000).toLong()) }) { v -> vm.updateSound(sel.id) { it.copy(fadeOutMs = (v * 1000).toInt()) } }
                    AdjustRow("Start", sel.startMs / 1000f, 0f..maxOf(1f, vm.project.durationMs / 1000f), { fmtSecs((it * 1000).toLong()) }) { v -> vm.moveSound(sel.id, (v * 1000).toLong()) }
                } else Text("Drag the sheet up for fades & placement. Drag the block's handles on the timeline to trim.", color = DZ.sub, fontSize = 11.sp)
            }
        }
        when (tab) {
            2 -> OpenMusicTab(vm, false, Modifier.weight(1f))
            3 -> OpenMusicTab(vm, true, Modifier.weight(1f))
            4 -> DeviceMusicTab(vm, Modifier.weight(1f))
            6 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton(if (recording) "Stop recording" else "Record voiceover at playhead", if (recording) Icons.Rounded.Stop else Icons.Rounded.Mic, Modifier.fillMaxWidth(), filled = !recording) {
                    if (recording) { rec.stop()?.let { vm.addAudioFile(it, "Voiceover", "voice") }; recording = false }
                    else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { if (rec.start()) recording = true else vm.toast = "Couldn't start recording" }
                    else askMic.launch(Manifest.permission.RECORD_AUDIO)
                }
                BigButton("Import an audio file", Icons.Rounded.AudioFile, Modifier.fillMaxWidth(), filled = false) { pickAudio.launch("audio/*") }
                Text("Recordings and imported files are added to the timeline at the playhead and get their own waveform.", color = DZ.sub, fontSize = 13.sp)
            }
            else -> {
                val base = when (tab) {
                    0 -> SoundLib.all.filter { it.loop }
                    1 -> SoundLib.all.filter { !it.loop }
                    else -> Prefs.str("recent", "").split("|").filter { it.isNotEmpty() }.mapNotNull { SoundLib.byName(it) }
                }
                val cats = if (tab == 5) emptyList() else listOf("All") + base.map { it.cat }.distinct().sortedBy { SoundLib.ORDER.indexOf(it) }
                if (cats.isNotEmpty()) Box(Modifier.padding(vertical = 8.dp, horizontal = 16.dp)) { Chips(cats, cat) { cat = it } }
                val list = base.filter { (cat == "All" || it.cat == cat) && (query.isBlank() || it.name.contains(query, true)) }
                if (list.isEmpty()) Hint(if (tab == 5) "Sounds you add will show up here." else "No sounds match.")
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    items(list, key = { it.name }) { m ->
                        val on = Previewer.playing == m.name
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { Previewer.toggle(ctx, m) }.padding(horizontal = 6.dp, vertical = (6 * dens).dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(DZ.card), contentAlignment = Alignment.Center) {
                                Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = DZ.text)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(m.name, color = DZ.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text("${m.cat} · ${"%.1f".format(m.ms / 1000f)}s${if (m.loop) " · loops" else ""}", color = DZ.sub, fontSize = 12.sp)
                            }
                            Box(Modifier.size(36.dp).clip(CircleShape).background(DZ.card).clickable { vm.addSound(m); vm.toast = "Added at playhead" }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = DZ.text) }
                        }
                    }
                }
            }
        }
    }
}

// =================================================================== Effects
@Composable
fun EffectsSheet(vm: EditorVM, close: () -> Unit) {
    val cl = vm.clip
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Effects", close)
        if (cl == null) { Hint("Select a clip first."); return }
        TextTabs(FX_CATS, tab) { tab = it }
        val list = EFFECTS.filter { it.cat == FX_CATS[tab] || (it.id == "none" && tab == 0) }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Grid(list, 3) { f ->
                val on = cl.effect == f.id
                Column(Modifier.clickable { vm.updateClip(cl.id) { it.copy(effect = f.id) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    LookThumb(vm, cl, f.a, if (f.id == "none") 0f else cl.effectAmount, vm.project.ratio, on, f.a.vignette)
                    Text(f.name, color = if (on) DZ.text else DZ.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        AdjustRow("Intensity", cl.effectAmount, 0f..1.5f, { pct(it) + "%" }, ) { v -> vm.updateClip(cl.id) { it.copy(effectAmount = v) } }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Apply to all clips", false) { vm.allClips { it.copy(effect = cl.effect, effectAmount = cl.effectAmount) }; vm.toast = "Effect applied to all clips" }
        }
    }
}

/** A real frame from the selected clip with the look's colour maths applied (approximate: no grain / glow). */
@Composable
fun LookThumb(vm: EditorVM, cl: Clip, a: Adjust, amount: Float, ratio: String, selected: Boolean, vignette: Float = 0f, extra: Adjust? = null) {
    val img = vm.thumbs[cl.uri]
    val shape = RoundedCornerShape(12.dp)
    Box(Modifier.fillMaxWidth().aspectRatio(1.35f).clip(shape).background(DZ.card2).border(if (selected) 2.dp else 0.dp, if (selected) DZ.text else Color.Transparent, shape)) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, colorFilter = (extra?.let { it + a.scaled(amount) } ?: a.scaled(amount)).toColorFilter(1f))
        if (vignette > 0f && amount > 0f) Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = (vignette * amount * 0.7f).coerceIn(0f, 0.8f))))))
    }
}

// =================================================================== Color (Filters & Color)
@Composable
fun ColorSheet(vm: EditorVM, close: () -> Unit) {
    val cl = vm.clip
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Filters & Color", close) {
            IconButton(onClick = { cl?.let { c -> vm.updateClip(c.id) { it.copy(filter = "none", filterAmount = 1f, adjust = Adjust()) } } }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.RestartAlt, null, tint = DZ.text) }
        }
        if (cl == null) { Hint("Select a clip first."); return }
        val a = cl.adjust
        fun set(f: (Adjust) -> Adjust) = vm.updateClip(cl.id) { it.copy(adjust = f(it.adjust)) }
        androidx.compose.foundation.lazy.LazyRow(contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FILTERS, key = { it.id }) { f ->
                val on = cl.filter == f.id
                Column(Modifier.width(92.dp).clickable { vm.updateClip(cl.id) { it.copy(filter = f.id) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                    LookThumb(vm, cl, f.a, if (f.id == "none") 0f else 1f, vm.project.ratio, on, f.a.vignette)
                    Text(f.name, color = if (on) DZ.text else DZ.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 6.dp)) {
            AdjustRow("Filter Intensity", cl.filterAmount, 0f..1.5f, { pct(it) + "%" }) { v -> vm.updateClip(cl.id) { it.copy(filterAmount = v) } }
            AdjustRow("Exposure", a.exposure, -1f..1f, ::signed) { v -> set { it.copy(exposure = v) } }
            AdjustRow("Contrast", a.contrast, -0.6f..0.6f, { signed(it / 0.6f) }) { v -> set { it.copy(contrast = v) } }
            AdjustRow("Saturation", a.saturation, -1f..1f, ::signed) { v -> set { it.copy(saturation = v) } }
            AdjustRow("Temperature", a.warmth, -1f..1f, ::signed) { v -> set { it.copy(warmth = v) } }
            AdjustRow("Tint", a.tint, -1f..1f, ::signed) { v -> set { it.copy(tint = v) } }
            AdjustRow("Highlights", a.highlights, -1f..1f, ::signed) { v -> set { it.copy(highlights = v) } }
            AdjustRow("Shadows", a.shadows, -1f..1f, ::signed) { v -> set { it.copy(shadows = v) } }
            AdjustRow("Hue", a.hue, -1f..1f, ::signed) { v -> set { it.copy(hue = v) } }
            AdjustRow("Fade", a.fade, 0f..1f, ::pct) { v -> set { it.copy(fade = v) } }
            AdjustRow("Sharpness", a.sharpen, 0f..1f, ::pct) { v -> set { it.copy(sharpen = v) } }
            AdjustRow("Vignette", a.vignette, 0f..1f, ::pct) { v -> set { it.copy(vignette = v) } }
            AdjustRow("Film Grain", a.grain, 0f..1f, ::pct) { v -> set { it.copy(grain = v) } }
            AdjustRow("Glow", a.glow, 0f..1f, ::pct) { v -> set { it.copy(glow = v) } }
            AdjustRow("Chromatic Aberr.", a.chroma, 0f..1f, ::pct) { v -> set { it.copy(chroma = v) } }
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Apply to all clips", false) { vm.allClips { it.copy(filter = cl.filter, filterAmount = cl.filterAmount, adjust = cl.adjust) }; vm.toast = "Look applied to all clips" }
            }
        }
    }
}

// =================================================================== Text
private data class TextPreset(val name: String, val make: (TextItem) -> TextItem)
private val TEXT_PRESETS = listOf(
    TextPreset("Title") { it.copy(text = "Your title", font = "black", size = 0.09f, anim = "pop") },
    TextPreset("Caption") { it.copy(text = "Caption", font = "medium", size = 0.05f, y = 0.82f, pill = true, pillColor = "000000", anim = "fade") },
    TextPreset("Neon") { it.copy(text = "NEON", font = "orbitron", size = 0.09f, color = "FF3DCB", glow = 0.9f, anim = "pulse") },
    TextPreset("Outline") { it.copy(text = "OUTLINE", font = "anton", size = 0.1f, color = "FFFFFF", outlineW = 0.06f, outlineColor = "000000", anim = "zoom") },
    TextPreset("Script") { it.copy(text = "Hello", font = "pacifico", size = 0.1f, color = "FFD166", anim = "slideup") },
    TextPreset("Retro") { it.copy(text = "PLAY ▶", font = "arcade", size = 0.06f, color = "7CFFB2", glow = 0.4f, anim = "typewriter") },
)

@Composable
fun TextSheet(vm: EditorVM, close: () -> Unit) {
    val t = vm.text?.takeIf { !it.sticker }
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var fcat by remember { mutableStateOf("All") }
    val focus = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Text", close) {
            if (t != null) IconButton(onClick = { runCatching { focus.requestFocus() } }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Keyboard, null, tint = DZ.text) }
        }
        if (t == null) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BigButton("Add text at playhead", Icons.Rounded.Add, Modifier.fillMaxWidth()) { vm.addText() }
                SectionLabel("Quick styles")
                Grid(TEXT_PRESETS, 3) { pr ->
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DZ.card).clickable {
                        vm.addText(); vm.text?.let { cur -> vm.updateText(cur.id) { pr.make(it) } }
                    }.padding(vertical = 16.dp), contentAlignment = Alignment.Center) { Text(pr.name, color = DZ.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
                Text("Tip: tap a text block on the timeline to edit it. On the preview, drag to move, pinch to scale, twist to rotate.", color = DZ.sub, fontSize = 12.sp)
            }
            return
        }
        var field by remember(t.id) { mutableStateOf(t.text) }
        Row(Modifier.padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(field, { field = it; vm.updateText(t.id) { x -> x.copy(text = it) } }, Modifier.weight(1f).focusRequester(focus).clip(RoundedCornerShape(12.dp)).background(DZ.card).padding(12.dp),
                textStyle = TextStyle(color = DZ.text, fontSize = 15.sp), cursorBrush = SolidColor(DZ.accent))
            IconButton(onClick = { vm.addText() }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.AddCircleOutline, null, tint = DZ.text) }
            IconButton(onClick = { vm.duplicateText() }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.ContentCopy, null, tint = DZ.text, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = { vm.deleteText() }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.Delete, null, tint = DZ.danger, modifier = Modifier.size(20.dp)) }
        }
        TextTabs(listOf("Font", "Style", "Color", "Effects", "Animation"), tab) { tab = it }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 6.dp, bottom = 8.dp)) {
            when (tab) {
                0 -> {
                    Chips(FONT_CATS, fcat) { fcat = it }
                    Spacer(Modifier.height(10.dp))
                    Grid(FONTS.filter { fcat == "All" || it.cat == fcat }, 2) { f ->
                        val on = t.font == f.id
                        val fam = remember(f.id) { FontFamily(TextPainter.typeface(f.id)) }
                        Box(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(12.dp)).background(DZ.card).border(if (on) 1.5.dp else 0.dp, if (on) DZ.text else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { vm.updateText(t.id) { it.copy(font = f.id) } }, contentAlignment = Alignment.Center) {
                            Text(f.name, color = DZ.text, fontFamily = fam, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    AdjustRow("Size", t.size, 0.03f..0.3f, { "${(it * 100).toInt()}pt" }) { v -> vm.updateText(t.id) { it.copy(size = v) } }
                }
                1 -> {
                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Align", color = DZ.sub, fontSize = 14.sp, modifier = Modifier.width(96.dp))
                        listOf("left" to Icons.Rounded.FormatAlignLeft, "center" to Icons.Rounded.FormatAlignCenter, "right" to Icons.Rounded.FormatAlignRight).forEach { (id, ic) ->
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (t.align == id) DZ.accentSoft else DZ.card).border(1.dp, if (t.align == id) DZ.accent else Color.Transparent, RoundedCornerShape(10.dp)).clickable { vm.updateText(t.id) { it.copy(align = id) } }, contentAlignment = Alignment.Center) { Icon(ic, null, tint = DZ.text) }
                        }
                    }
                    AdjustRow("Size", t.size, 0.03f..0.3f, { "${(it * 100).toInt()}pt" }) { v -> vm.updateText(t.id) { it.copy(size = v) } }
                    AdjustRow("Letter spacing", t.letterSp, -0.05f..0.4f, { signed(it) }) { v -> vm.updateText(t.id) { it.copy(letterSp = v) } }
                    AdjustRow("Line spacing", t.lineSp, 0.8f..2f, { "%.2f".format(it) }) { v -> vm.updateText(t.id) { it.copy(lineSp = v) } }
                    AdjustRow("Tilt", t.rotation, -90f..90f, { "${it.toInt()}°" }) { v -> vm.updateText(t.id) { it.copy(rotation = v) } }
                    AdjustRow("Position X", t.x, 0f..1f, ::pct) { v -> vm.updateText(t.id) { it.copy(x = v) } }
                    AdjustRow("Position Y", t.y, 0f..1f, ::pct) { v -> vm.updateText(t.id) { it.copy(y = v) } }
                }
                2 -> {
                    SectionLabel("Text colour")
                    Grid(TEXT_COLORS, 8, 10.dp) { hex -> Swatch(col(hex), t.color == hex, 34) { vm.updateText(t.id) { it.copy(color = hex) } } }
                }
                3 -> {
                    SectionLabel("Outline")
                    AdjustRow("Width", t.outlineW, 0f..0.2f, { pct(it * 5) }) { v -> vm.updateText(t.id) { it.copy(outlineW = v) } }
                    if (t.outlineW > 0f) Grid(TEXT_COLORS, 8, 10.dp, Modifier.padding(vertical = 6.dp)) { hex -> Swatch(col(hex), t.outlineColor == hex, 30) { vm.updateText(t.id) { it.copy(outlineColor = hex) } } }
                    SectionLabel("Shadow & glow")
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Drop shadow", color = DZ.text, fontSize = 15.sp, modifier = Modifier.weight(1f)); DzSwitch(t.shadow) { v -> vm.updateText(t.id) { it.copy(shadow = v) } } }
                    AdjustRow("Glow", t.glow, 0f..1f, ::pct) { v -> vm.updateText(t.id) { it.copy(glow = v) } }
                    SectionLabel("Background")
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Label background", color = DZ.text, fontSize = 15.sp, modifier = Modifier.weight(1f)); DzSwitch(t.pill) { v -> vm.updateText(t.id) { it.copy(pill = v) } } }
                    if (t.pill) {
                        AdjustRow("Opacity", t.bgAlpha, 0.1f..1f, ::pct) { v -> vm.updateText(t.id) { it.copy(bgAlpha = v) } }
                        Grid(TEXT_COLORS, 8, 10.dp, Modifier.padding(vertical = 6.dp)) { hex -> Swatch(col(hex), t.pillColor == hex, 30) { vm.updateText(t.id) { it.copy(pillColor = hex) } } }
                    }
                }
                else -> {
                    Grid(TEXT_ANIMS, 3) { a ->
                        val on = t.anim == a.id
                        Box(Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(12.dp)).background(if (on) DZ.accentSoft else DZ.card).border(1.dp, if (on) DZ.accent else Color.Transparent, RoundedCornerShape(12.dp))
                            .clickable { vm.updateText(t.id) { it.copy(anim = a.id) } }, contentAlignment = Alignment.Center) { Text(a.name, color = if (on) DZ.text else DZ.sub, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1) }
                    }
                    Spacer(Modifier.height(8.dp))
                    AdjustRow("Start", t.startMs / 1000f, 0f..maxOf(1f, vm.project.durationMs / 1000f), { fmtSecs((it * 1000).toLong()) }) { v -> vm.updateText(t.id) { it.copy(startMs = (v * 1000).toLong()) } }
                    AdjustRow("Duration", t.durMs / 1000f, 0.5f..15f, { fmtSecs((it * 1000).toLong()) }) { v -> vm.updateText(t.id) { it.copy(durMs = (v * 1000).toLong()) } }
                }
            }
        }
    }
}
