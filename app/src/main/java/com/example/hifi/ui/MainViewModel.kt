package com.example.hifi.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hifi.HiFiApp
import com.example.hifi.data.Library
import com.example.hifi.music.Album
import com.example.hifi.music.SOURCE_LOCAL
import com.example.hifi.music.SOURCE_SPOTIFY
import com.example.hifi.music.Track
import com.example.hifi.playback.PlaybackState
import com.example.hifi.spotify.SpotifyApi
import com.example.hifi.usb.UsbOutput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

enum class Screen { NowPlaying, Library, Sources }

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val hifi = app as HiFiApp
    private val repo = hifi.repository
    private val local = hifi.playback
    private val spotify = hifi.spotifyPlayback
    private val auth = hifi.spotifyAuth

    // ---- navigation / browsing -------------------------------------------

    private val _screen = MutableStateFlow(Screen.NowPlaying)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _browse = MutableStateFlow(SOURCE_LOCAL)
    val browse: StateFlow<String> = _browse.asStateFlow()

    private val localLibrary = MutableStateFlow(Library())
    private val spotifyLibrary = MutableStateFlow(Library())

    val library: StateFlow<Library> = combine(_browse, localLibrary, spotifyLibrary) { b, l, s ->
        if (b == SOURCE_SPOTIFY) s else l
    }.stateIn(viewModelScope, SharingStarted.Eagerly, Library())

    private val loadingLocal = MutableStateFlow(false)
    private val loadingSpotify = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = combine(_browse, loadingLocal, loadingSpotify) { b, l, s ->
        if (b == SOURCE_SPOTIFY) s else l
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = combine(_status, spotify.error) { a, b -> a ?: b }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun dismissStatus() {
        _status.value = null
        spotify.clearError()
    }

    fun navigate(s: Screen) { _screen.value = s }
    fun setBrowse(source: String) { _browse.value = source }

    // ---- spotify account --------------------------------------------------

    val spotifyConfigured: Boolean get() = auth.isConfigured
    val spotifyConnected: StateFlow<Boolean> = auth.connected

    fun connectSpotify() {
        if (!auth.startLogin()) {
            _status.value = "Spotify Client ID is not set. See the README for setup."
        }
    }

    fun disconnectSpotify() {
        spotify.detach()
        auth.logout()
        hifi.spotifyProvider.invalidate()
        spotifyLibrary.value = Library()
        if (_browse.value == SOURCE_SPOTIFY) _browse.value = SOURCE_LOCAL
        if (_active.value == SOURCE_SPOTIFY) _active.value = SOURCE_LOCAL
    }

    fun onSpotifyCallback(uri: Uri) {
        if (!auth.isCallback(uri)) return
        viewModelScope.launch {
            val error = auth.handleCallback(uri)
            if (error != null) {
                _status.value = error
            } else {
                _browse.value = SOURCE_SPOTIFY
                _screen.value = Screen.Library
                refreshSpotify()
            }
        }
    }

    // ---- library loading --------------------------------------------------

    fun refresh() {
        viewModelScope.launch {
            loadingLocal.value = true
            localLibrary.value = runCatching { repo.load(repo.local) }.getOrDefault(Library())
            loadingLocal.value = false
        }
        refreshSpotify()
    }

    fun refreshSpotify() {
        if (!auth.isConfigured || !auth.connected.value) return
        viewModelScope.launch {
            loadingSpotify.value = true
            try {
                hifi.spotifyProvider.invalidate()
                spotifyLibrary.value = repo.load(repo.spotify)
            } catch (e: SpotifyApi.ApiException) {
                _status.value = e.message
            } catch (e: IOException) {
                _status.value = "Can't reach Spotify. Check your connection."
            } finally {
                loadingSpotify.value = false
            }
        }
    }

    fun recent(): List<Track> = repo.recentTracks(localLibrary.value.tracks)

    suspend fun albumTracks(album: Album): List<Track> =
        if (_browse.value == SOURCE_SPOTIFY) safe { hifi.spotifyProvider.getAlbumTracks(album) }
        else localLibrary.value.tracks.filter { it.albumId == album.id }
            .sortedBy { it.trackNumber ?: Int.MAX_VALUE }

    suspend fun artistTracks(name: String): List<Track> =
        library.value.tracks.filter { it.artist == name || it.artist?.substringBefore(", ") == name }

    suspend fun playlistTracks(id: String): List<Track> =
        safe { hifi.spotifyProvider.getPlaylistTracks(id) }

    private suspend fun safe(block: suspend () -> List<Track>): List<Track> = try {
        block()
    } catch (e: SpotifyApi.ApiException) {
        _status.value = e.message
        emptyList()
    } catch (e: IOException) {
        _status.value = "Can't reach Spotify. Check your connection."
        emptyList()
    }

    // ---- playback (routes to the local player or Spotify) ----------------

    private val _active = MutableStateFlow(SOURCE_LOCAL)
    val activeSource: StateFlow<String> = _active.asStateFlow()
    private val isSpotify get() = _active.value == SOURCE_SPOTIFY

    val playbackState: StateFlow<PlaybackState> = _active
        .flatMapLatest { if (it == SOURCE_SPOTIFY) spotify.state else local.state }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackState())

    val usb: StateFlow<UsbOutput?> = hifi.usb.output

    /** Current track; local files get technical metadata read on demand. */
    val currentTrack: StateFlow<Track?> = playbackState
        .map { it.currentTrackId }
        .distinctUntilChanged()
        .mapLatest { id ->
            val t = (if (isSpotify) spotify.trackFor(id) else local.trackFor(id))
                ?: localLibrary.value.tracks.firstOrNull { it.id == id }
            if (t != null && t.source == SOURCE_LOCAL) hifi.metadata.enrich(t) else t
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        local.connect()
        hifi.usb.start()
    }

    fun play(tracks: List<Track>, index: Int) {
        if (tracks.isEmpty()) return
        if (tracks[index].source == SOURCE_SPOTIFY) {
            local.pause()
            _active.value = SOURCE_SPOTIFY
            spotify.play(tracks, index)
        } else {
            spotify.detach()
            _active.value = SOURCE_LOCAL
            local.play(tracks, index)
        }
        _screen.value = Screen.NowPlaying
    }

    fun togglePlayPause() = if (isSpotify) spotify.togglePlayPause() else local.togglePlayPause()
    fun next() = if (isSpotify) spotify.next() else local.next()
    fun previous() = if (isSpotify) spotify.previous() else local.previous()
    fun seekTo(ms: Long) = if (isSpotify) spotify.seekTo(ms) else local.seekTo(ms)
    fun toggleShuffle() = if (isSpotify) spotify.toggleShuffle() else local.toggleShuffle()
    fun cycleRepeat() = if (isSpotify) spotify.cycleRepeat() else local.cycleRepeat()
    fun setVolume(v: Float) = if (isSpotify) spotify.setVolume(v) else local.setVolume(v)

    val providers get() = repo.providers

    override fun onCleared() {
        hifi.usb.stop()
        local.release()
    }
}
