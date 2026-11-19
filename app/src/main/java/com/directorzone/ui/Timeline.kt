package com.directorzone.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directorzone.catalog.transitionById
import com.directorzone.data.SoundItem
import androidx.compose.runtime.snapshotFlow

fun fmt(ms: Long): String { val s = ms / 1000; val d = (ms % 1000) / 100; return "%d:%02d.%d".format(s / 60, s % 60, d) }

@Composable
fun Timeline(vm: EditorVM, dpPerSec: Float, modifier: Modifier = Modifier) {
    val p = vm.project
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val dpPerMs = dpPerSec / 1000f
    fun pxPerMs() = with(density) { dpPerMs.dp.toPx() }

    val prog = remember { booleanArrayOf(false) }
    // scrub -> seek (only for the user's own drags / flings)
    LaunchedEffect(dpPerSec) {
        snapshotFlow { scroll.value }.collect { v ->
            if (scroll.isScrollInProgress && !prog[0]) { vm.playing = false; vm.seek((v / pxPerMs()).toLong()) }
        }
    }
    // playback / external seeks -> scroll
    LaunchedEffect(vm.posMs, dpPerSec) {
        if (!scroll.isScrollInProgress) { prog[0] = true; scroll.scrollTo((vm.posMs * pxPerMs()).toInt()); prog[0] = false }
    }

    val totalMs = maxOf(p.durationMs, p.texts.maxOfOrNull { it.startMs + it.durMs } ?: 0L, p.sounds.maxOfOrNull { it.startMs + it.durMs.coerceAtMost(60000) } ?: 0L) + 800
    BoxWithConstraints(modifier.fillMaxWidth().background(DZ.panel)) {
        val half = maxWidth / 2
        Row(Modifier.horizontalScroll(scroll).padding(horizontal = half)) {
            Column(Modifier.width((totalMs * dpPerMs).dp).padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Ruler(totalMs, dpPerSec)
                // video lane
                Row(Modifier.height(54.dp)) {
                    p.clips.forEachIndexed { i, c ->
                        val w = (c.lengthMs * dpPerMs).dp
                        val sel = c.id == vm.selClip
                        Box(
                            Modifier.width(w).fillMaxHeight().padding(end = 1.dp).clip(RoundedCornerShape(8.dp)).background(DZ.card2)
                                .border(if (sel) 2.dp else 0.dp, if (sel) Color.White else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable { vm.selClip = c.id; vm.selText = null; vm.selSound = null; if (vm.tool == null) vm.tool = Tool.Clip },
                        ) {
                            vm.thumbs[c.uri]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = 0.85f) }
                            Text(if (c.speed != 1f) "${"%.2g".format(c.speed)}×" else "", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp))
                            if (i > 0 && c.transition != "none") {
                                Box(Modifier.align(Alignment.CenterStart).offset(x = (-7).dp).size(16.dp).clip(RoundedCornerShape(50)).background(DZ.pink), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Rounded.Bolt, null, tint = Color.White, modifier = Modifier.size(11.dp))
                                }
                            }
                        }
                    }
                }
                // text lane
                Box(Modifier.height(26.dp).fillMaxWidth()) {
                    p.texts.forEach { t ->
                        val sel = t.id == vm.selText
                        Box(
                            Modifier.offset(x = (t.startMs * dpPerMs).dp).width((t.durMs * dpPerMs).dp).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                                .background(if (t.sticker) DZ.gold.copy(alpha = 0.8f) else DZ.violet.copy(alpha = 0.85f))
                                .border(if (sel) 2.dp else 0.dp, if (sel) Color.White else Color.Transparent, RoundedCornerShape(7.dp))
                                .clickable { vm.selText = t.id; vm.selSound = null; vm.tool = if (t.sticker) Tool.Stickers else Tool.Text },
                            contentAlignment = Alignment.CenterStart,
                        ) { Text(t.text.replace("\n", " "), color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.padding(horizontal = 6.dp)) }
                    }
                }
                // sound lanes (greedy rows)
                val rows = ArrayList<MutableList<SoundItem>>()
                p.sounds.sortedBy { it.startMs }.forEach { s ->
                    val r = rows.firstOrNull { row -> row.last().startMs + minOf(row.last().durMs, 60000) <= s.startMs } ?: ArrayList<SoundItem>().also { rows.add(it) }
                    r.add(s)
                }
                rows.take(3).forEach { row ->
                    Box(Modifier.height(24.dp).fillMaxWidth()) {
                        row.forEach { s ->
                            val sel = s.id == vm.selSound
                            val len = if (s.loop) maxOf(0L, p.durationMs - s.startMs) else s.durMs
                            Box(
                                Modifier.offset(x = (s.startMs * dpPerMs).dp).width((len * dpPerMs).dp.coerceAtLeast(18.dp)).fillMaxHeight().clip(RoundedCornerShape(7.dp))
                                    .background(when (s.kind) { "music" -> DZ.mint.copy(alpha = 0.75f); "voice" -> DZ.pink.copy(alpha = 0.8f); else -> Color(0xFF3E7BFF).copy(alpha = 0.85f) })
                                    .border(if (sel) 2.dp else 0.dp, if (sel) Color.White else Color.Transparent, RoundedCornerShape(7.dp))
                                    .clickable { vm.selSound = s.id; vm.selText = null; vm.tool = Tool.Sound },
                                contentAlignment = Alignment.CenterStart,
                            ) { Text(s.name, color = Color.White, fontSize = 10.5.sp, maxLines = 1, overflow = TextOverflow.Clip, modifier = Modifier.padding(horizontal = 6.dp)) }
                        }
                    }
                }
            }
        }
        // playhead
        Box(Modifier.align(Alignment.TopCenter).fillMaxHeight().width(2.dp).background(Color.White))
    }
}

@Composable
private fun Ruler(totalMs: Long, dpPerSec: Float) {
    val step = if (dpPerSec >= 90) 1 else if (dpPerSec >= 45) 2 else 5
    Box(Modifier.height(14.dp).fillMaxWidth()) {
        var s = 0
        while (s * 1000L < totalMs) {
            Text("${s}s", color = DZ.sub, fontSize = 9.sp, modifier = Modifier.offset(x = (s * dpPerSec).dp + 2.dp))
            s += step
        }
    }
}
