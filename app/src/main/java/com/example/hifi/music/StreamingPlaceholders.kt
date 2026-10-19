package com.example.hifi.music

/**
 * Placeholder only. Not part of the current build.
 *
 * A future implementation must use only officially supported Apple Music
 * capabilities (MusicKit). No stream extraction, no DRM circumvention,
 * no downloading of protected audio.
 */
class AppleMusicProvider : MusicProvider {
    override val id = "apple_music"
    override val displayName = "Apple Music"
    override val isAvailable = false
    override suspend fun getTracks(): List<Track> = emptyList()
    override suspend fun getAlbums(): List<Album> = emptyList()
    override suspend fun getArtists(): List<Artist> = emptyList()
}
