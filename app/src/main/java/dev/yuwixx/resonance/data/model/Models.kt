package dev.yuwixx.resonance.data.model

import android.net.Uri

/**
 * Core Song domain model. Populated from MediaStore + Room metadata.
 */
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val artists: List<String>,     // Parsed with configurable delimiters (à la PixelPlayer)
    val albumArtist: String,
    val album: String,
    val albumId: Long,
    val genre: String,
    val duration: Long,            // Milliseconds
    val size: Long,                // Bytes
    val bitrate: Int,              // kbps
    val sampleRate: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val dateAdded: Long,           // Unix timestamp
    val dateModified: Long,
    val path: String,
    val folder: String,
    val mimeType: String,
    val replayGainTrack: Float?,   // ReplayGain 2.0 (Gramophone-style)
    val replayGainAlbum: Float?,
    val artworkUri: Uri?,
    val listenCount: Int = 0,      // From Namida's history system
    val lastListened: Long = 0L,
) {
    val displayArtist: String get() = artists.joinToString(", ")
}

/**
 * Album model, derived from songs.
 */
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songCount: Int,
    val artworkUri: Uri?,
    val songs: List<Song> = emptyList(),
    val totalDuration: Long = songs.sumOf { it.duration },
)

/**
 * Artist model.
 */
data class Artist(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val artworkUrl: String? = null,  // Fetched from Deezer API
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
)

/**
 * Playlist model. Supports both user-created and read-only M3U playlists (Gramophone-style).
 */
data class Playlist(
    val id: Long,
    val name: String,
    val songs: List<Song> = emptyList(),
    val isReadOnly: Boolean = false,   // For M3U/system playlists
    val artworkUri: Uri? = null,
    val dateCreated: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
) {
    val songCount: Int get() = songs.size
    val totalDuration: Long get() = songs.sumOf { it.duration }
}

/**
 * Queue model for session persistence (Namida-style persistent queue).
 */
data class PlaybackQueue(
    val songs: List<Song>,
    val currentIndex: Int,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode,
    val originalOrder: List<Int>,   // For shuffle/unshuffle
)

enum class RepeatMode { NONE, ONE, ALL }

/**
 * Synced lyric line — supports LRC, TTML, and word-synced karaoke (Gramophone + Namida).
 */
data class LyricLine(
    val timeMs: Long,
    val text: String,
    val wordTimings: List<WordTiming> = emptyList(),   // For karaoke/word-sync
)

data class WordTiming(
    val startMs: Long,
    val endMs: Long,
    val word: String,
)

/**
 * Smart generated queue result (Namida's SMORT algorithm).
 */
data class SmartQueueResult(
    val songs: List<Song>,
    val reason: SmartQueueReason,
)

enum class SmartQueueReason {
    RELATED_BY_HISTORY,
    SIMILAR_RELEASE_DATE,
    SAME_GENRE,
    SAME_ERA,
    RANDOM_DISCOVERY,
    MOST_PLAYED,
    LOST_MEMORIES,    // Tracks you listened to around this date N years ago
}

/**
 * Sleep timer state.
 */
sealed class SleepTimer {
    data object Off : SleepTimer()
    data class AfterTracks(val count: Int) : SleepTimer()
    data class AfterMinutes(val minutes: Int, val startedAt: Long) : SleepTimer()
}

/**
 * Waveform data for the waveform seekbar.
 */
data class WaveformData(
    val amplitudes: FloatArray,   // Normalized 0..1
    val resolution: Int = 200,
) {
    override fun equals(other: Any?): Boolean =
        other is WaveformData && amplitudes.contentEquals(other.amplitudes)

    override fun hashCode(): Int = amplitudes.contentHashCode()
}

/**
 * Sort options supported for the library.
 */
enum class SortOrder {
    TITLE_ASC, TITLE_DESC,
    ARTIST_ASC, ARTIST_DESC,
    ALBUM_ASC, ALBUM_DESC,
    DATE_ADDED_ASC, DATE_ADDED_DESC,
    DURATION_ASC, DURATION_DESC,
    TRACK_NUMBER,
    LISTEN_COUNT_DESC,
    NATURAL,    // Gramophone-style natural string sort
}
