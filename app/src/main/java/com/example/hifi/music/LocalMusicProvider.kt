package com.example.hifi.music

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the device's MediaStore. Fast fields only; deep technical info is read on demand. */
class LocalMusicProvider(private val context: Context) : MusicProvider {

    override val id = "local"
    override val displayName = "Local Music"
    override val isAvailable = true

    override suspend fun getTracks(): List<Track> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Track>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.MIME_TYPE
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sort = "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE, " +
            "${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE, ${MediaStore.Audio.Media.TRACK}"

        context.contentResolver.query(collection, projection, selection, null, sort)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iTrack = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val iMime = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val albumId = c.getLong(iAlbumId).takeIf { it > 0 }
                val artist = c.getString(iArtist)?.takeUnless { it == "<unknown>" }
                val rawTrack = c.getInt(iTrack)
                // MediaStore encodes disc number in thousands (e.g. 1005)
                val trackNo = (rawTrack % 1000).takeIf { it > 0 }
                out += Track(
                    id = id.toString(),
                    title = c.getString(iTitle) ?: "Unknown title",
                    artist = artist,
                    album = c.getString(iAlbum),
                    albumId = albumId,
                    artworkUri = albumId?.let { albumArtUri(it) },
                    contentUri = ContentUris.withAppendedId(collection, id).toString(),
                    durationMs = c.getLong(iDur),
                    trackNumber = trackNo,
                    codec = codecFromMime(c.getString(iMime)),
                    sampleRate = null,
                    bitDepth = null,
                    channels = null
                )
            }
        }
        out
    }

    override suspend fun getAlbums(): List<Album> =
        getTracks().filter { it.albumId != null }
            .groupBy { it.albumId!! }
            .map { (albumId, tracks) ->
                Album(
                    id = albumId,
                    title = tracks.first().album ?: "Unknown album",
                    artist = tracks.map { it.artist }.distinct().singleOrNull(),
                    artworkUri = tracks.first().artworkUri,
                    trackCount = tracks.size
                )
            }
            .sortedBy { it.title.lowercase() }

    override suspend fun getArtists(): List<Artist> =
        getTracks().filter { it.artist != null }
            .groupBy { it.artist!! }
            .map { (name, tracks) ->
                Artist(name, tracks.size, tracks.mapNotNull { it.albumId }.distinct().size)
            }
            .sortedBy { it.name.lowercase() }

    private fun albumArtUri(albumId: Long): String =
        ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId).toString()

    companion object {
        fun codecFromMime(mime: String?): String? = when (mime?.lowercase()) {
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/alac" -> "ALAC"
            "audio/wav", "audio/x-wav", "audio/vnd.wave" -> "WAV"
            "audio/mpeg", "audio/mp3" -> "MP3"
            "audio/mp4", "audio/x-m4a", "audio/aac", "audio/aacp" -> "AAC/M4A"
            "audio/ogg", "application/ogg" -> "OGG"
            else -> null
        }
    }
}
