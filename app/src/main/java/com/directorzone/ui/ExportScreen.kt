package com.directorzone.ui

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.directorzone.catalog.ratioById
import com.directorzone.data.ExportOptions

@Composable
fun ExportScreen(vm: EditorVM) {
    val ctx = LocalContext.current
    val o = vm.exportOpts
    val ex = vm.export
    val p = vm.project
    val close = { if (!ex.running) { vm.showExport = false; vm.engine.load(vm.project, 0) } }
    BackHandler { close() }
    val r = ratioById(p.ratio)
    val long = (o.shortSide * maxOf(r.w, r.h) / minOf(r.w, r.h)) / 2 * 2
    val wh = if (r.w >= r.h) "$long × ${o.shortSide}" else "${o.shortSide} × $long"
    val sizes = listOf(720 to "720p", 1080 to "1080p", 1440 to "2K", 2160 to "4K")
    val okSize = remember(o.hevc) { sizes.map { vm.encodeSupported(it.first, o.hevc) } }
    val hevcOk = remember { vm.hevcSupported() }
    val estMb = (o.bitrateMbps * (p.durationMs / 1000f) / 8f + 0.12f * (p.durationMs / 1000f) / 8f)
    val free = vm.storage.free
    LaunchedEffect(Unit) { vm.refreshAssets(); p.clips.firstOrNull()?.let { vm.loadThumb(it) } }

    Column(Modifier.fillMaxSize().background(DZ.bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { close() }, enabled = !ex.running) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = DZ.text) }
            Text("Export", color = DZ.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(sizes.first { it.first == o.shortSide }.second + " Master", color = DZ.text, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp).clip(RoundedCornerShape(50)).background(DZ.card2).padding(horizontal = 12.dp, vertical = 5.dp))
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(150.dp).aspectRatio(1.7f).clip(RoundedCornerShape(12.dp)).background(DZ.card)) {
                    p.clips.firstOrNull()?.let { c -> vm.thumbs[c.uri]?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(p.name, color = DZ.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${fmtTime(p.durationMs).drop(3)} · $wh · ${if (o.hevc) "H.265" else "H.264"}", color = DZ.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.height(26.dp))
            when {
                ex.running -> {
                    Text("Rendering ${(ex.progress * 100).toInt()}%", color = DZ.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Keep the app open while the video is being rendered.", color = DZ.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 14.dp))
                    LinearProgressIndicator(progress = { ex.progress }, Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = DZ.accent, trackColor = DZ.card2)
                    Spacer(Modifier.height(20.dp))
                    BigButton("Cancel", null, Modifier.fillMaxWidth(), filled = false) { vm.cancelExport() }
                }
                ex.file != null -> {
                    Text("Your video is ready", color = DZ.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("${"%.1f".format(ex.file.length() / 1048576f)} MB · $wh", color = DZ.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        BigButton("Save to gallery", Icons.Rounded.Download, Modifier.weight(1f)) { vm.saveExport() }
                        BigButton("Share", Icons.Rounded.Share, Modifier.weight(1f), filled = false) {
                            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", ex.file)
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "video/mp4"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share video"))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    BigButton("Back to editing", null, Modifier.fillMaxWidth(), filled = false) { close() }
                }
                else -> {
                    ex.error?.let { Text("Export failed: $it", color = DZ.danger, fontSize = 13.sp, modifier = Modifier.padding(bottom = 12.dp)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Resolution", color = DZ.text, fontSize = 16.sp, modifier = Modifier.width(110.dp))
                        Segmented(sizes.map { it.second }, sizes.indexOfFirst { it.first == o.shortSide }.coerceAtLeast(0), Modifier.weight(1f), enabled = okSize) { i ->
                            val s = sizes[i].first
                            vm.exportOpts = o.copy(shortSide = s, bitrateMbps = when (s) { 720 -> 6; 1080 -> 12; 1440 -> 24; else -> 45 })
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Frame Rate", color = DZ.text, fontSize = 16.sp, modifier = Modifier.width(110.dp))
                        val fps = listOf(24, 30, 60)
                        Segmented(fps.map { "$it" }, fps.indexOf(o.fps).coerceAtLeast(0), Modifier.weight(1f)) { vm.exportOpts = o.copy(fps = fps[it]) }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Format & Codec", color = DZ.text, fontSize = 16.sp, modifier = Modifier.width(110.dp))
                        Segmented(listOf("MP4 · H.264", "MP4 · HEVC"), if (o.hevc) 1 else 0, Modifier.weight(1f), enabled = listOf(true, hevcOk)) { vm.exportOpts = o.copy(hevc = it == 1) }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row { Text("Bitrate", color = DZ.text, fontSize = 16.sp, modifier = Modifier.weight(1f)); Text("${if (o.bitrateMbps < 8) "Standard" else if (o.bitrateMbps < 20) "High" else "Ultra"} · ${o.bitrateMbps} Mbps", color = DZ.sub, fontSize = 14.sp) }
                    Slider(o.bitrateMbps.toFloat(), { vm.exportOpts = o.copy(bitrateMbps = it.toInt()) }, valueRange = 3f..60f, colors = SliderDefaults.colors(thumbColor = DZ.accent, activeTrackColor = DZ.accent, inactiveTrackColor = DZ.card2))
                    val credits = p.sounds.map { it.credit }.filter { it.isNotBlank() }.distinct()
                    if (credits.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(DZ.card).padding(14.dp)) {
                            Text("Music credits", color = DZ.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text("These tracks are Creative Commons licensed and need attribution. Paste this in your video's description.", color = DZ.sub, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp))
                            Text(credits.joinToString("\n"), color = DZ.text, fontSize = 12.sp)
                            TextButton(onClick = {
                                (ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(android.content.ClipData.newPlainText("credits", credits.joinToString("\n")))
                                vm.toast = "Credits copied"
                            }) { Text("Copy credits", color = DZ.accent) }
                        }
                    }
                    if (!okSize[sizes.indexOfFirst { it.first == o.shortSide }.coerceAtLeast(0)]) Text("This device's encoder may not support ${sizes.first { it.first == o.shortSide }.second}; it will fall back to the closest size.", color = DZ.sub, fontSize = 12.sp)
                }
            }
        }
        if (!ex.running && ex.file == null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                    Text("Estimated file size: ${if (estMb >= 1000) "%.1f GB".format(estMb / 1000f) else "%.0f MB".format(estMb)}", color = DZ.sub, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text("Available storage: ${"%.1f".format(free / 1073741824.0)} GB", color = DZ.sub, fontSize = 13.sp)
                }
                BigButton("Export Video", Icons.Rounded.FileUpload, Modifier.fillMaxWidth()) { vm.startExport() }
            }
        }
    }
}
