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
    private val artworkRepository: ArtworkRepository,
    private val prefs: ResonancePreferences,
    private val lastFmRepository: LastFmRepository,
    private val likedSongsDao: LikedSongsDao,
) : ViewModel() {

    // ─── Media Controller ─────────────────────────────────────────────────────

    private var _controller: MediaController? = null
    val controllerReady = MutableStateFlow(false)

    init {
        connectToService()
    }

    @OptIn(UnstableApi::class)
    private fun connectToService(retryCount: Int = 0) {
        val sessionToken = SessionToken(
            context, ComponentName(context, MusicService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                _controller = future.get()
                _controller?.addListener(playerListener)
                controllerReady.value = true
                syncPlayerState()
            } catch (e: Exception) {
                if (retryCount < 4) {
                    viewModelScope.launch {
                        delay(300L * (retryCount + 1))
                        connectToService(retryCount + 1)
                    }
                }
            }
        }, MoreExecutors.directExecutor())
    }

    // ─── Playback state ───────────────────────────────────────────────────────

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

    // ─── Smart shuffle ────────────────────────────────────────────────────────

    val smartShuffleEnabled: StateFlow<Boolean> = prefs.smartShuffleEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    // ─── Smart queue loading state ────────────────────────────────────────────

    private val _isLoadingSmartQueue = MutableStateFlow(false)
    val isLoadingSmartQueue: StateFlow<Boolean> = _isLoadingSmartQueue.asStateFlow()

    private val _smartQueueError = MutableStateFlow<String?>(null)
    val smartQueueError: StateFlow<String?> = _smartQueueError.asStateFlow()

    // ─── Visual state ─────────────────────────────────────────────────────────

    val dynamicColor: StateFlow<Color?> = combine(
        prefs.dynamicColorEnabled,
        prefs.presetColor
    ) { dynamicEnabled, preset ->
        when {
            dynamicEnabled -> null
            preset != null -> Color(preset)
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _waveformData = MutableStateFlow<WaveformData?>(null)
    val waveformData: StateFlow<WaveformData?> = _waveformData.asStateFlow()

    // ─── Lyrics state ─────────────────────────────────────────────────────────

    private val _lyricsResult = MutableStateFlow<LyricsResult>(LyricsResult.NotFound)
    val lyricsResult: StateFlow<LyricsResult> = _lyricsResult.asStateFlow()

    private val _activeLyricIndex = MutableStateFlow(-1)
    val activeLyricIndex: StateFlow<Int> = _activeLyricIndex.asStateFlow()

    // ─── Sleep timer ──────────────────────────────────────────────────────────

    private val _sleepTimer = MutableStateFlow<SleepTimer>(SleepTimer.Off)
    val sleepTimer: StateFlow<SleepTimer> = _sleepTimer.asStateFlow()
    private var sleepTimerJob: Job? = null

    // ─── Liked songs ──────────────────────────────────────────────────────────

    private val _isCurrentSongLiked = MutableStateFlow(false)
    val isCurrentSongLiked: StateFlow<Boolean> = _isCurrentSongLiked.asStateFlow()

    /** Emits the ordered list of liked song IDs (most-recently-liked first). */
    val likedSongIds: kotlinx.coroutines.flow.Flow<List<Long>> = likedSongsDao.getLikedSongIds()

    // ─── UI preferences ───────────────────────────────────────────────────────

    val showWaveform = prefs.showWaveformSeekbar
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // ─── Scrobble threshold prefs (read here so checkScrobbleThreshold uses live values)
    private val scrobbleMinSecs = prefs.lastFmScrobbleMinSecs
        .stateIn(viewModelScope, SharingStarted.Eagerly, 30)
    private val scrobblePct = prefs.lastFmScrobblePercent
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0.5f)

    // ─── Last.fm scrobble tracking ────────────────────────────────────────────
    private var scrobbleStartedAt: Long = 0L
    private var scrobbleSubmittedForCurrentTrack = false

    // ─── Position polling + speed/pitch wiring ────────────────────────────────

    init {
        // Apply playback speed and pitch to the MediaController whenever either pref
        // changes, or when the controller first becomes ready.
        viewModelScope.launch {
            combine(
                prefs.playbackSpeed,
                prefs.playbackPitch,
                controllerReady,
            ) { speed, pitch, ready -> Triple(speed, pitch, ready) }
                .collect { (speed, pitch, ready) ->
                    if (ready) _controller?.setPlaybackParameters(PlaybackParameters(speed, pitch))
                }
        }

        viewModelScope.launch {
            while (true) {
                delay(if (_isPlaying.value) 250L else 1000L)
                _controller?.let { ctrl ->
                    _positionMs.value = ctrl.currentPosition
                    _durationMs.value = ctrl.duration.coerceAtLeast(0L)
                    updateActiveLyric(ctrl.currentPosition)
                    checkSleepTimer()
                    checkScrobbleThreshold(ctrl.currentPosition, ctrl.duration)
                }
            }
        }

        // When smart shuffle is toggled on while shuffle is already active,
        // immediately reorder the existing queue.
        viewModelScope.launch {
            prefs.smartShuffleEnabled.collect { smartOn ->
                if (smartOn && _shuffleEnabled.value && _queue.value.size > 1) {
                    applySmartShuffle()
                }
            }
        }
    }

    // ─── Playback controls ────────────────────────────────────────────────────

    fun play(songs: List<Song>, startIndex: Int = 0) {
        val ctrl = _controller ?: return

        _queue.value = songs
        _currentQueueIndex.value = startIndex
        if (startIndex in songs.indices) {
            _currentSong.value = songs[startIndex]
        }

        val mediaItems = songs.map { it.toMediaItem() }
        ctrl.setMediaItems(mediaItems, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()
    }

    fun playPause() { _controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun skipNext() { _controller?.seekToNextMediaItem() }
    fun skipPrevious() {
        val ctrl = _controller ?: return
        if (ctrl.currentPosition > 3000L) ctrl.seekTo(0L) else ctrl.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) { _controller?.seekTo(positionMs) }

    fun toggleRepeat() {
        val ctrl = _controller ?: return
        val next = when (_repeatMode.value) {
            dev.yuwixx.resonance.data.model.RepeatMode.NONE -> dev.yuwixx.resonance.data.model.RepeatMode.ALL
            dev.yuwixx.resonance.data.model.RepeatMode.ALL -> dev.yuwixx.resonance.data.model.RepeatMode.ONE
            dev.yuwixx.resonance.data.model.RepeatMode.ONE -> dev.yuwixx.resonance.data.model.RepeatMode.NONE
        }
        _repeatMode.value = next
        ctrl.repeatMode = when (next) {
            dev.yuwixx.resonance.data.model.RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            dev.yuwixx.resonance.data.model.RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            dev.yuwixx.resonance.data.model.RepeatMode.NONE -> Player.REPEAT_MODE_OFF
        }
        viewModelScope.launch { prefs.setRepeatMode(next) }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    fun toggleShuffle() {
        val ctrl = _controller ?: return
        val next = !_shuffleEnabled.value
        _shuffleEnabled.value = next
        viewModelScope.launch { prefs.setShuffleEnabled(next) }

        if (next && smartShuffleEnabled.value) {
            // Smart shuffle: reorder the queue using listen-history weights
            // instead of handing off to ExoPlayer's Fisher-Yates shuffle.
            // We manage the order ourselves, so keep ExoPlayer shuffle OFF.
            ctrl.shuffleModeEnabled = false
            applySmartShuffle()
        } else {
            ctrl.shuffleModeEnabled = next
        }
    }

    /**
     * Smart Shuffle — reorders the queue using a weighted probability that
     * pushes frequently-played and recently-heard tracks further back, surfacing
     * less-played and long-forgotten songs sooner.
     *
     * Inspired by Namida's SMORT algorithm.
     *
     * Weight formula (higher weight = picked sooner):
     *   w = 1 / (1 + listenCount × 0.3 + staleness × 0.5)
     * where staleness = (days since last listened / 30), capped [0, 1].
     * Songs never listened to get staleness = 0 (maximum recency bonus).
     *
     * The current song is preserved at index 0; the remainder is reordered.
     */
    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun applySmartShuffle() {
        val ctrl = _controller ?: return
        val queue = _queue.value
        if (queue.size < 2) return

        val currentSong = _currentSong.value
        val currentIdx = queue.indexOfFirst { it.id == currentSong?.id }
        val rest = queue.toMutableList().also { if (currentIdx >= 0) it.removeAt(currentIdx) }

        val nowMs = System.currentTimeMillis()
        val thirtyDaysMs = 30L * 24L * 60L * 60L * 1_000L

        data class Weighted(val song: Song, val weight: Float)

        val pool = rest.map { song ->
            val staleness = if (song.lastListened > 0L)
                ((nowMs - song.lastListened).toFloat() / thirtyDaysMs).coerceIn(0f, 1f)
            else
                0f   // never played → maximum freshness, gets higher weight

            // Invert: rarely-played, long-unheard tracks get the highest weight
            val weight = 1f / (1f + song.listenCount * 0.3f + staleness * 0.5f)
            Weighted(song, weight)
        }.toMutableList()

        // Weighted reservoir sampling (O(n log n))
        val reordered = ArrayList<Song>(pool.size)
        while (pool.isNotEmpty()) {
            val totalWeight = pool.fold(0.0) { acc, w -> acc + w.weight }
            var r = (Math.random() * totalWeight).toFloat()
            val iter = pool.iterator()
            var picked = false
            while (iter.hasNext()) {
                val w = iter.next()
                r -= w.weight
                if (r <= 0f) {
                    reordered += w.song
                    iter.remove()
                    picked = true
                    break
                }
            }
            // Floating-point safety: if nothing was picked, take the last item
            if (!picked && pool.isNotEmpty()) {
                reordered += pool.removeLast().song
            }
        }

        val finalQueue: List<Song> = buildList {
            if (currentSong != null) add(currentSong)
            addAll(reordered)
        }

        // Preserve playback position while replacing the queue
        val savedPosition = ctrl.currentPosition
        ctrl.setMediaItems(finalQueue.map { it.toMediaItem() }, 0, savedPosition)
        ctrl.prepare()
        if (_isPlaying.value) ctrl.play()

        _queue.value = finalQueue
        _currentQueueIndex.value = 0
    }

    fun moveQueueItem(from: Int, to: Int) {
        _controller?.moveMediaItem(from, to)
        val mutable = _queue.value.toMutableList()
        val item = mutable.removeAt(from)
        mutable.add(to, item)
        _queue.value = mutable
    }

    fun removeFromQueue(index: Int) {
        _controller?.removeMediaItem(index)
        val mutable = _queue.value.toMutableList()
        if (index in mutable.indices) mutable.removeAt(index)
        _queue.value = mutable
    }

    fun clearQueue() {
        _controller?.clearMediaItems()
        _queue.value = emptyList()
        _currentSong.value = null
        _currentQueueIndex.value = 0
    }

    fun addToQueueNext(song: Song) {
        val ctrl = _controller ?: return
        val insertAt = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
        ctrl.addMediaItem(insertAt, song.toMediaItem())
        val mutable = _queue.value.toMutableList()
        mutable.add(insertAt, song)
        _queue.value = mutable
    }

    fun addToQueueEnd(song: Song) {
        _controller?.addMediaItem(song.toMediaItem())
        _queue.value = _queue.value + song
    }

    // ─── Smart queue ──────────────────────────────────────────────────────────

    fun loadSmartQueue(reason: SmartQueueReason) {
        val seed = _currentSong.value
        if (seed == null) {
            _smartQueueError.value = "Play a song first to generate a mix."
            return
        }
        if (_isLoadingSmartQueue.value) return

        viewModelScope.launch {
            _isLoadingSmartQueue.value = true
            _smartQueueError.value = null
            try {
                val result = musicRepository.generateSmartQueue(seed, reason)
                if (result.songs.isEmpty()) {
                    _smartQueueError.value = "Not enough songs found for this mix."
                } else {
                    result.songs.forEach { addToQueueEnd(it) }
                }
            } catch (e: Exception) {
                _smartQueueError.value = "Failed to generate mix."
            } finally {
                _isLoadingSmartQueue.value = false
            }
        }
    }

    fun clearSmartQueueError() { _smartQueueError.value = null }

    // ─── Sleep timer ──────────────────────────────────────────────────────────

    fun setSleepTimer(timer: SleepTimer) {
        sleepTimerJob?.cancel()
        _sleepTimer.value = timer
        if (timer is SleepTimer.AfterMinutes) {
            sleepTimerJob = viewModelScope.launch {
                delay(timer.minutes * 60_000L)
                _controller?.pause()
                _sleepTimer.value = SleepTimer.Off
            }
        }
    }

    private var sleepTracksRemaining = -1

    fun setSleepAfterTracks(count: Int) {
        sleepTracksRemaining = count
        _sleepTimer.value = SleepTimer.AfterTracks(count)
    }

    private fun checkSleepTimer() { }

    // ─── Player state listener ────────────────────────────────────────────────

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.let { onSongChanged(it) }

            if (sleepTracksRemaining > 0) {
                sleepTracksRemaining--
                _sleepTimer.value = SleepTimer.AfterTracks(sleepTracksRemaining)
                if (sleepTracksRemaining == 0) {
                    _controller?.pause()
                    _sleepTimer.value = SleepTimer.Off
                    sleepTracksRemaining = -1
                }
            }

            _currentQueueIndex.value = _controller?.currentMediaItemIndex ?: 0
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _repeatMode.value = when (repeatMode) {
                Player.REPEAT_MODE_ONE -> dev.yuwixx.resonance.data.model.RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> dev.yuwixx.resonance.data.model.RepeatMode.ALL
                else -> dev.yuwixx.resonance.data.model.RepeatMode.NONE
            }
        }

        override fun onShuffleModeEnabledChanged(enabled: Boolean) {
            _shuffleEnabled.value = enabled
        }
    }

    private fun onSongChanged(mediaItem: MediaItem) {
        val songId = mediaItem.mediaId.toLongOrNull() ?: return
        val song = _queue.value.find { it.id == songId } ?: return
        _currentSong.value = song

        scrobbleStartedAt = System.currentTimeMillis()
        scrobbleSubmittedForCurrentTrack = false

        lastFmRepository.updateNowPlaying(song)

        viewModelScope.launch {
            _isCurrentSongLiked.value = likedSongsDao.isLiked(song.id) > 0
        }

        fetchLyrics(song)
    }

    // ─── Lyrics ───────────────────────────────────────────────────────────────

    private fun fetchLyrics(song: Song) {
        viewModelScope.launch {
            _lyricsResult.value = LyricsResult.NotFound
            val result = lyricsRepository.getLyrics(
                songId = song.id,
                title = song.title,
                artist = song.displayArtist,
                album = song.album,
                durationMs = song.duration,
            )
            _lyricsResult.value = result
        }
    }

    private fun updateActiveLyric(positionMs: Long) {
        val result = _lyricsResult.value
        if (result !is LyricsResult.Synced) return
        val activeIdx = result.lines.indexOfLast { it.timeMs <= positionMs }
        if (activeIdx != _activeLyricIndex.value) {
            _activeLyricIndex.value = activeIdx
        }
    }

    // ─── Last.fm Scrobbling ───────────────────────────────────────────────────

    private fun checkScrobbleThreshold(positionMs: Long, durationMs: Long) {
        if (scrobbleSubmittedForCurrentTrack) return
        val song = _currentSong.value ?: return
        if (!_isPlaying.value) return
        if (durationMs <= 0L) return

        val listenedMs = System.currentTimeMillis() - scrobbleStartedAt
        val minSecs = scrobbleMinSecs.value   // reads live pref — was hardcoded 30
        val minPct  = scrobblePct.value       // reads live pref — was hardcoded 0.5f

        val meetsTimeCriteria = listenedMs >= minSecs * 1000L
        val meetsPctCriteria  = positionMs.toFloat() / durationMs >= minPct

        if (meetsTimeCriteria && meetsPctCriteria) {
            scrobbleSubmittedForCurrentTrack = true
            lastFmRepository.scrobble(song, scrobbleStartedAt)
        }
    }

    fun toggleLike() {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            if (_isCurrentSongLiked.value) {
                likedSongsDao.unlikeSong(song.id)
                lastFmRepository.unloveTrack(song)
            } else {
                likedSongsDao.likeSong(LikedSongEntity(song.id))
                lastFmRepository.loveTrack(song)
            }
            _isCurrentSongLiked.value = !_isCurrentSongLiked.value
        }
    }

    fun loveCurrentTrack() {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            lastFmRepository.loveTrack(song)
        }
    }

    fun unloveCurrentTrack() {
        val song = _currentSong.value ?: return
        viewModelScope.launch {
            lastFmRepository.unloveTrack(song)
        }
    }

    // ─── Misc ─────────────────────────────────────────────────────────────────

    private fun syncPlayerState() {
        _controller?.let { ctrl ->
            _isPlaying.value = ctrl.isPlaying
            _repeatMode.value = when (ctrl.repeatMode) {
                Player.REPEAT_MODE_ONE -> dev.yuwixx.resonance.data.model.RepeatMode.ONE
                Player.REPEAT_MODE_ALL -> dev.yuwixx.resonance.data.model.RepeatMode.ALL
                else -> dev.yuwixx.resonance.data.model.RepeatMode.NONE
            }
            _shuffleEnabled.value = ctrl.shuffleModeEnabled
        }
    }

    override fun onCleared() {
        _controller?.removeListener(playerListener)
        _controller?.release()
        super.onCleared()
    }
}

private fun Song.toMediaItem(): MediaItem {
    // Embed ReplayGain values in MediaMetadata extras so MusicService can
    // apply volume gain without needing a direct Song reference.
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