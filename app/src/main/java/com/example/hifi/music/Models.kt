package com.example.hifi.music

/**
 * A single playable track. Technical fields are nullable: null means
 * "not available from Android or the file", never a guessed value.
 */
data class Track(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val albumId: Long?,
    val artworkUri: String?,
    val contentUri: String,
    val durationMs: Long,
    val trackNumber: Int?,
    val codec: String?,
    val sampleRate: Int?,
    val bitDepth: Int?,
    val channels: Int?
)

data class Album(
    val id: Long,
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
