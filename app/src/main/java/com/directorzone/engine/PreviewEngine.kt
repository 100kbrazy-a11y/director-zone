package com.directorzone.engine

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.transformer.CompositionPlayer
import com.directorzone.data.Project
import kotlinx.coroutines.*

/**
 * Owns the ONE preview player. The preview plays the same Composition that export uses.
 *
 * Single clock: [timelineMs] is authoritative. While playing it is derived from player.currentPosition,
 * while paused / scrubbing it is whatever the user asked for and the player is told to seek there.
 */
class PreviewEngine(private val ctx: Context, private val scope: CoroutineScope) {
    companion object { const val TAG = "DZ" }

    var player by mutableStateOf<CompositionPlayer?>(null); private set
    var isPlaying by mutableStateOf(false); private set
    var building by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var frameRendered by mutableStateOf(false); private set
    /** authoritative playhead */
    var timelineMs by mutableStateOf(0L); private set
    var durationMs = 0L; private set
    /** loop comes from Settings > Playback; previewShortSide (0 = use the Settings proxy resolution) is only overridden by the self-test */
    var previewShortSide = 0
    private val loop get() = com.directorzone.data.Prefs.loop
    /** the PlayerView currently showing the player (used by the self-test to grab real frames) */
    var hostView: androidx.media3.ui.PlayerView? = null

    /** bumped every time a frame has been drawn for the current player (diagnostics / tests) */
    var framesRenderedSignals = 0; private set

    private var buildJob: Job? = null
    private var tickJob: Job? = null
    private var wantPlay = false
    private var lastSeekAt = 0L
    private var seekJob: Job? = null
    private var gen = 0
    private var lastProject: Project? = null
    private var autoRetries = 0
    private var seekInFlight = false
    private var pendingSeek = -1L
    private var seekWatch: Job? = null

    // ---- loading ---------------------------------------------------------------------------------
    /** (Re)builds the composition. Cheap to call repeatedly: debounced; keeps playhead + play state. */
    /** never park the player within the last ~120 ms: audio EOS with no buffer delivered crashes Media3's AudioGraphInputAudioSink */
    private fun endClamp(d: Long) = (d - 120).coerceAtLeast(0)

    fun load(p: Project, debounceMs: Long = 250) {
        buildJob?.cancel()
        lastProject = p
        durationMs = p.durationMs
        if (p.clips.isEmpty()) { release(); return }
        buildJob = scope.launch {
            delay(debounceMs)
            building = true
            try {
                val t0 = SystemClock.elapsedRealtime()
                val comp = withContext(Dispatchers.Default) { ProjectBuilder.build(ctx, p, if (previewShortSide > 0) previewShortSide else com.directorzone.data.Prefs.previewSide, com.directorzone.data.Prefs.previewFps) }
                val resumeAt = timelineMs.coerceIn(0, endClamp(p.durationMs))
                val resume = wantPlay
                val myGen = ++gen
                seekInFlight = false; pendingSeek = -1L; seekWatch?.cancel()
                val np = CompositionPlayer.Builder(ctx).build()
                np.addListener(listener(np, myGen))
                np.setComposition(comp)
                np.prepare()
                np.seekTo(resumeAt)
                np.playWhenReady = resume
                val old = player
                frameRendered = false
                error = null
                player = np
                Log.i(TAG, "built composition in ${SystemClock.elapsedRealtime() - t0}ms clips=${p.clips.size} dur=${p.durationMs} resumeAt=$resumeAt play=$resume")
                if (old != null) scope.launch { delay(400); try { old.release() } catch (_: Exception) {} }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
                Log.e(TAG, "build failed", e)
            } finally { building = false }
        }
    }

    private fun listener(pl: CompositionPlayer, myGen: Int) = object : Player.Listener {
        override fun onPlayerError(e: PlaybackException) {
            if (myGen != gen) return
            Log.e(TAG, "PLAYER ERROR ${e.errorCodeName}: ${e.message}", e)
            isPlaying = false
            // a failed player never recovers on its own: rebuild it automatically a couple of times before showing the error
            val lp = lastProject
            if (lp != null && autoRetries < 2) {
                autoRetries++
                scope.launch { delay(350); if (myGen == gen) load(lp, 0) }
            } else error = "${e.errorCodeName}: ${e.message}"
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (myGen != gen) return
            if (state == Player.STATE_READY) finishSeek()
            Log.i(TAG, "state=${stateName(state)} pos=${pl.currentPosition} dur=${pl.duration}")
            if (state == Player.STATE_ENDED) {
                if (loop) { pl.seekTo(0); pl.playWhenReady = true } else { wantPlay = false; isPlaying = false; timelineMs = durationMs.coerceAtLeast(0) }
            }
        }
        override fun onIsPlayingChanged(playing: Boolean) {
            if (myGen != gen) return
            Log.i(TAG, "isPlaying=$playing pos=${pl.currentPosition}")
            isPlaying = playing
            if (playing) startTicking(pl, myGen)
        }
        override fun onRenderedFirstFrame() {
            if (myGen != gen) return
            frameRendered = true; framesRenderedSignals++; autoRetries = 0
            Log.i(TAG, "FIRST_FRAME pos=${pl.currentPosition}")
        }
        override fun onPositionDiscontinuity(old: Player.PositionInfo, new: Player.PositionInfo, reason: Int) {
            if (myGen != gen) return
            Log.i(TAG, "discontinuity reason=$reason ${old.positionMs}->${new.positionMs}")
        }
    }

    private fun stateName(s: Int) = when (s) { Player.STATE_IDLE -> "IDLE"; Player.STATE_BUFFERING -> "BUFFERING"; Player.STATE_READY -> "READY"; else -> "ENDED" }

    /** while playing: player.currentPosition → timelineMs */
    private fun startTicking(pl: CompositionPlayer, myGen: Int) {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive && myGen == gen && pl.isPlaying) {
                timelineMs = pl.currentPosition.coerceIn(0, durationMs.coerceAtLeast(0))
                delay(16)
            }
        }
    }

    // ---- transport -------------------------------------------------------------------------------
    fun play() {
        val pl = player
        wantPlay = true
        if (pl == null) return
        if (timelineMs >= durationMs - 40) { timelineMs = 0; pl.seekTo(0) }
        pl.playWhenReady = true
        Log.i(TAG, "play() at ${timelineMs}")
    }

    fun pause() {
        wantPlay = false
        val pl = player ?: return
        pl.playWhenReady = false
        timelineMs = pl.currentPosition.coerceIn(0, durationMs.coerceAtLeast(0))
        Log.i(TAG, "pause() at $timelineMs")
    }

    fun toggle() { if (isPlaying || wantPlay) pause() else play() }

    /**
     * User-driven seek (scrub, tap, skip). Updates the single clock immediately. Seeks are sent one at a time: a new one goes out as soon as the
     * previous frame has landed (or after a short timeout), always with the latest position, so the preview follows the finger live while scrubbing
     * instead of being starved by a flood of cancelled seeks.
     */
    fun seek(ms: Long) {
        val target = ms.coerceIn(0, endClamp(durationMs))
        timelineMs = target
        pendingSeek = target
        pumpSeek()
    }

    private fun pumpSeek() {
        val pl = player ?: return
        if (seekInFlight) return
        val t = pendingSeek; if (t < 0) return
        pendingSeek = -1L; seekInFlight = true
        pl.seekTo(t)
        seekWatch?.cancel()
        seekWatch = scope.launch { delay(280); finishSeek() }
    }

    private fun finishSeek() {
        seekWatch?.cancel()
        if (!seekInFlight) return
        seekInFlight = false
        if (pendingSeek >= 0) scope.launch { delay(16); pumpSeek() }
    }

    /** Rebuilds the preview from the current project (used by the "Reload preview" button). */
    fun reload() { val p = lastProject ?: return; autoRetries = 0; error = null; load(p, 0) }

    fun release() {
        buildJob?.cancel(); tickJob?.cancel(); seekJob?.cancel(); seekWatch?.cancel(); seekInFlight = false; pendingSeek = -1L
        gen++
        try { player?.release() } catch (_: Exception) {}
        player = null; isPlaying = false; wantPlay = false
    }

    fun positionNow(): Long = player?.currentPosition ?: -1
    fun stateNow(): String = player?.let { stateName(it.playbackState) } ?: "NONE"
}
