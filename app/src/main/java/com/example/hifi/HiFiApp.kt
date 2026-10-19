package com.example.hifi

import android.app.Application
import com.example.hifi.data.LibraryRepository
import com.example.hifi.data.RecentlyPlayedStore
import com.example.hifi.metadata.MetadataExtractor
import com.example.hifi.music.AppleMusicProvider
import com.example.hifi.music.LocalMusicProvider
import com.example.hifi.playback.PlaybackManager
import com.example.hifi.spotify.SpotifyApi
import com.example.hifi.spotify.SpotifyAuth
import com.example.hifi.spotify.SpotifyPlayback
import com.example.hifi.spotify.SpotifyProvider
import com.example.hifi.usb.UsbAudioMonitor

/** Hand-rolled DI: the graph is small enough that a framework adds nothing. */
class HiFiApp : Application() {
    lateinit var repository: LibraryRepository
    lateinit var playback: PlaybackManager
    lateinit var metadata: MetadataExtractor
    lateinit var usb: UsbAudioMonitor
    lateinit var spotifyAuth: SpotifyAuth
    lateinit var spotifyApi: SpotifyApi
    lateinit var spotifyProvider: SpotifyProvider
    lateinit var spotifyPlayback: SpotifyPlayback

    override fun onCreate() {
        super.onCreate()
        val local = LocalMusicProvider(this)
        spotifyAuth = SpotifyAuth(this)
        spotifyApi = SpotifyApi(spotifyAuth)
        spotifyProvider = SpotifyProvider(spotifyAuth, spotifyApi)
        spotifyPlayback = SpotifyPlayback(spotifyApi)
        repository = LibraryRepository(
            local = local,
            spotify = spotifyProvider,
            providers = listOf(local, spotifyProvider, AppleMusicProvider()),
            recent = RecentlyPlayedStore(this)
        )
        playback = PlaybackManager(this, onTrackStarted = { repository.recordPlayed(it) })
        metadata = MetadataExtractor(this)
        usb = UsbAudioMonitor(this)
    }
}
