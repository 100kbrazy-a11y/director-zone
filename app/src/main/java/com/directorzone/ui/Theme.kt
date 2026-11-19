package com.directorzone.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directorzone.data.Prefs
import kotlin.math.cos
import kotlin.math.sin

/** One complete colour system. Every surface, text, accent and line in the app reads from the active palette. */
data class Palette(
    val id: String, val name: String, val dark: Boolean,
    val bg: Color, val panel: Color, val card: Color, val card2: Color,
    val text: Color, val sub: Color, val accent: Color, val accent2: Color, val onAccent: Color,
)

private fun c(v: Long) = Color(v)
val PALETTES = listOf(
    Palette("obsidian", "Obsidian", true, c(0xFF0B0B0D), c(0xFF141416), c(0xFF1D1D20), c(0xFF29292D), c(0xFFF5F5F7), c(0xFF9A9AA2), c(0xFFFFFFFF), c(0xFFB9BCC4), c(0xFF0B0B0D)),
    Palette("midnight", "Midnight", true, c(0xFF080C18), c(0xFF0F1526), c(0xFF171F36), c(0xFF222C49), c(0xFFEEF2FF), c(0xFF8E9BC0), c(0xFF7B93FF), c(0xFF5BD6FF), c(0xFF081030)),
    Palette("crimson", "Crimson", true, c(0xFF0E0708), c(0xFF171011), c(0xFF21161A), c(0xFF2E2025), c(0xFFFBEFF1), c(0xFFB09097), c(0xFFFF4B5F), c(0xFFFF8A5B), c(0xFFFFFFFF)),
    Palette("emerald", "Emerald", true, c(0xFF060F0C), c(0xFF0E1814), c(0xFF152620), c(0xFF1F362E), c(0xFFEBFAF4), c(0xFF86AFA0), c(0xFF2FD99A), c(0xFF9BF0C6), c(0xFF04130C)),
    Palette("ocean", "Ocean", true, c(0xFF050E17), c(0xFF0C1824), c(0xFF13263A), c(0xFF1C364F), c(0xFFEAF5FF), c(0xFF86A8C4), c(0xFF33AFFF), c(0xFF5CF0E0), c(0xFF031422)),
    Palette("violet", "Violet", true, c(0xFF0C0813), c(0xFF150F20), c(0xFF1F1730), c(0xFF2C2142), c(0xFFF3EEFF), c(0xFF9F92C2), c(0xFF9468FF), c(0xFFFF6BD0), c(0xFFFFFFFF)),
    Palette("amber", "Amber", true, c(0xFF100C05), c(0xFF191409), c(0xFF251D0E), c(0xFF342914), c(0xFFFFF6E6), c(0xFFB5A27F), c(0xFFFFB224), c(0xFFFF7A3D), c(0xFF1B1000)),
    Palette("rose", "Rose", true, c(0xFF110910), c(0xFF1B0F19), c(0xFF281825), c(0xFF38222F), c(0xFFFFEFF6), c(0xFFBB93A8), c(0xFFFF6FA5), c(0xFFFFB38A), c(0xFF230613)),
    Palette("arctic", "Arctic", false, c(0xFFF2F5F9), c(0xFFFFFFFF), c(0xFFE9EEF4), c(0xFFDCE3EC), c(0xFF0E1726), c(0xFF5F6C80), c(0xFF2563EB), c(0xFF0EA5E9), c(0xFFFFFFFF)),
)
fun paletteById(id: String) = PALETTES.firstOrNull { it.id == id } ?: PALETTES[0]

object DZ {
    private val pal: Palette get() = paletteById(Prefs.theme)
    val dark get() = pal.dark
    val bg get() = pal.bg
    val panel get() = pal.panel
    val card get() = pal.card
    val card2 get() = pal.card2
    val line get() = pal.text.copy(alpha = 0.10f)
    val text get() = pal.text
    val sub get() = pal.sub
    /** primary accent (selection, active states) */
    val accent get() = pal.accent
    val accent2 get() = pal.accent2
    val onAccent get() = pal.onAccent
    // legacy names used across the editor
    val violet get() = pal.accent
    val pink get() = pal.accent2
    val mint = Color(0xFF2FD99A)
    val gold = Color(0xFFFFC857)
    val danger = Color(0xFFFF5A5F)
    val grad get() = Brush.linearGradient(listOf(pal.accent, pal.accent2))
    /** soft translucent tint of the accent for selected backgrounds */
    val accentSoft get() = pal.accent.copy(alpha = if (pal.dark) 0.16f else 0.12f)
}

/** Standard easing + durations: calm, no bounce. */
object Motion {
    val ease = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    fun ms(base: Int): Int = when (Prefs.animQuality) { 0 -> 0; 1 -> base * 2 / 3; else -> base }
    fun <T> tween(base: Int = 240): FiniteAnimationSpec<T> = androidx.compose.animation.core.tween(ms(base), easing = ease)
}

@Composable
fun DirectorTheme(content: @Composable () -> Unit) {
    val p = paletteById(Prefs.theme)
    val scheme = if (p.dark) darkColorScheme(primary = p.accent, onPrimary = p.onAccent, secondary = p.accent2, background = p.bg, surface = p.panel, onSurface = p.text, onBackground = p.text, surfaceVariant = p.card, onSurfaceVariant = p.sub, surfaceContainer = p.panel, surfaceContainerHigh = p.card, outline = p.sub)
    else lightColorScheme(primary = p.accent, onPrimary = p.onAccent, secondary = p.accent2, background = p.bg, surface = p.panel, onSurface = p.text, onBackground = p.text, surfaceVariant = p.card, onSurfaceVariant = p.sub, surfaceContainer = p.panel, surfaceContainerHigh = p.card, outline = p.sub)
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun GradientButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (enabled) DZ.accent else DZ.card2)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (enabled) DZ.onAccent else DZ.sub, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = if (enabled) DZ.onAccent else DZ.sub, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun Pill(label: String, selected: Boolean, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val bg by animateColorAsState(if (selected) DZ.accentSoft else DZ.card, Motion.tween(160), label = "pillbg")
    Row(
        modifier.clip(shape).background(bg).border(1.dp, if (selected) DZ.accent else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(7.dp)) }
        Text(label, color = if (selected) DZ.text else DZ.sub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Swatch(color: Color, selected: Boolean, size: Int = 26, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color).border(if (selected) 2.5.dp else 1.dp, if (selected) DZ.text else DZ.line, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = if (lum(color) > 0.6f) Color.Black else Color.White, modifier = Modifier.size(14.dp))
    }
}
private fun lum(c: Color) = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String = { "%.2f".format(it) }, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DZ.sub, fontSize = 12.sp, modifier = Modifier.width(88.dp), maxLines = 1)
        Slider(value.coerceIn(range.start, range.endInclusive), onChange, valueRange = range, modifier = Modifier.weight(1f).height(30.dp),
            colors = SliderDefaults.colors(thumbColor = DZ.accent, activeTrackColor = DZ.accent, inactiveTrackColor = DZ.card2))
        Text(format(value), color = DZ.text, fontSize = 11.sp, modifier = Modifier.width(46.dp), maxLines = 1, textAlign = androidx.compose.ui.text.style.TextAlign.End)
    }
}

@Composable
fun SectionLabel(t: String) { Text(t.uppercase(), color = DZ.sub, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(vertical = 6.dp)) }

/** A tiny animated phone used as the "preview" on templates and animation chips. */
@Composable
fun PhoneGlyph(accent: Color, code: Int, modifier: Modifier = Modifier, speed: Int = 3200) {
    val tr = rememberInfiniteTransition(label = "glyph")
    val running = Prefs.animQuality > 0
    val t0 by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(speed, easing = LinearEasing)), label = "t")
    val t = if (running) t0 else 0.12f
    val bodyCol = if (DZ.dark) Color(0xFF2A2A33) else Color(0xFF3A3A45)
    Canvas(modifier) {
        val ph = size.height * 0.84f; val pw = ph * 0.49f
        val a = t * 6.2832f
        var yaw = 0f; var bob = 0f; var sc = 1f; var roll = 0f; var dy = 0f
        when (code) {
            1, 13 -> yaw = 0.9f * sin(a)
            2, 12 -> yaw = a
            3, 15 -> yaw = 0.3f * sin(a)
            4 -> { yaw = 0.5f; bob = sin(a) * 0.03f }
            5 -> sc = 0.7f + 0.3f * (0.5f + 0.5f * sin(a))
            6, 7 -> dy = -(0.5f + 0.5f * sin(a)) * 0.2f
            8 -> sc = 0.8f + 0.25f * (0.5f + 0.5f * sin(a))
            9 -> { yaw = 0.5f * sin(a); roll = 0.3f * sin(a * 2) }
            10 -> { yaw = 0.7f; roll = 0.4f }
            11 -> { yaw = 0.3f * sin(a * 3); roll = 0.07f * sin(a * 3) }
            14 -> {}
            else -> { yaw = 0.25f * sin(a); bob = 0.03f * sin(a * 2) }
        }
        val flip = if (code == 3 || code == 15) cos(a) else 1f
        val w = pw * sc * kotlin.math.abs(cos(yaw)).coerceAtLeast(0.12f)
        val h = ph * sc * (if (code == 3 || code == 15) kotlin.math.abs(flip).coerceAtLeast(0.12f) else 1f)
        val cx = size.width / 2; val cy = size.height / 2 + (bob + dy) * size.height
        rotate(roll * 57f, Offset(cx, cy)) {
            drawRoundRect(bodyCol, Offset(cx - w / 2, cy - h / 2), Size(w, h), CornerRadius(w * 0.2f))
            drawRoundRect(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f)), Offset(cx - w / 2, cy - h / 2), Offset(cx + w / 2, cy + h / 2)), Offset(cx - w / 2 + w * 0.07f, cy - h / 2 + w * 0.07f), Size(w * 0.86f, h - w * 0.14f), CornerRadius(w * 0.15f))
            drawRoundRect(Color.Black, Offset(cx - w * 0.14f, cy - h / 2 + w * 0.12f), Size(w * 0.28f, w * 0.07f), CornerRadius(w * 0.04f))
        }
    }
}
