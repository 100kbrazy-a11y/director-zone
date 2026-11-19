package com.directorzone.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

private fun Tool.icon(): ImageVector = when (this) {
    Tool.Clip -> Icons.Rounded.ContentCut
    Tool.Filters -> Icons.Rounded.AutoAwesome
    Tool.Adjust -> Icons.Rounded.Tune
    Tool.Transitions -> Icons.Rounded.SwapHoriz
    Tool.Phone -> Icons.Rounded.PhoneIphone
    Tool.Text -> Icons.Rounded.TextFields
    Tool.Stickers -> Icons.Rounded.EmojiEmotions
    Tool.Sound -> Icons.Rounded.MusicNote
    Tool.Canvas -> Icons.Rounded.Crop
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorVM) {
    BackHandler { if (vm.tool != null) vm.tool = null else vm.closeEditor() }
    var showExport by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(70f) }
    val addMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { vm.importMedia(it) }
    val p = vm.project

    Column(Modifier.fillMaxSize().background(DZ.bg).statusBarsPadding().navigationBarsPadding()) {
        // top bar
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.closeEditor() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = DZ.text) }
            Column(Modifier.weight(1f)) {
                Text(p.name, color = DZ.text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 1)
                Text("${fmt(vm.posMs)} / ${fmt(p.durationMs)}  ·  ${p.ratio}", color = DZ.sub, fontSize = 11.sp)
            }
            IconButton(onClick = { vm.undo() }, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = if (vm.canUndo) DZ.text else DZ.card2) }
            IconButton(onClick = { vm.redo() }, enabled = vm.canRedo) { Icon(Icons.AutoMirrored.Rounded.Redo, null, tint = if (vm.canRedo) DZ.text else DZ.card2) }
            GradientButton("Export", Icons.Rounded.FileUpload, enabled = p.clips.isNotEmpty()) { vm.engine.pause(); showExport = true }
            Spacer(Modifier.width(6.dp))
        }
        // preview
        PreviewPane(vm, Modifier.weight(1f).fillMaxWidth())
        // transport
        Row(Modifier.fillMaxWidth().background(DZ.panel).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.togglePlay() }) { Icon(if (vm.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color.White) }
            IconButton(onClick = { vm.seek(0) }) { Icon(Icons.Rounded.SkipPrevious, null, tint = DZ.sub) }
            Spacer(Modifier.weight(1f))
            Icon(Icons.Rounded.ZoomOut, null, tint = DZ.sub, modifier = Modifier.size(18.dp))
            Slider(zoom, { zoom = it }, valueRange = 20f..160f, modifier = Modifier.width(120.dp), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = DZ.violet, inactiveTrackColor = DZ.card2))
            Icon(Icons.Rounded.ZoomIn, null, tint = DZ.sub, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            IconButton(onClick = { addMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) {
                if (vm.importing) CircularProgressIndicator(Modifier.size(20.dp), color = DZ.pink, strokeWidth = 2.dp) else Icon(Icons.Rounded.AddCircle, null, tint = DZ.pink)
            }
        }
        Timeline(vm, zoom, Modifier.height(if (p.sounds.size > 1) 150.dp else 128.dp))
        // active panel
        vm.tool?.let { t ->
            Column(Modifier.fillMaxWidth().heightIn(max = 250.dp).background(DZ.panel).border(1.dp, DZ.line)) {
                Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(t.label, color = DZ.text, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.tool = null }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.Check, null, tint = DZ.mint) }
                }
                Box(Modifier.fillMaxWidth().weight(1f, fill = false).padding(horizontal = 14.dp).padding(bottom = 8.dp)) {
                    when (t) {
                        Tool.Clip -> ClipPanel(vm)
                        Tool.Filters -> FiltersPanel(vm)
                        Tool.Adjust -> AdjustPanel(vm)
                        Tool.Transitions -> TransitionsPanel(vm)
                        Tool.Phone -> PhonePanel(vm)
                        Tool.Text -> TextPanel(vm)
                        Tool.Stickers -> StickerPanel(vm)
                        Tool.Sound -> SoundPanel(vm)
                        Tool.Canvas -> CanvasPanel(vm)
                    }
                }
            }
        }
        // tool rail
        LazyRow(Modifier.fillMaxWidth().background(DZ.bg).padding(vertical = 6.dp), contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            items(Tool.values().toList()) { t ->
                val on = vm.tool == t
                Column(Modifier.width(66.dp).clip(RoundedCornerShape(14.dp)).background(if (on) DZ.violet.copy(alpha = 0.25f) else Color.Transparent).clickable { vm.tool = if (on) null else t }.padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.icon(), null, tint = if (on) Color.White else DZ.sub, modifier = Modifier.size(22.dp))
                    Text(t.label, color = if (on) Color.White else DZ.sub, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }

    vm.toast?.let { msg ->
        LaunchedEffect(msg) { kotlinx.coroutines.delay(2200); vm.toast = null }
        Box(Modifier.fillMaxSize().padding(bottom = 140.dp), contentAlignment = Alignment.BottomCenter) {
            Text(msg, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(50)).background(DZ.card2).padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 13.sp)
        }
    }
    if (showExport) ExportSheet(vm) { showExport = false }
}
