package com.example.hifi.music

/**
 * Source abstraction. The UI and playback layers depend on this only,
 * so providers can be added without rewriting them.
 */
interface MusicProvider {
    val id: String
    val displayName: String
    val isAvailable: Boolean

    suspend fun getTracks(): List<Track>
    suspend fun getAlbums(): List<Album>
    suspend fun getArtists(): List<Artist>
    suspend fun getPlaylists(): List<Playlist> = emptyList()
}
