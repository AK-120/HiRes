package com.example.hifi.spotify

import com.example.hifi.music.Album
import com.example.hifi.music.Playlist
import com.example.hifi.music.SOURCE_SPOTIFY
import com.example.hifi.music.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** What /me/player says right now. */
data class PlayerSnapshot(
    val isPlaying: Boolean,
    val progressMs: Long,
    val durationMs: Long,
    val shuffle: Boolean,
    val repeat: String,
    val volumePercent: Int?,
    val deviceName: String?,
    val track: Track?
)

/**
 * Spotify Web API client: library browsing and Spotify Connect playback control.
 * Audio is played by Spotify itself on one of the user's Spotify devices; this
 * app only sends commands. Playback control needs Spotify Premium.
 */
class SpotifyApi(private val auth: SpotifyAuth) {

    class ApiException(val status: Int, message: String, val reason: String? = null) : Exception(message)
    data class Device(val id: String, val name: String, val isActive: Boolean)

    private companion object {
        const val BASE = "https://api.spotify.com/v1"
    }

    // ---- low level -------------------------------------------------------

    private suspend fun call(method: String, path: String, body: JSONObject? = null): String =
        withContext(Dispatchers.IO) {
            suspend fun once(force: Boolean): SpotifyHttp.Response {
                val token = auth.accessToken(force) ?: throw ApiException(401, "Not signed in to Spotify.")
                return SpotifyHttp.request(
                    method, BASE + path,
                    headers = mapOf("Authorization" to "Bearer $token"),
                    body = body?.toString(),
                    contentType = if (body != null) "application/json" else null
                )
            }

            var r = once(false)
            if (r.code == 401) r = once(true)
            if (r.code == 429) throw ApiException(429, "Spotify is rate limiting requests. Try again shortly.")
            if (r.code !in 200..299) {
                val err = runCatching { JSONObject(r.body).optJSONObject("error") }.getOrNull()
                throw ApiException(
                    r.code,
                    err?.optString("message")?.takeIf { it.isNotBlank() } ?: "Spotify error ${r.code}",
                    err?.optString("reason")?.takeIf { it.isNotBlank() }
                )
            }
            r.body
        }

    private suspend fun <T> paged(path: String, pageSize: Int, max: Int, map: (JSONObject) -> T?): List<T> {
        val out = mutableListOf<T>()
        var offset = 0
        while (offset < max) {
            val sep = if ('?' in path) '&' else '?'
            val json = JSONObject(call("GET", "$path${sep}limit=$pageSize&offset=$offset"))
            val items = json.optJSONArray("items") ?: break
            for (i in 0 until items.length()) items.optJSONObject(i)?.let(map)?.let(out::add)
            if (items.length() == 0 || json.isNull("next")) break
            offset += pageSize
        }
        return out
    }

    // ---- library ---------------------------------------------------------

    suspend fun savedTracks(max: Int = 1000): List<Track> =
        paged("/me/tracks", 50, max) { it.optJSONObject("track")?.let(::parseTrack) }

    suspend fun savedAlbums(max: Int = 500): List<Album> =
        paged("/me/albums", 50, max) { it.optJSONObject("album")?.let(::parseAlbum) }

    suspend fun albumTracks(album: Album): List<Track> =
        paged("/albums/${album.id}/tracks", 50, 500) { parseTrack(it, album) }

    suspend fun playlists(max: Int = 300): List<Playlist> =
        paged("/me/playlists", 50, max) { p ->
            val id = p.optString("id").takeIf { it.isNotBlank() } ?: return@paged null
            // Spotify renamed `tracks` to `items` in this object (Feb 2026); accept both.
            val total = (p.optJSONObject("items") ?: p.optJSONObject("tracks"))?.optInt("total") ?: 0
            Playlist(id, p.optString("name", "Playlist"), total, firstImage(p))
        }

    suspend fun playlistTracks(id: String, max: Int = 1000): List<Track> = try {
        // `/items` replaced `/tracks` in Feb 2026; each entry carries `item` (older: `track`).
        paged("/playlists/$id/items", 100, max) { e ->
            (e.optJSONObject("item") ?: e.optJSONObject("track"))?.let(::parseTrack)
        }
    } catch (e: ApiException) {
        if (e.status == 403) {
            throw ApiException(403, "Spotify only lets this app read playlists you own or collaborate on.")
        }
        throw e
    }

    // ---- player ----------------------------------------------------------

    suspend fun playerState(): PlayerSnapshot? {
        val body = call("GET", "/me/player")
        if (body.isBlank()) return null
        val j = JSONObject(body)
        val device = j.optJSONObject("device")
        val item = j.optJSONObject("item")
        return PlayerSnapshot(
            isPlaying = j.optBoolean("is_playing"),
            progressMs = j.optLong("progress_ms"),
            durationMs = item?.optLong("duration_ms") ?: 0L,
            shuffle = j.optBoolean("shuffle_state"),
            repeat = j.optString("repeat_state", "off"),
            volumePercent = device?.takeIf { !it.isNull("volume_percent") }?.optInt("volume_percent"),
            deviceName = device?.optString("name")?.takeIf { it.isNotBlank() },
            track = item?.let { parseTrack(it) }
        )
    }

    suspend fun devices(): List<Device> {
        val arr = JSONObject(call("GET", "/me/player/devices")).optJSONArray("devices") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val d = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = d.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Device(id, d.optString("name"), d.optBoolean("is_active"))
        }
    }

    suspend fun play(uris: List<String>, offset: Int, deviceId: String? = null) {
        val body = JSONObject()
            .put("uris", JSONArray(uris))
            .put("offset", JSONObject().put("position", offset))
        call("PUT", "/me/player/play" + deviceQuery(deviceId), body)
    }

    suspend fun resume(deviceId: String? = null) { call("PUT", "/me/player/play" + deviceQuery(deviceId)) }
    suspend fun pause() { call("PUT", "/me/player/pause") }
    suspend fun next() { call("POST", "/me/player/next") }
    suspend fun previous() { call("POST", "/me/player/previous") }
    suspend fun seek(ms: Long) { call("PUT", "/me/player/seek?position_ms=$ms") }
    suspend fun setVolume(percent: Int) { call("PUT", "/me/player/volume?volume_percent=${percent.coerceIn(0, 100)}") }
    suspend fun setShuffle(on: Boolean) { call("PUT", "/me/player/shuffle?state=$on") }
    suspend fun setRepeat(state: String) { call("PUT", "/me/player/repeat?state=$state") }

    private fun deviceQuery(id: String?) = if (id == null) "" else "?device_id=$id"

    // ---- parsing ---------------------------------------------------------

    private fun parseTrack(t: JSONObject): Track? = parseTrack(t, null)

    private fun parseTrack(t: JSONObject, album: Album?): Track? {
        val uri = t.optString("uri")
        if (!uri.startsWith("spotify:track:")) return null // skips episodes and local files
        val alb = t.optJSONObject("album")
        return Track(
            id = uri,
            title = t.optString("name", "Unknown title"),
            artist = artistNames(t.optJSONArray("artists")),
            album = album?.title ?: alb?.optString("name")?.takeIf { it.isNotBlank() },
            albumId = album?.id ?: alb?.optString("id")?.takeIf { it.isNotBlank() },
            artworkUri = album?.artworkUri ?: firstImage(alb),
            contentUri = uri,
            durationMs = t.optLong("duration_ms"),
            trackNumber = t.optInt("track_number").takeIf { it > 0 },
            codec = null,
            sampleRate = null,
            bitDepth = null,
            channels = null,
            source = SOURCE_SPOTIFY
        )
    }

    private fun parseAlbum(a: JSONObject): Album? {
        val id = a.optString("id").takeIf { it.isNotBlank() } ?: return null
        return Album(
            id = id,
            title = a.optString("name", "Unknown album"),
            artist = artistNames(a.optJSONArray("artists")),
            artworkUri = firstImage(a),
            trackCount = a.optInt("total_tracks")
        )
    }

    private fun artistNames(arr: JSONArray?): String? {
        if (arr == null) return null
        val names = (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
        return names.joinToString(", ").ifEmpty { null }
    }

    private fun firstImage(o: JSONObject?): String? {
        val imgs = o?.optJSONArray("images") ?: return null
        if (imgs.length() == 0) return null
        return imgs.optJSONObject(0)?.optString("url")?.takeIf { it.isNotBlank() }
    }
}
