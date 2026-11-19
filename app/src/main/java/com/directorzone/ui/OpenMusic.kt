package com.directorzone.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.directorzone.engine.OpenAudio

/** Streams a short preview of an online track (the same player object as the built-in sound previews). */
object OnlinePreview {
    private var mp: MediaPlayer? = null
    var playing by mutableStateOf<String?>(null); private set
    var loading by mutableStateOf<String?>(null); private set
    fun toggle(t: OpenAudio.Track, onError: () -> Unit) {
        if (playing == t.id || loading == t.id) { stop(); return }
        stop(); Previewer.stop()
        loading = t.id
        try {
            val p = MediaPlayer()
            mp = p
            p.setDataSource(t.url)
            p.setOnPreparedListener { if (mp === p) { loading = null; playing = t.id; p.start() } }
            p.setOnCompletionListener { if (mp === p) stop() }
            p.setOnErrorListener { _, _, _ -> if (mp === p) { stop(); onError() }; true }
            p.prepareAsync()
        } catch (e: Exception) { stop(); onError() }
    }
    fun stop() { try { mp?.stop() } catch (_: Exception) {}; try { mp?.release() } catch (_: Exception) {}; mp = null; playing = null; loading = null }
}

private val QUICK = listOf("Cinematic", "Upbeat", "Chill", "Lo-fi", "Ambient", "Epic", "Piano", "Acoustic", "Electronic", "Hip hop", "Rock", "Jazz", "Happy", "Sad")

@Composable
fun OpenMusicTab(vm: EditorVM, sfx: Boolean, modifier: Modifier = Modifier) {
    var text by remember(sfx) { mutableStateOf("") }
    var query by remember(sfx) { mutableStateOf(if (sfx) "" else "cinematic") }
    var chip by remember(sfx) { mutableStateOf(if (sfx) "All" else "Cinematic") }
    var tracks by remember(sfx) { mutableStateOf<List<OpenAudio.Track>>(emptyList()) }
    var page by remember(sfx) { mutableIntStateOf(1) }
    var more by remember(sfx) { mutableStateOf(false) }
    var busy by remember(sfx) { mutableStateOf(false) }
    var error by remember(sfx) { mutableStateOf<String?>(null) }
    var reload by remember(sfx) { mutableIntStateOf(0) }

    LaunchedEffect(query, sfx, page, reload) {
        busy = true; error = null
        try {
            val r = OpenAudio.search(query, sfx, page)
            tracks = if (page == 1) r.tracks else tracks + r.tracks.filter { n -> tracks.none { it.id == n.id } }
            more = r.hasMore
        } catch (e: Exception) { error = "Couldn't reach the free music library. Check your connection and try again." }
        busy = false
    }
    DisposableEffect(Unit) { onDispose { OnlinePreview.stop() } }
    fun run(q: String) { query = q; page = 1; tracks = emptyList() }

    Column(modifier.fillMaxSize()) {
        BasicTextField(text, { text = it }, Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(DZ.card).padding(12.dp),
            singleLine = true, textStyle = TextStyle(color = DZ.text, fontSize = 15.sp), cursorBrush = SolidColor(DZ.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { chip = ""; run(text.trim()) }),
            decorationBox = { inner -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Search, null, tint = DZ.sub, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) { if (text.isEmpty()) Text(if (sfx) "Search free sound effects" else "Search free music", color = DZ.sub, fontSize = 15.sp); inner() }
            } })
        Box(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Chips(if (sfx) listOf("All", "Whoosh", "Impact", "Click", "Nature", "Crowd", "Ambience", "Glitch") else QUICK, chip) { chip = it; text = ""; run(if (it == "All") "" else it) }
        }
        Text("Free to use (Creative Commons / public domain) via Openverse", color = DZ.sub, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp))
        when {
            error != null && tracks.isEmpty() -> Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(error!!, color = DZ.sub, fontSize = 14.sp)
                BigButton("Try again", Icons.Rounded.Refresh, Modifier.fillMaxWidth(), filled = false) { reload++ }
            }
            tracks.isEmpty() && busy -> Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DZ.text, strokeWidth = 2.dp, modifier = Modifier.size(26.dp)) }
            tracks.isEmpty() -> Hint("Nothing found. Try another word.")
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                items(tracks, key = { it.id }) { t ->
                    val on = OnlinePreview.playing == t.id; val ld = OnlinePreview.loading == t.id
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { OnlinePreview.toggle(t) { vm.toast = "Couldn't play the preview" } }.padding(horizontal = 6.dp, vertical = (6 * dens).dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(DZ.card), contentAlignment = Alignment.Center) {
                            if (ld) CircularProgressIndicator(color = DZ.text, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            else Icon(if (on) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = DZ.text)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, color = DZ.text, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text("${t.creator} · ${t.licenseLabel}${if (t.durationMs > 0) " · " + fmtSecs(t.durationMs) else ""}", color = DZ.sub, fontSize = 12.sp, maxLines = 1)
                        }
                        val dl = vm.downloading == t.id
                        Box(Modifier.size(36.dp).clip(CircleShape).background(DZ.card).clickable(enabled = vm.downloading == null) { OnlinePreview.stop(); vm.addOpenTrack(t) }, contentAlignment = Alignment.Center) {
                            if (dl) CircularProgressIndicator(color = DZ.text, strokeWidth = 2.dp, modifier = Modifier.size(18.dp)) else Icon(Icons.Rounded.Add, null, tint = DZ.text)
                        }
                    }
                }
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        if (busy) CircularProgressIndicator(color = DZ.text, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                        else if (more) TextButton(onClick = { page++ }) { Text("Load more", color = DZ.text) }
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceMusicTab(vm: EditorVM, modifier: Modifier = Modifier) {
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importAudios(it) }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BigButton("Add songs from this phone", Icons.Rounded.LibraryMusic, Modifier.fillMaxWidth()) { pick.launch(arrayOf("audio/*")) }
        Text("Opens your phone's file picker. Pick one or several songs from Downloads, your music app's folder, Drive or any app that shares audio. They are placed one after another from the playhead and get a waveform.", color = DZ.sub, fontSize = 13.sp)
        Text("Only add music you own or have the right to use.", color = DZ.sub, fontSize = 12.sp)
    }
}
