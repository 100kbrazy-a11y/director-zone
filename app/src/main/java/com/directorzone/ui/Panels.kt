package com.directorzone.ui

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.directorzone.catalog.*
import com.directorzone.data.Adjust
import com.directorzone.engine.SoundLib
import com.directorzone.engine.SoundMeta
import com.directorzone.engine.VoiceRecorder

private fun c(v: Long) = Color(v)

@Composable
private fun Hint(t: String) { Text(t, color = DZ.sub, fontSize = 13.sp, modifier = Modifier.padding(vertical = 14.dp)) }

@Composable
private fun ActionBtn(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean = true, tint: Color = DZ.text, onClick: () -> Unit) {
    Column(Modifier.width(62.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (enabled) tint else DZ.card2, modifier = Modifier.size(22.dp))
        Text(label, color = if (enabled) DZ.sub else DZ.card2, fontSize = 10.sp, maxLines = 1)
    }
}

// ---------------------------------------------------------------- Clip
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClipPanel(vm: EditorVM) {
    val cl = vm.clip
    if (cl == null) { Hint("Tap a clip in the timeline."); return }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ActionBtn("Split", Icons.Rounded.ContentCut, !cl.isImage) { vm.splitAtPlayhead() }
            ActionBtn("Duplicate", Icons.Rounded.ContentCopy) { vm.duplicateClip() }
            ActionBtn("Move ◀", Icons.Rounded.ArrowBack) { vm.moveClip(-1) }
            ActionBtn("Move ▶", Icons.Rounded.ArrowForward) { vm.moveClip(1) }
            ActionBtn("Delete", Icons.Rounded.Delete, tint = DZ.pink) { vm.deleteClip() }
        }
        if (!cl.isImage) {
            LabeledSlider("Speed", cl.speed, 0.25f..4f, { "%.2f×".format(it) }) { v -> vm.updateClip(cl.id) { it.copy(speed = v) } }
            LabeledSlider("Volume", cl.volume, 0f..2f, { "${(it * 100).toInt()}%" }) { v -> vm.updateClip(cl.id) { it.copy(volume = v) } }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Trim", color = DZ.sub, fontSize = 12.sp, modifier = Modifier.width(84.dp))
                RangeSlider(
                    value = (cl.inMs / 1000f)..(cl.outMs / 1000f),
                    onValueChange = { r ->
                        val a = (r.start * 1000).toLong(); val b = (r.endInclusive * 1000).toLong()
                        if (b - a >= 300) vm.updateClip(cl.id) { it.copy(inMs = a, outMs = b) }
                    },
                    valueRange = 0f..(cl.srcDurMs / 1000f), modifier = Modifier.weight(1f).height(30.dp),
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = DZ.violet, inactiveTrackColor = DZ.card2),
                )
                Text("${"%.1f".format(cl.lengthMs / 1000f)}s", color = DZ.text, fontSize = 11.sp, modifier = Modifier.padding(start = 6.dp))
            }
        } else {
            LabeledSlider("Duration", cl.outMs / 1000f, 0.5f..10f, { "${"%.1f".format(it)}s" }) { v -> vm.updateClip(cl.id) { it.copy(outMs = (v * 1000).toLong(), srcDurMs = maxOf(it.srcDurMs, (v * 1000).toLong())) } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Ken Burns zoom", color = DZ.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(cl.kenBurns || cl.isImage, { v -> vm.updateClip(cl.id) { it.copy(kenBurns = v) } }, enabled = !cl.isImage)
        }
    }
}

// ---------------------------------------------------------------- Filters
@Composable
fun FiltersPanel(vm: EditorVM) {
    val cl = vm.clip
    if (cl == null) { Hint("Select a clip first."); return }
    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FILTERS) { f ->
                val sel = cl.filter == f.id
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { vm.updateClip(cl.id) { it.copy(filter = f.id) } }) {
                    Box(Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(c(f.swatch), c(f.swatch).copy(alpha = 0.35f)))).border(if (sel) 2.dp else 0.dp, if (sel) Color.White else Color.Transparent, RoundedCornerShape(14.dp)))
                    Text(f.name, color = if (sel) Color.White else DZ.sub, fontSize = 10.5.sp, maxLines = 1, modifier = Modifier.width(60.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        LabeledSlider("Intensity", cl.filterAmount, 0f..1.5f, { "${(it * 100).toInt()}%" }) { v -> vm.updateClip(cl.id) { it.copy(filterAmount = v) } }
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Apply to all clips", false) { vm.allClips { it.copy(filter = cl.filter, filterAmount = cl.filterAmount) }; vm.toast = "Filter applied to all clips" }
        }
    }
}

// ---------------------------------------------------------------- Adjust
@Composable
fun AdjustPanel(vm: EditorVM) {
    val cl = vm.clip
    if (cl == null) { Hint("Select a clip first."); return }
    val a = cl.adjust
    fun set(f: (Adjust) -> Adjust) = vm.updateClip(cl.id) { it.copy(adjust = f(it.adjust)) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        LabeledSlider("Exposure", a.exposure, -1f..1f) { v -> set { it.copy(exposure = v) } }
        LabeledSlider("Contrast", a.contrast, -0.6f..0.6f) { v -> set { it.copy(contrast = v) } }
        LabeledSlider("Saturation", a.saturation, -1f..1f) { v -> set { it.copy(saturation = v) } }
        LabeledSlider("Warmth", a.warmth, -1f..1f) { v -> set { it.copy(warmth = v) } }
        LabeledSlider("Tint", a.tint, -1f..1f) { v -> set { it.copy(tint = v) } }
        LabeledSlider("Highlights", a.highlights, -1f..1f) { v -> set { it.copy(highlights = v) } }
        LabeledSlider("Shadows", a.shadows, -1f..1f) { v -> set { it.copy(shadows = v) } }
        LabeledSlider("Hue", a.hue, -1f..1f) { v -> set { it.copy(hue = v) } }
        LabeledSlider("Fade", a.fade, 0f..1f) { v -> set { it.copy(fade = v) } }
        LabeledSlider("Glow", a.glow, 0f..1f) { v -> set { it.copy(glow = v) } }
        LabeledSlider("Chromatic", a.chroma, 0f..1f) { v -> set { it.copy(chroma = v) } }
        LabeledSlider("Vignette", a.vignette, 0f..1f) { v -> set { it.copy(vignette = v) } }
        LabeledSlider("Film grain", a.grain, 0f..1f) { v -> set { it.copy(grain = v) } }
        Pill("Reset adjustments", false) { set { Adjust() } }
    }
}

// ---------------------------------------------------------------- Transitions
@Composable
fun TransitionsPanel(vm: EditorVM) {
    val cl = vm.clip
    val idx = vm.project.clips.indexOfFirst { it.id == cl?.id }
    if (cl == null) { Hint("Select a clip — the transition plays into it."); return }
    if (idx <= 0) { Hint("Select the 2nd (or later) clip to set the transition from the clip before it."); return }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TRANSITIONS) { t ->
                Pill(t.name, cl.transition == t.id, leading = { Icon(if (t.id == "none") Icons.Rounded.Block else Icons.Rounded.Bolt, null, tint = if (cl.transition == t.id) DZ.pink else DZ.sub, modifier = Modifier.size(15.dp)) }) {
                    vm.updateClip(cl.id) { it.copy(transition = t.id) }
                }
            }
        }
        LabeledSlider("Length", cl.transitionMs / 1000f, 0.2f..1.5f, { "${"%.1f".format(it)}s" }) { v -> vm.updateClip(cl.id) { it.copy(transitionMs = (v * 1000).toInt()) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Auto whoosh sound on transitions", color = DZ.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(vm.project.autoSfx, { v -> vm.edit { it.copy(autoSfx = v) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Use on every cut", false) { vm.edit { p -> p.copy(clips = p.clips.mapIndexed { i, k -> if (i == 0) k else k.copy(transition = cl.transition, transitionMs = cl.transitionMs) }) }; vm.toast = "Applied to all cuts" }
        }
    }
}

// ---------------------------------------------------------------- Phone
@Composable
fun PhonePanel(vm: EditorVM) {
    val ph = vm.project.phone
    fun set(f: (com.directorzone.data.PhoneCfg) -> com.directorzone.data.PhoneCfg) = vm.edit { it.copy(phone = f(it.phone)) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Show video inside a 3D phone", color = DZ.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Switch(ph.enabled, { v -> set { it.copy(enabled = v) } })
        }
        if (!ph.enabled) { Hint("Turn it on to pick a device, animation and backdrop."); return@Column }
        SectionLabel("Animation")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(PHONE_ANIMS) { a ->
                val on = ph.anim == a.id
                Column(Modifier.width(78.dp).clip(RoundedCornerShape(14.dp)).background(if (on) DZ.violet.copy(alpha = 0.28f) else DZ.card).border(1.dp, if (on) DZ.violet else Color.Transparent, RoundedCornerShape(14.dp)).clickable { set { it.copy(anim = a.id) } }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PhoneGlyph(if (on) DZ.pink else DZ.violet, a.code, Modifier.size(54.dp, 58.dp), speed = 2600)
                    Text(a.name, color = if (on) Color.White else DZ.sub, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        SectionLabel("Device")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BEZELS) { b -> Pill(b.name, ph.bezel == b.id, leading = { Box(Modifier.size(14.dp).clip(CircleShape).background(c(b.frame))) }) { set { it.copy(bezel = b.id) } } }
        }
        SectionLabel("Backdrop")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(BACKGROUNDS) { b -> Pill(b.name, ph.bg == b.id, leading = { Box(Modifier.size(16.dp).clip(CircleShape).background(Brush.linearGradient(listOf(c(b.c1), c(b.c2), c(b.c3))))) }) { set { it.copy(bg = b.id) } } }
        }
        Spacer(Modifier.height(6.dp))
        LabeledSlider("Size", ph.scale, 0.5f..1.4f, { "${(it * 100).toInt()}%" }) { v -> set { it.copy(scale = v) } }
        LabeledSlider("Tempo", ph.tempo, 0.3f..2.5f, { "%.1f×".format(it) }) { v -> set { it.copy(tempo = v) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Drop shadow", color = DZ.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Switch(ph.shadow, { v -> set { it.copy(shadow = v) } })
        }
    }
}

// ---------------------------------------------------------------- Text
@Composable
fun TextPanel(vm: EditorVM) {
    val t = vm.text?.takeIf { !it.sticker }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        if (t == null) {
            Hint("Add animated text at the playhead.")
            GradientButton("Add text", Icons.Rounded.Add) { vm.addText() }
            return@Column
        }
        var field by remember(t.id) { mutableStateOf(t.text) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(field, { field = it; vm.updateText(t.id) { x -> x.copy(text = it) } }, Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(DZ.card).padding(12.dp),
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp), cursorBrush = SolidColor(DZ.pink))
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { vm.addText() }) { Icon(Icons.Rounded.AddCircle, null, tint = DZ.pink) }
            IconButton(onClick = { vm.deleteText() }) { Icon(Icons.Rounded.Delete, null, tint = DZ.sub) }
        }
        SectionLabel("Animation")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(TEXT_ANIMS) { a -> Pill(a.name, t.anim == a.id) { vm.updateText(t.id) { it.copy(anim = a.id) } } } }
        SectionLabel("Font")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(FONTS) { (id, n) -> Pill(n, t.font == id) { vm.updateText(t.id) { it.copy(font = id) } } } }
        SectionLabel("Colour")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(TEXT_COLORS) { hex -> Swatch(Color(("FF$hex").toLong(16)), t.color == hex) { vm.updateText(t.id) { it.copy(color = hex) } } } }
        Spacer(Modifier.height(4.dp))
        LabeledSlider("Size", t.size, 0.03f..0.2f, { "${(it * 100).toInt()}" }) { v -> vm.updateText(t.id) { it.copy(size = v) } }
        LabeledSlider("Position X", t.x, 0f..1f, { "${(it * 100).toInt()}" }) { v -> vm.updateText(t.id) { it.copy(x = v) } }
        LabeledSlider("Position Y", t.y, 0f..1f, { "${(it * 100).toInt()}" }) { v -> vm.updateText(t.id) { it.copy(y = v) } }
        LabeledSlider("Start", t.startMs / 1000f, 0f..maxOf(1f, vm.project.durationMs / 1000f), { "${"%.1f".format(it)}s" }) { v -> vm.updateText(t.id) { it.copy(startMs = (v * 1000).toLong()) } }
        LabeledSlider("Length", t.durMs / 1000f, 0.5f..10f, { "${"%.1f".format(it)}s" }) { v -> vm.updateText(t.id) { it.copy(durMs = (v * 1000).toLong()) } }
        LabeledSlider("Tilt", t.rotation, -45f..45f, { "${it.toInt()}°" }) { v -> vm.updateText(t.id) { it.copy(rotation = v) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Label background", t.pill) { vm.updateText(t.id) { it.copy(pill = !it.pill) } }
            Pill("Shadow", t.shadow) { vm.updateText(t.id) { it.copy(shadow = !it.shadow) } }
        }
    }
}

// ---------------------------------------------------------------- Stickers
@Composable
fun StickerPanel(vm: EditorVM) {
    val s = vm.text?.takeIf { it.sticker }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(EMOJI) { e -> Text(e, fontSize = 30.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.addText(true, e) }.padding(6.dp)) }
        }
        if (s != null) {
            SectionLabel("Animation")
            val stickerAnims = listOf("pop", "bounce", "float", "spin", "pulse", "wave", "zoom", "shake", "none")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(stickerAnims) { a -> Pill(TEXT_ANIMS.first { it.id == a }.name, s.anim == a) { vm.updateText(s.id) { it.copy(anim = a) } } } }
            LabeledSlider("Size", s.size, 0.05f..0.4f, { "${(it * 100).toInt()}" }) { v -> vm.updateText(s.id) { it.copy(size = v) } }
            LabeledSlider("Position X", s.x, 0f..1f, { "${(it * 100).toInt()}" }) { v -> vm.updateText(s.id) { it.copy(x = v) } }
            LabeledSlider("Position Y", s.y, 0f..1f, { "${(it * 100).toInt()}" }) { v -> vm.updateText(s.id) { it.copy(y = v) } }
            LabeledSlider("Length", s.durMs / 1000f, 0.5f..10f, { "${"%.1f".format(it)}s" }) { v -> vm.updateText(s.id) { it.copy(durMs = (v * 1000).toLong()) } }
            Pill("Delete sticker", false) { vm.deleteText() }
        } else Hint("Tap an emoji to drop an animated sticker at the playhead.")
    }
}

// ---------------------------------------------------------------- Sound
object Previewer {
    private var mp: MediaPlayer? = null
    fun play(ctx: android.content.Context, m: SoundMeta) {
        stop()
        try {
            val fd = ctx.assets.openFd("sfx/${m.file}")
            mp = MediaPlayer().apply { setDataSource(fd.fileDescriptor, fd.startOffset, fd.length); isLooping = false; prepare(); start(); setOnCompletionListener { stop() } }
            fd.close()
        } catch (_: Exception) {}
    }
    fun stop() { try { mp?.stop(); mp?.release() } catch (_: Exception) {}; mp = null }
}

@Composable
fun SoundPanel(vm: EditorVM) {
    val ctx = LocalContext.current
    var cat by remember { mutableStateOf(SoundLib.categories.firstOrNull() ?: "UI") }
    val rec = remember { VoiceRecorder(ctx) }
    var recording by remember { mutableStateOf(false) }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) vm.importAudio(u) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok && rec.start()) recording = true else vm.toast = "Microphone permission needed" }
    DisposableEffect(Unit) { onDispose { Previewer.stop(); if (recording) rec.stop() } }
    val sel = vm.sound
    Column {
        if (sel != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(sel.name, color = DZ.text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1)
                IconButton(onClick = { vm.deleteSound() }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.Delete, null, tint = DZ.pink) }
            }
            LabeledSlider("Volume", sel.volume, 0f..2f, { "${(it * 100).toInt()}%" }) { v -> vm.updateSound(sel.id) { it.copy(volume = v) } }
            LabeledSlider("Start", sel.startMs / 1000f, 0f..maxOf(1f, vm.project.durationMs / 1000f), { "${"%.1f".format(it)}s" }) { v -> vm.updateSound(sel.id) { it.copy(startMs = (v * 1000).toLong()) } }
            LabeledSlider("Fade in", sel.fadeInMs / 1000f, 0f..3f, { "${"%.1f".format(it)}s" }) { v -> vm.updateSound(sel.id) { it.copy(fadeInMs = (v * 1000).toInt()) } }
            LabeledSlider("Fade out", sel.fadeOutMs / 1000f, 0f..3f, { "${"%.1f".format(it)}s" }) { v -> vm.updateSound(sel.id) { it.copy(fadeOutMs = (v * 1000).toInt()) } }
            HorizontalDivider(color = DZ.line, modifier = Modifier.padding(vertical = 6.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Pill(if (recording) "■ Stop recording" else "🎙 Voiceover", recording) {
                if (recording) { rec.stop()?.let { vm.addAudioFile(it, "Voiceover", "voice") }; recording = false }
                else if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { if (rec.start()) recording = true else vm.toast = "Couldn't start recording" }
                else askMic.launch(Manifest.permission.RECORD_AUDIO)
            }
            Pill("＋ My audio", false) { pickAudio.launch("audio/*") }
        }
        LazyRow(Modifier.padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(SoundLib.categories) { k -> Pill(k, cat == k) { cat = k } } }
        LazyColumn(Modifier.heightIn(max = 150.dp)) {
            items(SoundLib.all.filter { it.cat == cat }) { m ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp)).background(DZ.card).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { Previewer.play(ctx, m) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.PlayArrow, null, tint = DZ.mint) }
                    Column(Modifier.weight(1f)) { Text(m.name, color = DZ.text, fontSize = 13.sp, maxLines = 1); Text("${"%.1f".format(m.ms / 1000f)}s${if (m.loop) " · loops" else ""}", color = DZ.sub, fontSize = 10.5.sp) }
                    IconButton(onClick = { vm.addSound(m) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Rounded.AddCircle, null, tint = DZ.pink) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Canvas
@Composable
fun CanvasPanel(vm: EditorVM) {
    val p = vm.project
    Column(Modifier.verticalScroll(rememberScrollState())) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(RATIOS) { r -> Pill("${r.id}  ${r.name}", p.ratio == r.id) { vm.edit { it.copy(ratio = r.id) } } }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("Fill (crop)", !p.fit) { vm.edit { it.copy(fit = false) } }
            Pill("Fit (bars)", p.fit) { vm.edit { it.copy(fit = true) } }
        }
        Hint("Fill/Fit applies when the phone mockup is off.")
    }
}
