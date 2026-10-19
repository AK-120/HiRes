package com.example.hifi.data

import com.example.hifi.music.Album
import com.example.hifi.music.Artist
import com.example.hifi.music.MusicProvider
import com.example.hifi.music.Track

data class Library(
    val tracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList()
)

class LibraryRepository(
    private val local: MusicProvider,
    val providers: List<MusicProvider>,
    private val recent: RecentlyPlayedStore
) {
    suspend fun load(): Library = Library(
        tracks = local.getTracks(),
        albums = local.getAlbums(),
        artists = local.getArtists()
    )

    fun recordPlayed(id: String) = recent.record(id)

    fun recentTracks(all: List<Track>): List<Track> {
        val byId = all.associateBy { it.id }
        return recent.ids().mapNotNull { byId[it] }
    }
}
