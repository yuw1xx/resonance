package dev.yuwixx.resonance.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yuwixx.resonance.data.database.dao.PlaylistDao
import dev.yuwixx.resonance.data.database.dao.SongDao
import dev.yuwixx.resonance.data.database.entity.PlaylistEntity
import dev.yuwixx.resonance.data.database.entity.PlaylistSongCrossRef
import dev.yuwixx.resonance.data.model.Playlist
import dev.yuwixx.resonance.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val songDao: SongDao,
) {
    val allPlaylists: Flow<List<Playlist>> = playlistDao.getAllPlaylists().flatMapLatest { entities ->
        if (entities.isEmpty()) return@flatMapLatest flowOf(emptyList())

        val playlistFlows = entities.map { entity ->
            flow {
                val refs = playlistDao.getPlaylistSongRefs(entity.id)
                val songs = refs.mapNotNull { ref ->
                    songDao.getSongById(ref.songId)?.toDomain()
                }
                emit(
                    Playlist(
                        id = entity.id,
                        name = entity.name,
                        songs = songs,
                        isReadOnly = entity.isReadOnly,
                        artworkUri = entity.artworkUri?.let { Uri.parse(it) },
                        dateCreated = entity.dateCreated,
                        dateModified = entity.dateModified,
                    )
                )
            }
        }
        combine(playlistFlows) { it.toList() }
    }

    fun getPlaylistById(id: Long): Flow<Playlist?> = allPlaylists.map { playlists ->
        playlists.find { it.id == id }
    }

    suspend fun createPlaylist(name: String): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name, artworkUri = null)
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        val entity = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.deletePlaylist(entity)
    }

    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) {
        val refs = playlistDao.getPlaylistSongRefs(playlistId)
        val nextPos = refs.size
        playlistDao.addSongToPlaylist(
            PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = nextPos)
        )
        updateModifiedTime(playlistId)
    }

    suspend fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        val refs = playlistDao.getPlaylistSongRefs(playlistId)
        var nextPos = refs.size
        songIds.forEach { songId ->
            playlistDao.addSongToPlaylist(
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, position = nextPos++)
            )
        }
        updateModifiedTime(playlistId)
    }

    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
        updateModifiedTime(playlistId)
    }

    suspend fun renamePlaylist(playlistId: Long, newName: String) {
        val entity = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(
            entity.copy(name = newName, dateModified = System.currentTimeMillis())
        )
    }

    /**
     * Returns M3U8 playlist content ready to be written to a file.
     * Compatible with VLC, Poweramp, foobar2000, and all major media players.
     */
    fun exportPlaylistAsM3U(playlist: Playlist): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#PLAYLIST:${playlist.name}")
        playlist.songs.forEach { song ->
            val durationSec = (song.duration / 1000).toInt()
            sb.appendLine("#EXTINF:$durationSec,${song.artist} - ${song.title}")
            sb.appendLine(song.path)
        }
        return sb.toString().trimEnd()
    }

    private suspend fun updateModifiedTime(playlistId: Long) {
        val entity = playlistDao.getPlaylistById(playlistId) ?: return
        playlistDao.updatePlaylist(entity.copy(dateModified = System.currentTimeMillis()))
    }
}