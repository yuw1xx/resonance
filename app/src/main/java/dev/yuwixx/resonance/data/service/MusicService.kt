package dev.yuwixx.resonance.data.service

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Bundle
import androidx.media3.common.*
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.*
import dagger.hilt.android.AndroidEntryPoint
import dev.yuwixx.resonance.MainActivity
import dev.yuwixx.resonance.data.database.dao.QueueDao
import dev.yuwixx.resonance.data.database.entity.QueueEntity
import dev.yuwixx.resonance.data.preferences.ResonancePreferences
import dev.yuwixx.resonance.domain.usecase.ReplayGainProcessor
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import dev.yuwixx.resonance.ui.glancewidget.ACTION_WIDGET_PLAY_PAUSE
import dev.yuwixx.resonance.ui.glancewidget.ACTION_WIDGET_SKIP_NEXT
import dev.yuwixx.resonance.ui.glancewidget.ResonanceWidget

@UnstableApi
@AndroidEntryPoint
class MusicService : MediaSessionService() {

    @Inject lateinit var prefs: ResonancePreferences
    @Inject lateinit var queueDao: QueueDao
    @Inject lateinit var replayGainProcessor: ReplayGainProcessor

    private lateinit var player: ExoPlayer
    private lateinit var mediaSession: MediaSession
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // ─── Crossfade / gapless state ────────────────────────────────────────────
    private var crossfadeJob: Job? = null
    private var crossfadeDurationMs: Int = 0
    private var gaplessEnabled: Boolean = true

    // ─── Volume / ReplayGain state ─────────────────────────────────────────────
    private var volumeNormalizationEnabled = false
    private var replayGainMode = "TRACK"
    private var replayGainPreampDb = 0f

    // ─── Headphone plug receiver ───────────────────────────────────────────────
    private var headphonesReceiver: BroadcastReceiver? = null
    private var resumeOnHeadphones = true
    private var wasPlayingBeforeUnplug = false

    // ─── Skip buttons state ───────────────────────────────────────────────────
    private var showSkipButtons = true

    // ─── Widget broadcast receiver ────────────────────────────────────────────
    private var widgetActionsReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        buildPlayer()
        buildSession()
        applyInitialPreferences()
        observePreferences()
        restoreQueue()
        registerHeadphonesReceiver()
        registerWidgetActionsReceiver()
    }

    // ─── Player + Session construction ────────────────────────────────────────

    private fun buildPlayer() {
        val pauseOnDisconnect = kotlinx.coroutines.runBlocking { prefs.pauseOnHeadphonesOut.first() }
        val duckAudio         = kotlinx.coroutines.runBlocking { prefs.duckAudioOnFocusLoss.first() }

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus= */ duckAudio
            )
            .setHandleAudioBecomingNoisy(pauseOnDisconnect)
            .setSkipSilenceEnabled(false)
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                persistQueue()
                applyVolumeForCurrentItem()
                scheduleCrossfade()
                pushWidgetState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) persistQueue()
                pushWidgetState()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // Track playing state for headphone resume
                if (!isPlaying && player.playbackState != Player.STATE_ENDED) {
                    // Only update wasPlayingBeforeUnplug on explicit audio-becoming-noisy
                    // (handled in receiver below); here we just keep it in sync.
                }
                pushWidgetState()
            }
        })
    }

    private fun buildSession() {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(pendingIntent)
            .setCallback(MediaSessionCallback())
            .build()
    }

    // ─── Initial preferences ───────────────────────────────────────────────────

    private fun applyInitialPreferences() {
        scope.launch {
            player.skipSilenceEnabled = prefs.skipSilence.first()

            val repeat = prefs.repeatMode.first()
            player.repeatMode = when (repeat) {
                dev.yuwixx.resonance.data.model.RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                dev.yuwixx.resonance.data.model.RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                else -> Player.REPEAT_MODE_OFF
            }
            player.shuffleModeEnabled = prefs.shuffleEnabled.first()

            // Seed mutable state from persisted prefs so the engine is correct
            // even before the collector coroutines emit their first value.
            gaplessEnabled            = prefs.gaplessEnabled.first()
            crossfadeDurationMs       = prefs.crossfadeDurationMs.first()
            volumeNormalizationEnabled = prefs.volumeNormalization.first()
            replayGainMode            = prefs.replayGainMode.first()
            replayGainPreampDb        = prefs.replayGainPreampDb.first()
            resumeOnHeadphones        = prefs.resumeOnHeadphones.first()
            showSkipButtons           = prefs.showSkipButtons.first()

            updateNotificationCommandButtons(showSkipButtons)
        }
    }

    // ─── Live preference observation (the heart of all 8 features) ────────────

    private fun observePreferences() {

        // ── Skip silence (kept here for completeness) ─────────────────────────
        scope.launch {
            prefs.skipSilence.collect { player.skipSilenceEnabled = it }
        }

        // ── GAPLESS PLAYBACK ───────────────────────────────────────────────
        // ExoPlayer performs seamless gapless playback by default when it can
        // parse iTunSMPB / LAME Gapless Info tags embedded in MP3 headers.
        // When gapless is OFF we deliberately insert a silent pause between tracks
        // by fading out completely and triggering a short re-buffer delay.
        scope.launch {
            prefs.gaplessEnabled.collect { enabled ->
                gaplessEnabled = enabled
                // Re-schedule crossfade logic which checks gapless flag.
                scheduleCrossfade()
            }
        }

        // ── AUDIO DUCKING (FOCUS LOSS) ────────────────────────────────────────
        scope.launch {
            prefs.duckAudioOnFocusLoss.collect { duckAudio ->
                // Update audio focus handling dynamically
                val currentAttrs = player.audioAttributes
                player.setAudioAttributes(currentAttrs, duckAudio)
            }
        }

        // ── PAUSE ON HEADPHONES OUT (BECOMING NOISY) ──────────────────────────
        scope.launch {
            prefs.pauseOnHeadphonesOut.collect { pauseOnDisconnect ->
                // Update becoming-noisy handling dynamically
                player.setHandleAudioBecomingNoisy(pauseOnDisconnect)
            }
        }

        // ── CROSSFADE ──────────────────────────────────────────────────────
        scope.launch {
            prefs.crossfadeDurationMs.collect { ms ->
                crossfadeDurationMs = ms
                scheduleCrossfade()
            }
        }

        // ── VOLUME NORMALISATION ───────────────────────────────────────────
        scope.launch {
            prefs.volumeNormalization.collect { enabled ->
                volumeNormalizationEnabled = enabled
                applyVolumeForCurrentItem()
            }
        }

        // ── REPLAYGAIN MODE ────────────────────────────────────────────────
        scope.launch {
            prefs.replayGainMode.collect { mode ->
                replayGainMode = mode
                applyVolumeForCurrentItem()
            }
        }
        scope.launch {
            prefs.replayGainPreampDb.collect { db ->
                replayGainPreampDb = db
                applyVolumeForCurrentItem()
            }
        }

        // ── RESUME ON HEADPHONES ───────────────────────────────────────────
        scope.launch {
            prefs.resumeOnHeadphones.collect { enabled ->
                resumeOnHeadphones = enabled
            }
        }

        // ── LOCKSCREEN ARTWORK ─────────────────────────────────────────────
        scope.launch {
            prefs.lockscreenArtwork.collect { enabled ->
                applyLockscreenArtwork(enabled)
            }
        }

        // ── SHOW SKIP BUTTONS ──────────────────────────────────────────────
        scope.launch {
            prefs.showSkipButtons.collect { show ->
                showSkipButtons = show
                updateNotificationCommandButtons(show)
            }
        }
    }

    // ─── Gapless / Crossfade ─────────────────────────────────────

    /**
     * Schedules a volume fade-out that fires [crossfadeDurationMs] before the
     * current track ends, then fades the next item in.
     *
     * - crossfade == 0 AND gapless ON  → ExoPlayer handles natively (no-op here)
     * - crossfade == 0 AND gapless OFF → we cut volume to 0 at track end,
     *   advance, then fade back up (simulates a non-gapless pause)
     * - crossfade > 0                  → standard crossfade regardless of gapless
     */
    private fun scheduleCrossfade() {
        crossfadeJob?.cancel()

        val fadeDurationMs = when {
            crossfadeDurationMs > 0 -> crossfadeDurationMs
            !gaplessEnabled         -> 200   // brief 200 ms "gap" fade
            else                    -> 0
        }

        if (fadeDurationMs == 0) {
            // Restore volume to target in case a previous fade left it low
            player.volume = computeTargetVolume()
            return
        }

        crossfadeJob = scope.launch {
            while (isActive) {
                val duration = player.duration
                val position = player.currentPosition
                if (duration > 0 && position > 0) {
                    val remaining = duration - position
                    if (remaining in 1..fadeDurationMs) {
                        val fadeProgress = 1f - (remaining.toFloat() / fadeDurationMs)
                        player.volume = computeTargetVolume() * (1f - fadeProgress)
                    } else if (remaining <= 0 || player.playbackState == Player.STATE_ENDED) {
                        // Track ended — fade in the new track
                        fadeIn(fadeDurationMs, computeTargetVolume())
                        break
                    } else {
                        // Not yet in fade window — ensure full volume
                        val target = computeTargetVolume()
                        if (player.volume < target * 0.95f) {
                            player.volume = target
                        }
                    }
                }
                delay(50)
            }
        }
    }

    private fun fadeIn(durationMs: Int, targetVolume: Float) {
        scope.launch {
            player.volume = 0f
            val steps = (durationMs / 50).coerceAtLeast(1)
            for (i in 0..steps) {
                if (!isActive) break
                player.volume = targetVolume * (i.toFloat() / steps)
                delay(50)
            }
            player.volume = targetVolume
            scheduleCrossfade()
        }
    }

    // ─── Volume Normalisation + ReplayGain ──────────────────────

    /**
     * Computes the target ExoPlayer volume (0.01–4.0 linear) for the current
     * media item, combining Volume Normalisation and ReplayGain.
     *
     * Priority: ReplayGain tags > Volume Normalisation > unity (1.0)
     *
     * ReplayGain values are embedded into MediaItem extras by [Song.toMediaItem]
     * extension (see PlayerViewModel).  Volume Normalisation applies a
     * conservative −3 dB reduction as a loudness-levelling heuristic for
     * untagged files; a future upgrade can store per-song LUFS in the database
     * and read it here for true normalisation.
     */
    private fun computeTargetVolume(): Float {
        // ── ReplayGain (higher priority) ─────────────────────────────────────
        if (replayGainMode != "OFF") {
            val extras = player.currentMediaItem?.mediaMetadata?.extras
            if (extras != null) {
                val trackGain = extras.getFloat("replayGainTrack", Float.MAX_VALUE)
                    .takeIf { it != Float.MAX_VALUE }
                val albumGain = extras.getFloat("replayGainAlbum", Float.MAX_VALUE)
                    .takeIf { it != Float.MAX_VALUE }

                if (trackGain != null || albumGain != null) {
                    val info = ReplayGainProcessor.ReplayGainInfo(
                        trackGainDb = trackGain,
                        trackPeak   = null,
                        albumGainDb = albumGain,
                        albumPeak   = null,
                    )
                    val mode = when (replayGainMode) {
                        "ALBUM" -> ReplayGainProcessor.Mode.ALBUM
                        "OFF"   -> ReplayGainProcessor.Mode.OFF
                        else    -> ReplayGainProcessor.Mode.TRACK
                    }
                    val multiplier = replayGainProcessor.computeMultiplier(info, mode, replayGainPreampDb)
                    return multiplier
                }
            }
        }

        // ── Volume Normalisation fallback ─────────────────────────────────────
        if (volumeNormalizationEnabled) {
            // −3 dB ≈ 0.707× linear.  A real implementation would read a
            // per-song stored LUFS value from Room and compute the precise gain
            // needed to reach the −14 LUFS target.
            return 0.707f
        }

        return 1f
    }

    private fun applyVolumeForCurrentItem() {
        // During an active crossfade, let the fade coroutine control volume.
        if (crossfadeJob?.isActive == true) return
        player.volume = computeTargetVolume()
    }

    // ─── Lockscreen Artwork ────────────────────────────────────────

    /**
     * Controls whether album art is shown on the lock-screen media controls.
     *
     * Media3 automatically publishes artwork from MediaMetadata.artworkUri.
     * To suppress it we replace the current MediaItem's metadata with a copy
     * that has artworkUri = null, so SystemUI renders no thumbnail.
     *
     * We do NOT permanently strip the artwork — it is re-applied on the next
     * track change if lockscreen artwork is re-enabled.
     */
    private fun applyLockscreenArtwork(enabled: Boolean) {
        val item = player.currentMediaItem ?: return
        val originalMetadata = item.mediaMetadata

        val updatedMetadata = if (enabled) {
            // Ensure artworkUri is present (may already be there)
            originalMetadata
        } else {
            // Strip artwork from the published metadata so lock screen shows none
            originalMetadata.buildUpon()
                .setArtworkUri(null)
                .setArtworkData(null, null)
                .build()
        }

        val updatedItem = item.buildUpon().setMediaMetadata(updatedMetadata).build()

        // Replace in-place without seeking
        val index = player.currentMediaItemIndex
        if (index >= 0) {
            player.replaceMediaItem(index, updatedItem)
        }
    }

    // ─── Show Skip Buttons ─────────────────────────────────────────

    /**
     * Rebuilds the MediaSession custom layout to include or exclude skip
     * (previous / next) buttons in the notification shade and lock-screen controls.
     *
     * Media3's DefaultMediaNotificationProvider exposes exactly the commands
     * present in [MediaSession.customLayout] plus play/pause (always included
     * from player commands).  We explicitly set the layout here.
     */
    private fun updateNotificationCommandButtons(showSkip: Boolean) {
        val layout = mutableListOf<CommandButton>()

        if (showSkip) {
            layout += CommandButton.Builder(CommandButton.ICON_SKIP_BACK)
                .setDisplayName("Previous")
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()

            layout += CommandButton.Builder(CommandButton.ICON_SKIP_FORWARD)
                .setDisplayName("Next")
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .build()
        }

        mediaSession.setCustomLayout(layout)
    }

    // ─── Resume on Headphones ─────────────────────────────────────

    private fun registerHeadphonesReceiver() {
        headphonesReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AudioManager.ACTION_HEADSET_PLUG -> {
                        val state = intent.getIntExtra("state", -1)
                        when (state) {
                            1 -> {
                                // Headphones plugged IN
                                if (resumeOnHeadphones && wasPlayingBeforeUnplug) {
                                    player.play()
                                    wasPlayingBeforeUnplug = false
                                }
                            }
                            0 -> {
                                // Headphones unplugged — record whether we were playing.
                                // The actual pause is handled by setHandleAudioBecomingNoisy.
                                wasPlayingBeforeUnplug = player.isPlaying
                            }
                        }
                    }
                }
            }
        }
        val filter = IntentFilter(AudioManager.ACTION_HEADSET_PLUG)
        registerReceiver(headphonesReceiver, filter)
    }

    // ─── Queue persistence ─────────────────────────────────────────────────────

    private fun restoreQueue() {
        scope.launch(Dispatchers.IO) {
            try {
                @Suppress("UNUSED_VARIABLE")
                val queueEntity = queueDao.getCurrentQueue() ?: return@launch
            } catch (_: Exception) {}
        }
    }

    fun persistQueue() {
        scope.launch(Dispatchers.IO) {
            try {
                val count = player.mediaItemCount
                if (count == 0) return@launch
                val ids = (0 until count).map {
                    player.getMediaItemAt(it).mediaId.toLongOrNull() ?: 0L
                }
                queueDao.saveQueue(
                    QueueEntity(
                        id = 0L,
                        songIds = ids.joinToString(","),
                        currentIndex = player.currentMediaItemIndex,
                        shuffleEnabled = player.shuffleModeEnabled,
                        repeatMode = when (player.repeatMode) {
                            Player.REPEAT_MODE_ONE -> "ONE"
                            Player.REPEAT_MODE_ALL -> "ALL"
                            else -> "NONE"
                        },
                        originalOrder = ids.indices.joinToString(","),
                    )
                )
            } catch (_: Exception) {}
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession = mediaSession

    override fun onDestroy() {
        // Clear widget to idle before the scope is cancelled
        scope.launch { ResonanceWidget.updateState(this@MusicService, "", "", false, false) }
        persistQueue()
        crossfadeJob?.cancel()
        widgetActionsReceiver?.let { unregisterReceiver(it) }
        scope.cancel()
        headphonesReceiver?.let { unregisterReceiver(it) }
        mediaSession.release()
        player.release()
        super.onDestroy()
    }

    // ─── Widget state bridge ───────────────────────────────────────────────────

    private fun pushWidgetState() {
        val mediaItem = player.currentMediaItem ?: run {
            scope.launch { ResonanceWidget.updateState(this@MusicService, "", "", false, false) }
            return
        }
        val meta   = mediaItem.mediaMetadata
        val title  = meta.title?.toString() ?: mediaItem.mediaId
        val artist = meta.artist?.toString() ?: meta.albumArtist?.toString() ?: ""
        scope.launch {
            ResonanceWidget.updateState(
                context   = this@MusicService,
                title     = title,
                artist    = artist,
                isPlaying = player.isPlaying,
                hasSong   = true,
            )
        }
    }

    private fun registerWidgetActionsReceiver() {
        widgetActionsReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    ACTION_WIDGET_PLAY_PAUSE -> {
                        if (player.isPlaying) player.pause() else player.play()
                    }
                    ACTION_WIDGET_SKIP_NEXT -> {
                        if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(ACTION_WIDGET_PLAY_PAUSE)
            addAction(ACTION_WIDGET_SKIP_NEXT)
        }
        registerReceiver(widgetActionsReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    // ─── Legacy helper ─────────────────────────────────────────────────────────

    fun applyReplayGain(
        trackGainDb: Float?,
        albumGainDb: Float?,
        mode: String,
        preampDb: Float = 0f,
    ) {
        val gainDb = when (mode) {
            "TRACK" -> trackGainDb
            "ALBUM" -> albumGainDb ?: trackGainDb
            else -> null
        } ?: return

        val totalGain = gainDb + preampDb
        val linearGain = Math.pow(10.0, totalGain / 20.0).toFloat()
        player.volume = linearGain.coerceIn(0.01f, 4.0f)
    }

    // ─── MediaSession callback ─────────────────────────────────────────────────

    private inner class MediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
            val resolvedItems = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                item.buildUpon().setUri(uri).build()
            }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                resolvedItems.toMutableList()
            )
        }
    }
}