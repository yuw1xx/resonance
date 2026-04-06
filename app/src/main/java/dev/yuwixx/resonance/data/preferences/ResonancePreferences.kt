package dev.yuwixx.resonance.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yuwixx.resonance.data.model.RepeatMode
import dev.yuwixx.resonance.data.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "resonance_prefs")

@Singleton
class ResonancePreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val ds = context.dataStore

    companion object Keys {
        val IS_FIRST_RUN = booleanPreferencesKey("is_first_run")

        // Playback
        val REPEAT_MODE = stringPreferencesKey("repeat_mode")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val GAPLESS_ENABLED = booleanPreferencesKey("gapless_enabled")
        val CROSSFADE_DURATION_MS = intPreferencesKey("crossfade_duration_ms")
        val SKIP_SILENCE = booleanPreferencesKey("skip_silence")
        val REPLAY_GAIN_MODE = stringPreferencesKey("replay_gain_mode")
        val REPLAY_GAIN_PREAMP_DB = floatPreferencesKey("replay_gain_preamp_db")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
        val SMART_SHUFFLE_ENABLED = booleanPreferencesKey("smart_shuffle_enabled")
        val RESUME_ON_HEADPHONES = booleanPreferencesKey("resume_on_headphones")
        val PAUSE_ON_HEADPHONES_OUT = booleanPreferencesKey("pause_on_headphones_out")
        val DUCK_AUDIO_ON_FOCUS_LOSS = booleanPreferencesKey("duck_audio_on_focus_loss")
        val VOLUME_NORMALIZATION = booleanPreferencesKey("volume_normalization")

        // Library
        val MIN_TRACK_DURATION_MS = longPreferencesKey("min_track_duration_ms")
        val ARTIST_DELIMITER = stringPreferencesKey("artist_delimiter")
        val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")
        val SORT_ORDER = stringPreferencesKey("sort_order")
        val SHOW_ARTWORK_IN_LIST = booleanPreferencesKey("show_artwork_in_list")
        val FETCH_ARTIST_IMAGES = booleanPreferencesKey("fetch_artist_images")
        val GROUP_BY_ALBUM_ARTIST = booleanPreferencesKey("group_by_album_artist")
        val SHOW_FILENAME_AS_TITLE = booleanPreferencesKey("show_filename_as_title")
        val IGNORE_ARTICLES = booleanPreferencesKey("ignore_articles")
        val AUTO_SCAN_INTERVAL_HOURS = intPreferencesKey("auto_scan_interval_hours")

        // UI
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
        val PRESET_COLOR = intPreferencesKey("preset_color")
        val DARK_THEME = stringPreferencesKey("dark_theme")
        val SHOW_WAVEFORM_SEEKBAR = booleanPreferencesKey("show_waveform_seekbar")
        val CORNER_RADIUS = intPreferencesKey("corner_radius")
        val BLUR_ARTWORK_BACKGROUND = booleanPreferencesKey("blur_artwork_background")
        val BLUR_STRENGTH = floatPreferencesKey("blur_strength")
        val ARTWORK_ANIMATION = booleanPreferencesKey("artwork_animation")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SHOW_BITRATE_INFO = booleanPreferencesKey("show_bitrate_info")
        val ALBUM_GRID_COLUMNS = intPreferencesKey("album_grid_columns")
        val MINI_PLAYER_STYLE = stringPreferencesKey("mini_player_style")
        val PLAYER_LAYOUT = stringPreferencesKey("player_layout")
        val SHOW_LYRICS_BUTTON = booleanPreferencesKey("show_lyrics_button")
        val LYRICS_FONT_SCALE = floatPreferencesKey("lyrics_font_scale")

        // Notification
        val LOCKSCREEN_ARTWORK = booleanPreferencesKey("lockscreen_artwork")
        val SHOW_SKIP_BUTTONS = booleanPreferencesKey("show_skip_buttons")

        // History
        val HISTORY_ENABLED = booleanPreferencesKey("history_enabled")
        val MIN_LISTEN_SECONDS = intPreferencesKey("min_listen_seconds")
        val MIN_LISTEN_PERCENTAGE = floatPreferencesKey("min_listen_percentage")
        val MAX_HISTORY_ITEMS = intPreferencesKey("max_history_items")

        // Last.fm
        val LAST_FM_ENABLED = booleanPreferencesKey("last_fm_enabled")
        val LAST_FM_NOW_PLAYING = booleanPreferencesKey("last_fm_now_playing")
        val LAST_FM_SCROBBLE_PERCENT = floatPreferencesKey("last_fm_scrobble_percent")
        val LAST_FM_SCROBBLE_MIN_SECS = intPreferencesKey("last_fm_scrobble_min_secs")
        val LAST_FM_ONLY_ON_WIFI = booleanPreferencesKey("last_fm_only_on_wifi")
        val LAST_FM_SCROBBLE_OFFLINE = booleanPreferencesKey("last_fm_scrobble_offline")
        val LAST_FM_USERNAME = stringPreferencesKey("last_fm_username")
        val LAST_FM_SESSION_KEY = stringPreferencesKey("last_fm_session_key")
        val LAST_FM_API_KEY = stringPreferencesKey("last_fm_api_key")
        val LAST_FM_API_SECRET = stringPreferencesKey("last_fm_api_secret")
    }

    val isFirstRun: Flow<Boolean> = ds.data.map { it[IS_FIRST_RUN] ?: true }

    suspend fun setFirstRunCompleted() {
        ds.edit { it[IS_FIRST_RUN] = false }
    }

    // ─── Playback ─────────────────────────────────────────────────────────────

    val repeatMode: Flow<RepeatMode> = ds.data.map {
        when (it[REPEAT_MODE]) {
            "ONE" -> RepeatMode.ONE
            "ALL" -> RepeatMode.ALL
            else -> RepeatMode.NONE
        }
    }

    suspend fun setRepeatMode(mode: RepeatMode) {
        ds.edit { it[REPEAT_MODE] = mode.name }
    }

    val shuffleEnabled: Flow<Boolean> = ds.data.map { it[SHUFFLE_ENABLED] ?: false }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        ds.edit { it[SHUFFLE_ENABLED] = enabled }
    }

    val crossfadeDurationMs: Flow<Int> = ds.data.map { it[CROSSFADE_DURATION_MS] ?: 0 }

    suspend fun setCrossfadeDuration(ms: Int) {
        ds.edit { it[CROSSFADE_DURATION_MS] = ms }
    }

    val skipSilence: Flow<Boolean> = ds.data.map { it[SKIP_SILENCE] ?: false }

    suspend fun setSkipSilence(enabled: Boolean) {
        ds.edit { it[SKIP_SILENCE] = enabled }
    }

    val replayGainMode: Flow<String> = ds.data.map { it[REPLAY_GAIN_MODE] ?: "TRACK" }

    suspend fun setReplayGainMode(mode: String) {
        ds.edit { it[REPLAY_GAIN_MODE] = mode }
    }

    val replayGainPreampDb: Flow<Float> = ds.data.map { it[REPLAY_GAIN_PREAMP_DB] ?: 0f }

    suspend fun setReplayGainPreamp(db: Float) {
        ds.edit { it[REPLAY_GAIN_PREAMP_DB] = db }
    }

    val playbackSpeed: Flow<Float> = ds.data.map { it[PLAYBACK_SPEED] ?: 1.0f }

    suspend fun setPlaybackSpeed(speed: Float) {
        ds.edit { it[PLAYBACK_SPEED] = speed }
    }

    val playbackPitch: Flow<Float> = ds.data.map { it[PLAYBACK_PITCH] ?: 1.0f }

    suspend fun setPlaybackPitch(pitch: Float) {
        ds.edit { it[PLAYBACK_PITCH] = pitch }
    }

    val smartShuffleEnabled: Flow<Boolean> = ds.data.map { it[SMART_SHUFFLE_ENABLED] ?: false }

    suspend fun setSmartShuffleEnabled(enabled: Boolean) {
        ds.edit { it[SMART_SHUFFLE_ENABLED] = enabled }
    }

    val resumeOnHeadphones: Flow<Boolean> = ds.data.map { it[RESUME_ON_HEADPHONES] ?: true }

    suspend fun setResumeOnHeadphones(enabled: Boolean) {
        ds.edit { it[RESUME_ON_HEADPHONES] = enabled }
    }

    val pauseOnHeadphonesOut: Flow<Boolean> = ds.data.map { it[PAUSE_ON_HEADPHONES_OUT] ?: true }

    suspend fun setPauseOnHeadphonesOut(enabled: Boolean) {
        ds.edit { it[PAUSE_ON_HEADPHONES_OUT] = enabled }
    }

    val duckAudioOnFocusLoss: Flow<Boolean> = ds.data.map { it[DUCK_AUDIO_ON_FOCUS_LOSS] ?: true }

    suspend fun setDuckAudioOnFocusLoss(enabled: Boolean) {
        ds.edit { it[DUCK_AUDIO_ON_FOCUS_LOSS] = enabled }
    }

    val volumeNormalization: Flow<Boolean> = ds.data.map { it[VOLUME_NORMALIZATION] ?: false }

    suspend fun setVolumeNormalization(enabled: Boolean) {
        ds.edit { it[VOLUME_NORMALIZATION] = enabled }
    }

    val gaplessEnabled: Flow<Boolean> = ds.data.map { it[GAPLESS_ENABLED] ?: true }

    suspend fun setGaplessEnabled(enabled: Boolean) {
        ds.edit { it[GAPLESS_ENABLED] = enabled }
    }

    // ─── Library ─────────────────────────────────────────────────────────────

    val minTrackDurationMs: Flow<Long> = ds.data.map { it[MIN_TRACK_DURATION_MS] ?: 0L }

    suspend fun setMinTrackDuration(ms: Long) {
        ds.edit { it[MIN_TRACK_DURATION_MS] = ms }
    }

    val artistDelimiter: Flow<String> = ds.data.map { it[ARTIST_DELIMITER] ?: ",;/&" }

    suspend fun setArtistDelimiter(delimiter: String) {
        ds.edit { it[ARTIST_DELIMITER] = delimiter }
    }

    val excludedFolders: Flow<Set<String>> = ds.data.map { it[EXCLUDED_FOLDERS] ?: emptySet() }

    suspend fun setExcludedFolders(folders: Set<String>) {
        ds.edit { it[EXCLUDED_FOLDERS] = folders }
    }

    val showArtworkInList: Flow<Boolean> = ds.data.map { it[SHOW_ARTWORK_IN_LIST] ?: true }

    suspend fun setShowArtworkInList(enabled: Boolean) {
        ds.edit { it[SHOW_ARTWORK_IN_LIST] = enabled }
    }

    val fetchArtistImages: Flow<Boolean> = ds.data.map { it[FETCH_ARTIST_IMAGES] ?: true }

    suspend fun setFetchArtistImages(enabled: Boolean) {
        ds.edit { it[FETCH_ARTIST_IMAGES] = enabled }
    }

    val groupByAlbumArtist: Flow<Boolean> = ds.data.map { it[GROUP_BY_ALBUM_ARTIST] ?: true }

    suspend fun setGroupByAlbumArtist(enabled: Boolean) {
        ds.edit { it[GROUP_BY_ALBUM_ARTIST] = enabled }
    }

    val showFilenameAsTitle: Flow<Boolean> = ds.data.map { it[SHOW_FILENAME_AS_TITLE] ?: false }

    suspend fun setShowFilenameAsTitle(enabled: Boolean) {
        ds.edit { it[SHOW_FILENAME_AS_TITLE] = enabled }
    }

    val ignoreArticles: Flow<Boolean> = ds.data.map { it[IGNORE_ARTICLES] ?: true }

    suspend fun setIgnoreArticles(enabled: Boolean) {
        ds.edit { it[IGNORE_ARTICLES] = enabled }
    }

    val autoScanIntervalHours: Flow<Int> = ds.data.map { it[AUTO_SCAN_INTERVAL_HOURS] ?: 0 }

    suspend fun setAutoScanIntervalHours(hours: Int) {
        ds.edit { it[AUTO_SCAN_INTERVAL_HOURS] = hours }
    }

    // ─── UI ──────────────────────────────────────────────────────────────────

    val dynamicColorEnabled: Flow<Boolean> = ds.data.map { it[DYNAMIC_COLOR_ENABLED] ?: true }

    suspend fun setDynamicColorEnabled(enabled: Boolean) {
        ds.edit { it[DYNAMIC_COLOR_ENABLED] = enabled }
    }

    val presetColor: Flow<Int?> = ds.data.map { it[PRESET_COLOR] }

    suspend fun setPresetColor(color: Int) {
        ds.edit { it[PRESET_COLOR] = color }
    }

    val darkTheme: Flow<String> = ds.data.map { it[DARK_THEME] ?: "SYSTEM" }

    suspend fun setDarkTheme(mode: String) {
        ds.edit { it[DARK_THEME] = mode }
    }

    val showWaveformSeekbar: Flow<Boolean> = ds.data.map { it[SHOW_WAVEFORM_SEEKBAR] ?: true }

    suspend fun setShowWaveformSeekbar(enabled: Boolean) {
        ds.edit { it[SHOW_WAVEFORM_SEEKBAR] = enabled }
    }

    val cornerRadius: Flow<Int> = ds.data.map { it[CORNER_RADIUS] ?: 28 }

    suspend fun setCornerRadius(radius: Int) {
        ds.edit { it[CORNER_RADIUS] = radius }
    }

    val blurArtworkBackground: Flow<Boolean> = ds.data.map { it[BLUR_ARTWORK_BACKGROUND] ?: true }

    suspend fun setBlurArtworkBackground(enabled: Boolean) {
        ds.edit { it[BLUR_ARTWORK_BACKGROUND] = enabled }
    }

    val blurStrength: Flow<Float> = ds.data.map { it[BLUR_STRENGTH] ?: 0.3f }

    suspend fun setBlurStrength(strength: Float) {
        ds.edit { it[BLUR_STRENGTH] = strength }
    }

    val artworkAnimation: Flow<Boolean> = ds.data.map { it[ARTWORK_ANIMATION] ?: true }

    suspend fun setArtworkAnimation(enabled: Boolean) {
        ds.edit { it[ARTWORK_ANIMATION] = enabled }
    }

    val hapticFeedback: Flow<Boolean> = ds.data.map { it[HAPTIC_FEEDBACK] ?: true }

    suspend fun setHapticFeedback(enabled: Boolean) {
        ds.edit { it[HAPTIC_FEEDBACK] = enabled }
    }

    val showBitrateInfo: Flow<Boolean> = ds.data.map { it[SHOW_BITRATE_INFO] ?: false }

    suspend fun setShowBitrateInfo(enabled: Boolean) {
        ds.edit { it[SHOW_BITRATE_INFO] = enabled }
    }

    val albumGridColumns: Flow<Int> = ds.data.map { it[ALBUM_GRID_COLUMNS] ?: 2 }

    suspend fun setAlbumGridColumns(columns: Int) {
        ds.edit { it[ALBUM_GRID_COLUMNS] = columns }
    }

    val miniPlayerStyle: Flow<String> = ds.data.map { it[MINI_PLAYER_STYLE] ?: "CARD" }

    suspend fun setMiniPlayerStyle(style: String) {
        ds.edit { it[MINI_PLAYER_STYLE] = style }
    }

    val playerLayout: Flow<String> = ds.data.map { it[PLAYER_LAYOUT] ?: "STANDARD" }

    suspend fun setPlayerLayout(layout: String) {
        ds.edit { it[PLAYER_LAYOUT] = layout }
    }

    val showLyricsButton: Flow<Boolean> = ds.data.map { it[SHOW_LYRICS_BUTTON] ?: true }

    suspend fun setShowLyricsButton(enabled: Boolean) {
        ds.edit { it[SHOW_LYRICS_BUTTON] = enabled }
    }

    val lyricsFontScale: Flow<Float> = ds.data.map { it[LYRICS_FONT_SCALE] ?: 1.0f }

    suspend fun setLyricsFontScale(scale: Float) {
        ds.edit { it[LYRICS_FONT_SCALE] = scale }
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    val lockscreenArtwork: Flow<Boolean> = ds.data.map { it[LOCKSCREEN_ARTWORK] ?: true }

    suspend fun setLockscreenArtwork(enabled: Boolean) {
        ds.edit { it[LOCKSCREEN_ARTWORK] = enabled }
    }

    val showSkipButtons: Flow<Boolean> = ds.data.map { it[SHOW_SKIP_BUTTONS] ?: true }

    suspend fun setShowSkipButtons(enabled: Boolean) {
        ds.edit { it[SHOW_SKIP_BUTTONS] = enabled }
    }

    // ─── History ─────────────────────────────────────────────────────────────

    val historyEnabled: Flow<Boolean> = ds.data.map { it[HISTORY_ENABLED] ?: true }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        ds.edit { it[HISTORY_ENABLED] = enabled }
    }

    val minListenSeconds: Flow<Int> = ds.data.map { it[MIN_LISTEN_SECONDS] ?: 30 }
    val minListenPercentage: Flow<Float> = ds.data.map { it[MIN_LISTEN_PERCENTAGE] ?: 0.5f }

    suspend fun setListenThresholds(minSeconds: Int, minPercent: Float) {
        ds.edit {
            it[MIN_LISTEN_SECONDS] = minSeconds
            it[MIN_LISTEN_PERCENTAGE] = minPercent
        }
    }

    val maxHistoryItems: Flow<Int> = ds.data.map { it[MAX_HISTORY_ITEMS] ?: 1000 }

    suspend fun setMaxHistoryItems(max: Int) {
        ds.edit { it[MAX_HISTORY_ITEMS] = max }
    }

    // ─── Last.fm ─────────────────────────────────────────────────────────────

    val lastFmEnabled: Flow<Boolean> = ds.data.map { it[LAST_FM_ENABLED] ?: false }

    suspend fun setLastFmEnabled(enabled: Boolean) {
        ds.edit { it[LAST_FM_ENABLED] = enabled }
    }

    val lastFmNowPlaying: Flow<Boolean> = ds.data.map { it[LAST_FM_NOW_PLAYING] ?: true }

    suspend fun setLastFmNowPlaying(enabled: Boolean) {
        ds.edit { it[LAST_FM_NOW_PLAYING] = enabled }
    }

    val lastFmScrobblePercent: Flow<Float> = ds.data.map { it[LAST_FM_SCROBBLE_PERCENT] ?: 0.5f }

    suspend fun setLastFmScrobblePercent(percent: Float) {
        ds.edit { it[LAST_FM_SCROBBLE_PERCENT] = percent }
    }

    val lastFmScrobbleMinSecs: Flow<Int> = ds.data.map { it[LAST_FM_SCROBBLE_MIN_SECS] ?: 30 }

    suspend fun setLastFmScrobbleMinSecs(seconds: Int) {
        ds.edit { it[LAST_FM_SCROBBLE_MIN_SECS] = seconds }
    }

    val lastFmOnlyOnWifi: Flow<Boolean> = ds.data.map { it[LAST_FM_ONLY_ON_WIFI] ?: false }

    suspend fun setLastFmOnlyOnWifi(enabled: Boolean) {
        ds.edit { it[LAST_FM_ONLY_ON_WIFI] = enabled }
    }

    val lastFmScrobbleOffline: Flow<Boolean> = ds.data.map { it[LAST_FM_SCROBBLE_OFFLINE] ?: true }

    suspend fun setLastFmScrobbleOffline(enabled: Boolean) {
        ds.edit { it[LAST_FM_SCROBBLE_OFFLINE] = enabled }
    }

    val lastFmUsername: Flow<String?> = ds.data.map { it[LAST_FM_USERNAME] }
    val lastFmSessionKey: Flow<String?> = ds.data.map { it[LAST_FM_SESSION_KEY] }
    val lastFmApiKey: Flow<String?> = ds.data.map { it[LAST_FM_API_KEY] }
    val lastFmApiSecret: Flow<String?> = ds.data.map { it[LAST_FM_API_SECRET] }

    suspend fun setLastFmSession(username: String, sessionKey: String) {
        ds.edit {
            it[LAST_FM_USERNAME] = username
            it[LAST_FM_SESSION_KEY] = sessionKey
        }
    }

    suspend fun clearLastFmSession() {
        ds.edit {
            it.remove(LAST_FM_USERNAME)
            it.remove(LAST_FM_SESSION_KEY)
        }
    }
}