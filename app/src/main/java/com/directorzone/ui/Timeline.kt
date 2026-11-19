package com.directorzone.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directorzone.data.Clip
import com.directorzone.data.Prefs
import com.directorzone.data.SoundItem
import com.directorzone.data.TextItem
import com.directorzone.engine.Waveform
import kotlin.math.abs
import kotlin.math.ceil

enum class ItemKind { Clip, Text, Sound }
class MenuAnchor(val kind: ItemKind, val id: String, val bounds: Rect)

private val HEADER_W = 76.dp

private fun <T> rowsOf(items: List<T>, start: (T) -> Long, end: (T) -> Long, max: Int): List<List<T>> {
    val rows = ArrayList<MutableList<T>>()
    items.sortedBy(start).forEach { s ->
        val r = rows.firstOrNull { row -> end(row.last()) <= start(s) } ?: (if (rows.size < max) ArrayList<T>().also { rows.add(it) } else rows.last())
        r.add(s)
    }
    return rows
}

@Composable
fun Timeline(vm: EditorVM, dpPerSec: Float, onMenu: (MenuAnchor) -> Unit, onAddMedia: () -> Unit, modifier: Modifier = Modifier) {
    val p = vm.project
    val scroll = rememberScrollState()
    val vscroll = rememberScrollState()
    val density = LocalDensity.current
    val dpPerMs = dpPerSec / 1000f
    val pxPerMs = with(density) { dpPerMs.dp.toPx() }
    val haptic = rememberHaptic()

    // --- playhead <-> scroll sync. User scrolling is detected from real drag interactions; programmatic scrollTo never emits them.
    var userScrolling by remember { mutableStateOf(false) }
    var resumeAfter by remember { mutableStateOf(false) }
    LaunchedEffect(scroll) {
        scroll.interactionSource.interactions.collect { i ->
            if (i is DragInteraction.Start) { resumeAfter = vm.engine.isPlaying; vm.engine.pause(); userScrolling = true }
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.isScrollInProgress }.collect { moving ->
            if (!moving && userScrolling) { userScrolling = false; if (resumeAfter) { resumeAfter = false; vm.engine.play() } }
        }
    }
    LaunchedEffect(dpPerSec) { snapshotFlow { scroll.value }.collect { v -> if (userScrolling) vm.seek((v / pxPerMs).toLong()) } }
    LaunchedEffect(vm.posMs, dpPerSec) { if (!userScrolling) scroll.scrollTo((vm.posMs * pxPerMs).toInt()) }

    // --- snapping targets (ms) other than the item being moved
    fun snapTargets(): List<Long> {
        val t = ArrayList<Long>(); t.add(0L); t.add(vm.posMs)
        var acc = 0L; p.clips.forEach { acc += it.lengthMs; t.add(acc) }
        p.texts.forEach { t.add(it.startMs); t.add(it.startMs + it.durMs) }
        p.sounds.forEach { t.add(it.startMs); t.add(it.startMs + it.durMs) }
        return t
    }
    fun snap(v: Long, ignore: List<Long> = emptyList()): Long {
        if (!Prefs.snapping) return v
        val th = (10f / dpPerMs).toLong()
        val best = snapTargets().filter { it !in ignore }.minByOrNull { abs(it - v) } ?: return v
        return if (abs(best - v) <= th) best else v
    }

    val d = dens
    val vH = (58 * d).dp; val tH = (30 * d).dp; val aH = (30 * d).dp
    val totalMs = maxOf(p.durationMs, p.texts.maxOfOrNull { it.startMs + it.durMs } ?: 0L, p.sounds.maxOfOrNull { it.startMs + (if (it.loop) 0 else it.durMs) } ?: 0L) + 1200
    val textRows = remember(p.texts) { rowsOf(p.texts, { it.startMs }, { it.startMs + it.durMs }, 3) }
    val soundRows = remember(p.sounds, p.durationMs) { rowsOf(p.sounds, { it.startMs }, { it.startMs + (if (it.loop) maxOf(0L, p.durationMs - it.startMs) else it.durMs) }, 4) }
    val tLanes = maxOf(1, textRows.size); val aLanes = maxOf(1, soundRows.size)

    BoxWithConstraints(modifier.fillMaxWidth().background(DZ.bg)) {
        val areaW = maxWidth - HEADER_W
        val half = areaW / 2
        Row(Modifier.fillMaxSize().verticalScroll(vscroll)) {
            // ---------------- headers
            Column(Modifier.width(HEADER_W).background(DZ.bg)) {
                Spacer(Modifier.height(22.dp))
                HeaderCell(vH, if (p.lockV) Icons.Rounded.Lock else Icons.Rounded.LockOpen, null, "V1", p.lockV, false) { vm.edit { it.copy(lockV = !it.lockV) } }
                Spacer(Modifier.height(4.dp))
                repeat(tLanes) { i ->
                    if (i == 0) HeaderCell(tH, if (p.lockT) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (p.hideT) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, "T", p.lockT, p.hideT, { vm.edit { it.copy(hideT = !it.hideT) } }) { vm.edit { it.copy(lockT = !it.lockT) } }
                    else Spacer(Modifier.height(tH))
                    Spacer(Modifier.height(4.dp))
                }
                repeat(aLanes) { i ->
                    if (i == 0) HeaderCell(aH, if (p.lockA) Icons.Rounded.Lock else Icons.Rounded.LockOpen, if (p.muteA) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp, "A1", p.lockA, p.muteA, { vm.edit { it.copy(muteA = !it.muteA) } }) { vm.edit { it.copy(lockA = !it.lockA) } }
                    else Spacer(Modifier.height(aH))
                    Spacer(Modifier.height(4.dp))
                }
                Spacer(Modifier.height(30.dp))
            }
            // ---------------- tracks
            Box(Modifier.weight(1f).pointerInput(Unit) { detectTapGestures(onTap = { vm.deselectAll() }) }) {
                Row(Modifier.horizontalScroll(scroll).padding(start = half, end = half)) {
                    Column(Modifier.width((totalMs * dpPerMs).dp)) {
                        Ruler(totalMs, dpPerSec)
                        // ---- V1
                        Row(Modifier.height(vH)) {
                            var start = 0L
                            p.clips.forEachIndexed { i, c ->
                                ClipTile(vm, c, i, start, dpPerMs, pxPerMs, vH, onMenu, haptic, ::snap)
                                start += c.lengthMs
                            }
                            Box(Modifier.padding(start = 6.dp).size(vH).clip(RoundedCornerShape(10.dp)).background(DZ.card).clickable(onClick = onAddMedia), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Add, null, tint = DZ.text, modifier = Modifier.size(24.dp))
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        // ---- text lanes
                        if (textRows.isEmpty()) Box(Modifier.height(tH).fillMaxWidth()) else textRows.forEachIndexed { _, row ->
                            Box(Modifier.height(tH).fillMaxWidth().alpha(if (p.hideT) 0.35f else 1f)) {
                                row.forEach { t -> TextTile(vm, t, dpPerMs, pxPerMs, tH, onMenu, haptic, ::snap) }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (textRows.isEmpty()) Spacer(Modifier.height(4.dp))
                        // ---- audio lanes
                        if (soundRows.isEmpty()) Box(Modifier.height(aH).fillMaxWidth()) else soundRows.forEach { row ->
                            Box(Modifier.height(aH).fillMaxWidth().alpha(if (p.muteA) 0.35f else 1f)) {
                                row.forEach { s -> SoundTile(vm, s, dpPerMs, pxPerMs, aH, onMenu, haptic, ::snap) }
                            }
                            Spacer(Modifier.height(4.dp))
                        }
                        if (soundRows.isEmpty()) Spacer(Modifier.height(4.dp))
                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }
        // playhead: always at the centre of the track area
        Box(Modifier.align(Alignment.TopStart).offset(x = HEADER_W + half - 1.dp).fillMaxHeight().width(2.dp).background(DZ.text))
        Box(Modifier.align(Alignment.TopStart).offset(x = HEADER_W + half - 5.dp, y = 14.dp).size(10.dp).clip(RoundedCornerShape(50)).background(DZ.text))
    }
}

@Composable
private fun HeaderCell(h: Dp, lockIcon: androidx.compose.ui.graphics.vector.ImageVector, second: androidx.compose.ui.graphics.vector.ImageVector?, label: String, locked: Boolean, off: Boolean, onSecond: () -> Unit = {}, onLock: () -> Unit) {
    Row(Modifier.height(h).padding(start = 6.dp, end = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.clip(RoundedCornerShape(8.dp)), verticalAlignment = Alignment.CenterVertically) {
            Icon(lockIcon, null, tint = if (locked) DZ.text else DZ.sub, modifier = Modifier.size(18.dp).clickable(onClick = onLock))
            if (second != null) { Spacer(Modifier.width(8.dp)); Icon(second, null, tint = if (off) DZ.text else DZ.sub, modifier = Modifier.size(18.dp).clickable(onClick = onSecond)) }
        }
        Spacer(Modifier.width(5.dp))
        Text(label, color = DZ.sub, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Ruler(totalMs: Long, dpPerSec: Float) {
    val steps = listOf(1, 2, 5, 10, 15, 30, 60, 120)
    val step = steps.firstOrNull { it * dpPerSec >= 56f } ?: 120
    val lineCol = DZ.sub.copy(alpha = 0.5f)
    Box(Modifier.fillMaxWidth().height(22.dp)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            var s = 0
            while (s * 1000L < totalMs) {
                val x = s * dpPerSec.dp.toPx()
                drawLine(lineCol, Offset(x, size.height - 8.dp.toPx()), Offset(x, size.height), 1.dp.toPx())
                // minor ticks
                for (k in 1 until 4) { val mx = x + k * step * dpPerSec.dp.toPx() / 4f; drawLine(lineCol.copy(alpha = 0.25f), Offset(mx, size.height - 4.dp.toPx()), Offset(mx, size.height), 1.dp.toPx()) }
                s += step
            }
        }
        var s = 0
        while (s * 1000L < totalMs) {
            Text("%02d:%02d".format(s / 60, s % 60), color = DZ.sub, fontSize = 10.sp, modifier = Modifier.offset(x = (s * dpPerSec).dp + 3.dp, y = 0.dp))
            s += step
        }
    }
}

// =============================================================== V1 clip
@Composable
private fun ClipTile(vm: EditorVM, c: Clip, index: Int, startMs: Long, dpPerMs: Float, pxPerMs: Float, h: Dp, onMenu: (MenuAnchor) -> Unit, haptic: () -> Unit, snap: (Long, List<Long>) -> Long) {
    val sel = c.id == vm.selClip
    val w = (c.lengthMs * dpPerMs).dp
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val shape = RoundedCornerShape(8.dp)
    val locked = vm.project.lockV
    Box(
        Modifier.width(w).height(h).padding(end = 2.dp).onGloballyPositioned { coords = it }
            .pointerInput(c.id, locked) {
                detectTapGestures(
                    onTap = { vm.selectClip(c.id, moveHead = false); haptic() },
                    onLongPress = { vm.selectClip(c.id, moveHead = false); haptic(); coords?.let { onMenu(MenuAnchor(ItemKind.Clip, c.id, it.boundsInWindow())) } },
                )
            },
    ) {
        Box(Modifier.fillMaxSize().clip(shape).background(DZ.card2).border(if (sel) 2.dp else 0.dp, if (sel) DZ.text else Color.Transparent, shape)) {
            // filmstrip
            val strip = vm.strips[c.uri].orEmpty()
            val one = vm.thumbs[c.uri]
            if (strip.isNotEmpty() || one != null) {
                val tileW = h * 1.6f
                val n = ceil(w.value / tileW.value).toInt().coerceAtLeast(1)
                Row(Modifier.fillMaxSize()) {
                    for (i in 0 until n) {
                        val img = if (strip.isNotEmpty() && !c.isImage) {
                            val frac = ((c.inMs + ((i + 0.5f) * tileW.value / dpPerMs * c.speed)) / c.srcDurMs.coerceAtLeast(1)).coerceIn(0f, 0.999f)
                            strip[(frac * strip.size).toInt().coerceIn(0, strip.size - 1)]
                        } else one!!
                        Image(img, null, Modifier.width(tileW).fillMaxHeight(), contentScale = ContentScale.Crop, alpha = 0.92f)
                    }
                }
            }
            // waveform of the clip's own audio along the bottom
            if (!c.isImage && c.volume > 0f) Wave(vm.waves[c.uri], c.inMs, c.outMs, false, Modifier.align(Alignment.BottomStart).fillMaxWidth().height(h * 0.3f), Color.White.copy(alpha = 0.75f), c.volume)
            Text("${fmtSecs(c.lengthMs)}${if (c.speed != 1f) "  ${"%.2g".format(c.speed)}×" else ""}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.TopStart).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 4.dp, vertical = 1.dp))
        }
        if (index > 0 && c.transition != "none") {
            Box(Modifier.align(Alignment.CenterStart).offset(x = (-9).dp).size(18.dp).clip(RoundedCornerShape(50)).background(DZ.text).clickable { vm.selectClip(c.id); vm.sheet = Sheet.Transitions }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Bolt, null, tint = DZ.bg, modifier = Modifier.size(12.dp))
            }
        }
        if (sel && !locked) {
            TrimHandle(Modifier.align(Alignment.CenterStart), left = true) { acc, ended -> trimClip(vm, c.id, true, acc / pxPerMs, startMs, snap, ended) }
            TrimHandle(Modifier.align(Alignment.CenterEnd), left = false) { acc, ended -> trimClip(vm, c.id, false, acc / pxPerMs, startMs, snap, ended) }
        }
    }
}

private val clipBase = HashMap<String, Clip>()
/** drag deltas are accumulated from the drag start, so the base clip is captured once per gesture */
private fun trimClip(vm: EditorVM, id: String, left: Boolean, deltaMs: Float, startMs: Long, snap: (Long, List<Long>) -> Long, ended: Boolean) {
    if (ended) { clipBase.remove(id); return }
    val c0 = clipBase.getOrPut(id) { vm.project.clips.first { it.id == id } }
    val sp = c0.speed
    if (c0.isImage) {
        val len = (c0.outMs - c0.inMs + (if (left) -deltaMs else deltaMs)).toLong()
        var l = len.coerceIn(300, 60_000)
        if (!left) l = (snap(startMs + l, emptyList()) - startMs).coerceIn(300, 60_000)
        vm.setImageLen(id, l); return
    }
    if (left) {
        val nIn = (c0.inMs + deltaMs * sp).toLong().coerceIn(0, (c0.outMs - 300 * sp).toLong().coerceAtLeast(0))
        vm.setTrim(id, nIn, c0.outMs)
    } else {
        var nOut = (c0.outMs + deltaMs * sp).toLong().coerceIn((c0.inMs + 300 * sp).toLong(), c0.srcDurMs)
        val endTl = startMs + ((nOut - c0.inMs) / sp).toLong()
        val sn = snap(endTl, listOf(startMs + c0.lengthMs))
        if (sn != endTl) nOut = (c0.inMs + (sn - startMs) * sp).toLong().coerceIn((c0.inMs + 300 * sp).toLong(), c0.srcDurMs)
        vm.setTrim(id, c0.inMs, nOut)
    }
}

@Composable
private fun TrimHandle(modifier: Modifier, left: Boolean, onDrag: (acc: Float, ended: Boolean) -> Unit) {
    val shape = if (left) RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp) else RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
    var acc by remember { mutableFloatStateOf(0f) }
    Box(
        modifier.fillMaxHeight().width(16.dp).clip(shape).background(DZ.text).pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { acc = 0f },
                onDragEnd = { onDrag(acc, true) }, onDragCancel = { onDrag(acc, true) },
                onHorizontalDrag = { ch, d -> ch.consume(); acc += d; onDrag(acc, false) },
            )
        },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.width(2.dp).height(18.dp).clip(RoundedCornerShape(1.dp)).background(DZ.bg.copy(alpha = 0.7f))) }
}

// =============================================================== text lane
@Composable
private fun TextTile(vm: EditorVM, t: TextItem, dpPerMs: Float, pxPerMs: Float, h: Dp, onMenu: (MenuAnchor) -> Unit, haptic: () -> Unit, snap: (Long, List<Long>) -> Long) {
    val sel = t.id == vm.selText
    val col = if (t.sticker) DZ.gold else DZ.accent2
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val locked = vm.project.lockT
    val shape = RoundedCornerShape(8.dp)
    var base by remember { mutableStateOf<TextItem?>(null) }
    var acc by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.offset(x = (t.startMs * dpPerMs).dp).width((t.durMs * dpPerMs).dp).fillMaxHeight().onGloballyPositioned { coords = it }
            .clip(shape).background(col.copy(alpha = 0.30f)).border(if (sel) 2.dp else 1.dp, if (sel) DZ.text else col.copy(alpha = 0.7f), shape)
            .pointerInput(t.id, locked) {
                detectTapGestures(
                    onTap = { vm.selText = t.id; vm.selSound = null; haptic() },
                    onLongPress = { vm.selText = t.id; vm.selSound = null; haptic(); coords?.let { onMenu(MenuAnchor(ItemKind.Text, t.id, it.boundsInWindow())) } },
                )
            }
            .pointerInput(t.id, locked) {
                if (!locked) detectHorizontalDragGestures(
                    onDragStart = { base = vm.project.texts.firstOrNull { it.id == t.id }; acc = 0f; vm.selText = t.id; vm.selSound = null },
                    onDragEnd = { base = null }, onDragCancel = { base = null },
                    onHorizontalDrag = { ch, d ->
                        ch.consume(); acc += d
                        val b = base ?: return@detectHorizontalDragGestures
                        val raw = (b.startMs + acc / pxPerMs).toLong().coerceAtLeast(0)
                        var s = snap(raw, listOf(b.startMs, b.startMs + b.durMs))
                        val e = snap(raw + b.durMs, listOf(b.startMs, b.startMs + b.durMs)); if (e != raw + b.durMs && s == raw) s = e - b.durMs
                        vm.moveText(t.id, s.coerceAtLeast(0))
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(t.text.replace("\n", " "), color = DZ.text, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.padding(horizontal = 14.dp))
        if (sel && !locked) {
            TrimHandle(Modifier.align(Alignment.CenterStart), true) { a, ended -> trimItemText(vm, t.id, true, a / pxPerMs, ended, snap) }
            TrimHandle(Modifier.align(Alignment.CenterEnd), false) { a, ended -> trimItemText(vm, t.id, false, a / pxPerMs, ended, snap) }
        }
    }
}

private val textBase = HashMap<String, TextItem>()
private fun trimItemText(vm: EditorVM, id: String, left: Boolean, deltaMs: Float, ended: Boolean, snap: (Long, List<Long>) -> Long) {
    if (ended) { textBase.remove(id); return }
    val b = textBase.getOrPut(id) { vm.project.texts.first { it.id == id } }
    if (left) {
        val ns = snap((b.startMs + deltaMs).toLong().coerceIn(0, b.startMs + b.durMs - 300), listOf(b.startMs))
        vm.trimText(id, ns, b.startMs + b.durMs - ns)
    } else {
        val ne = snap(b.startMs + (b.durMs + deltaMs).toLong().coerceAtLeast(300), listOf(b.startMs + b.durMs))
        vm.trimText(id, b.startMs, ne - b.startMs)
    }
}

// =============================================================== audio lane
@Composable
private fun SoundTile(vm: EditorVM, s: SoundItem, dpPerMs: Float, pxPerMs: Float, h: Dp, onMenu: (MenuAnchor) -> Unit, haptic: () -> Unit, snap: (Long, List<Long>) -> Long) {
    val sel = s.id == vm.selSound
    val col = when (s.kind) { "music" -> DZ.mint; "voice" -> DZ.pink; else -> Color(0xFF5B9BFF) }
    val len = if (s.loop) maxOf(0L, vm.project.durationMs - s.startMs) else s.durMs
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val locked = vm.project.lockA
    val shape = RoundedCornerShape(8.dp)
    var base by remember { mutableStateOf<SoundItem?>(null) }
    var acc by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.offset(x = (s.startMs * dpPerMs).dp).width((len * dpPerMs).dp.coerceAtLeast(22.dp)).fillMaxHeight().onGloballyPositioned { coords = it }
            .clip(shape).background(col.copy(alpha = 0.26f)).border(if (sel) 2.dp else 1.dp, if (sel) DZ.text else col.copy(alpha = 0.7f), shape)
            .pointerInput(s.id, locked) {
                detectTapGestures(
                    onTap = { vm.selSound = s.id; vm.selText = null; haptic() },
                    onLongPress = { vm.selSound = s.id; vm.selText = null; haptic(); coords?.let { onMenu(MenuAnchor(ItemKind.Sound, s.id, it.boundsInWindow())) } },
                )
            }
            .pointerInput(s.id, locked) {
                if (!locked) detectHorizontalDragGestures(
                    onDragStart = { base = vm.project.sounds.firstOrNull { it.id == s.id }; acc = 0f; vm.selSound = s.id; vm.selText = null },
                    onDragEnd = { base = null }, onDragCancel = { base = null },
                    onHorizontalDrag = { ch, d ->
                        ch.consume(); acc += d
                        val b = base ?: return@detectHorizontalDragGestures
                        val raw = (b.startMs + acc / pxPerMs).toLong().coerceAtLeast(0)
                        vm.moveSound(s.id, snap(raw, listOf(b.startMs, b.startMs + b.durMs)))
                    },
                )
            },
    ) {
        val pk = vm.waves[s.source]
        Wave(pk, s.inMs, s.inMs + s.durMs, s.loop, Modifier.fillMaxSize().padding(vertical = 3.dp), col, s.volume.coerceAtMost(1.5f))
        Text(s.name, color = DZ.text, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 1.dp))
        if (sel && !locked) {
            TrimHandle(Modifier.align(Alignment.CenterStart), true) { a, ended -> trimSoundItem(vm, s.id, true, a / pxPerMs, ended, snap) }
            TrimHandle(Modifier.align(Alignment.CenterEnd), false) { a, ended -> trimSoundItem(vm, s.id, false, a / pxPerMs, ended, snap) }
        }
    }
}

private val soundBase = HashMap<String, SoundItem>()
private val soundLast = HashMap<String, Long>()
private fun trimSoundItem(vm: EditorVM, id: String, left: Boolean, deltaMs: Float, ended: Boolean, snap: (Long, List<Long>) -> Long) {
    if (ended) { soundBase.remove(id); soundLast.remove(id); return }
    val b = soundBase.getOrPut(id) { vm.project.sounds.first { it.id == id } }
    // trimSound() is relative, so apply only the change since the previous event
    val cur = if (left) snap(b.startMs + deltaMs.toLong(), listOf(b.startMs)) - b.startMs else snap(b.startMs + b.durMs + deltaMs.toLong(), listOf(b.startMs + b.durMs)) - (b.startMs + b.durMs)
    val prev = soundLast[id] ?: 0L
    if (cur != prev) { vm.trimSound(id, left, cur - prev); soundLast[id] = cur }
}

/** Real waveform bars from decoded audio peaks. `loop` repeats the peaks to fill the tile. */
@Composable
fun Wave(peaks: ByteArray?, inMs: Long, outMs: Long, loop: Boolean, modifier: Modifier, color: Color, gain: Float = 1f) {
    androidx.compose.foundation.Canvas(modifier) {
        val pk = peaks
        val mid = size.height / 2f
        if (pk == null || pk.isEmpty()) { drawLine(color.copy(alpha = 0.4f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx()); return@Canvas }
        val barW = 2.dp.toPx(); val gap = 1.dp.toPx()
        val bars = (size.width / (barW + gap)).toInt().coerceAtLeast(1)
        val lenBins = ((outMs - inMs) / Waveform.BIN_MS).toInt().coerceAtLeast(1)
        val firstBin = (inMs / Waveform.BIN_MS).toInt()
        for (i in 0 until bars) {
            var bin = firstBin + (i.toLong() * lenBins / bars).toInt()
            if (loop) bin = firstBin + ((i.toLong() * lenBins / bars) % lenBins).toInt()
            val v = (pk.getOrElse(bin.coerceAtLeast(0)) { 0 }.toInt() and 0xFF) / 255f
            val hh = (v * gain * 1.6f).coerceIn(0.04f, 1f) * size.height * 0.5f
            val x = i * (barW + gap)
            drawRoundRect(color, Offset(x, mid - hh), Size(barW, hh * 2), androidx.compose.ui.geometry.CornerRadius(1f))
        }
    }
}
