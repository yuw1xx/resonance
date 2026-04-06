package dev.yuwixx.resonance.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val albumArtist: String,
    val album: String,
    val albumId: Long,
    val genre: String,
    val duration: Long,
    val size: Long,
    val bitrate: Int,
    val sampleRate: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val year: Int,
    val dateAdded: Long,
    val dateModified: Long,
    val path: String,
    val folder: String,
    val mimeType: String,
    val replayGainTrack: Float?,
    val replayGainAlbum: Float?,
    val listenCount: Int = 0,
    val lastListened: Long = 0L,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isReadOnly: Boolean = false,
    val artworkUri: String?,
    val dateCreated: Long = System.currentTimeMillis(),
    val dateModified: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val listenedAt: Long,
    val durationListened: Long,  // How long the track was actually listened to
    val percentageListened: Float,
)

@Entity(tableName = "artist_artwork")
data class ArtistArtworkEntity(
    @PrimaryKey val artistName: String,
    val artworkUrl: String?,
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val songId: Long,
    val synced: String?,     // LRC/TTML raw text
    val plain: String?,
    val source: String,      // "lrclib", "embedded", "manual"
    val fetchedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "queues")
data class QueueEntity(
    @PrimaryKey val id: Long = 0L,  // Always 0 for the current queue
    val songIds: String,            // JSON array of Long
    val currentIndex: Int,
    val shuffleEnabled: Boolean,
    val repeatMode: String,
    val originalOrder: String,      // JSON array of Int
    val savedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "liked_songs")
data class LikedSongEntity(
    @PrimaryKey val songId: Long,
    val likedAt: Long = System.currentTimeMillis(),
)
