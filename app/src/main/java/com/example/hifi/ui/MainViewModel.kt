package com.example.hifi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.hifi.HiFiApp
import com.example.hifi.data.Library
import com.example.hifi.music.Track
import com.example.hifi.usb.UsbOutput
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class Screen { NowPlaying, Library, Sources }

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val hifi = app as HiFiApp
    private val repo = hifi.repository
    private val playback = hifi.playback

    private val _library = MutableStateFlow(Library())
    val library: StateFlow<Library> = _library.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _screen = MutableStateFlow(Screen.NowPlaying)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    val playbackState = playback.state
    val usb: StateFlow<UsbOutput?> = hifi.usb.output

    /** Current track with technical metadata read from the file on demand. */
    val currentTrack: StateFlow<Track?> = playback.state
        .map { it.currentTrackId }
        .distinctUntilChanged()
        .mapLatest { id ->
            val base = playback.trackFor(id) ?: _library.value.tracks.firstOrNull { it.id == id }
            base?.let { hifi.metadata.enrich(it) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        playback.connect()
        hifi.usb.start()
    }

    fun navigate(s: Screen) { _screen.value = s }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _library.value = runCatching { repo.load() }.getOrDefault(Library())
            _loading.value = false
        }
    }

    fun recent(): List<Track> = repo.recentTracks(_library.value.tracks)

    fun play(tracks: List<Track>, index: Int) {
        playback.play(tracks, index)
        _screen.value = Screen.NowPlaying
    }

    val providers get() = repo.providers

    fun togglePlayPause() = playback.togglePlayPause()
    fun next() = playback.next()
    fun previous() = playback.previous()
    fun seekTo(ms: Long) = playback.seekTo(ms)
    fun toggleShuffle() = playback.toggleShuffle()
    fun cycleRepeat() = playback.cycleRepeat()
    fun setVolume(v: Float) = playback.setVolume(v)

    override fun onCleared() {
        hifi.usb.stop()
        playback.release()
    }
}
