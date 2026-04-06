package dev.yuwixx.resonance.domain.usecase

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yuwixx.resonance.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tag editor use case.
 *
 * Reads current embedded metadata via MediaMetadataRetriever and writes
 * updated values back to MediaStore (which propagates to the file).
 *
 * For full FFmpeg-based tag writing (FLAC, OGG etc.), integrate the
 * media3-transformer or jaudiotagger library — this implementation handles
 * MediaStore-writable fields for MP3/M4A/MP4.
 */
@Singleton
class TagEditor @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class SongTags(
        val title: String,
        val artist: String,
        val albumArtist: String,
        val album: String,
        val genre: String,
        val year: String,
        val trackNumber: String,
        val discNumber: String,
        val comment: String,
        val lyrics: String,
    )

    /**
     * Read all embedded tags from the audio file.
     */
    suspend fun readTags(song: Song): SongTags = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, song.uri)
            SongTags(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: song.title,
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: song.artist,
                albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST) ?: song.albumArtist,
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: song.album,
                genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) ?: song.genre,
                year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR) ?: song.year.toString(),
                trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER) ?: song.trackNumber.toString(),
                discNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER) ?: song.discNumber.toString(),
                comment = "",
                lyrics = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "",
            )
        } finally {
            retriever.release()
        }
    }

    /**
     * Write updated tags back to MediaStore.
     * Note: MANAGE_EXTERNAL_STORAGE or write access via SAF is required
     * for direct file modification. MediaStore values update the system DB
     * immediately; file-level tag writing requires a separate TagLib binding.
     */
    suspend fun writeTags(song: Song, tags: SongTags): Boolean = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, tags.title)
                put(MediaStore.Audio.Media.ARTIST, tags.artist)
                put(MediaStore.Audio.Media.ALBUM_ARTIST, tags.albumArtist)
                put(MediaStore.Audio.Media.ALBUM, tags.album)
                put(MediaStore.Audio.Media.GENRE, tags.genre)
                put(MediaStore.Audio.Media.YEAR, tags.year.toIntOrNull() ?: 0)
                put(MediaStore.Audio.Media.TRACK, tags.trackNumber.toIntOrNull() ?: 0)
            }
            val updated = context.contentResolver.update(song.uri, values, null, null)
            updated > 0
        } catch (e: Exception) {
            false
        }
    }
}
