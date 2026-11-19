package com.directorzone.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directorzone.data.Adjust
import com.directorzone.data.Prefs

/** Density factor from Settings > Interface Density. */
val dens: Float get() = if (Prefs.compact) 0.86f else 1f

/** Light tick feedback if Haptics is on in Settings. */
@Composable
fun rememberHaptic(): () -> Unit {
    val h = LocalHapticFeedback.current
    return remember(h) { { if (Prefs.haptics) h.performHapticFeedback(HapticFeedbackType.TextHandleMove) } }
}

// ------------------------------------------------------------------ small controls
@Composable
fun DzSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = DZ.onAccent, checkedTrackColor = DZ.accent, uncheckedThumbColor = DZ.sub, uncheckedTrackColor = DZ.card2, uncheckedBorderColor = DZ.line))
}

/** Segmented control as in the Export reference: one rounded track, the selected option is a filled pill. */
@Composable
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, enabled: List<Boolean>? = null, onSelect: (Int) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(50)).background(DZ.card).border(1.dp, DZ.line, RoundedCornerShape(50)).padding(3.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val ok = enabled?.getOrNull(i) ?: true
            val bg by animateColorAsState(if (on) DZ.accent else Color.Transparent, Motion.tween(180), label = "seg")
            Box(Modifier.weight(1f).clip(RoundedCornerShape(50)).background(bg).clickable(enabled = ok) { onSelect(i) }.padding(horizontal = 12.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                Text(o, color = if (on) DZ.onAccent else if (ok) DZ.text else DZ.sub.copy(alpha = 0.4f), fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
            }
        }
    }
}

/** Plain scrollable text tabs with a thin accent underline (Font / Style / Color …). */
@Composable
fun TextTabs(tabs: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        items(tabs.size) { i ->
            val on = i == selected
            Column(Modifier.clip(RoundedCornerShape(8.dp)).clickable { onSelect(i) }.padding(horizontal = 12.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tabs[i], color = if (on) DZ.text else DZ.sub, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                val w by animateDpAsState(if (on) 22.dp else 0.dp, Motion.tween(200), label = "ul")
                Box(Modifier.height(2.dp).width(w).clip(RoundedCornerShape(2.dp)).background(DZ.accent))
            }
        }
    }
}

@Composable
fun Chips(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(options.size) { i ->
            val o = options[i]; val on = o == selected
            val bg by animateColorAsState(if (on) DZ.accent else DZ.card, Motion.tween(160), label = "chip")
            Text(o, color = if (on) DZ.onAccent else DZ.sub, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Medium, maxLines = 1,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).clickable { onSelect(o) }.padding(horizontal = 14.dp, vertical = 7.dp))
        }
    }
}

@Composable
fun IconTextButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, tint: Color = DZ.text, enabled: Boolean = true, size: Dp = 22.dp, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = if (enabled) tint else DZ.sub.copy(alpha = 0.35f), modifier = Modifier.size(size))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (enabled) DZ.sub else DZ.sub.copy(alpha = 0.35f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Clip, textAlign = TextAlign.Center)
    }
}

/** A labelled row used by Settings: title left, value/control right. */
@Composable
fun SettingRow(title: String, value: String? = null, subtitle: String? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 18.dp, vertical = (13 * dens).dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = DZ.text, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = DZ.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (value != null) Text(value, color = DZ.sub, fontSize = 15.sp)
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
        if (onClick != null && trailing == null) Icon(Icons.Rounded.ChevronRight, null, tint = DZ.sub, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title.uppercase(), color = DZ.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 8.dp))
    Column(Modifier.fillMaxWidth().background(DZ.card)) { content() }
}

/** Colour-matrix approximation of an Adjust (+ filter amount) so thumbnails show what a look does to the real frame. */
fun Adjust.toColorFilter(amount: Float = 1f): ColorFilter {
    val a = this.scaled(amount)
    val sat = (1f + a.saturation).coerceIn(0f, 2.5f)
    val con = (1f + a.contrast * 1.4f).coerceIn(0.3f, 2f)
    val off = a.exposure * 70f + (a.fade * 40f) - (a.contrast * 1.4f * 127f * 0.0f)
    val m = ColorMatrix().apply { setToSaturation(sat) }
    val c = ColorMatrix(floatArrayOf(
        con, 0f, 0f, 0f, off + (1f - con) * 127f + a.warmth * 24f,
        0f, con, 0f, 0f, off + (1f - con) * 127f + a.tint * -10f,
        0f, 0f, con, 0f, off + (1f - con) * 127f - a.warmth * 24f,
        0f, 0f, 0f, 1f, 0f))
    m.timesAssign(c)
    return ColorFilter.colorMatrix(m)
}

/** Drag handle + drag-to-dismiss / drag-to-expand container for all editor bottom sheets. */
@Composable
fun SheetFrame(height: Dp, expanded: Boolean, onExpand: (Boolean) -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val h by animateDpAsState(height, Motion.tween(260), label = "sheetH")
    Column(modifier.fillMaxWidth().height(h).clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)).background(DZ.panel).border(1.dp, DZ.line, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))) {
        var drag by remember { mutableFloatStateOf(0f) }
        Box(
            Modifier.fillMaxWidth().height(26.dp).pointerInput(expanded) {
                detectVerticalDragGestures(
                    onDragStart = { drag = 0f },
                    onVerticalDrag = { c, d -> c.consume(); drag += d },
                    onDragEnd = {
                        if (drag < -40f) onExpand(true)
                        else if (drag > 40f) { if (expanded) onExpand(false) else onDismiss() }
                        drag = 0f
                    },
                )
            }.clickable { onExpand(!expanded) },
            contentAlignment = Alignment.Center,
        ) { Box(Modifier.width(38.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(DZ.sub.copy(alpha = 0.45f))) }
        content()
    }
}

@Composable
fun SheetHeader(title: String, onDone: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = DZ.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1)
        actions()
        Box(Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onDone), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, null, tint = DZ.text, modifier = Modifier.size(24.dp)) }
    }
}

/** Slider row with a tiny centered zero tick style: label left, value right. */
@Composable
fun AdjustRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, fmt: (Float) -> String, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth().height((38 * dens).dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DZ.sub, fontSize = 14.sp, modifier = Modifier.width(104.dp), maxLines = 1)
        androidx.compose.material3.Slider(value.coerceIn(range.start, range.endInclusive), onChange, valueRange = range, modifier = Modifier.weight(1f).height(28.dp),
            colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = DZ.accent, activeTrackColor = DZ.accent, inactiveTrackColor = DZ.card2))
        Text(fmt(value), color = DZ.text, fontSize = 14.sp, modifier = Modifier.width(52.dp), maxLines = 1, textAlign = TextAlign.End)
    }
}

fun pct(v: Float) = "${(v * 100).toInt()}"
fun signed(v: Float) = (v * 100).toInt().let { if (it > 0) "+$it" else "$it" }
fun fmtTime(ms: Long): String { val t = ms.coerceAtLeast(0); val s = t / 1000; return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) }
fun fmt(ms: Long): String { val s = ms / 1000; val d = (ms % 1000) / 100; return "%d:%02d.%d".format(s / 60, s % 60, d) }
fun fmtSecs(ms: Long) = "%.1fs".format(ms / 1000f)

@Composable
fun animatedFloat(target: Float, base: Int = 220): Float = animateFloatAsState(target, Motion.tween(base), label = "af").value
