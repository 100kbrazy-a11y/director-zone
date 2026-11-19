package com.directorzone.ui

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin

object DZ {
    val bg = Color(0xFF0A0A0F)
    val panel = Color(0xFF13131B)
    val card = Color(0xFF1B1B26)
    val card2 = Color(0xFF242433)
    val line = Color(0x22FFFFFF)
    val text = Color(0xFFF4F4F8)
    val sub = Color(0xFF9A9AB0)
    val violet = Color(0xFF7C5CFF)
    val pink = Color(0xFFFF5CA8)
    val mint = Color(0xFF18E0C0)
    val gold = Color(0xFFFFC857)
    val grad = Brush.linearGradient(listOf(violet, pink))
}

@Composable
fun DirectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(primary = DZ.violet, secondary = DZ.pink, background = DZ.bg, surface = DZ.panel, onSurface = DZ.text, onBackground = DZ.text, surfaceVariant = DZ.card, onSurfaceVariant = DZ.sub),
        content = content,
    )
}

@Composable
fun GradientButton(text: String, icon: ImageVector? = null, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(if (enabled) DZ.grad else Brush.linearGradient(listOf(DZ.card2, DZ.card2)))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
fun Pill(label: String, selected: Boolean, modifier: Modifier = Modifier, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.clip(shape).background(if (selected) DZ.violet.copy(alpha = 0.28f) else DZ.card)
            .border(1.dp, if (selected) DZ.violet else Color.Transparent, shape)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(7.dp)) }
        Text(label, color = if (selected) Color.White else DZ.sub, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun Swatch(color: Color, selected: Boolean, size: Int = 26, onClick: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color).border(if (selected) 2.5.dp else 1.dp, if (selected) Color.White else DZ.line, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = if (color.luminance() > 0.6f) Color.Black else Color.White, modifier = Modifier.size(14.dp))
    }
}
private fun Color.luminance() = 0.299f * red + 0.587f * green + 0.114f * blue

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String = { "%.2f".format(it) }, onChange: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = DZ.sub, fontSize = 12.sp, modifier = Modifier.width(84.dp), maxLines = 1)
        Slider(value.coerceIn(range.start, range.endInclusive), onChange, valueRange = range, modifier = Modifier.weight(1f).height(30.dp),
            colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = DZ.violet, inactiveTrackColor = DZ.card2))
        Text(format(value), color = DZ.text, fontSize = 11.sp, modifier = Modifier.width(42.dp), maxLines = 1)
    }
}

@Composable
fun SectionLabel(t: String) { Text(t.uppercase(), color = DZ.sub, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(vertical = 6.dp)) }

/** A tiny animated phone used as the "preview" on templates and animation chips. */
@Composable
fun PhoneGlyph(accent: Color, code: Int, modifier: Modifier = Modifier, speed: Int = 3200) {
    val tr = rememberInfiniteTransition(label = "glyph")
    val t by tr.animateFloat(0f, 1f, infiniteRepeatable(tween(speed, easing = LinearEasing)), label = "t")
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
            drawRoundRect(Color(0xFF222230), Offset(cx - w / 2, cy - h / 2), Size(w, h), CornerRadius(w * 0.2f))
            drawRoundRect(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.35f)), Offset(cx - w / 2, cy - h / 2), Offset(cx + w / 2, cy + h / 2)), Offset(cx - w / 2 + w * 0.07f, cy - h / 2 + w * 0.07f), Size(w * 0.86f, h - w * 0.14f), CornerRadius(w * 0.15f))
            drawRoundRect(Color.Black, Offset(cx - w * 0.14f, cy - h / 2 + w * 0.12f), Size(w * 0.28f, w * 0.07f), CornerRadius(w * 0.04f))
        }
    }
}
