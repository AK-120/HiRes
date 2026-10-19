package com.example.hifi.spotify

import com.example.hifi.music.Album
import com.example.hifi.music.Artist
import com.example.hifi.music.MusicProvider
import com.example.hifi.music.Playlist
import com.example.hifi.music.Track

/** The signed-in user's Spotify library, via the official Web API. */
class SpotifyProvider(
    private val auth: SpotifyAuth,
    private val api: SpotifyApi
) : MusicProvider {

    override val id = "spotify"
    override val displayName = "Spotify"
    override val isAvailable: Boolean get() = auth.isConfigured && auth.connected.value

    private var cachedTracks: List<Track>? = null

    fun invalidate() { cachedTracks = null }

    override suspend fun getTracks(): List<Track> {
        if (!isAvailable) return emptyList()
        return cachedTracks ?: api.savedTracks().also { cachedTracks = it }
    }

    override suspend fun getAlbums(): List<Album> =
        if (isAvailable) api.savedAlbums() else emptyList()

    override suspend fun getArtists(): List<Artist> =
        getTracks().filter { it.artist != null }
            .groupBy { it.artist!!.substringBefore(", ") }
            .map { (name, tracks) ->
                Artist(name, tracks.size, tracks.mapNotNull { it.albumId }.distinct().size)
            }
            .sortedBy { it.name.lowercase() }

    override suspend fun getPlaylists(): List<Playlist> =
        if (isAvailable) api.playlists() else emptyList()

    suspend fun getAlbumTracks(album: Album): List<Track> = api.albumTracks(album)
    suspend fun getPlaylistTracks(id: String): List<Track> = api.playlistTracks(id)
}
