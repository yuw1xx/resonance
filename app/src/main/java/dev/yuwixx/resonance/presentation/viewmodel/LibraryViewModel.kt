package dev.yuwixx.resonance.presentation.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yuwixx.resonance.data.model.*
import dev.yuwixx.resonance.data.preferences.ResonancePreferences
import dev.yuwixx.resonance.data.repository.ArtworkRepository
import dev.yuwixx.resonance.data.repository.MusicRepository
import dev.yuwixx.resonance.data.repository.PlaylistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val playlistRepository: PlaylistRepository,
    val prefs: ResonancePreferences,
    private val artworkRepository: ArtworkRepository,
) : ViewModel() {

    suspend fun getArtistArtworkUrl(artistName: String): String? =
        artworkRepository.getArtistArtworkUrl(artistName)

    // ─── Search ───────────────────────────────────────────────────────────────

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Song>> = _searchQuery
        .debounce(300)
        .flatMapLatest { query ->
            if (query.isBlank()) flowOf(emptyList())
            else musicRepository.searchSongs(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearch(query: String) { _searchQuery.value = query }

    // ─── Library tabs ─────────────────────────────────────────────────────────

    val allSongs: StateFlow<List<Song>> = musicRepository.allSongs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allAlbums: StateFlow<List<Album>> = musicRepository.allAlbums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allArtists: StateFlow<List<Artist>> = musicRepository.allArtists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allFolders: StateFlow<List<String>> = musicRepository.allFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allGenres: StateFlow<List<String>> = musicRepository.allGenres
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Playlists ────────────────────────────────────────────────────────────

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getPlaylistById(id: Long): Flow<Playlist?> = playlistRepository.getPlaylistById(id)

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(id)
        }
    }

    fun updatePlaylistArtwork(id: Long, artworkUri: Uri?) {
        viewModelScope.launch { playlistRepository.updatePlaylistArtwork(id, artworkUri) }
    }

    fun reorderPlaylist(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch { playlistRepository.reorderPlaylist(playlistId, songs) }
    }

    suspend fun deduplicatePlaylist(playlistId: Long): Int =
        playlistRepository.deduplicatePlaylist(playlistId)

    fun renamePlaylist(id: Long, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(id, newName)
        }
    }

    fun addSongsToPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            playlistRepository.addSongsToPlaylist(playlistId, songIds)
        }
    }

    fun removeSongsFromPlaylist(playlistId: Long, songIds: List<Long>) {
        viewModelScope.launch {
            songIds.forEach { songId ->
                playlistRepository.removeSongFromPlaylist(playlistId, songId)
            }
        }
    }

    fun exportPlaylistAsM3U(playlist: dev.yuwixx.resonance.data.model.Playlist): String =
        playlistRepository.exportPlaylistAsM3U(playlist)

    // ─── Loading state ────────────────────────────────────────────────────────

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun syncLibrary(force: Boolean = false) {
        if (_isSyncing.value && !force) return
        viewModelScope.launch {
            _isSyncing.value = true
            try { musicRepository.syncWithMediaStore() }
            finally { _isSyncing.value = false }
        }
    }

    // ─── History ──────────────────────────────────────────────────────────────

    private val _isClearingHistory = MutableStateFlow(false)
    val isClearingHistory: StateFlow<Boolean> = _isClearingHistory.asStateFlow()

    fun clearHistory() {
        viewModelScope.launch {
            _isClearingHistory.value = true
            try {
                musicRepository.clearAllHistory()
                // Reload most-played so the home screen reflects the cleared state immediately
                loadMostPlayed()
            } finally {
                _isClearingHistory.value = false
            }
        }
    }

    // ─── Most played + smart suggestions ─────────────────────────────────────

    private val _mostPlayed = MutableStateFlow<List<Song>>(emptyList())
    val mostPlayed: StateFlow<List<Song>> = _mostPlayed.asStateFlow()

    fun loadMostPlayed() {
        viewModelScope.launch {
            _mostPlayed.value = musicRepository.getMostPlayedSongs(20)
        }
    }

    init {
        // Initial scan on load
        syncLibrary()
        loadMostPlayed()

        // Refresh most played when library changes
        allSongs.onEach { loadMostPlayed() }.launchIn(viewModelScope)
    }
}