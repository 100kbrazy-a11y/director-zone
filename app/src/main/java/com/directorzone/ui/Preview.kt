package com.directorzone.ui

import android.graphics.Paint
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.directorzone.catalog.ratioById
import com.directorzone.data.TextItem
import com.directorzone.engine.TextPainter
import kotlin.math.roundToInt

/** Hosts the single engine player. All state (position, play/pause) lives in vm.engine. */
@Composable
fun PreviewPane(vm: EditorVM, modifier: Modifier = Modifier) {
    val eng = vm.engine
    val pl = eng.player
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { eng.hostView = it; if (it.player !== pl) it.player = pl },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        if (vm.project.clips.isEmpty()) Text("Add media to start", color = DZ.sub)
        if (eng.building) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(10.dp).size(18.dp), color = DZ.text, strokeWidth = 2.dp)
        eng.error?.let { Text("Preview error: $it", color = DZ.danger, modifier = Modifier.padding(16.dp)) }
        // tap the preview: pause while playing; otherwise select the text/sticker under the finger, or deselect everything
        val arT = ratioById(vm.project.ratio).let { it.w.toFloat() / it.h }
        Box(Modifier.fillMaxSize().pointerInput(arT) {
            detectTapGestures { off ->
                if (eng.isPlaying) { eng.pause(); return@detectTapGestures }
                val w = size.width.toFloat(); val h = size.height.toFloat()
                val fwP = if (w / h > arT) h * arT else w; val fhP = if (w / h > arT) h else w / arT
                val ox = (w - fwP) / 2f; val oy = (h - fhP) / 2f
                val pm = vm.posMs
                val hit = if (vm.project.lockT || vm.project.hideT) null else vm.project.texts.lastOrNull { t ->
                    if (pm < t.startMs || pm >= t.startMs + t.durMs) return@lastOrNull false
                    val (wU, hU) = textBox(t, arT)
                    val bw = ((wU + 0.024f) * fhP).coerceAtLeast(40f * density); val bh = ((hU + 0.024f) * fhP).coerceAtLeast(30f * density)
                    kotlin.math.abs(off.x - (ox + t.x * fwP)) <= bw / 2f && kotlin.math.abs(off.y - (oy + t.y * fhP)) <= bh / 2f
                }
                if (hit != null) vm.selectText(hit.id) else vm.deselectAll()
            }
        })
        if (pl != null && !eng.isPlaying) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable { eng.play() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        }

        // ---- direct manipulation of the selected text / sticker, over the video frame
        val r = ratioById(vm.project.ratio)
        val ar = r.w.toFloat() / r.h
        val fw = if (maxWidth / maxHeight > ar) maxHeight * ar else maxWidth
        val fh = if (maxWidth / maxHeight > ar) maxHeight else maxWidth / ar
        val t = vm.text
        if (t != null && !vm.project.lockT && !vm.project.hideT && vm.posMs >= t.startMs - 50 && vm.posMs < t.startMs + t.durMs) {
            Box(Modifier.size(fw, fh)) { TextGizmo(vm, t, fw.value, fh.value) }
        }
    }
}

private val measure = Paint(Paint.ANTI_ALIAS_FLAG)

/** Bounding box of a text item in units of canvas height (matches TextPainter's layout closely). */
private fun textBox(t: TextItem, aspect: Float): Pair<Float, Float> {
    val lines = t.text.split("\n")
    measure.typeface = if (t.sticker) android.graphics.Typeface.DEFAULT else TextPainter.typeface(t.font)
    measure.textSize = 1000f
    var wU = (lines.maxOf { measure.measureText(it) } / 1000f) * t.size + t.letterSp * t.size * lines.maxOf { it.length }
    var fit = 1f
    if (wU > aspect * 0.92f) { fit = aspect * 0.92f / wU; wU = aspect * 0.92f }
    val hU = lines.size * t.size * fit * t.lineSp
    return wU to hU
}

@Composable
private fun TextGizmo(vm: EditorVM, t: TextItem, frameW: Float, frameH: Float) {
    val density = LocalDensity.current
    val aspect = frameW / frameH
    val (wU, hU) = textBox(t, aspect)
    val padU = 0.012f
    val boxW = ((wU + padU * 2) * frameH).coerceAtLeast(40f)
    val boxH = ((hU + padU * 2) * frameH).coerceAtLeast(30f)
    val cx = t.x * frameW; val cy = t.y * frameH
    val latest = rememberUpdatedState(t)
    Box(
        Modifier.offset((cx - boxW / 2).dp, (cy - boxH / 2).dp).size(boxW.dp, boxH.dp)
            .pointerInput(t.id) {
                detectTransformGestures { _, pan, zoom, rot ->
                    val cur = latest.value
                    val dxFrac = pan.x / (frameW * density.density)
                    val dyFrac = pan.y / (frameH * density.density)
                    vm.updateText(cur.id) {
                        it.copy(
                            x = (it.x + dxFrac).coerceIn(0.02f, 0.98f), y = (it.y + dyFrac).coerceIn(0.02f, 0.98f),
                            size = (it.size * zoom).coerceIn(0.03f, 0.4f), rotation = it.rotation + rot,
                        )
                    }
                }
            },
    ) {
      Box(Modifier.fillMaxSize().rotate(t.rotation).border(1.5.dp, Color.White, RoundedCornerShape(3.dp))) {
        // corner handles (visual affordance: scale/rotate with two fingers, drag anywhere inside to move)
        listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd).forEach { al ->
            Box(Modifier.align(al).offset(x = if (al == Alignment.TopStart || al == Alignment.BottomStart) (-5).dp else 5.dp, y = if (al == Alignment.TopStart || al == Alignment.TopEnd) (-5).dp else 5.dp).size(10.dp).clip(CircleShape).background(Color.White))
        }
        Box(Modifier.align(Alignment.TopCenter).offset(y = (-22).dp).size(20.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.RotateRight, null, tint = Color.Black, modifier = Modifier.size(14.dp))
        }
      }
    }
}
