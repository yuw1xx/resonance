package dev.yuwixx.resonance.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.yuwixx.resonance.data.database.dao.*
import dev.yuwixx.resonance.data.database.entity.*

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        HistoryEntity::class,
        ArtistArtworkEntity::class,
        LyricsEntity::class,
        QueueEntity::class,
        LikedSongEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ResonanceDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun historyDao(): HistoryDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun artworkDao(): ArtworkDao
    abstract fun queueDao(): QueueDao
    abstract fun likedSongsDao(): LikedSongsDao
}
