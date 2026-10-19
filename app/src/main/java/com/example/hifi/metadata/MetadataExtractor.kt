package com.example.hifi.metadata

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import com.example.hifi.music.LocalMusicProvider
import com.example.hifi.music.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads technical details for one file on demand. Anything the platform does
 * not report stays null. Nothing is inferred or guessed.
 */
class MetadataExtractor(private val context: Context) {

    private val cache = HashMap<String, Track>()

    suspend fun enrich(track: Track): Track = withContext(Dispatchers.IO) {
        cache[track.id]?.let { return@withContext it }

        var codec = track.codec
        var sampleRate: Int? = null
        var channels: Int? = null
        var bitDepth: Int? = null
        val uri = Uri.parse(track.contentUri)

        runCatching {
            val ex = MediaExtractor()
            try {
                ex.setDataSource(context, uri, null)
                for (i in 0 until ex.trackCount) {
                    val f = ex.getTrackFormat(i)
                    val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                    if (!mime.startsWith("audio/")) continue
                    codec = LocalMusicProvider.codecFromMime(mime) ?: codec
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE))
                        sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT))
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    if (f.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
                        bitDepth = when (f.getInteger(MediaFormat.KEY_PCM_ENCODING)) {
                            android.media.AudioFormat.ENCODING_PCM_8BIT -> 8
                            android.media.AudioFormat.ENCODING_PCM_16BIT -> 16
                            android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                            android.media.AudioFormat.ENCODING_PCM_32BIT -> 32
                            android.media.AudioFormat.ENCODING_PCM_FLOAT -> 32
                            else -> null
                        }
                    }
                    break
                }
            } finally {
                ex.release()
            }
        }

        if (bitDepth == null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    bitDepth = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)
                        ?.toIntOrNull()?.takeIf { it > 0 }
                } finally {
                    r.release()
                }
            }
        }

        val result = track.copy(
            codec = codec,
            sampleRate = sampleRate,
            bitDepth = bitDepth,
            channels = channels
        )
        cache[track.id] = result
        result
    }
}

/** Display helpers. Missing data is stated plainly, never invented. */
object TechFormat {
    fun summary(t: Track): String {
        val parts = mutableListOf<String>()
        parts += t.codec ?: "Codec unavailable"
        parts += t.bitDepth?.let { "$it-bit" } ?: "Bit depth unavailable"
        t.sampleRate?.let { parts += formatKhz(it) }
        return parts.joinToString(" · ")
    }

    fun formatKhz(hz: Int): String {
        val k = hz / 1000.0
        return if (hz % 1000 == 0) "${hz / 1000} kHz" else "${"%.1f".format(k)} kHz"
    }

    fun channelsLabel(ch: Int?): String? = when (ch) {
        null -> null
        1 -> "Mono"
        2 -> "Stereo"
        else -> "$ch channels"
    }
}
