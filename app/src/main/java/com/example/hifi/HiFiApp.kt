package com.example.hifi

import android.app.Application
import com.example.hifi.data.LibraryRepository
import com.example.hifi.data.RecentlyPlayedStore
import com.example.hifi.metadata.MetadataExtractor
import com.example.hifi.music.AppleMusicProvider
import com.example.hifi.music.LocalMusicProvider
import com.example.hifi.music.SpotifyProvider
import com.example.hifi.playback.PlaybackManager
import com.example.hifi.usb.UsbAudioMonitor

/** Hand-rolled DI: the graph is small enough that a framework adds nothing. */
class HiFiApp : Application() {
    lateinit var repository: LibraryRepository
    lateinit var playback: PlaybackManager
    lateinit var metadata: MetadataExtractor
    lateinit var usb: UsbAudioMonitor

    override fun onCreate() {
        super.onCreate()
        val local = LocalMusicProvider(this)
        repository = LibraryRepository(
            local = local,
            providers = listOf(local, AppleMusicProvider(), SpotifyProvider()),
            recent = RecentlyPlayedStore(this)
        )
        playback = PlaybackManager(this, onTrackStarted = { repository.recordPlayed(it) })
        metadata = MetadataExtractor(this)
        usb = UsbAudioMonitor(this)
    }
}
