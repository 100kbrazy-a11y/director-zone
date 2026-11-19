package com.directorzone.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directorzone.catalog.*
import com.directorzone.data.*
import kotlin.math.PI
import kotlin.math.sin

private fun c(v: Long) = Color(v)

// =================================================================== Transitions
@Composable
fun TransitionsSheet(vm: EditorVM, close: () -> Unit) {
    val cl = vm.clip
    val idx = vm.project.clips.indexOfFirst { it.id == cl?.id }
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Transitions", close) {
            if (cl != null && idx > 0) Text("Apply to All", color = DZ.text, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(50)).background(DZ.card).clickable {
                    vm.edit { p -> p.copy(clips = p.clips.mapIndexed { i, k -> if (i == 0) k else k.copy(transition = cl.transition, transitionMs = cl.transitionMs) }) }; vm.toast = "Applied to every cut"
                }.padding(horizontal = 14.dp, vertical = 8.dp))
        }
        if (cl == null) { Hint("Select a clip — the transition plays into it from the clip before."); return }
        if (vm.project.clips.size < 2) { Hint("Add a second clip to put a transition between them."); return }
        if (idx <= 0) { Hint("Select the 2nd (or a later) clip: a transition happens at the cut before it."); return }
        TextTabs(TRANSITION_CATS, tab) { tab = it }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 10.dp)) {
            Grid(TRANSITIONS.filter { TRANSITION_CATS[tab] in it.cats }, 3) { t ->
                val on = cl.transition == t.id
                Column(Modifier.clickable { vm.setTransition(cl.id, t.id) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    TransitionTile(t, on)
                    Text(t.name, color = if (on) DZ.text else DZ.sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 5.dp))
                }
            }
            Text("Tiles show a schematic of the motion; tap Preview to play the real cut.", color = DZ.sub, fontSize = 11.sp, modifier = Modifier.padding(top = 10.dp))
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Duration", color = DZ.text, fontSize = 15.sp, modifier = Modifier.width(80.dp))
            Slider(cl.transitionMs / 1000f, { v -> vm.updateClip(cl.id) { it.copy(transitionMs = (v * 1000).toInt()) } }, valueRange = 0.2f..1.5f, modifier = Modifier.weight(1f).height(30.dp), enabled = cl.transition != "none",
                colors = SliderDefaults.colors(thumbColor = DZ.accent, activeTrackColor = DZ.accent, inactiveTrackColor = DZ.card2))
            Text(fmtSecs(cl.transitionMs.toLong()), color = DZ.text, fontSize = 14.sp, modifier = Modifier.width(48.dp))
            Icon(Icons.Rounded.PlayCircle, null, tint = DZ.text, modifier = Modifier.size(34.dp).clip(CircleShape).clickable { vm.previewTransition() })
        }
    }
}

@Composable
private fun TransitionTile(t: TransitionDef, selected: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val tr = rememberInfiniteTransition(label = "tt")
    val ph by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Restart), label = "p")
    val p = if (selected && Prefs.animQuality > 0) ph else 0.5f
    Box(Modifier.fillMaxWidth().aspectRatio(1.35f).clip(shape).background(DZ.card2).border(if (selected) 2.dp else 0.dp, if (selected) DZ.text else Color.Transparent, shape)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawTransition(t.mode, p, DZ.sub.copy(alpha = 0.75f), DZ.accent) }
    }
}

/** Schematic of each transition: panel A (outgoing) and panel B (incoming). p = 0..1. */
private fun DrawScope.drawTransition(mode: Int, p: Float, a: Color, b: Color) {
    val w = size.width; val h = size.height
    val r = CornerRadius(10f)
    fun panelA(alpha: Float = 1f, dx: Float = 0f, dy: Float = 0f) = drawRoundRect(a.copy(alpha = a.alpha * alpha), Offset(dx, dy), Size(w, h), r)
    fun panelB(alpha: Float = 1f, dx: Float = 0f, dy: Float = 0f) = drawRoundRect(b.copy(alpha = 0.9f * alpha), Offset(dx, dy), Size(w, h), r)
    if (mode == 0) { panelA(); return }
    panelA()
    when (mode) {
        1 -> { drawRect(Color.Black.copy(alpha = if (p < 0.5f) p * 2 else (1 - p) * 2)) ; panelB(alpha = (p * p)) }
        2 -> { panelB(alpha = p); drawRect(Color.White.copy(alpha = sin(p * PI).toFloat())) }
        3, 4 -> { val s = if (mode == 3) 1f + p * 0.9f else 1f - p * 0.5f; scale(s, Offset(w / 2, h / 2)) { panelB(alpha = if (mode == 3) p else 1f - p * 0.3f) } }
        5, 6, 7, 8, 19 -> {
            val ux = when (mode) { 5, 19 -> -1f; 6 -> 1f; else -> 0f }; val uy = when (mode) { 7 -> -1f; 8 -> 1f; else -> 0f }
            clipRect(0f, 0f, w, h) { panelB(dx = -ux * w * (1 - p), dy = -uy * h * (1 - p)); drawRect(Color.White.copy(alpha = 0.12f * sin(p * PI).toFloat())) }
        }
        9, 15 -> rotate(p * 160f, Offset(w / 2, h / 2)) { scale(1f - p * 0.35f, Offset(w / 2, h / 2)) { panelB(alpha = p) } }
        10, 11, 14, 29 -> {
            for (i in 0 until 6) { val y = h * i / 6f; val off = ((i * 37 % 11) - 5) * 3f * sin(p * PI).toFloat(); clipRect(0f, y, w, y + h / 6f) { panelB(alpha = p, dx = off) } }
            if (mode == 11) drawRect(Color.Red.copy(alpha = 0.25f * sin(p * PI).toFloat()), Offset(4f, 0f), Size(w, h))
        }
        12, 34 -> { for (k in 0..3) panelB(alpha = p * 0.3f, dx = (k - 1.5f) * 4f * sin(p * PI).toFloat()); panelB(alpha = p * 0.5f) }
        13 -> { val n = 6; for (i in 0 until n) for (j in 0 until n) if (((i * 7 + j * 13) % 10) / 10f < p) drawRect(b.copy(alpha = 0.9f), Offset(w * i / n, h * j / n), Size(w / n + 1, h / n + 1)) }
        16 -> { panelB(alpha = p); drawLine(Color.White.copy(alpha = 0.5f), Offset(w / 2, 0f), Offset(w / 2, h), 2f); drawLine(Color.White.copy(alpha = 0.5f), Offset(0f, h / 2), Offset(w, h / 2), 2f) }
        17, 20, 31 -> { panelB(alpha = p); drawRect(Brush.linearGradient(listOf(Color(0xFFFF6A00).copy(alpha = sin(p * PI).toFloat() * 0.85f), Color(0xFFFFD27A).copy(alpha = sin(p * PI).toFloat() * 0.6f)))) ; if (mode == 31) drawRect(Color(0xFF9BC7FF).copy(alpha = sin(p * PI).toFloat()), Offset(0f, h * 0.45f), Size(w, h * 0.1f)) }
        18 -> clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(w / 2, h / 2) - Offset(w * p * 0.8f, w * p * 0.8f), Size(w * p * 1.6f, w * p * 1.6f))) }) { panelB() }
        21 -> scale(1f, 1f + p * 0.8f, Offset(w / 2, h / 2)) { panelB(alpha = p) }
        22 -> clipRect(0f, 0f, w * p, h) { panelB() }
        23 -> clipRect(w * (1 - p), 0f, w, h) { panelB() }
        24 -> clipRect(0f, 0f, w, h * p) { panelB() }
        25 -> clipRect(0f, h * (1 - p), w, h) { panelB() }
        26 -> clipPath(Path().apply { moveTo(0f, 0f); lineTo(w * 2 * p, 0f); lineTo(0f, h * 2 * p); close() }) { panelB() }
        27 -> clipPath(Path().apply { moveTo(w / 2, h / 2); lineTo(w / 2, -h); val steps = 24; for (i in 0..steps) { val ang = -PI / 2 + 2 * PI * p * i / steps; lineTo(w / 2 + (w * 1.5f * kotlin.math.cos(ang)).toFloat(), h / 2 + (w * 1.5f * sin(ang)).toFloat()) }; close() }) { panelB() }
        28 -> { clipRect(w / 2 - w * p / 2, 0f, w / 2 + w * p / 2, h) { panelB() } }
        30 -> { val sy = 1f - p * 0.95f; scale(1f, sy, Offset(w / 2, h / 2)) { panelB() } }
        32, 33 -> { panelB(alpha = p); for (k in 1..3) drawCircle(Color.White.copy(alpha = 0.35f * (1 - p)), w * 0.12f * k * (0.4f + p), Offset(w / 2, h / 2), style = androidx.compose.ui.graphics.drawscope.Stroke(2f)) }
        else -> panelB(alpha = p)
    }
}

// =================================================================== Canvas
@Composable
fun CanvasSheet(vm: EditorVM, close: () -> Unit) {
    val p = vm.project
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Canvas", close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionLabel("Aspect ratio")
            Grid(RATIOS, 4) { r ->
                val on = p.ratio == r.id
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) DZ.accentSoft else DZ.card).border(1.dp, if (on) DZ.accent else Color.Transparent, RoundedCornerShape(12.dp)).clickable { vm.edit { it.copy(ratio = r.id) } }.padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val ar = r.w.toFloat() / r.h
                    Box(Modifier.height(30.dp).width((30 * ar).coerceIn(14f, 52f).dp).clip(RoundedCornerShape(4.dp)).border(1.5.dp, if (on) DZ.text else DZ.sub, RoundedCornerShape(4.dp)))
                    Text(r.id, color = DZ.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                    Text(r.name, color = DZ.sub, fontSize = 9.5.sp, maxLines = 1)
                }
            }
            SectionLabel("Media in frame")
            Segmented(listOf("Fill (crop)", "Fit (bars)"), if (p.fit) 1 else 0, Modifier.fillMaxWidth()) { vm.edit { pr -> pr.copy(fit = it == 1) } }
            Text("Fill / Fit applies when the phone mockup is off.", color = DZ.sub, fontSize = 12.sp)
        }
    }
}

// =================================================================== More
@Composable
fun MoreSheet(vm: EditorVM, pickMedia: () -> Unit, close: () -> Unit) {
    class Entry(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val go: () -> Unit)
    val hasClip = vm.project.clips.isNotEmpty()
    fun toSheet(s: Sheet) = { vm.sheet = s }
    val entries = listOf(
        Entry(Icons.Rounded.PhoneIphone, "Phone Studio", toSheet(Sheet.Phone)),
        Entry(Icons.Rounded.EmojiEmotions, "Stickers", toSheet(Sheet.Stickers)),
        Entry(Icons.Rounded.Dashboard, "Templates", toSheet(Sheet.Templates)),
        Entry(Icons.Rounded.ContentCut, "Trim clip") { if (hasClip) vm.sheet = Sheet.Trim },
        Entry(Icons.Rounded.Speed, "Speed") { if (hasClip) vm.sheet = Sheet.Speed },
        Entry(Icons.Rounded.VolumeUp, "Clip volume") { if (hasClip) vm.sheet = Sheet.Volume },
        Entry(Icons.Rounded.Tune, "Clip options", toSheet(Sheet.ClipMore)),
        Entry(Icons.Rounded.CallSplit, "Split") { if (hasClip) { vm.splitAtPlayhead(); vm.sheet = null } },
        Entry(Icons.Rounded.FileUpload, "Export") { vm.sheet = null; vm.openExport() },
    )
    Column(Modifier.fillMaxSize()) {
        SheetHeader("More", close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Grid(entries, 4, 6.dp) { e ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DZ.card).clickable { e.go() }.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(e.icon, null, tint = DZ.text, modifier = Modifier.size(26.dp))
                    Text(e.label, color = DZ.sub, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Auto whoosh on transitions", color = DZ.text, fontSize = 15.sp); Text("Adds a matching sound at every cut that has a transition", color = DZ.sub, fontSize = 12.sp) }
                DzSwitch(vm.project.autoSfx) { v -> vm.edit { it.copy(autoSfx = v) } }
            }
        }
    }
}

// =================================================================== Stickers
@Composable
fun StickersSheet(vm: EditorVM, close: () -> Unit) {
    val s = vm.text?.takeIf { it.sticker }
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Stickers", close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            Grid(EMOJI, 8, 2.dp) { e -> Text(e, fontSize = 28.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.addText(true, e) }.padding(vertical = 6.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            if (s != null) {
                Spacer(Modifier.height(10.dp))
                SectionLabel("Selected sticker")
                val anims = listOf("pop", "bounce", "float", "spin", "pulse", "wave", "zoom", "shake", "none")
                Chips(anims.map { a -> TEXT_ANIMS.first { it.id == a }.name }, TEXT_ANIMS.first { it.id == s.anim }.name) { n -> vm.updateText(s.id) { it.copy(anim = TEXT_ANIMS.first { a -> a.name == n }.id) } }
                AdjustRow("Size", s.size, 0.05f..0.4f, ::pct) { v -> vm.updateText(s.id) { it.copy(size = v) } }
                AdjustRow("Position X", s.x, 0f..1f, ::pct) { v -> vm.updateText(s.id) { it.copy(x = v) } }
                AdjustRow("Position Y", s.y, 0f..1f, ::pct) { v -> vm.updateText(s.id) { it.copy(y = v) } }
                AdjustRow("Duration", s.durMs / 1000f, 0.5f..10f, { fmtSecs((it * 1000).toLong()) }) { v -> vm.updateText(s.id) { it.copy(durMs = (v * 1000).toLong()) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 6.dp)) {
                    Pill("Duplicate", false) { vm.duplicateText() }; Pill("Delete", false) { vm.deleteText() }
                }
            } else Hint("Tap an emoji to drop an animated sticker at the playhead. Drag it on the preview to place it.")
        }
    }
}

// =================================================================== Phone Studio
@Composable
fun PhoneSheet(vm: EditorVM, close: () -> Unit) {
    val ph = vm.project.phone
    fun set(f: (PhoneCfg) -> PhoneCfg) = vm.edit { it.copy(phone = f(it.phone)) }
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Phone Studio", close) { DzSwitch(ph.enabled) { v -> set { it.copy(enabled = v) } }; Spacer(Modifier.width(8.dp)) }
        if (!ph.enabled) { Hint("Turn it on to show your video inside a 3D phone with premade animations."); return }
        TextTabs(listOf("Animation", "Device", "Background", "Rotation", "Scene"), tab) { tab = it }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).padding(top = 8.dp, bottom = 8.dp)) {
            when (tab) {
                0 -> {
                    Grid(PHONE_ANIMS, 4, 8.dp) { a ->
                        val on = ph.anim == a.id
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) DZ.accentSoft else DZ.card).border(1.dp, if (on) DZ.accent else Color.Transparent, RoundedCornerShape(12.dp)).clickable { set { it.copy(anim = a.id) } }.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            if (on) PhoneGlyph(DZ.accent2, a.code, Modifier.size(50.dp, 56.dp), speed = 2600) else PhoneGlyph(DZ.sub, a.code, Modifier.size(50.dp, 56.dp), speed = 3600)
                            Text(a.name, color = if (on) DZ.text else DZ.sub, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AdjustRow("Movement", ph.motion, 0f..2f, ::pct) { v -> set { it.copy(motion = v) } }
                    AdjustRow("Tempo", ph.tempo, 0.3f..2.5f, { "%.1f×".format(it) }) { v -> set { it.copy(tempo = v) } }
                }
                1 -> {
                    Grid(BEZELS, 2, 8.dp) { b ->
                        val on = ph.bezel == b.id
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (on) DZ.accentSoft else DZ.card).border(1.dp, if (on) DZ.accent else Color.Transparent, RoundedCornerShape(12.dp)).clickable { set { it.copy(bezel = b.id) } }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(20.dp, 30.dp).clip(RoundedCornerShape(5.dp)).background(c(b.frame)).border(1.dp, DZ.line, RoundedCornerShape(5.dp)))
                            Spacer(Modifier.width(10.dp)); Text(b.name, color = DZ.text, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                2 -> Grid(BACKGROUNDS, 3, 8.dp) { b ->
                    val on = ph.bg == b.id
                    Column(Modifier.clickable { set { it.copy(bg = b.id) } }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(c(b.c1), c(b.c2), c(b.c3)))).border(if (on) 2.dp else 0.dp, if (on) DZ.text else Color.Transparent, RoundedCornerShape(12.dp)))
                        Text(b.name, color = if (on) DZ.text else DZ.sub, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                3 -> {
                    AdjustRow("Rotate (yaw)", ph.yaw, -90f..90f, { "${it.toInt()}°" }) { v -> set { it.copy(yaw = v) } }
                    AdjustRow("Tilt (pitch)", ph.pitch, -60f..60f, { "${it.toInt()}°" }) { v -> set { it.copy(pitch = v) } }
                    AdjustRow("Roll", ph.roll, -45f..45f, { "${it.toInt()}°" }) { v -> set { it.copy(roll = v) } }
                    AdjustRow("Scale", ph.scale, 0.5f..1.5f, ::pct) { v -> set { it.copy(scale = v) } }
                    AdjustRow("Position X", ph.offX, -1f..1f, ::signed) { v -> set { it.copy(offX = v) } }
                    AdjustRow("Position Y", ph.offY, -1f..1f, ::signed) { v -> set { it.copy(offY = v) } }
                    Pill("Reset rotation & position", false) { set { it.copy(yaw = 0f, pitch = 0f, roll = 0f, offX = 0f, offY = 0f, scale = 1f) } }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Shadow", color = DZ.text, fontSize = 15.sp, modifier = Modifier.weight(1f)); DzSwitch(ph.shadow) { v -> set { it.copy(shadow = v) } } }
                    AdjustRow("Reflections", ph.glare, 0f..2f, ::pct) { v -> set { it.copy(glare = v) } }
                    AdjustRow("Lighting", ph.light, 0f..2f, ::pct) { v -> set { it.copy(light = v) } }
                    AdjustRow("Depth", ph.depth, 0f..2.5f, ::pct) { v -> set { it.copy(depth = v) } }
                }
            }
        }
    }
}

// =================================================================== Trim / Speed / Volume / Clip options
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimSheet(vm: EditorVM, close: () -> Unit) {
    val cl = vm.clip
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Trim", close)
        if (cl == null) { Hint("Select a clip first."); return }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (cl.isImage) AdjustRow("Duration", cl.outMs / 1000f, 0.5f..20f, { fmtSecs((it * 1000).toLong()) }) { v -> vm.setImageLen(cl.id, (v * 1000).toLong()) }
            else {
                RangeSlider(
                    value = (cl.inMs / 1000f)..(cl.outMs / 1000f),
                    onValueChange = { r -> val a = (r.start * 1000).toLong(); val b = (r.endInclusive * 1000).toLong(); if (b - a >= 300) vm.setTrim(cl.id, a, b) },
                    valueRange = 0f..(cl.srcDurMs / 1000f).coerceAtLeast(0.5f),
                    colors = SliderDefaults.colors(thumbColor = DZ.accent, activeTrackColor = DZ.accent, inactiveTrackColor = DZ.card2),
                )
                Row { Text("In ${fmtSecs(cl.inMs)}", color = DZ.sub, fontSize = 13.sp, modifier = Modifier.weight(1f)); Text("Length ${fmtSecs(cl.lengthMs)}", color = DZ.text, fontSize = 13.sp); Text("   Out ${fmtSecs(cl.outMs)}", color = DZ.sub, fontSize = 13.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Set in at playhead", false) { val l = vm.posMs - vm.clipStartMs(cl.id); val nin = (cl.inMs + l * cl.speed).toLong(); if (l > 0 && nin < cl.outMs - 300) vm.setTrim(cl.id, nin, cl.outMs) else vm.toast = "Move the playhead inside the clip" }
                    Pill("Set out at playhead", false) { val l = vm.posMs - vm.clipStartMs(cl.id); val nout = (cl.inMs + l * cl.speed).toLong(); if (l > 0 && nout > cl.inMs + 300 && nout <= cl.srcDurMs) vm.setTrim(cl.id, cl.inMs, nout) else vm.toast = "Move the playhead inside the clip" }
                }
                Text("You can also drag the handles on the selected clip in the timeline.", color = DZ.sub, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SpeedSheet(vm: EditorVM, close: () -> Unit) {
    val cl = vm.clip
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Speed", close)
        if (cl == null) { Hint("Select a clip first."); return }
        if (cl.isImage) { Hint("Photos have no speed."); return }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Chips(listOf("0.25×", "0.5×", "0.75×", "1×", "1.5×", "2×", "3×", "4×"), (if (cl.speed % 1f == 0f) cl.speed.toInt().toString() else cl.speed.toString().trimEnd('0')) + "×") { s -> vm.updateClip(cl.id) { it.copy(speed = s.removeSuffix("×").toFloat()) } }
            AdjustRow("Speed", cl.speed, 0.25f..4f, { "%.2f×".format(it) }) { v -> vm.updateClip(cl.id) { it.copy(speed = (Math.round(v * 20f) / 20f)) } }
            Text("Clip length ${fmtSecs(cl.lengthMs)} · audio follows the speed", color = DZ.sub, fontSize = 12.sp)
        }
    }
}

@Composable
fun VolumeSheet(vm: EditorVM, close: () -> Unit) {
    val cl = vm.clip
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Volume", close)
        if (cl == null) { Hint("Select a clip first."); return }
        if (cl.isImage) { Hint("Photos have no audio."); return }
        Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AdjustRow("Volume", cl.volume, 0f..2f, { pct(it) + "%" }) { v -> vm.updateClip(cl.id) { it.copy(volume = v) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Mute", cl.volume == 0f) { vm.updateClip(cl.id) { it.copy(volume = 0f) } }
                Pill("100%", cl.volume == 1f) { vm.updateClip(cl.id) { it.copy(volume = 1f) } }
                Pill("Boost 150%", cl.volume == 1.5f) { vm.updateClip(cl.id) { it.copy(volume = 1.5f) } }
            }
        }
    }
}

@Composable
fun ClipMoreSheet(vm: EditorVM, close: () -> Unit) {
    val cl = vm.clip
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Clip options", close)
        if (cl == null) { Hint("Select a clip first."); return }
        val idx = vm.project.clips.indexOfFirst { it.id == cl.id }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Move earlier", Icons.Rounded.ArrowBack, Modifier.weight(1f), filled = false) { if (idx > 0) vm.moveClip(-1) else vm.toast = "Already first" }
                BigButton("Move later", Icons.Rounded.ArrowForward, Modifier.weight(1f), filled = false) { if (idx < vm.project.clips.size - 1) vm.moveClip(1) else vm.toast = "Already last" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BigButton("Duplicate", Icons.Rounded.ContentCopy, Modifier.weight(1f), filled = false) { vm.duplicateClip() }
                BigButton("Delete", Icons.Rounded.Delete, Modifier.weight(1f), filled = false) { vm.deleteClip(); vm.sheet = null }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Ken Burns zoom", color = DZ.text, fontSize = 15.sp); Text(if (cl.isImage) "Always on for photos" else "Slow push-in across the clip", color = DZ.sub, fontSize = 12.sp) }
                DzSwitch(cl.kenBurns || cl.isImage) { v -> if (!cl.isImage) vm.updateClip(cl.id) { it.copy(kenBurns = v) } }
            }
            if (cl.isImage) AdjustRow("Duration", cl.outMs / 1000f, 0.5f..20f, { fmtSecs((it * 1000).toLong()) }) { v -> vm.setImageLen(cl.id, (v * 1000).toLong()) }
            BigButton("Filters & color", Icons.Rounded.Palette, Modifier.fillMaxWidth(), filled = false) { vm.sheet = Sheet.Color }
            BigButton("Effects", Icons.Rounded.AutoAwesome, Modifier.fillMaxWidth(), filled = false) { vm.sheet = Sheet.Effects }
        }
    }
}

// =================================================================== Templates (inside the editor)
@Composable
fun TemplatesSheet(vm: EditorVM, close: () -> Unit) {
    var cat by remember { mutableStateOf(TEMPLATE_CATS[0]) }
    Column(Modifier.fillMaxSize()) {
        SheetHeader("Templates", close)
        Box(Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) { Chips(TEMPLATE_CATS, cat) { cat = it } }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Grid(TEMPLATES.filter { it.category == cat }, 2) { t -> TemplateCard(t) { if (vm.project.clips.isEmpty()) vm.toast = "Add media first" else { vm.applyTemplate(t); vm.sheet = null; vm.toast = "Applied ${t.name}" } } }
            Text("Applying a template replaces the cut's titles, sounds, transitions and look. Undo brings it back.", color = DZ.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
fun TemplateCard(t: TemplateDef, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val accent = c(t.accent)
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), DZ.card))).clickable(onClick = onClick).padding(10.dp)) {
        Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            if (t.phone) PhoneGlyph(accent, phoneAnimById(t.anim).code, Modifier.fillMaxSize().padding(6.dp)) else Text(t.emoji, fontSize = 44.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(t.name, color = DZ.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        Text(t.blurb, color = DZ.sub, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        Text("${t.ratio} · ${transitionById(t.transition).name}", color = accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
    }
}
