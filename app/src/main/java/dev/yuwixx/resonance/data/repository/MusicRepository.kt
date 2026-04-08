package dev.yuwixx.resonance.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yuwixx.resonance.data.database.dao.*
import dev.yuwixx.resonance.data.database.entity.*
import dev.yuwixx.resonance.data.model.*
import dev.yuwixx.resonance.data.preferences.ResonancePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val historyDao: HistoryDao,
    private val playlistDao: PlaylistDao,
    private val prefs: ResonancePreferences,
) {
    // ─── Flows from Room ─────────────────────────────────────────────────────

    val allSongs: Flow<List<Song>> = combine(
        songDao.getAllSongs(),
        prefs.artistDelimiter,
        prefs.excludedFolders,
        prefs.showFilenameAsTitle
    ) { entities, delimiter, excluded, showFilename ->
        entities
            .filter { entity -> excluded.none { entity.folder.startsWith(it) } }
            .map { entity ->
                val song = entity.toDomain(delimiter)
                // Apply "Show Filename as Title" if title is unknown or preference is forced
                if (showFilename && (song.title == "Unknown" || song.title.isBlank())) {
                    val fallbackTitle = java.io.File(song.path).nameWithoutExtension
                    song.copy(title = fallbackTitle)
                } else song
            }
    }.flowOn(Dispatchers.Default)

    val allFolders: Flow<List<String>> = songDao.getAllFolders()

    val allGenres: Flow<List<String>> = songDao.getAllGenres()

    val allAlbums: Flow<List<Album>> = combine(
        allSongs,
        prefs.groupByAlbumArtist,
        prefs.ignoreArticles
    ) { songs, groupByAlbumArtist, ignoreArticles ->
        songs.groupBy {
            // Group by Album Artist if enabled, otherwise fallback to strictly Album ID
            if (groupByAlbumArtist && it.albumArtist.isNotBlank()) "${it.albumArtist}_${it.album}"
            else it.albumId.toString()
        }.map { (_, albumSongs) ->
            val first = albumSongs.first()
            Album(
                id = first.albumId,
                title = first.album,
                artist = first.albumArtist.ifEmpty { first.displayArtist },
                year = first.year,
                songCount = albumSongs.size,
                artworkUri = first.artworkUri,
                songs = albumSongs.sortedWith(compareBy({ it.discNumber }, { it.trackNumber })),
            )
        }.sortedWith(Comparator { a, b ->
            naturalCompare(a.title.stripArticles(ignoreArticles), b.title.stripArticles(ignoreArticles))
        })
    }.flowOn(Dispatchers.Default)

    val allArtists: Flow<List<Artist>> = combine(
        allSongs,
        prefs.ignoreArticles
    ) { songs, ignoreArticles ->
        val artistMap = mutableMapOf<String, MutableList<Song>>()
        songs.forEach { song ->
            song.artists.forEach { artist ->
                artistMap.getOrPut(artist) { mutableListOf() }.add(song)
            }
        }
        artistMap.map { (name, artistSongs) ->
            val albums = artistSongs.map { it.album }.distinct()
            Artist(
                name = name,
                songCount = artistSongs.size,
                albumCount = albums.size,
                songs = artistSongs,
            )
        }.sortedWith(Comparator { a, b ->
            naturalCompare(a.name.stripArticles(ignoreArticles), b.name.stripArticles(ignoreArticles))
        })
    }.flowOn(Dispatchers.Default)

    // Helper to strip "The ", "A ", "An " for sorting purposes only
    private fun String.stripArticles(ignore: Boolean): String {
        if (!ignore) return this
        val lower = this.lowercase()
        return when {
            lower.startsWith("the ") -> this.substring(4)
            lower.startsWith("a ") -> this.substring(2)
            lower.startsWith("an ") -> this.substring(3)
            else -> this
        }.trim()
    }

    // FIX: Pass delimiter from prefs so user-configured delimiters are respected
    fun searchSongs(query: String): Flow<List<Song>> =
        combine(songDao.searchSongs(query), prefs.artistDelimiter) { entities, delimiter ->
            entities.map { it.toDomain(delimiter) }
        }

    // FIX: Same — pass delimiter from prefs
    fun getSongsByFolder(folder: String): Flow<List<Song>> =
        combine(songDao.getSongsByFolder(folder), prefs.artistDelimiter) { entities, delimiter ->
            entities.map { it.toDomain(delimiter) }
        }

    fun getSongsByGenre(genre: String): Flow<List<Song>> =
        combine(songDao.getSongsByGenre(genre), prefs.artistDelimiter) { entities, delimiter ->
            entities.map { it.toDomain(delimiter) }
        }

    // ─── MediaStore scan ─────────────────────────────────────────────────────

    suspend fun syncWithMediaStore() = withContext(Dispatchers.IO) {
        val minDuration = prefs.minTrackDurationMs.first()
        val foundIds = mutableListOf<Long>()
        val entities = mutableListOf<SongEntity>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= ?"
        val selectionArgs = arrayOf(minDuration.toString())

        try {
            context.contentResolver.query(
                collection, projection, selection, selectionArgs, null
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumArtistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val path = cursor.getString(dataCol) ?: ""
                    val folder = path.substringBeforeLast("/", "")
                    val trackRaw = cursor.getInt(trackCol)

                    foundIds.add(id)
                    entities.add(
                        SongEntity(
                            id = id,
                            uri = ContentUris.withAppendedId(collection, id).toString(),
                            title = cursor.getString(titleCol) ?: "Unknown",
                            artist = cursor.getString(artistCol) ?: "Unknown Artist",
                            albumArtist = cursor.getString(albumArtistCol) ?: "",
                            album = cursor.getString(albumCol) ?: "Unknown Album",
                            albumId = cursor.getLong(albumIdCol),
                            genre = "",
                            duration = cursor.getLong(durationCol),
                            size = cursor.getLong(sizeCol),
                            bitrate = 0,
                            sampleRate = 0,
                            trackNumber = trackRaw % 1000,
                            discNumber = trackRaw / 1000,
                            year = cursor.getInt(yearCol),
                            dateAdded = cursor.getLong(dateAddedCol) * 1000L,
                            dateModified = cursor.getLong(dateModifiedCol) * 1000L,
                            path = path,
                            folder = folder,
                            mimeType = cursor.getString(mimeCol) ?: "",
                            replayGainTrack = null,
                            replayGainAlbum = null,
                        )
                    )
                }
            }

            // FIX: Always run both operations regardless of whether entities is empty.
            // Previously, if the library was cleared, deleteRemovedSongs was never called
            // and stale entries would remain in the DB forever.
            songDao.upsertSongs(entities)
            if (foundIds.isEmpty()) {
                songDao.deleteAllSongs()
            } else {
                songDao.deleteRemovedSongs(foundIds)
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Sync failed", e)
        }
    }

    // ─── History ─────────────────────────────────────────────────────────────

    suspend fun recordListen(songId: Long, durationListened: Long, totalDuration: Long) {
        // Fix 1: Abort completely if the user disabled tracking in Settings
        if (!prefs.historyEnabled.first()) return

        val minSeconds = prefs.minListenSeconds.first() * 1000L
        val minPct = prefs.minListenPercentage.first()
        val pct = if (totalDuration > 0) durationListened.toFloat() / totalDuration else 0f

        if (durationListened >= minSeconds || pct >= minPct) {
            historyDao.insertHistory(
                HistoryEntity(
                    songId = songId,
                    listenedAt = System.currentTimeMillis(),
                    durationListened = durationListened,
                    percentageListened = pct,
                )
            )
            songDao.incrementListenCount(songId, System.currentTimeMillis())

            val maxItems = prefs.maxHistoryItems.first()
            // Note: Make sure your HistoryDao has a method like this:
            // @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY listenedAt DESC LIMIT :limit)")
            // suspend fun trimHistory(limit: Int)
            historyDao.trimHistory(maxItems)
        }
    }

    suspend fun getMostPlayedSongs(limit: Int = 50): List<Song> =
        songDao.getMostPlayed(limit).map { it.toDomain() }

    suspend fun clearAllHistory() = withContext(Dispatchers.IO) {
        historyDao.clearAllHistory()
    }

    // ─── Smart Queue Generation ──────────────────────────────────────────────

    suspend fun generateSmartQueue(
        seedSong: Song,
        reason: SmartQueueReason,
        limit: Int = 25,
    ): SmartQueueResult = withContext(Dispatchers.Default) {
        val allSongs = songDao.getMostPlayed(500).map { it.toDomain() }

        val result = when (reason) {
            SmartQueueReason.RELATED_BY_HISTORY -> {
                allSongs.filter { s ->
                    s.id != seedSong.id &&
                            (s.artists.any { it in seedSong.artists } || s.genre == seedSong.genre)
                }.sortedByDescending { it.listenCount }.take(limit)
            }
            SmartQueueReason.SIMILAR_RELEASE_DATE -> {
                val range = 2
                allSongs.filter { s ->
                    s.id != seedSong.id && seedSong.year > 0 &&
                            s.year in (seedSong.year - range)..(seedSong.year + range)
                }.sortedByDescending { it.listenCount }.take(limit)
            }
            SmartQueueReason.SAME_GENRE -> {
                allSongs.filter { s ->
                    s.id != seedSong.id && s.genre == seedSong.genre && s.genre.isNotEmpty()
                }.sortedByDescending { it.listenCount }.take(limit)
            }
            SmartQueueReason.MOST_PLAYED -> {
                allSongs.filter { it.id != seedSong.id }
                    .sortedByDescending { it.listenCount }.take(limit)
            }
            SmartQueueReason.LOST_MEMORIES -> {
                // FIX: Previous code used `now % oneYearMs` which produced a relative offset
                // within the current year — not comparable to absolute epoch timestamps in the DB.
                // Correct approach: query history entries that fall within ±7 days of the same
                // calendar day-of-year, but from at least one year ago.
                val now = System.currentTimeMillis()
                val oneYearAgoMs = now - 365L * 24 * 60 * 60 * 1000L
                val sevenDaysMs = 7L * 24 * 60 * 60 * 1000L

                val cal = Calendar.getInstance()
                val todayDayOfYear = cal.get(Calendar.DAY_OF_YEAR)
                val todayYear = cal.get(Calendar.YEAR)

                // Collect history entries from the same ±7 day window in past years
                val history = historyDao.getAllHistory()
                val memorySongIds = history
                    .filter { entry ->
                        if (entry.listenedAt >= oneYearAgoMs) return@filter false
                        cal.timeInMillis = entry.listenedAt
                        val entryDay = cal.get(Calendar.DAY_OF_YEAR)
                        kotlin.math.abs(entryDay - todayDayOfYear) <= 7
                    }
                    .map { it.songId }
                    .distinct()

                allSongs.filter { it.id in memorySongIds }.take(limit)
            }
            else -> allSongs.shuffled().take(limit)
        }

        SmartQueueResult(songs = result, reason = reason)
    }

    // ─── Tag Editor ──────────────────────────────────────────────────────────

    suspend fun updateSongTags(songId: Long, title: String, artist: String, album: String, genre: String, year: Int, trackNumber: Int) = withContext(Dispatchers.IO) {
        val entity = songDao.getSongById(songId) ?: return@withContext
        val updated = entity.copy(
            title = title,
            artist = artist,
            album = album,
            genre = genre,
            year = year,
            trackNumber = trackNumber
        )

        // Update Database instantly for snappy UI
        songDao.upsertSongs(listOf(updated))

        // Attempt to update the physical file's ID3 tags
        try {
            val file = java.io.File(entity.path)
            if (file.exists() && file.canWrite()) {
                val audioFile = org.jaudiotagger.audio.AudioFileIO.read(file)
                val tag = audioFile.tagOrCreateAndSetDefault
                tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, title)
                tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, artist)
                tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, album)
                tag.setField(org.jaudiotagger.tag.FieldKey.GENRE, genre)
                tag.setField(org.jaudiotagger.tag.FieldKey.YEAR, year.toString())
                tag.setField(org.jaudiotagger.tag.FieldKey.TRACK, trackNumber.toString())
                org.jaudiotagger.audio.AudioFileIO.write(audioFile)
            }
        } catch (e: Exception) {
            Log.e("MusicRepository", "Failed to write physical ID3 tag (Likely Android 11+ Scoped Storage restriction). Database updated successfully.", e)
        }
    }

    // ─── Natural sort ────────────────────────────────────────────────────────

    private fun naturalCompare(a: String, b: String): Int {
        val re = Regex("(\\d+|\\D+)")
        val chunksA = re.findAll(a).map { it.value }.toList()
        val chunksB = re.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(chunksA.size, chunksB.size)) {
            val ca = chunksA[i]; val cb = chunksB[i]
            val diff = if (ca[0].isDigit() && cb[0].isDigit()) {
                ca.toLong().compareTo(cb.toLong())
            } else ca.compareTo(cb, ignoreCase = true)
            if (diff != 0) return diff
        }
        return chunksA.size - chunksB.size
    }
}

// ─── Extension: Entity → Domain ───────────────────────────────────────────────

fun SongEntity.toDomain(artistDelimiter: String = ",;/&"): Song {
    val artworkUri = ContentUris.withAppendedId(
        Uri.parse("content://media/external/audio/albumart"), albumId
    )
    val delimPattern = Regex("[${Regex.escape(artistDelimiter)}]")
    val artists = artist.split(delimPattern)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .ifEmpty { listOf(artist) }

    return Song(
        id = id,
        uri = Uri.parse(uri),
        title = title,
        artist = artist,
        artists = artists,
        albumArtist = albumArtist,
        album = album,
        albumId = albumId,
        genre = genre,
        duration = duration,
        size = size,
        bitrate = bitrate,
        sampleRate = sampleRate,
        trackNumber = trackNumber,
        discNumber = discNumber,
        year = year,
        dateAdded = dateAdded,
        dateModified = dateModified,
        path = path,
        folder = folder,
        mimeType = mimeType,
        replayGainTrack = replayGainTrack,
        replayGainAlbum = replayGainAlbum,
        artworkUri = artworkUri,
        listenCount = listenCount,
        lastListened = lastListened,
    )
}