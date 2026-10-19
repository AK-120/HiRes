package com.example.hifi.data

import com.example.hifi.music.Album
import com.example.hifi.music.Artist
import com.example.hifi.music.MusicProvider
import com.example.hifi.music.Playlist
import com.example.hifi.music.Track
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class Library(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)

class LibraryRepository(
    val local: MusicProvider,
    val spotify: MusicProvider,
    val providers: List<MusicProvider>,
    private val recent: RecentlyPlayedStore
) {
    /** Tracks first (providers may cache them), then the rest in parallel. */
    suspend fun load(provider: MusicProvider): Library = coroutineScope {
        val tracks = provider.getTracks()
        val albums = async { provider.getAlbums() }
        val artists = async { provider.getArtists() }
        val playlists = async { provider.getPlaylists() }
        Library(tracks, albums.await(), artists.await(), playlists.await())
    }

    fun recordPlayed(id: String) = recent.record(id)

    fun recentTracks(all: List<Track>): List<Track> {
        val byId = all.associateBy { it.id }
        return recent.ids().mapNotNull { byId[it] }
    }
}
