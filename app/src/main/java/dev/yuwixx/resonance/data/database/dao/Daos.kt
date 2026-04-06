package dev.yuwixx.resonance.data.database.dao

import androidx.room.*
import dev.yuwixx.resonance.data.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun getSongById(id: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE folder = :folder")
    fun getSongsByFolder(folder: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY trackNumber ASC")
    fun getSongsByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist LIKE '%' || :artist || '%'")
    fun getSongsByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE genre = :genre")
    fun getSongsByGenre(genre: String): Flow<List<SongEntity>>

    @Query("""
        SELECT * FROM songs 
        WHERE title LIKE '%' || :query || '%'
           OR artist LIKE '%' || :query || '%'
           OR album LIKE '%' || :query || '%'
           OR genre LIKE '%' || :query || '%'
        ORDER BY listenCount DESC
    """)
    fun searchSongs(query: String): Flow<List<SongEntity>>

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id NOT IN (:existingIds)")
    suspend fun deleteRemovedSongs(existingIds: List<Long>)

    @Query("UPDATE songs SET listenCount = listenCount + 1, lastListened = :time WHERE id = :id")
    suspend fun incrementListenCount(id: Long, time: Long)

    // Most played
    @Query("SELECT * FROM songs ORDER BY listenCount DESC LIMIT :limit")
    suspend fun getMostPlayed(limit: Int = 50): List<SongEntity>

    // Distinct folders
    @Query("SELECT DISTINCT folder FROM songs ORDER BY folder ASC")
    fun getAllFolders(): Flow<List<String>>

    // Distinct genres
    @Query("SELECT DISTINCT genre FROM songs WHERE genre != '' ORDER BY genre ASC")
    fun getAllGenres(): Flow<List<String>>
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY dateModified DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun getPlaylistById(id: Long): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistSongRefs(playlistId: Long): List<PlaylistSongCrossRef>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSongToPlaylist(ref: PlaylistSongCrossRef)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("UPDATE playlist_songs SET position = :position WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun updateSongPosition(playlistId: Long, songId: Long, position: Int)
}

@Dao
interface HistoryDao {
    @Insert
    suspend fun insertHistory(history: HistoryEntity)

    @Query("""
        SELECT * FROM history 
        WHERE listenedAt BETWEEN :from AND :to
        ORDER BY listenedAt DESC
    """)
    fun getHistoryInRange(from: Long, to: Long): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY listenedAt DESC LIMIT 200")
    fun getRecentHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history")
    suspend fun getAllHistory(): List<HistoryEntity>

    // Namida's "Lost Memories" — same date window N years ago
    @Query("""
        SELECT * FROM history
        WHERE (listenedAt % 31536000000) BETWEEN :windowStart AND :windowEnd
        AND listenedAt < :beforeTime
        ORDER BY listenedAt DESC
        LIMIT 50
    """)
    suspend fun getLostMemories(windowStart: Long, windowEnd: Long, beforeTime: Long): List<HistoryEntity>

    @Query("DELETE FROM history WHERE listenedAt < :before")
    suspend fun pruneHistory(before: Long)

    @Query("DELETE FROM history")
    suspend fun clearAllHistory()
}

@Dao
interface LyricsDao {
    @Query("SELECT * FROM lyrics WHERE songId = :songId")
    suspend fun getLyrics(songId: Long): LyricsEntity?

    @Upsert
    suspend fun upsertLyrics(lyrics: LyricsEntity)
}

@Dao
interface LikedSongsDao {
    @Query("SELECT songId FROM liked_songs ORDER BY likedAt DESC")
    fun getLikedSongIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun likeSong(entity: LikedSongEntity)

    @Query("DELETE FROM liked_songs WHERE songId = :songId")
    suspend fun unlikeSong(songId: Long)

    @Query("SELECT COUNT(*) FROM liked_songs WHERE songId = :songId")
    suspend fun isLiked(songId: Long): Int
}

@Dao
interface ArtworkDao {
    @Query("SELECT * FROM artist_artwork WHERE artistName = :artistName")
    suspend fun getArtistArtwork(artistName: String): ArtistArtworkEntity?

    @Upsert
    suspend fun upsertArtistArtwork(artwork: ArtistArtworkEntity)
}

@Dao
interface QueueDao {
    @Query("SELECT * FROM queues WHERE id = 0")
    suspend fun getCurrentQueue(): QueueEntity?

    @Upsert
    suspend fun saveQueue(queue: QueueEntity)
}