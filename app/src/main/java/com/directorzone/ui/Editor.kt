package com.directorzone.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.directorzone.data.Prefs

private class Tool(val label: String, val icon: ImageVector, val sheet: Sheet)
private val TOOLS = listOf(
    Tool("Media", Icons.Rounded.AddBox, Sheet.Media), Tool("Audio", Icons.Rounded.MusicNote, Sheet.Audio), Tool("Text", Icons.Rounded.TextFields, Sheet.Text),
    Tool("Effects", Icons.Rounded.AutoAwesome, Sheet.Effects), Tool("Color", Icons.Rounded.Palette, Sheet.Color), Tool("Transitions", Icons.Rounded.SwapHoriz, Sheet.Transitions),
    Tool("Canvas", Icons.Rounded.Crop, Sheet.Canvas), Tool("More", Icons.Rounded.MoreHoriz, Sheet.More),
)

@Composable
fun EditorScreen(vm: EditorVM) {
    BackHandler {
        when {
            vm.showExport -> { if (!vm.export.running) { vm.showExport = false; vm.engine.load(vm.project, 0) } }
            vm.sheet != null -> vm.sheet = null
            else -> vm.closeEditor()
        }
    }
    var zoom by remember { mutableFloatStateOf(70f) }
    var menu by remember { mutableStateOf<MenuAnchor?>(null) }
    var rename by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    val addMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { vm.importMedia(it) }
    fun pickMedia() = addMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
    val p = vm.project
    val screenH = LocalConfiguration.current.screenHeightDp
    val th = (screenH * 0.30f).coerceIn(150f, 250f)
    val haptic = rememberHaptic()

    Box(Modifier.fillMaxSize().background(DZ.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            // ------------------------------------------------ top bar
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeEditor() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = DZ.text) }
                Text(p.name, color = DZ.text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).clickable { rename = true })
                Spacer(Modifier.width(8.dp))
                Text("${p.ratio} · ${Prefs.exportFps}fps", color = DZ.text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(DZ.card2).padding(horizontal = 7.dp, vertical = 3.dp))
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.undo() }, enabled = vm.canUndo, modifier = Modifier.size(38.dp)) { Icon(Icons.AutoMirrored.Rounded.Undo, null, tint = if (vm.canUndo) DZ.text else DZ.sub.copy(alpha = 0.35f)) }
                IconButton(onClick = { vm.redo() }, enabled = vm.canRedo, modifier = Modifier.size(38.dp)) { Icon(Icons.AutoMirrored.Rounded.Redo, null, tint = if (vm.canRedo) DZ.text else DZ.sub.copy(alpha = 0.35f)) }
                Text("Export", color = if (p.clips.isNotEmpty()) DZ.onAccent else DZ.sub, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(50)).background(if (p.clips.isNotEmpty()) DZ.accent else DZ.card2)
                        .clickable(enabled = p.clips.isNotEmpty()) { vm.openExport() }.padding(horizontal = 16.dp, vertical = 8.dp))
                Box {
                    IconButton(onClick = { overflow = true }, modifier = Modifier.size(38.dp)) { Icon(Icons.Rounded.MoreVert, null, tint = DZ.text) }
                    DropdownMenu(overflow, { overflow = false }, containerColor = DZ.card) {
                        DropdownMenuItem(text = { Text("Rename project", color = DZ.text) }, onClick = { overflow = false; rename = true })
                        DropdownMenuItem(text = { Text(if (p.autoSfx) "Auto whoosh on cuts: on" else "Auto whoosh on cuts: off", color = DZ.text) }, onClick = { overflow = false; vm.edit { it.copy(autoSfx = !it.autoSfx) } })
                        DropdownMenuItem(text = { Text("Settings", color = DZ.text) }, onClick = { overflow = false; vm.engine.pause(); vm.showSettings = true })
                    }
                }
            }
            // ------------------------------------------------ preview
            PreviewPane(vm, Modifier.weight(1f).fillMaxWidth())
            // ------------------------------------------------ transport
            Row(Modifier.fillMaxWidth().height(46.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(fmtTime(vm.posMs).drop(3), color = DZ.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(" / ${fmtTime(p.durationMs).drop(3)}", color = DZ.sub, fontSize = 13.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { vm.seek(prevBoundary(vm)) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.SkipPrevious, null, tint = DZ.text) }
                Box(Modifier.size(42.dp).clip(CircleShape).border(1.5.dp, DZ.text, CircleShape).clickable { vm.togglePlay() }, contentAlignment = Alignment.Center) {
                    Icon(if (vm.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = DZ.text, modifier = Modifier.size(26.dp))
                }
                IconButton(onClick = { vm.seek(nextBoundary(vm)) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.SkipNext, null, tint = DZ.text) }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { Prefs.put("snap", !Prefs.snapping); vm.toast = if (Prefs.snapping) "Snapping on" else "Snapping off"; haptic() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.VerticalAlignCenter, null, tint = if (Prefs.snapping) DZ.text else DZ.sub.copy(alpha = 0.55f), modifier = Modifier.size(22.dp))
                }
                IconButton(onClick = { zoom = (zoom / 1.3f).coerceAtLeast(12f) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.ZoomOut, null, tint = DZ.sub, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { zoom = (zoom * 1.3f).coerceAtMost(320f) }, modifier = Modifier.size(34.dp)) { Icon(Icons.Rounded.ZoomIn, null, tint = DZ.sub, modifier = Modifier.size(20.dp)) }
            }
            // ------------------------------------------------ timeline + toolbar (always laid out, so the preview never resizes)
            Column(Modifier.fillMaxWidth()) {
                Timeline(vm, zoom, onMenu = { a -> openMenu(vm, a)?.let { menu = it } }, onAddMedia = { pickMedia() }, modifier = Modifier.fillMaxWidth().height(th.dp))
                Row(Modifier.fillMaxWidth().height((60 * dens).dp).background(DZ.bg).border(0.5.dp, DZ.line)) {
                    LazyRow(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(0.dp), verticalAlignment = Alignment.CenterVertically) {
                        items(TOOLS) { t ->
                            IconTextButton(t.icon, t.label, Modifier.width(68.dp), tint = DZ.text) { if (t.sheet == Sheet.Media && p.clips.isEmpty()) pickMedia() else openSheet(vm, t.sheet) }
                        }
                    }
                }
            }
        }

        // ------------------------------------------------ bottom sheet: slides over the timeline, the preview keeps its size
        val sh = vm.sheet
        var lastSheet by remember { mutableStateOf(sh) }
        if (sh != null) lastSheet = sh
        AnimatedVisibility(
            sh != null, Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            enter = slideInVertically(tween(Motion.ms(260))) { it } + fadeIn(tween(Motion.ms(160))),
            exit = slideOutVertically(tween(Motion.ms(200))) { it } + fadeOut(tween(Motion.ms(160))),
        ) {
            val h = if (vm.sheetExpanded) (screenH * 0.62f).dp else (th + 60 * dens).dp
            SheetFrame(h, vm.sheetExpanded, { vm.sheetExpanded = it }, { vm.sheet = null }) {
                lastSheet?.let { SheetHost(vm, it, ::pickMedia) }
            }
        }

        // ------------------------------------------------ contextual menu (long press on a clip / text / sound)
        menu?.let { a -> ContextMenu(vm, a) { menu = null } }

        vm.toast?.let { msg ->
            LaunchedEffect(msg) { kotlinx.coroutines.delay(2000); vm.toast = null }
            Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 150.dp), contentAlignment = Alignment.BottomCenter) {
                Text(msg, color = DZ.text, modifier = Modifier.clip(RoundedCornerShape(50)).background(DZ.card2).border(1.dp, DZ.line, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 10.dp), fontSize = 13.sp)
            }
        }
        if (vm.importing) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DZ.text) }
    }

    if (rename) RenameDialog("Rename project", p.name, { rename = false }) { vm.renameCurrent(it) }
}

/** Opens a toolbar sheet; tools that act on a clip first make sure one is selected under the playhead. */
fun openSheet(vm: EditorVM, s: Sheet) {
    if (s == Sheet.Effects || s == Sheet.Color || s == Sheet.Transitions) {
        if (vm.project.clips.isEmpty()) { vm.toast = "Add media first"; return }
        if (vm.clip == null) vm.selClip = clipAt(vm, vm.posMs)?.id
        if (s == Sheet.Transitions) {
            val idx = vm.project.clips.indexOfFirst { it.id == vm.selClip }
            if (idx <= 0 && vm.project.clips.size > 1) vm.selClip = vm.project.clips[1].id
        }
    }
    if (s == Sheet.Text) { if (vm.text == null || vm.text?.sticker == true) vm.selText = vm.project.texts.firstOrNull { !it.sticker && vm.posMs >= it.startMs && vm.posMs < it.startMs + it.durMs }?.id }
    vm.sheetExpanded = false
    vm.sheet = s
}

private fun clipAt(vm: EditorVM, ms: Long) = run {
    var t = 0L; var found: com.directorzone.data.Clip? = null
    for (c in vm.project.clips) { if (ms < t + c.lengthMs) { found = c; break }; t += c.lengthMs }
    found ?: vm.project.clips.lastOrNull()
}
private fun boundaries(vm: EditorVM): List<Long> { var t = 0L; val l = arrayListOf(0L); vm.project.clips.forEach { t += it.lengthMs; l.add(t) }; return l }
private fun prevBoundary(vm: EditorVM): Long = boundaries(vm).filter { it < vm.posMs - 400 }.maxOrNull() ?: 0L
private fun nextBoundary(vm: EditorVM): Long = boundaries(vm).filter { it > vm.posMs + 100 }.minOrNull() ?: vm.project.durationMs

/** Returns the anchor if the track isn't locked; otherwise tells the user. */
private fun openMenu(vm: EditorVM, a: MenuAnchor): MenuAnchor? {
    val locked = when (a.kind) { ItemKind.Clip -> vm.project.lockV; ItemKind.Text -> vm.project.lockT; ItemKind.Sound -> vm.project.lockA }
    if (locked) { vm.toast = "Track is locked"; return null }
    return a
}

@Composable
private fun ContextMenu(vm: EditorVM, a: MenuAnchor, onDismiss: () -> Unit) {
    class Item(val icon: ImageVector, val label: String, val danger: Boolean = false, val run: () -> Unit)
    val items: List<Item> = when (a.kind) {
        ItemKind.Clip -> {
            val img = vm.clip?.isImage == true
            listOf(
                Item(Icons.Rounded.ContentCut, "Trim") { vm.sheetExpanded = false; vm.sheet = Sheet.Trim },
                Item(Icons.Rounded.CallSplit, "Split") { vm.splitAtPlayhead() },
                Item(Icons.Rounded.Speed, "Speed") { if (img) vm.toast = "Images have no speed" else { vm.sheetExpanded = false; vm.sheet = Sheet.Speed } },
                Item(Icons.Rounded.VolumeUp, "Volume") { if (img) vm.toast = "Images have no audio" else { vm.sheetExpanded = false; vm.sheet = Sheet.Volume } },
                Item(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateClip() },
                Item(Icons.Rounded.Delete, "Delete", true) { vm.deleteClip() },
                Item(Icons.Rounded.MoreHoriz, "More") { vm.sheetExpanded = false; vm.sheet = Sheet.ClipMore },
            )
        }
        ItemKind.Text -> listOf(
            Item(Icons.Rounded.Edit, "Edit") { vm.sheetExpanded = false; vm.sheet = if (vm.text?.sticker == true) Sheet.Stickers else Sheet.Text },
            Item(Icons.Rounded.CallSplit, "Split") { vm.splitText() },
            Item(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateText() },
            Item(Icons.Rounded.Delete, "Delete", true) { vm.deleteText() },
        )
        ItemKind.Sound -> listOf(
            Item(Icons.Rounded.VolumeUp, "Volume") { vm.sheetExpanded = false; vm.sheet = Sheet.Audio },
            Item(Icons.Rounded.CallSplit, "Split") { vm.splitSound() },
            Item(Icons.Rounded.ContentCopy, "Duplicate") { vm.duplicateSound() },
            Item(Icons.Rounded.Delete, "Delete", true) { vm.deleteSound() },
        )
    }
    val margin = with(LocalDensity.current) { 8.dp.roundToPx() }
    val top = with(LocalDensity.current) { 56.dp.roundToPx() }
    val provider = remember(a) {
        object : PopupPositionProvider {
            override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                val cx = a.bounds.center.x.toInt()
                val x = (cx - popupContentSize.width / 2).coerceIn(margin, (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin))
                var y = a.bounds.top.toInt() - popupContentSize.height - margin
                if (y < top) y = a.bounds.bottom.toInt() + margin
                if (y + popupContentSize.height > windowSize.height - margin) y = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(top)
                return IntOffset(x, y)
            }
        }
    }
    Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Row(Modifier.shadow(12.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(DZ.card).border(1.dp, DZ.line, RoundedCornerShape(14.dp)).padding(horizontal = 4.dp, vertical = 2.dp)) {
            items.forEach { it0 ->
                IconTextButton(it0.icon, it0.label, Modifier.width(54.dp), tint = if (it0.danger) DZ.danger else DZ.text, size = 20.dp) { onDismiss(); it0.run() }
            }
        }
    }
}

@Composable
fun RenameDialog(title: String, initial: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = DZ.card,
        title = { Text(title, color = DZ.text) },
        text = { OutlinedTextField(v, { v = it }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedTextColor = DZ.text, unfocusedTextColor = DZ.text, focusedBorderColor = DZ.accent, unfocusedBorderColor = DZ.line, cursorColor = DZ.accent)) },
        confirmButton = { TextButton(onClick = { onOk(v); onDismiss() }) { Text("OK", color = DZ.text) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = DZ.sub) } },
    )
}
