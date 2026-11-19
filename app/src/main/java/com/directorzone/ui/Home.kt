package com.directorzone.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directorzone.catalog.*
import com.directorzone.data.Project

@Composable
fun HomeScreen(vm: EditorVM) {
    var pendingTemplate by remember { mutableStateOf<TemplateDef?>(null) }
    var phoneStudio by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris: List<Uri> ->
        val tpl = pendingTemplate; val phone = phoneStudio
        pendingTemplate = null; phoneStudio = false
        if (uris.isNotEmpty()) {
            vm.newProject()
            vm.importMedia(uris) {
                if (tpl != null) vm.applyTemplate(tpl)
                else if (phone) vm.edit { it.copy(phone = it.phone.copy(enabled = true)) }
            }
        }
    }
    fun pick() = picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))

    LazyColumn(Modifier.fillMaxSize().background(DZ.bg), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 52.dp, bottom = 8.dp)) {
                Text("Director Zone", fontSize = 34.sp, fontWeight = FontWeight.Black, style = androidx.compose.ui.text.TextStyle(brush = DZ.grad))
                Text("Phone mockups, templates, transitions & sounds — all in one cut.", color = DZ.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
        item {
            Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BigAction("New project", "Start from scratch", Icons.Rounded.Add, listOf(DZ.violet, Color(0xFF4B3FD0)), Modifier.weight(1f)) { pick() }
                BigAction("Phone Studio", "Put a video in a 3D phone", Icons.Rounded.PhoneIphone, listOf(DZ.pink, Color(0xFFB0307A)), Modifier.weight(1f)) { phoneStudio = true; pick() }
            }
        }
        item { SectionHeader("Animated templates", "Pick a look, drop in your clips") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(TEMPLATES) { t -> TemplateCard(t) { pendingTemplate = t; pick() } }
            }
        }
        item { SectionHeader("Phone animations", "${PHONE_ANIMS.size} premade moves · ${BEZELS.size} devices · ${BACKGROUNDS.size} backdrops") }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(PHONE_ANIMS) { a ->
                    Column(Modifier.width(92.dp).clip(RoundedCornerShape(16.dp)).background(DZ.card).clickable { phoneStudio = true; pick() }.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        PhoneGlyph(DZ.violet, a.code, Modifier.size(72.dp, 84.dp))
                        Text(a.name, color = DZ.text, fontSize = 11.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item { SectionHeader("Everything inside", "") }
        item {
            val feats = listOf("${FILTERS.size} filters" to "🎨", "${TRANSITIONS.size - 1} transitions" to "⚡", "${TEXT_ANIMS.size - 1} text animations" to "✍️", "58 sounds & beats" to "🔊", "Voiceover" to "🎙️", "Speed 0.25–4×" to "⏩", "Ken Burns" to "🎞️", "7 aspect ratios" to "📐", "Up to 1440p · 60fps · HEVC" to "📤", "Undo / redo" to "↩️")
            FlowRowCompat(feats)
        }
        if (vm.recents.isNotEmpty()) {
            item { SectionHeader("Recent projects", "") }
            items(vm.recents, key = { it.id }) { p -> RecentRow(vm, p) }
        }
    }
}

@Composable
private fun FlowRowCompat(feats: List<Pair<String, String>>) {
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        feats.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (t, e) ->
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(DZ.card).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(e, fontSize = 16.sp); Spacer(Modifier.width(8.dp)); Text(t, color = DZ.text, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, sub: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 10.dp)) {
        Text(title, color = DZ.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (sub.isNotEmpty()) Text(sub, color = DZ.sub, fontSize = 12.5.sp)
    }
}

@Composable
private fun BigAction(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, colors: List<Color>, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(colors)).clickable(onClick = onClick).padding(16.dp).heightIn(min = 104.dp), verticalArrangement = Arrangement.SpaceBetween) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        Column { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(sub, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp) }
    }
}

@Composable
private fun TemplateCard(t: TemplateDef, onClick: () -> Unit) {
    val accent = Color(t.accent)
    Column(
        Modifier.width(158.dp).clip(RoundedCornerShape(22.dp)).background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.35f), DZ.card))).clickable(onClick = onClick).padding(12.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(16.dp)).background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) {
            if (t.phone) PhoneGlyph(accent, phoneAnimById(t.anim).code, Modifier.fillMaxSize().padding(8.dp))
            else Text(t.emoji, fontSize = 52.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(t.name, color = DZ.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        Text(t.blurb, color = DZ.sub, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text("${t.ratio} · ${transitionById(t.transition).name}", color = accent, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RecentRow(vm: EditorVM, p: Project) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).clip(RoundedCornerShape(16.dp)).background(DZ.card).clickable { vm.openProject(p) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Movie, null, tint = DZ.violet)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, color = DZ.text, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${p.clips.size} clips · ${p.durationMs / 1000}s · ${p.ratio}${if (p.phone.enabled) " · phone" else ""}", color = DZ.sub, fontSize = 12.sp)
        }
        IconButton(onClick = { vm.deleteProject(p.id) }) { Icon(Icons.Rounded.Delete, null, tint = DZ.sub) }
    }
}
