package com.example.hifi.music

/**
 * Source abstraction. The UI and playback layers depend on this only,
 * so streaming providers can be added later without rewriting them.
 */
interface MusicProvider {
    val id: String
    val displayName: String
    val isAvailable: Boolean

    suspend fun getTracks(): List<Track>
    suspend fun getAlbums(): List<Album>
    suspend fun getArtists(): List<Artist>
}
