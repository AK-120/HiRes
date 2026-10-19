package com.example.hifi.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.hifi.music.Track
import com.google.common.util.concurrent.ListenableFuture
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

data class PlaybackState(
    val currentTrackId: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val volume: Float = 1f
)

/** UI-facing wrapper around a MediaController connected to PlaybackService. */
class PlaybackManager(
    private val context: Context,
    private val onTrackStarted: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private val queueIndex = HashMap<String, Track>()

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(listener)
            syncAll(c)
        }, context.mainExecutor)
    }

    fun release() {
        ticker?.cancel()
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }

    /** Replace the queue with [tracks] and start at [startIndex]. */
    fun play(tracks: List<Track>, startIndex: Int) {
        val c = controller ?: return
        queueIndex.clear()
        tracks.forEach { queueIndex[it.id] = it }
        c.setMediaItems(tracks.map(::toMediaItem), startIndex, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }

    fun previous() {
        val c = controller ?: return
        // Standard behaviour: restart the track if past 3 seconds, else go back.
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPreviousMediaItem()
    }

    fun seekTo(ms: Long) { controller?.seekTo(ms) }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Player volume (0..1). Device/system volume is controlled by hardware keys. */
    fun setVolume(v: Float) {
        controller?.volume = v.coerceIn(0f, 1f)
        _state.update { it.copy(volume = v.coerceIn(0f, 1f)) }
    }

    fun trackFor(id: String?): Track? = id?.let { queueIndex[it] }

    private fun toMediaItem(t: Track): MediaItem =
        MediaItem.Builder()
            .setMediaId(t.id)
            .setUri(t.contentUri.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setAlbumTitle(t.album)
                    .setArtworkUri(t.artworkUri?.toUri())
                    .build()
            )
            .build()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = syncAll(player)

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.let(onTrackStarted)
        }
    }

    private fun syncAll(p: Player) {
        _state.update {
            it.copy(
                currentTrackId = p.currentMediaItem?.mediaId,
                isPlaying = p.isPlaying,
                positionMs = p.currentPosition.coerceAtLeast(0),
                durationMs = p.duration.takeIf { d -> d > 0 } ?: 0,
                shuffle = p.shuffleModeEnabled,
                repeatMode = p.repeatMode,
                volume = p.volume
            )
        }
        if (p.isPlaying) startTicker() else ticker?.cancel()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                controller?.let { c ->
                    _state.update { it.copy(positionMs = c.currentPosition.coerceAtLeast(0)) }
                }
                delay(500)
            }
        }
    }
}
