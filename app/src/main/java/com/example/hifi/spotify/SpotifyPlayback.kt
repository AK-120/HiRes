package com.example.hifi.spotify

import android.os.SystemClock
import androidx.media3.common.Player
import com.example.hifi.music.Track
import com.example.hifi.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONException
import java.io.IOException

/**
 * Remote control for Spotify playback (Spotify Connect). Spotify plays the audio
 * on one of the user's devices; this class sends commands and mirrors the state
 * into the same [PlaybackState] the local player uses.
 */
class SpotifyPlayback(private val api: SpotifyApi) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val queue = HashMap<String, Track>()
    private var loop: Job? = null
    private var volumeJob: Job? = null
    private var basePositionMs = 0L
    private var syncedAt = 0L

    fun trackFor(id: String?): Track? = id?.let { queue[it] }
    fun clearError() { _error.value = null }

    // ---- commands --------------------------------------------------------

    fun play(tracks: List<Track>, index: Int) {
        // Spotify accepts at most 100 URIs per request: send a window around the chosen track.
        val from = maxOf(0, index - 20)
        val window = tracks.subList(from, minOf(tracks.size, from + 100))
        queue.clear()
        window.forEach { queue[it.id] = it }
        val t = tracks[index]
        rebase(0)
        _state.update { it.copy(currentTrackId = t.id, isPlaying = true, positionMs = 0, durationMs = t.durationMs) }
        command { withDevice { id -> api.play(window.map { it.contentUri }, index - from, id) } }
        startLoop()
    }

    fun togglePlayPause() {
        val playing = _state.value.isPlaying
        rebase(_state.value.positionMs)
        _state.update { it.copy(isPlaying = !playing) }
        command { if (playing) api.pause() else withDevice { id -> api.resume(id) } }
    }

    fun next() = command { api.next() }

    fun previous() {
        val pos = _state.value.positionMs
        command { if (pos > 3000) api.seek(0) else api.previous() }
    }

    fun seekTo(ms: Long) {
        rebase(ms)
        _state.update { it.copy(positionMs = ms) }
        command { api.seek(ms) }
    }

    fun toggleShuffle() {
        val on = !_state.value.shuffle
        _state.update { it.copy(shuffle = on) }
        command { api.setShuffle(on) }
    }

    fun cycleRepeat() {
        val (mode, name) = when (_state.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL to "context"
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE to "track"
            else -> Player.REPEAT_MODE_OFF to "off"
        }
        _state.update { it.copy(repeatMode = mode) }
        command { api.setRepeat(name) }
    }

    /** Volume of the Spotify device (0..1). Debounced to avoid flooding the API while dragging. */
    fun setVolume(v: Float) {
        val level = v.coerceIn(0f, 1f)
        _state.update { it.copy(volume = level) }
        volumeJob?.cancel()
        volumeJob = scope.launch {
            delay(300)
            runCmd { api.setVolume((level * 100).toInt()) }
        }
    }

    /** Called when playback moves to the local player. Pauses Spotify if it was playing. */
    fun detach() {
        if (_state.value.isPlaying) {
            _state.update { it.copy(isPlaying = false) }
            scope.launch { runCmd(resync = false) { api.pause() } }
        }
        loop?.cancel()
        loop = null
    }

    // ---- internals -------------------------------------------------------

    private fun command(block: suspend () -> Unit) { scope.launch { runCmd(block = block) } }

    private suspend fun runCmd(resync: Boolean = true, block: suspend () -> Unit) {
        try {
            block()
            _error.value = null
        } catch (e: SpotifyApi.ApiException) {
            _error.value = describe(e)
        } catch (e: IOException) {
            _error.value = "Can't reach Spotify. Check your connection."
        } catch (e: JSONException) {
            _error.value = "Unexpected response from Spotify."
        }
        if (resync) {
            delay(600)
            refresh()
        }
    }

    /** Retries on another device if there is no active one (Spotify returns 404). */
    private suspend fun withDevice(block: suspend (String?) -> Unit) {
        try {
            block(null)
        } catch (e: SpotifyApi.ApiException) {
            if (e.status != 404) throw e
            val devices = api.devices()
            val device = devices.firstOrNull { it.isActive } ?: devices.firstOrNull() ?: throw e
            block(device.id)
        }
    }

    private fun describe(e: SpotifyApi.ApiException): String = when {
        e.reason == "PREMIUM_REQUIRED" -> "Spotify Premium is required to control playback."
        e.status == 404 || e.reason == "NO_ACTIVE_DEVICE" ->
            "No active Spotify device. Open the Spotify app and play any track once, then try again."
        e.status == 401 -> "Spotify session expired. Reconnect in Sources."
        else -> e.message ?: "Spotify error ${e.status}"
    }

    private suspend fun refresh() {
        try {
            applySnapshot(api.playerState())
        } catch (e: SpotifyApi.ApiException) {
            if (e.status == 401) _error.value = describe(e)
        } catch (_: IOException) {
        } catch (_: JSONException) {
        }
    }

    private fun applySnapshot(s: PlayerSnapshot?) {
        if (s == null) {
            _state.update { it.copy(isPlaying = false) }
            return
        }
        s.track?.let { queue.putIfAbsent(it.id, it) }
        rebase(s.progressMs)
        _state.update {
            it.copy(
                currentTrackId = s.track?.id ?: it.currentTrackId,
                isPlaying = s.isPlaying,
                positionMs = s.progressMs,
                durationMs = s.durationMs.takeIf { d -> d > 0 } ?: it.durationMs,
                shuffle = s.shuffle,
                repeatMode = when (s.repeat) {
                    "context" -> Player.REPEAT_MODE_ALL
                    "track" -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                },
                volume = s.volumePercent?.let { v -> v / 100f } ?: it.volume,
                outputName = s.deviceName
            )
        }
    }

    private fun rebase(positionMs: Long) {
        basePositionMs = positionMs
        syncedAt = SystemClock.elapsedRealtime()
    }

    /** Polls Spotify every 3 s and advances the position locally in between. */
    private fun startLoop() {
        if (loop?.isActive == true) return
        loop = scope.launch {
            var n = 0
            while (isActive) {
                if (n % 6 == 0) refresh()
                val s = _state.value
                if (s.isPlaying) {
                    val pos = basePositionMs + (SystemClock.elapsedRealtime() - syncedAt)
                    val capped = if (s.durationMs > 0) minOf(pos, s.durationMs) else pos
                    _state.update { it.copy(positionMs = capped) }
                }
                n++
                delay(500)
            }
        }
    }
}
