package com.example.hifi.music

const val SOURCE_LOCAL = "local"
const val SOURCE_SPOTIFY = "spotify"

/**
 * A single playable track. Technical fields are nullable: null means
 * "not available from Android or the file", never a guessed value.
 * For streaming sources [contentUri] holds the provider's own URI and the
 * technical fields stay null (the provider does not expose them).
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: String?,
    val artworkUri: String?,
    val contentUri: String,
    val durationMs: Long,
    val trackNumber: Int?,
    val codec: String?,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val channels: Int?,
    val source: String = SOURCE_LOCAL
)

data class Album(
    val id: String,
    val title: String,
    val artist: String?,
    val artworkUri: String?,
    val trackCount: Int
)

data class Artist(
    val name: String,
    val trackCount: Int,
    val albumCount: Int
)

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val artworkUri: String?
)
