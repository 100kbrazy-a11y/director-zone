package com.directorzone.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(vm: EditorVM, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val o = vm.exportOpts
    val ex = vm.export
    ModalBottomSheet(onDismissRequest = { if (!ex.running) onDismiss() }, containerColor = DZ.panel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Export", color = DZ.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            when {
                ex.running -> {
                    Text("Rendering ${(ex.progress * 100).toInt()}% — keep the app open", color = DZ.sub, fontSize = 13.sp)
                    LinearProgressIndicator(progress = { ex.progress }, Modifier.fillMaxWidth().height(8.dp), color = DZ.pink, trackColor = DZ.card2)
                    Pill("Cancel", false) { vm.cancelExport() }
                }
                ex.file != null -> {
                    Text("Done 🎉  ${ex.file.length() / 1024 / 1024} MB", color = DZ.mint, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientButton("Save to gallery", Icons.Rounded.Download) { vm.saveExport() }
                        GradientButton("Share", Icons.Rounded.Share) {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", ex.file)
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share video"))
                        }
                    }
                }
                else -> {
                    ex.error?.let { Text("Export failed: $it", color = DZ.pink, fontSize = 12.sp) }
                    SectionLabel("Resolution")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf(720 to "720p", 1080 to "1080p", 1440 to "2K")) { (v, n) -> Pill(n, o.shortSide == v) { vm.exportOpts = o.copy(shortSide = v) } } }
                    SectionLabel("Frame rate")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(listOf(24, 30, 60)) { v -> Pill("$v fps", o.fps == v) { vm.exportOpts = o.copy(fps = v) } } }
                    LabeledSlider("Bitrate", o.bitrateMbps.toFloat(), 4f..40f, { "${it.toInt()} Mbps" }) { v -> vm.exportOpts = o.copy(bitrateMbps = v.toInt()) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("HEVC (smaller files, newer phones)", color = DZ.sub, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Switch(o.hevc, { vm.exportOpts = o.copy(hevc = it) })
                    }
                    Spacer(Modifier.height(4.dp))
                    GradientButton("Render video", Icons.Rounded.Movie, Modifier.fillMaxWidth()) { vm.startExport() }
                }
            }
        }
    }
}
