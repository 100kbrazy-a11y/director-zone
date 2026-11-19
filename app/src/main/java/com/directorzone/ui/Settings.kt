package com.directorzone.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directorzone.data.Prefs

private fun mb(b: Long) = if (b >= 1L shl 30) "%.1f GB".format(b / 1073741824.0) else "%.0f MB".format(b / 1048576.0)

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = DZ.card, title = { Text(title, color = DZ.text) },
        text = {
            Column {
                options.forEach { (v, label) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(v); onDismiss() }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(v == selected, { onPick(v); onDismiss() }, colors = RadioButtonDefaults.colors(selectedColor = DZ.accent, unselectedColor = DZ.sub))
                        Text(label, color = DZ.text, fontSize = 16.sp)
                    }
                }
            }
        }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Close", color = DZ.sub) } })
}

@Composable
fun SettingsScreen(vm: EditorVM) {
    BackHandler { vm.showSettings = false }
    var dlg by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.refreshAssets() }
    val st = vm.storage
    val pal = paletteById(Prefs.theme)
    Column(Modifier.fillMaxSize().background(DZ.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showSettings = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = DZ.text) }
            Text("Settings", color = DZ.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            SettingGroup("Appearance") {
                SettingRow("Themes", pal.name, trailing = { Box(Modifier.size(22.dp).clip(CircleShape).background(Brush.linearGradient(listOf(pal.accent, pal.bg)))) }, onClick = { vm.showThemes = true })
                HorizontalDivider(color = DZ.line)
                SettingRow("Interface Density", if (Prefs.compact) "Compact" else "Comfortable", onClick = { dlg = "density" })
                HorizontalDivider(color = DZ.line)
                SettingRow("Animation Quality", listOf("Off", "Standard", "High")[Prefs.animQuality.coerceIn(0, 2)], onClick = { dlg = "anim" })
                HorizontalDivider(color = DZ.line)
                SettingRow("Haptics", trailing = { DzSwitch(Prefs.haptics) { Prefs.put("haptics", it) } })
            }
            SettingGroup("Playback") {
                SettingRow("Preview Resolution", "${Prefs.previewSide}p", subtitle = "Lower is smoother on slower devices. Takes effect next time a project opens.", onClick = { dlg = "proxy" })
                HorizontalDivider(color = DZ.line)
                SettingRow("Preview Frame Rate", "${Prefs.previewFps} fps", onClick = { dlg = "pfps" })
                HorizontalDivider(color = DZ.line)
                SettingRow("Loop Playback", trailing = { DzSwitch(Prefs.loop) { Prefs.put("loop", it) } })
            }
            SettingGroup("Editing") {
                SettingRow("Default Photo Duration", "%.1fs".format(Prefs.defaultClipMs / 1000f), onClick = { dlg = "clipms" })
                HorizontalDivider(color = DZ.line)
                SettingRow("Magnetic Timeline Snapping", trailing = { DzSwitch(Prefs.snapping) { Prefs.put("snap", it) } })
                HorizontalDivider(color = DZ.line)
                SettingRow("Auto-Save", subtitle = "Saves the project a moment after every change", trailing = { DzSwitch(Prefs.autoSave) { Prefs.put("autosave", it) } })
                HorizontalDivider(color = DZ.line)
                SettingRow("Default Transition Duration", "%.1fs".format(Prefs.defaultTransMs / 1000f), onClick = { dlg = "transms" })
            }
            SettingGroup("Export") {
                SettingRow("Default Resolution", when (Prefs.exportSide) { 720 -> "720p"; 1080 -> "1080p"; 1440 -> "2K"; else -> "4K" }, onClick = { dlg = "exside" })
                HorizontalDivider(color = DZ.line)
                SettingRow("Default Frame Rate", "${Prefs.exportFps} fps", onClick = { dlg = "exfps" })
                HorizontalDivider(color = DZ.line)
                SettingRow("Default Codec", if (Prefs.exportHevc) "HEVC (H.265)" else "H.264", onClick = { dlg = "excodec" })
                HorizontalDivider(color = DZ.line)
                SettingRow("Default Bitrate", "${Prefs.exportMbps} Mbps", onClick = { dlg = "exmbps" })
                HorizontalDivider(color = DZ.line)
                SettingRow("Safe decoding", subtitle = "Decodes clips in software while exporting. Slower, but fixes green flashing frames on phones whose hardware decoder misbehaves", trailing = { DzSwitch(Prefs.safeDecode) { Prefs.put("safedecode", it) } })
            }
            SettingGroup("Storage & Cache") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Cache", color = DZ.text, fontSize = 16.sp)
                            Text("${mb(st.cache)} cache · ${mb(st.media)} media · ${mb(st.projects)} projects · ${mb(st.free)} free", color = DZ.sub, fontSize = 12.sp)
                        }
                        Text("Clear Cache", color = DZ.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(DZ.accent).clickable { vm.clearCache() }.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(DZ.card2)) {
                        Box(Modifier.fillMaxWidth(((st.cache + st.media + st.projects).toFloat() / (st.cache + st.media + st.projects + st.free).coerceAtLeast(1)).coerceIn(0.01f, 1f)).fillMaxHeight().background(DZ.accent))
                    }
                }
                HorizontalDivider(color = DZ.line)
                SettingRow("Remove Unused Media", subtitle = "Deletes imported files that no project uses", onClick = { vm.removeUnusedMedia() })
            }
            Text("Hardware decoding and encoding are used automatically whenever the device supports them.", color = DZ.sub, fontSize = 12.sp, modifier = Modifier.padding(18.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
    when (dlg) {
        "density" -> ChoiceDialog("Interface Density", listOf(false to "Comfortable", true to "Compact"), Prefs.compact, { dlg = "" }) { Prefs.put("density", if (it) "compact" else "comfortable") }
        "anim" -> ChoiceDialog("Animation Quality", listOf(0 to "Off", 1 to "Standard", 2 to "High"), Prefs.animQuality, { dlg = "" }) { Prefs.put("anim", it) }
        "proxy" -> ChoiceDialog("Preview Resolution", listOf(360 to "360p (fastest)", 540 to "540p (balanced)", 720 to "720p (sharpest)"), Prefs.previewSide, { dlg = "" }) { Prefs.put("proxy", it) }
        "pfps" -> ChoiceDialog("Preview Frame Rate", listOf(24 to "24 fps", 30 to "30 fps"), Prefs.previewFps, { dlg = "" }) { Prefs.put("pfps", it) }
        "clipms" -> ChoiceDialog("Default Photo Duration", listOf(2000 to "2.0 s", 3000 to "3.0 s", 4000 to "4.0 s", 5000 to "5.0 s"), Prefs.defaultClipMs, { dlg = "" }) { Prefs.put("clipms", it) }
        "transms" -> ChoiceDialog("Default Transition Duration", listOf(300 to "0.3 s", 600 to "0.6 s", 800 to "0.8 s", 1000 to "1.0 s"), Prefs.defaultTransMs, { dlg = "" }) { Prefs.put("transms", it) }
        "exside" -> ChoiceDialog("Default Resolution", listOf(720 to "720p", 1080 to "1080p", 1440 to "2K", 2160 to "4K (if the device supports it)"), Prefs.exportSide, { dlg = "" }) { Prefs.put("exside", it) }
        "exfps" -> ChoiceDialog("Default Frame Rate", listOf(24 to "24 fps", 30 to "30 fps", 60 to "60 fps"), Prefs.exportFps, { dlg = "" }) { Prefs.put("exfps", it) }
        "excodec" -> ChoiceDialog("Default Codec", listOf(false to "H.264 (most compatible)", true to "HEVC / H.265 (smaller files)"), Prefs.exportHevc, { dlg = "" }) { Prefs.put("exhevc", it) }
        "exmbps" -> ChoiceDialog("Default Bitrate", listOf(6 to "6 Mbps", 12 to "12 Mbps", 20 to "20 Mbps", 35 to "35 Mbps", 50 to "50 Mbps"), Prefs.exportMbps, { dlg = "" }) { Prefs.put("exmbps", it) }
    }
}

@Composable
fun ThemesScreen(vm: EditorVM) {
    BackHandler { vm.showThemes = false }
    var sel by remember { mutableStateOf(Prefs.theme) }
    val pal = paletteById(sel)
    Column(Modifier.fillMaxSize().background(DZ.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.showThemes = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = DZ.text) }
            Text("Themes", color = DZ.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            // ---- live preview: a miniature editor drawn with the *selected* palette
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(DZ.panel).border(1.dp, DZ.line, RoundedCornerShape(20.dp)).padding(14.dp)) {
                Text("Live Preview", color = DZ.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(pal.bg).border(1.dp, pal.text.copy(alpha = 0.1f), RoundedCornerShape(14.dp)).padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Cinematic VLOG_04", color = pal.text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text("Export", color = pal.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(50)).background(pal.accent).padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(8.dp)).background(Brush.linearGradient(listOf(pal.card2, pal.card, pal.accent.copy(alpha = 0.35f)))), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(30.dp).clip(CircleShape).border(1.5.dp, pal.text, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.PlayArrow, null, tint = pal.text, modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.weight(2f).height(24.dp).clip(RoundedCornerShape(5.dp)).background(pal.card2))
                        Box(Modifier.weight(1.4f).height(24.dp).clip(RoundedCornerShape(5.dp)).background(pal.card2).border(1.5.dp, pal.text, RoundedCornerShape(5.dp)))
                        Box(Modifier.weight(2.2f).height(24.dp).clip(RoundedCornerShape(5.dp)).background(pal.card2))
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.weight(1f).height(14.dp).clip(RoundedCornerShape(5.dp)).background(pal.accent.copy(alpha = 0.55f)))
                        Box(Modifier.weight(2f).height(14.dp).clip(RoundedCornerShape(5.dp)).background(pal.accent2.copy(alpha = 0.45f)))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        listOf("Media", "Audio", "Text", "Effects").forEach { Text(it, color = pal.sub, fontSize = 9.sp) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(if (sel == Prefs.theme) "Active · ${pal.name}" else "Previewing · ${pal.name}", color = pal.text, fontSize = 12.sp, modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(50)).background(pal.card).padding(horizontal = 12.dp, vertical = 5.dp))
            }
            Text("CHOOSE THEME", color = DZ.sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp, start = 4.dp))
            Grid(PALETTES, 3, 8.dp) { p ->
                val on = p.id == sel
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DZ.card).border(if (on) 2.dp else 1.dp, if (on) DZ.text else DZ.line, RoundedCornerShape(14.dp)).clickable { sel = p.id }.padding(vertical = 12.dp, horizontal = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp)) {
                        Box(Modifier.fillMaxSize().clip(CircleShape).background(p.accent))
                        Box(Modifier.offset(x = 11.dp).fillMaxSize().clip(CircleShape).background(DZ.card))
                        Box(Modifier.size(22.dp).align(Alignment.CenterStart).offset(x = 1.dp).clip(CircleShape).background(p.bg).border(1.dp, p.text.copy(alpha = 0.25f), CircleShape))
                        if (on) Icon(Icons.Rounded.CheckCircle, null, tint = DZ.text, modifier = Modifier.align(Alignment.TopEnd).offset(x = 8.dp, y = (-8).dp).size(18.dp))
                    }
                    Text(p.name, color = DZ.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                    Text(THEME_DESC[p.id] ?: "", color = DZ.sub, fontSize = 10.sp, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center, lineHeight = 12.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Box(Modifier.fillMaxWidth().padding(14.dp)) {
            BigButton(if (sel == Prefs.theme) "Applied" else "Apply Theme", null, Modifier.fillMaxWidth(), filled = sel != Prefs.theme) { Prefs.put("theme", sel); vm.showThemes = false }
        }
    }
}
