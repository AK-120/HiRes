package com.example.hifi.spotify

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.example.hifi.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Spotify sign-in using the Authorization Code flow with PKCE (no client secret
 * in the app, no implicit grant). The user signs in on Spotify's own page.
 */
class SpotifyAuth(private val context: Context) {

    companion object {
        const val SCHEME = "com.example.hifi"
        const val HOST = "spotify-callback"
        const val REDIRECT_URI = "$SCHEME://$HOST"
        private const val SCOPES =
            "user-library-read playlist-read-private user-read-playback-state user-modify-playback-state"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val FORM = "application/x-www-form-urlencoded"
        private const val K_ACCESS = "access"
        private const val K_REFRESH = "refresh"
        private const val K_EXPIRES = "expires"
        private const val K_VERIFIER = "verifier"
        private const val K_STATE = "state"
    }

    private val prefs = context.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val clientId: String = BuildConfig.SPOTIFY_CLIENT_ID

    val isConfigured: Boolean get() = clientId.isNotBlank()

    private val _connected = MutableStateFlow(prefs.contains(K_REFRESH))
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun isCallback(uri: Uri): Boolean = uri.scheme == SCHEME && uri.host == HOST

    /** Opens Spotify's sign-in page. Returns false if no Client ID is configured. */
    fun startLogin(): Boolean {
        if (!isConfigured) return false
        val verifier = randomUrlSafe(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
        val state = randomUrlSafe(16)
        prefs.edit().putString(K_VERIFIER, verifier).putString(K_STATE, state).apply()
        val uri = Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("state", state)
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return true
    }

    /** Completes sign-in from the redirect. Returns an error message, or null on success. */
    suspend fun handleCallback(uri: Uri): String? {
        uri.getQueryParameter("error")?.let {
            return if (it == "access_denied") "Spotify connection was cancelled." else "Spotify returned an error: $it"
        }
        val code = uri.getQueryParameter("code") ?: return "Spotify did not return a sign-in code."
        val expected = prefs.getString(K_STATE, null)
        val verifier = prefs.getString(K_VERIFIER, null)
        if (expected == null || verifier == null || uri.getQueryParameter("state") != expected) {
            return "Sign-in could not be verified. Try connecting again."
        }
        prefs.edit().remove(K_STATE).remove(K_VERIFIER).apply()

        return withContext(Dispatchers.IO) {
            try {
                val r = SpotifyHttp.request(
                    "POST", TOKEN_URL, body = form(
                        "grant_type" to "authorization_code",
                        "code" to code,
                        "redirect_uri" to REDIRECT_URI,
                        "client_id" to clientId,
                        "code_verifier" to verifier
                    ), contentType = FORM
                )
                if (r.code !in 200..299) {
                    "Spotify sign-in failed (${r.code})."
                } else {
                    store(JSONObject(r.body))
                    _connected.value = true
                    null
                }
            } catch (e: IOException) {
                "Can't reach Spotify. Check your connection."
            }
        }
    }

    /**
     * A valid access token, refreshed when needed. Null if signed out.
     * Throws IOException on network or server problems.
     */
    suspend fun accessToken(force: Boolean = false): String? = mutex.withLock {
        val token = prefs.getString(K_ACCESS, null)
        if (!force && token != null && System.currentTimeMillis() < prefs.getLong(K_EXPIRES, 0)) {
            return@withLock token
        }
        val refresh = prefs.getString(K_REFRESH, null) ?: return@withLock null
        withContext(Dispatchers.IO) {
            val r = SpotifyHttp.request(
                "POST", TOKEN_URL, body = form(
                    "grant_type" to "refresh_token",
                    "refresh_token" to refresh,
                    "client_id" to clientId
                ), contentType = FORM
            )
            when {
                r.code in 200..299 -> {
                    store(JSONObject(r.body))
                    prefs.getString(K_ACCESS, null)
                }
                r.code == 400 || r.code == 401 -> {
                    logout() // refresh token revoked or expired
                    null
                }
                else -> throw IOException("Spotify auth server error ${r.code}")
            }
        }
    }

    fun logout() {
        prefs.edit().clear().apply()
        _connected.value = false
    }

    private fun store(j: JSONObject) {
        val e = prefs.edit()
            .putString(K_ACCESS, j.getString("access_token"))
            .putLong(K_EXPIRES, System.currentTimeMillis() + (j.optLong("expires_in", 3600) - 60) * 1000)
        j.optString("refresh_token").takeIf { it.isNotBlank() }?.let { e.putString(K_REFRESH, it) }
        e.apply()
    }

    private fun form(vararg pairs: Pair<String, String>): String =
        pairs.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun randomUrlSafe(len: Int): String {
        val b = ByteArray(len)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP).take(len)
    }
}
