package com.example.hifi.music

/**
 * Placeholders only. Not part of the MVP.
 *
 * Future implementations must use only officially supported APIs
 * (Apple MusicKit, Spotify's supported playback SDK). No stream extraction,
 * no DRM circumvention, no downloading of protected audio.
 */
class AppleMusicProvider : MusicProvider {
    override val id = "apple_music"
    override val displayName = "Apple Music"
    override val isAvailable = false
    override suspend fun getTracks(): List<Track> = emptyList()
    override suspend fun getAlbums(): List<Album> = emptyList()
    override suspend fun getArtists(): List<Artist> = emptyList()
}

class SpotifyProvider : MusicProvider {
    override val id = "spotify"
    override val displayName = "Spotify"
    override val isAvailable = false
    override suspend fun getTracks(): List<Track> = emptyList()
    override suspend fun getAlbums(): List<Album> = emptyList()
    override suspend fun getArtists(): List<Artist> = emptyList()
}
