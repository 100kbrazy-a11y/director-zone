package com.directorzone.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.transformer.CompositionPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.directorzone.engine.ProjectBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Live preview: the exact same Composition that gets exported, rendered at lower resolution. */
@Composable
fun PreviewPane(vm: EditorVM, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val p = vm.project
    var player by remember { mutableStateOf<CompositionPlayer?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(p.clips, p.texts, p.sounds, p.phone, p.ratio, p.fit, p.autoSfx) {
        if (p.clips.isEmpty()) { player?.release(); player = null; return@LaunchedEffect }
        delay(350)
        busy = true; failed = null
        try {
            val comp = withContext(Dispatchers.Default) { ProjectBuilder.build(ctx, p, 540, 30) }
            val np = CompositionPlayer.Builder(ctx).build()
            np.setComposition(comp)
            np.repeatMode = Player.REPEAT_MODE_OFF
            np.prepare()
            np.seekTo(vm.posMs.coerceAtMost((p.durationMs - 50).coerceAtLeast(0)))
            np.playWhenReady = vm.playing
            val old = player
            player = np
            old?.release()
        } catch (e: Exception) {
            failed = e.message ?: "Preview failed"
        }
        busy = false
    }
    DisposableEffect(Unit) { onDispose { player?.release(); player = null } }

    val pl = player
    LaunchedEffect(vm.playing, pl) { pl?.playWhenReady = vm.playing }
    LaunchedEffect(vm.scrubTick, pl) { pl?.seekTo(vm.posMs) }
    LaunchedEffect(pl, vm.playing) {
        while (pl != null && vm.playing) {
            vm.posMs = pl.currentPosition.coerceAtLeast(0)
            if (pl.playbackState == Player.STATE_ENDED || vm.posMs >= vm.project.durationMs - 30) {
                vm.playing = false; pl.playWhenReady = false; vm.posMs = 0; pl.seekTo(0)
            }
            delay(33)
        }
    }

    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (pl != null) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShutterBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { it.player = pl },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (p.clips.isEmpty()) {
            Text("Add media to start", color = DZ.sub)
        }
        if (busy) CircularProgressIndicator(Modifier.size(26.dp), color = DZ.pink, strokeWidth = 2.5.dp)
        failed?.let { Text("Preview: $it", color = DZ.pink, modifier = Modifier.padding(16.dp)) }
        if (pl != null && !vm.playing) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable { vm.playing = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        } else if (pl != null) {
            Box(Modifier.fillMaxSize().clickable { vm.playing = false })
        }
    }
}
