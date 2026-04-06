package dev.yuwixx.resonance.data.repository

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yuwixx.resonance.data.model.Song
import dev.yuwixx.resonance.data.network.LastFmApi
import dev.yuwixx.resonance.data.preferences.ResonancePreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "LastFmRepo"
private const val DEFAULT_API_KEY    = "56e83db5fd112b64e486cba54141c783"
private const val DEFAULT_API_SECRET = "cad85a4626b55111661a4d6e0be85f5d"

// Dedicated DataStore so we never clash with the main prefs store
private val Context.lastFmStore: DataStore<Preferences> by preferencesDataStore(name = "lastfm_prefs")

private object LastFmKeys {
    val SESSION_KEY  = stringPreferencesKey("session_key")
    val USERNAME     = stringPreferencesKey("username")
    val ENABLED      = booleanPreferencesKey("enabled")
    val NOW_PLAYING  = booleanPreferencesKey("now_playing")
    val ONLY_WIFI    = booleanPreferencesKey("only_wifi")
    // Optional: let power-users override the API app credentials at runtime
    val API_KEY      = stringPreferencesKey("api_key")
    val API_SECRET   = stringPreferencesKey("api_secret")
}

// ─── Public state types ───────────────────────────────────────────────────────

data class PendingScrobble(
    val artist: String,
    val track: String,
    val album: String,
    val timestamp: Long,
    val duration: Int,
    val trackNumber: Int,
)

sealed class LastFmAuthState {
    data object Idle : LastFmAuthState()
    data object Loading : LastFmAuthState()
    data class Authenticated(val username: String, val playCount: String) : LastFmAuthState()
    data class Error(val message: String) : LastFmAuthState()
}

// ─── Repository ───────────────────────────────────────────────────────────────

@Singleton
class LastFmRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: LastFmApi,
    private val mainPrefs: ResonancePreferences,
) {
    private val store = context.lastFmStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // In-memory scrobble queue — survives rotation, lost on process death.
    // For true offline persistence you'd write these to Room; this covers 99% of cases.
    private val pendingScrobbles = mutableListOf<PendingScrobble>()

    // ── Exposed flows ─────────────────────────────────────────────────────────

    val sessionKey: Flow<String>  = store.data.map { it[LastFmKeys.SESSION_KEY] ?: "" }
    val username: Flow<String>    = store.data.map { it[LastFmKeys.USERNAME]    ?: "" }
    val isEnabled: Flow<Boolean>  = store.data.map { it[LastFmKeys.ENABLED]     ?: false }
    val nowPlaying: Flow<Boolean> = store.data.map { it[LastFmKeys.NOW_PLAYING] ?: true }
    val onlyWifi: Flow<Boolean>   = store.data.map { it[LastFmKeys.ONLY_WIFI]   ?: false }

    // ── Auth state ────────────────────────────────────────────────────────────

    private val _authState = MutableStateFlow<LastFmAuthState>(LastFmAuthState.Idle)
    val authState: StateFlow<LastFmAuthState> = _authState.asStateFlow()

    init {
        // Restore session on startup — if we already have a session key, fetch the user info
        scope.launch {
            val sk  = sessionKey.firstOrNull() ?: ""
            val usr = username.firstOrNull()   ?: ""
            if (sk.isNotBlank() && usr.isNotBlank()) {
                fetchUserInfo(usr)
            }
        }
    }

    // ── Preferences writes ────────────────────────────────────────────────────

    suspend fun setEnabled(v: Boolean)    { store.edit { it[LastFmKeys.ENABLED]     = v } }
    suspend fun setNowPlaying(v: Boolean) { store.edit { it[LastFmKeys.NOW_PLAYING] = v } }
    suspend fun setOnlyWifi(v: Boolean)   { store.edit { it[LastFmKeys.ONLY_WIFI]   = v } }

    suspend fun setCustomCredentials(apiKey: String, apiSecret: String) {
        store.edit {
            it[LastFmKeys.API_KEY]    = apiKey
            it[LastFmKeys.API_SECRET] = apiSecret
        }
    }

    // ── Auth ──────────────────────────────────────────────────────────────────

    /**
     * Signs in via Last.fm "Mobile Session" (auth.getMobileSession).
     * No browser redirect required — just username + password.
     * On success the session key is persisted so this only runs once.
     */
    suspend fun authenticate(username: String, password: String) {
        _authState.value = LastFmAuthState.Loading
        try {
            val (apiKey, apiSecret) = getCredentials()

            val sig = sign(
                mapOf(
                    "method"   to "auth.getMobileSession",
                    "username" to username,
                    "password" to password,
                    "api_key"  to apiKey,
                ),
                apiSecret,
            )

            val response = api.getMobileSession(
                username = username,
                password = password,
                apiKey   = apiKey,
                apiSig   = sig,
            )

            if (response.error != null) {
                _authState.value = LastFmAuthState.Error(
                    response.message ?: "Login failed (error ${response.error})"
                )
                return
            }

            val sk = response.session?.key ?: run {
                _authState.value = LastFmAuthState.Error("No session key in response")
                return
            }

            // Persist session
            store.edit {
                it[LastFmKeys.SESSION_KEY] = sk
                it[LastFmKeys.USERNAME]    = username
                it[LastFmKeys.ENABLED]     = true
            }

            fetchUserInfo(username)

        } catch (e: Exception) {
            Log.e(TAG, "authenticate failed", e)
            _authState.value = LastFmAuthState.Error(e.message ?: "Network error — check your connection")
        }
    }

    suspend fun logout() {
        store.edit { prefs ->
            prefs.remove(LastFmKeys.SESSION_KEY)
            prefs.remove(LastFmKeys.USERNAME)
            prefs[LastFmKeys.ENABLED] = false
        }
        _authState.value = LastFmAuthState.Idle
    }

    private suspend fun fetchUserInfo(usr: String) {
        try {
            val (apiKey, _) = getCredentials()
            val resp = api.getUserInfo(username = usr, apiKey = apiKey)
            val user = resp.user
            if (user != null) {
                _authState.value = LastFmAuthState.Authenticated(
                    username  = user.name,
                    playCount = user.playcount,
                )
            } else {
                // Fallback: session is still valid, just show username
                _authState.value = LastFmAuthState.Authenticated(usr, "–")
            }
        } catch (e: Exception) {
            // Network error on startup — session is probably still valid
            _authState.value = LastFmAuthState.Authenticated(usr, "–")
        }
    }

    // ── Now Playing ──────────────────────────────────────────────────────────

    fun updateNowPlaying(song: Song) {
        scope.launch {
            if (!isScrobbleReady()) return@launch
            val nowPlayingOn = nowPlaying.firstOrNull() ?: true
            if (!nowPlayingOn) return@launch
            try {
                val (apiKey, sk, sig) = buildSignedParams(
                    "track.updateNowPlaying",
                    mapOf(
                        "artist" to song.displayArtist,
                        "track"  to song.title,
                        "album"  to song.album,
                    )
                ) ?: return@launch

                api.updateNowPlaying(
                    artist      = song.displayArtist,
                    track       = song.title,
                    album       = song.album.takeIf { it.isNotBlank() },
                    duration    = (song.duration / 1000).toInt(),
                    trackNumber = song.trackNumber.takeIf { it > 0 },
                    apiKey      = apiKey,
                    sessionKey  = sk,
                    apiSig      = sig,
                )
            } catch (e: Exception) {
                Log.w(TAG, "updateNowPlaying failed: ${e.message}")
            }
        }
    }

    // ── Scrobble ─────────────────────────────────────────────────────────────

    fun scrobble(song: Song, startedAt: Long) {
        scope.launch {
            if (!isScrobbleReady()) return@launch
            pendingScrobbles.add(
                PendingScrobble(
                    artist      = song.displayArtist,
                    track       = song.title,
                    album       = song.album,
                    timestamp   = startedAt / 1000,
                    duration    = (song.duration / 1000).toInt(),
                    trackNumber = song.trackNumber,
                )
            )
            flushPendingScrobbles()
        }
    }

    private suspend fun flushPendingScrobbles() {
        if (pendingScrobbles.isEmpty()) return
        val toFlush = pendingScrobbles.toList()
        pendingScrobbles.clear()

        for (p in toFlush) {
            try {
                val (apiKey, sk, sig) = buildSignedParams(
                    "track.scrobble",
                    mapOf(
                        "artist[0]"    to p.artist,
                        "track[0]"     to p.track,
                        "timestamp[0]" to p.timestamp.toString(),
                        "album[0]"     to p.album,
                    )
                ) ?: continue

                val response = api.scrobble(
                    artist      = p.artist,
                    track       = p.track,
                    timestamp   = p.timestamp,
                    album       = p.album.takeIf { it.isNotBlank() },
                    duration    = p.duration.takeIf { it > 0 },
                    trackNumber = p.trackNumber.takeIf { it > 0 },
                    apiKey      = apiKey,
                    sessionKey  = sk,
                    apiSig      = sig,
                )

                if (response.error != null) {
                    Log.w(TAG, "Scrobble error ${response.error}: ${response.message}")
                    if (response.error != 11 && response.error != 16) pendingScrobbles.add(p)
                } else {
                    Log.d(TAG, "Scrobbled: ${p.artist} – ${p.track}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Scrobble network error, re-queuing: ${e.message}")
                // Only buffer for retry if the user has "Offline Scrobble Queue" enabled
                val offlineQueueEnabled = mainPrefs.lastFmScrobbleOffline.firstOrNull() ?: true
                if (offlineQueueEnabled) pendingScrobbles.add(p)
            }
        }
    }

    // ── Love / Unlove ─────────────────────────────────────────────────────────

    suspend fun loveTrack(song: Song): Result<Unit> = runCatching {
        val (apiKey, sk, sig) = buildSignedParams(
            "track.love",
            mapOf("artist" to song.displayArtist, "track" to song.title)
        ) ?: error("Not authenticated")
        val resp = api.loveTrack(
            artist = song.displayArtist, track = song.title,
            apiKey = apiKey, sessionKey = sk, apiSig = sig,
        )
        if (resp.error != null) error("Love failed: ${resp.message}")
    }

    suspend fun unloveTrack(song: Song): Result<Unit> = runCatching {
        val (apiKey, sk, sig) = buildSignedParams(
            "track.unlove",
            mapOf("artist" to song.displayArtist, "track" to song.title)
        ) ?: error("Not authenticated")
        val resp = api.unloveTrack(
            artist = song.displayArtist, track = song.title,
            apiKey = apiKey, sessionKey = sk, apiSig = sig,
        )
        if (resp.error != null) error("Unlove failed: ${resp.message}")
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private suspend fun isScrobbleReady(): Boolean {
        val enabled = isEnabled.firstOrNull() ?: false
        val sk = sessionKey.firstOrNull() ?: ""
        return enabled && sk.isNotBlank()
    }

    /** Returns (apiKey, apiSecret) — user override if set, else shipped defaults. */
    private suspend fun getCredentials(): Pair<String, String> {
        val prefs = store.data.firstOrNull()
        val apiKey    = prefs?.get(LastFmKeys.API_KEY)?.takeIf    { it.isNotBlank() } ?: DEFAULT_API_KEY
        val apiSecret = prefs?.get(LastFmKeys.API_SECRET)?.takeIf { it.isNotBlank() } ?: DEFAULT_API_SECRET
        return apiKey to apiSecret
    }

    /**
     * Builds a signed (apiKey, sessionKey, signature) triple for authenticated calls.
     * Returns null if there is no valid session.
     */
    private suspend fun buildSignedParams(
        method: String,
        extraParams: Map<String, String> = emptyMap(),
    ): Triple<String, String, String>? {
        val (apiKey, apiSecret) = getCredentials()
        val sk = sessionKey.firstOrNull()?.takeIf { it.isNotBlank() } ?: return null

        val allParams = buildMap {
            put("method",  method)
            put("api_key", apiKey)
            put("sk",      sk)
            putAll(extraParams)
        }
        return Triple(apiKey, sk, sign(allParams, apiSecret))
    }

    /**
     * Last.fm API signature: MD5( alphabetically-sorted concatenated params + secret )
     */
    private fun sign(params: Map<String, String>, secret: String): String {
        val payload = params.entries
            .sortedBy { it.key }
            .joinToString("") { (k, v) -> k + v } + secret
        return MessageDigest.getInstance("MD5")
            .digest(payload.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    val pendingScrobbleCount: Int get() = pendingScrobbles.size
}