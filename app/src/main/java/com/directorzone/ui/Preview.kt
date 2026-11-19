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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** Hosts the single engine player. All state (position, play/pause) lives in vm.engine. */
@Composable
fun PreviewPane(vm: EditorVM, modifier: Modifier = Modifier) {
    val eng = vm.engine
    val pl = eng.player
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setKeepContentOnPlayerReset(true)
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            },
            update = { eng.hostView = it; if (it.player !== pl) it.player = pl },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        if (vm.project.clips.isEmpty()) Text("Add media to start", color = DZ.sub)
        if (eng.building) CircularProgressIndicator(Modifier.align(Alignment.TopEnd).padding(10.dp).size(20.dp), color = DZ.pink, strokeWidth = 2.dp)
        eng.error?.let { Text("Preview error: $it", color = DZ.pink, modifier = Modifier.padding(16.dp)) }
        if (pl != null && !eng.isPlaying) {
            Box(Modifier.size(58.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.45f)).clickable { eng.play() }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(34.dp))
            }
        } else if (pl != null) Box(Modifier.fillMaxSize().clickable { eng.pause() })
    }
}
