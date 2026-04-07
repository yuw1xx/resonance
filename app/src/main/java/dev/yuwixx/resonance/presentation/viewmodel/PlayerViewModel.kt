package dev.yuwixx.resonance.presentation.viewmodel

import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.OptIn
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yuwixx.resonance.data.database.dao.LikedSongsDao
import dev.yuwixx.resonance.data.database.entity.LikedSongEntity
import dev.yuwixx.resonance.data.model.*
import dev.yuwixx.resonance.data.preferences.ResonancePreferences
import dev.yuwixx.resonance.data.repository.*
import dev.yuwixx.resonance.data.service.MusicService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    val lyricsRepository: LyricsRepository,
    private val likedSongsDao: LikedSongsDao,
    private val lastFmRepository: LastFmRepository,
    private val waveformExtractor: dev.yuwixx.resonance.domain.usecase.WaveformExtractor,
    val prefs: ResonancePreferences,
) : ViewModel() {

    private var _controller: MediaController? = null

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _repeatMode = MutableStateFlow(dev.yuwixx.resonance.data.model.RepeatMode.NONE)
    val repeatMode: StateFlow<dev.yuwixx.resonance.data.model.RepeatMode> = _repeatMode.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _waveformData = MutableStateFlow<dev.yuwixx.resonance.data.model.WaveformData?>(null)
    val waveformData: StateFlow<dev.yuwixx.resonance.data.model.WaveformData?> = _waveformData.asStateFlow()

    private val _sleepTimer = MutableStateFlow<SleepTimer>(SleepTimer.Off)
    val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()

    private val _lyricsResult = MutableStateFlow<LyricsResult>(LyricsResult.NotFound)
    val lyricsResult: StateFlow<LyricsResult> = _lyricsResult.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(-1)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    val isCurrentSongLiked: StateFlow<Boolean> = _currentSong
        .flatMapLatest { song ->
            if (song == null) flowOf(false)
            else likedSongsDao.getLikedSongIds().map { ids -> song.id in ids }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // ─── UI Preferences ────────────────────────────────────────────────────────

    val dynamicColor = combine(
        prefs.dynamicColorEnabled,
        prefs.presetColor
    ) { enabled, preset ->
        if (enabled || preset == null) null else Color(preset)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val showWaveform = prefs.showWaveformSeekbar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blurBackground = prefs.blurArtworkBackground
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val blurStrength = prefs.blurStrength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.3f)

    val artworkAnimation = prefs.artworkAnimation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // NEW PLAYER PREFERENCES
    val playerLayout = prefs.playerLayout
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "STANDARD")

    val miniPlayerStyle = prefs.miniPlayerStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "CARD")

    val showLyricsButton = prefs.showLyricsButton
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lyricsFontScale = prefs.lyricsFontScale
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    // ─── Last.fm Scrobbling State ─────────────────────────────────────────────

    private var scrobbleStartedAt: Long = 0L
    private var scrobbleSubmittedForCurrentTrack = false

    val scrobblePct = prefs.lastFmScrobblePercent.stateIn(viewModelScope, SharingStarted.Eagerly, 0.5f)
    val scrobbleMinSecs = prefs.lastFmScrobbleMinSecs.stateIn(viewModelScope, SharingStarted.Eagerly, 30)

    init {
        initializeController()
        startProgressTracker()
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(prefs.playbackSpeed, prefs.playbackPitch) { speed, pitch ->
                PlaybackParameters(speed, pitch)
            }.collect { params ->
                _controller?.playbackParameters = params
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializeController() {
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        val controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                _controller = controllerFuture.get()
                _controller?.addListener(playerListener)
                syncStateWithController()
            },
            MoreExecutors.directExecutor()
        )
    }

    private fun startProgressTracker() {
        viewModelScope.launch {
            while (isActive) {
                _controller?.let { ctrl ->
                    if (ctrl.isPlaying) {
                        _positionMs.value = ctrl.currentPosition
                        _durationMs.value = ctrl.duration.coerceAtLeast(0)
                        updateActiveLyricIndex(ctrl.currentPosition)
                        checkScrobbleThreshold(ctrl.currentPosition, ctrl.duration)
                    }
                }
                delay(100)
            }
        }
    }

    private fun checkScrobbleThreshold(positionMs: Long, durationMs: Long) {
        if (scrobbleSubmittedForCurrentTrack) return
        val song = _currentSong.value ?: return
        if (!_isPlaying.value) return
        if (durationMs <= 0L) return

        val listenedMs = System.currentTimeMillis() - scrobbleStartedAt
        val minSecs = scrobbleMinSecs.value
        val minPct  = scrobblePct.value

        val meetsTimeCriteria = listenedMs >= minSecs * 1000L
        val meetsPctCriteria  = positionMs.toFloat() / durationMs >= minPct

        if (meetsTimeCriteria && meetsPctCriteria) {
            scrobbleSubmittedForCurrentTrack = true
            lastFmRepository.scrobble(song, scrobbleStartedAt)

            viewModelScope.launch {
                musicRepository.recordListen(song.id, listenedMs, durationMs)
            }
        }
    }

    private fun updateActiveLyricIndex(positionMs: Long) {
        val result = _lyricsResult.value
        if (result is LyricsResult.Synced) {
            val lines = result.lines
            val activeIndex = lines.indexOfLast { it.timeMs <= positionMs }
            if (activeIndex != _activeLyricIndex.value) {
                _activeLyricIndex.value = activeIndex
            }
        }
    }

    // ─── Playback commands ────────────────────────────────────────────────────

    fun play(songs: List<Song>, startIndex: Int) {
        _controller?.let { ctrl ->
            ctrl.setMediaItems(songs.map { it.toMediaItem() }, startIndex, 0)
            ctrl.prepare()
            ctrl.play()
        }
    }

    fun playPause() {
        _controller?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    fun skipNext() = _controller?.seekToNextMediaItem()
    fun skipPrevious() = _controller?.seekToPreviousMediaItem()
    fun seekTo(position: Long) = _controller?.seekTo(position)

    fun toggleRepeat() {
        _controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun toggleShuffle() {
        _controller?.let {
            it.shuffleModeEnabled = !it.shuffleModeEnabled
        }
    }

    fun toggleLike() {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            val isLiked = likedSongsDao.isLiked(song.id) > 0
            if (isLiked) {
                likedSongsDao.unlikeSong(song.id)
            } else {
                likedSongsDao.likeSong(LikedSongEntity(songId = song.id, likedAt = System.currentTimeMillis()))
            }
        }
    }

    fun addToQueueEnd(song: Song) {
        _controller?.addMediaItem(song.toMediaItem())
    }

    fun addToQueueNext(song: Song) {
        _controller?.let { ctrl ->
            val insertIndex = (ctrl.currentMediaItemIndex + 1)
                .coerceAtMost(ctrl.mediaItemCount)
            ctrl.addMediaItem(insertIndex, song.toMediaItem())
        }
    }

    // ─── Queue exposure ───────────────────────────────────────────────────────

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    fun clearQueue() {
        _controller?.clearMediaItems()
    }

    fun removeFromQueue(index: Int) {
        _controller?.removeMediaItem(index)
    }

    // ─── Liked song IDs ───────────────────────────────────────────────────────

    val likedSongIds: StateFlow<List<Long>> = likedSongsDao.getLikedSongIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Smart Queue ──────────────────────────────────────────────────────────

    private val _isLoadingSmartQueue = MutableStateFlow(false)
    val isLoadingSmartQueue: StateFlow<Boolean> = _isLoadingSmartQueue.asStateFlow()

    private val _smartQueueError = MutableStateFlow<String?>(null)
    val smartQueueError: StateFlow<String?> = _smartQueueError.asStateFlow()

    fun clearSmartQueueError() {
        _smartQueueError.value = null
    }

    fun loadSmartQueue(reason: dev.yuwixx.resonance.data.model.SmartQueueReason) {
        if (_isLoadingSmartQueue.value) return
        val seedSong = _currentSong.value ?: return
        viewModelScope.launch {
            _isLoadingSmartQueue.value = true
            try {
                val result = musicRepository.generateSmartQueue(seedSong, reason)
                if (result.songs.isEmpty()) {
                    _smartQueueError.value = "No songs found for this queue type."
                } else {
                    _controller?.let { ctrl ->
                        val insertIndex = ctrl.currentMediaItemIndex + 1
                        result.songs.forEachIndexed { i, song ->
                            ctrl.addMediaItem(insertIndex + i, song.toMediaItem())
                        }
                    }
                }
            } catch (e: Exception) {
                _smartQueueError.value = e.message ?: "Failed to load smart queue."
            } finally {
                _isLoadingSmartQueue.value = false
            }
        }
    }

    // ─── Timers ───────────────────────────────────────────────────────────────

    private var sleepTimerJob: Job? = null

    fun setSleepTimer(timer: SleepTimer) {
        _sleepTimer.value = timer
        sleepTimerJob?.cancel()
        if (timer is SleepTimer.Time) {
            sleepTimerJob = viewModelScope.launch {
                val endMs = System.currentTimeMillis() + timer.minutes * 60 * 1000L
                while (System.currentTimeMillis() < endMs) { delay(1000) }
                _controller?.pause()
                _sleepTimer.value = SleepTimer.Off
            }
        }
    }

    fun setSleepAfterTracks(tracksLeft: Int) {
        if (tracksLeft <= 0) {
            _sleepTimer.value = SleepTimer.Off
            return
        }
        _sleepTimer.value = SleepTimer.Tracks(tracksLeft)
    }

    // ─── Listeners ────────────────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        @OptIn(UnstableApi::class)
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            syncStateWithController()
            val songId = mediaItem?.mediaId?.toLongOrNull()
            if (songId != null) {
                viewModelScope.launch {
                    val song = musicRepository.allSongs.first().find { it.id == songId }
                    if (song != null) {
                        _currentSong.value = song
                        _waveformData.value = waveformExtractor.extract(song.id, song.uri)

                        _lyricsResult.value = dev.yuwixx.resonance.data.repository.LyricsResult.NotFound
                        _activeLyricIndex.value = -1
                        _lyricsResult.value = lyricsRepository.getLyrics(
                            songId = song.id,
                            title = song.title,
                            artist = song.artist,
                            album = song.album,
                            durationMs = song.duration
                        )

                        scrobbleStartedAt = System.currentTimeMillis()
                        scrobbleSubmittedForCurrentTrack = false
                        lastFmRepository.updateNowPlaying(song)
                    }
                }
            }

            val currentTimer = _sleepTimer.value
            if (currentTimer is SleepTimer.Tracks) {
                val newCount = currentTimer.tracksLeft - 1
                if (newCount <= 0) {
                    _controller?.pause()
                    _sleepTimer.value = SleepTimer.Off
                } else {
                    _sleepTimer.value = SleepTimer.Tracks(newCount)
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying && !scrobbleSubmittedForCurrentTrack) {
                scrobbleStartedAt = System.currentTimeMillis()
            }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> dev.yuwixx.resonance.data.model.RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> dev.yuwixx.resonance.data.model.RepeatMode.ALL
                else -> dev.yuwixx.resonance.data.model.RepeatMode.NONE
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _shuffleEnabled.value = shuffleModeEnabled
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            syncQueueFromController()
        }
    }

    private fun syncStateWithController() {
        _controller?.let { ctrl ->
            _isPlaying.value = ctrl.isPlaying
            _repeatMode.value = when (ctrl.repeatMode) {
                Player.REPEAT_MODE_ONE -> dev.yuwixx.resonance.data.model.RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> dev.yuwixx.resonance.data.model.RepeatMode.ALL
                else -> dev.yuwixx.resonance.data.model.RepeatMode.NONE
            }
            _shuffleEnabled.value = ctrl.shuffleModeEnabled
            _currentQueueIndex.value = ctrl.currentMediaItemIndex
        }
        syncQueueFromController()
    }

    private fun syncQueueFromController() {
        val ctrl = _controller ?: return
        viewModelScope.launch {
            val allSongs = musicRepository.allSongs.first()
            val songMap = allSongs.associateBy { it.id }
            val songs = (0 until ctrl.mediaItemCount).mapNotNull { i ->
                ctrl.getMediaItemAt(i).mediaId.toLongOrNull()?.let { songMap[it] }
            }
            _queue.value = songs
            _currentQueueIndex.value = ctrl.currentMediaItemIndex
        }
    }

    override fun onCleared() {
        _controller?.removeListener(playerListener)
        _controller?.release()
        super.onCleared()
    }
}

private fun Song.toMediaItem(): MediaItem {
    val extras = android.os.Bundle().apply {
        replayGainTrack?.let { putFloat("replayGainTrack", it) }
        replayGainAlbum?.let { putFloat("replayGainAlbum", it) }
    }

    return MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(displayArtist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri)
                .setExtras(extras)
                .build()
        )
        .setRequestMetadata(
            MediaItem.RequestMetadata.Builder()
                .setMediaUri(uri)
                .build()
        )
        .build()
}