package dev.yuwixx.resonance.presentation.screens

import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.animation.core.RepeatMode as AnimationRepeatMode
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import dev.yuwixx.resonance.data.model.LyricLine
import dev.yuwixx.resonance.data.model.Playlist
import dev.yuwixx.resonance.data.model.RepeatMode
import dev.yuwixx.resonance.data.model.SleepTimer
import dev.yuwixx.resonance.data.model.Song
import dev.yuwixx.resonance.data.repository.LyricsResult
import dev.yuwixx.resonance.presentation.components.*
import dev.yuwixx.resonance.presentation.viewmodel.PlayerViewModel
import dev.yuwixx.resonance.presentation.viewmodel.ShareViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsEdit: () -> Unit,
    playlists: List<Playlist> = emptyList(),
    onAddToPlaylist: (Playlist) -> Unit = {},
    onCreatePlaylist: (String) -> Unit = {},
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    // State Hoisting Optimization - Deferred position reading!
    val positionProvider = remember { { playerViewModel.positionMs.value } }

    val durationMs by playerViewModel.durationMs.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val waveformData by playerViewModel.waveformData.collectAsState()
    val seekbarStyle by playerViewModel.seekbarStyle.collectAsState() // <--- CHANGED
    val blurBackground by playerViewModel.blurBackground.collectAsState()
    val blurStrength by playerViewModel.blurStrength.collectAsState()
    val artworkAnimation by playerViewModel.artworkAnimation.collectAsState()
    val sleepTimer by playerViewModel.sleepTimer.collectAsState()
    val lyricsResult by playerViewModel.lyricsResult.collectAsState()
    val activeLyricIndex by playerViewModel.activeLyricIndex.collectAsState()
    val isTrackLoved by playerViewModel.isCurrentSongLiked.collectAsState()

    // NEW PLAYER SETTINGS
    val playerLayout by playerViewModel.playerLayout.collectAsState()
    val showLyricsButton by playerViewModel.showLyricsButton.collectAsState()
    val lyricsFontScale by playerViewModel.lyricsFontScale.collectAsState()

    val song = currentSong ?: return
    val shareViewModel: ShareViewModel = hiltViewModel()

    var showLyricsOverlay by remember { mutableStateOf(false) }
    var showSleepTimerDialog by remember { mutableStateOf(false) }
    var showOptionsSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        DynamicBackground(
            artworkUri = song.artworkUri,
            isPlaying = isPlaying,
            blurBackground = blurBackground,
            blurStrength = blurStrength
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            PlayerHeader(
                onBack = onBack,
                onQueueClick = onQueueClick,
                onSleepTimer = { showSleepTimerDialog = true },
                onShare = { showShareSheet = true },
                sleepTimer = sleepTimer,
            )

            Spacer(modifier = Modifier.weight(1f))

            val artworkPadding = when (playerLayout) {
                "ARTWORK_BIG" -> 12.dp
                "LYRICS_FOCUS" -> 64.dp
                else -> 28.dp
            }

            ArtworkPanel(
                song = song,
                isPlaying = isPlaying,
                artworkAnimation = artworkAnimation,
                modifier = Modifier
                    .padding(horizontal = artworkPadding)
                    .aspectRatio(1f),
            )

            Spacer(modifier = Modifier.weight(1f))

            PlaybackControls(
                song = song,
                isPlaying = isPlaying,
                positionProvider = positionProvider,
                durationMs = durationMs,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                waveformData = waveformData,
                seekbarStyle = seekbarStyle, // <--- CHANGED
                isTrackLoved = isTrackLoved,
                onPlayPause = { playerViewModel.playPause() },
                onNext = { playerViewModel.skipNext() },
                onPrevious = { playerViewModel.skipPrevious() },
                onSeek = { playerViewModel.seekTo(it) },
                onRepeatModeChange = { playerViewModel.toggleRepeat() },
                onShuffleToggle = { playerViewModel.toggleShuffle() },
                onLoveTrack = { playerViewModel.toggleLike() },
            )

            PlayerFooter(
                lyricsResult = lyricsResult,
                showLyricsButton = showLyricsButton,
                onLyricsClick = { showLyricsOverlay = true },
                onLyricsEdit = onLyricsEdit,
                onMoreOptions = { showOptionsSheet = true },
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        // ── Lyrics overlay ──────────────────────────────────────────────────
        AnimatedVisibility(
            visible = showLyricsOverlay,
            enter = fadeIn(tween(280)) + slideInVertically(
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f),
            ) { it / 6 },
            exit = fadeOut(tween(220)) + slideOutVertically(
                animationSpec = tween(220, easing = EaseInCubic),
            ) { it / 6 },
        ) {
            val lines: List<LyricLine> = when (val r = lyricsResult) {
                is LyricsResult.Synced -> r.lines
                is LyricsResult.Plain  -> r.lines.mapIndexed { _, l ->
                    LyricLine(timeMs = 0L, text = l)
                }
                else -> emptyList()
            }
            if (lines.isNotEmpty()) {
                LyricsOverlay(
                    lyrics = lines,
                    activeLineIndex = activeLyricIndex,
                    fontScale = lyricsFontScale,
                    onSeek = { playerViewModel.seekTo(it) },
                    onClose = { showLyricsOverlay = false },
                )
            }
        }

        if (showSleepTimerDialog) {
            SleepTimerDialog(
                currentTimer = sleepTimer,
                onDismiss = { showSleepTimerDialog = false },
                onSetTimer = { timer ->
                    playerViewModel.setSleepTimer(timer)
                    showSleepTimerDialog = false
                },
                onSetAfterTracks = { n ->
                    playerViewModel.setSleepAfterTracks(n)
                    showSleepTimerDialog = false
                },
            )
        }

        if (showOptionsSheet) {
            PlayerOptionsSheet(
                song = song,
                playlists = playlists,
                onDismiss = { showOptionsSheet = false },
                onAddToQueue = {
                    playerViewModel.addToQueueEnd(song)
                    showOptionsSheet = false
                },
                onAddToPlaylist = onAddToPlaylist,
                onCreatePlaylist = onCreatePlaylist,
            )
        }

        if (showShareSheet) {
            ShareSheet(
                viewModel   = shareViewModel,
                currentSong = song,
                onDismiss   = { showShareSheet = false },
            )
        }
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun PlayerHeader(
    onBack: () -> Unit,
    onQueueClick: () -> Unit,
    onSleepTimer: () -> Unit,
    onShare: () -> Unit,
    sleepTimer: SleepTimer,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.Rounded.KeyboardArrowDown, "Close",
                modifier = Modifier.size(32.dp),
            )
        }
        Text(
            text = "Now Playing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        val sleepActive = sleepTimer !is SleepTimer.Off
        val sleepPulse by animateFloatAsState(
            targetValue = if (sleepActive) 1.15f else 1f,
            animationSpec = if (sleepActive)
                infiniteRepeatable(tween(800), AnimationRepeatMode.Reverse)
            else spring(),
            label = "sleep_pulse",
        )
        IconButton(onClick = onSleepTimer, modifier = Modifier.scale(sleepPulse)) {
            Icon(
                Icons.Rounded.Bedtime, "Sleep Timer",
                modifier = Modifier.size(24.dp),
                tint = if (sleepActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
        }
        IconButton(onClick = onShare) {
            Icon(Icons.Rounded.Share, "Share", modifier = Modifier.size(24.dp))
        }
        IconButton(onClick = onQueueClick) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Queue", modifier = Modifier.size(24.dp))
        }
    }
}

// ─── Artwork ─────────────────────────────────────────────────────────────────

@Composable
private fun ArtworkPanel(
    song: Song,
    isPlaying: Boolean,
    artworkAnimation: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        val scale by animateFloatAsState(
            targetValue = if (isPlaying || !artworkAnimation) 1f else 0.92f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
            label = "artwork_scale",
        )
        val elevation by animateDpAsState(
            targetValue = if (isPlaying) 24.dp else 6.dp,
            animationSpec = tween(600, easing = EaseOutCubic),
            label = "artwork_elevation",
        )
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .scale(scale),
            shape = RoundedCornerShape(24.dp),
            tonalElevation = elevation,
            shadowElevation = elevation,
        ) {
            ArtworkImage(
                uri = song.artworkUri,
                contentDescription = song.album,
                modifier = Modifier.fillMaxSize(),
                cornerRadius = 0.dp,
            )
        }
    }
}

// ─── Playback controls ───────────────────────────────────────────────────────

@Composable
private fun PlaybackControls(
    song: Song,
    isPlaying: Boolean,
    positionProvider: () -> Long,
    durationMs: Long,
    repeatMode: RepeatMode,
    shuffleEnabled: Boolean,
    waveformData: dev.yuwixx.resonance.data.model.WaveformData?,
    seekbarStyle: String,
    isTrackLoved: Boolean,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onRepeatModeChange: () -> Unit,
    onShuffleToggle: () -> Unit,
    onLoveTrack: () -> Unit,
) {
    val positionMs = positionProvider()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AnimatedContent(
                targetState = song.title to song.displayArtist,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 4 })
                        .togetherWith(fadeOut(tween(200)) + slideOutVertically(tween(200)) { it / 4 })
                },
                label = "song_info",
                modifier = Modifier.weight(1f),
            ) { (title, artist) ->
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            val loveScale by animateFloatAsState(
                targetValue = if (isTrackLoved) 1.2f else 1f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "love_scale",
            )
            val loveColor by animateColorAsState(
                targetValue = if (isTrackLoved) Color(0xFFE8384D)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(300),
                label = "love_color",
            )
            IconButton(onClick = onLoveTrack, modifier = Modifier.scale(loveScale)) {
                Icon(
                    imageVector = if (isTrackLoved) Icons.Rounded.Favorite
                    else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (isTrackLoved) "Unlike" else "Like",
                    tint = loveColor,
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // <--- CHANGED: Render appropriate seekbar
        when (seekbarStyle) {
            "WAVEFORM" -> {
                WaveformSeekbar(
                    waveformData = waveformData,
                    positionProvider = positionProvider,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                )
            }
            "SQUIGGLY" -> {
                SquigglySeekbar(
                    positionProvider = positionProvider,
                    durationMs = durationMs,
                    onSeek = onSeek,
                    isPlaying = isPlaying,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                )
            }
            else -> {
                Slider(
                    value = positionMs.toFloat(),
                    onValueChange = { onSeek(it.toLong()) },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatDuration(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onShuffleToggle) {
                Icon(
                    Icons.Rounded.Shuffle, "Shuffle",
                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onPrevious, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipPrevious, "Previous", modifier = Modifier.size(36.dp))
            }

            val playScale by animateFloatAsState(
                targetValue = if (isPlaying) 1f else 0.94f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                label = "play_scale",
            )
            Surface(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(72.dp)
                    .scale(playScale),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = if (isPlaying) 10.dp else 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AnimatedContent(
                        targetState = isPlaying,
                        transitionSpec = {
                            scaleIn(spring(Spring.DampingRatioMediumBouncy)) +
                                    fadeIn(tween(150)) togetherWith
                                    scaleOut(tween(100)) + fadeOut(tween(100))
                        },
                        label = "play_pause_icon",
                    ) { playing ->
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (playing) "Pause" else "Play",
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }

            IconButton(onClick = onNext, modifier = Modifier.size(52.dp)) {
                Icon(Icons.Rounded.SkipNext, "Next", modifier = Modifier.size(36.dp))
            }

            IconButton(onClick = onRepeatModeChange) {
                Icon(
                    if (repeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne
                    else Icons.Rounded.Repeat,
                    "Repeat",
                    tint = if (repeatMode != RepeatMode.NONE) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ─── Footer ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerFooter(
    lyricsResult: LyricsResult,
    showLyricsButton: Boolean,
    onLyricsClick: () -> Unit,
    onLyricsEdit: () -> Unit,
    onMoreOptions: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { /* Equalizer */ }) {
            Icon(
                Icons.Rounded.GraphicEq, "Equalizer",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showLyricsButton) {
            val hasLyrics = lyricsResult !is LyricsResult.NotFound
            val isLoading = lyricsResult is LyricsResult.Loading

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (hasLyrics && !isLoading)
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                else Color.Transparent,
                modifier = Modifier
                    .size(40.dp)
                    .combinedClickable(
                        onClick = onLyricsClick,
                        onLongClick = onLyricsEdit,
                    ),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Icon(
                            imageVector = if (hasLyrics) Icons.Rounded.MicExternalOn else Icons.Rounded.MicExternalOff,
                            contentDescription = "Lyrics — tap to view, long-press to edit",
                            modifier = Modifier.size(22.dp),
                            tint = if (hasLyrics) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }

        IconButton(onClick = onMoreOptions) {
            Icon(
                Icons.Rounded.MoreHoriz, "More options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ─── Options bottom sheet ────────────────────────────────────────────────────

private enum class OptionsPage { Main, Playlist, Info }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerOptionsSheet(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var page by remember { mutableStateOf(OptionsPage.Main) }
    var showCreatePlaylist by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState == OptionsPage.Main) {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { -it / 3 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { it / 3 })
                } else {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it / 3 })
                        .togetherWith(fadeOut(tween(160)) + slideOutHorizontally(tween(220)) { -it / 3 })
                }
            },
            label = "options_page",
        ) { currentPage ->
            when (currentPage) {
                OptionsPage.Main -> SheetMainPage(
                    song = song,
                    onAddToQueue = onAddToQueue,
                    onOpenPlaylists = { page = OptionsPage.Playlist },
                    onOpenInfo = { page = OptionsPage.Info },
                )
                OptionsPage.Playlist -> SheetPlaylistPage(
                    playlists = playlists,
                    showCreatePlaylist = showCreatePlaylist,
                    onShowCreate = { showCreatePlaylist = true },
                    onBack = { page = OptionsPage.Main },
                    onPlaylistSelected = { onAddToPlaylist(it); onDismiss() },
                    onCreatePlaylist = { name ->
                        onCreatePlaylist(name)
                        showCreatePlaylist = false
                    },
                )
                OptionsPage.Info -> SheetInfoPage(
                    song = song,
                    onBack = { page = OptionsPage.Main },
                )
            }
        }
    }
}

@Composable
private fun SheetMainPage(
    song: Song,
    onAddToQueue: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenInfo: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 28.dp)) {
        ListItem(
            headlineContent = {
                Text(
                    song.title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Text(
                    song.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                Surface(shape = RoundedCornerShape(10.dp), tonalElevation = 2.dp) {
                    ArtworkImage(
                        uri = song.artworkUri,
                        contentDescription = song.album,
                        modifier = Modifier.size(52.dp),
                        cornerRadius = 10.dp,
                    )
                }
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))

        ListItem(
            modifier = Modifier.clickable(onClick = onAddToQueue),
            headlineContent = { Text("Add to Queue") },
            leadingContent = {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic, null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )

        ListItem(
            modifier = Modifier.clickable(onClick = onOpenPlaylists),
            headlineContent = { Text("Add to Playlist") },
            leadingContent = {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistAdd, null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            trailingContent = {
                Icon(
                    Icons.Rounded.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )

        ListItem(
            modifier = Modifier.clickable(onClick = onOpenInfo),
            headlineContent = { Text("Info") },
            leadingContent = {
                Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
            },
            trailingContent = {
                Icon(
                    Icons.Rounded.ChevronRight, null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun SheetPlaylistPage(
    playlists: List<Playlist>,
    showCreatePlaylist: Boolean,
    onShowCreate: () -> Unit,
    onBack: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(bottom = 28.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text(
                "Add to Playlist",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onShowCreate) { Icon(Icons.Rounded.Add, "New playlist") }
        }

        AnimatedVisibility(
            visible = showCreatePlaylist,
            enter = expandVertically(spring(dampingRatio = 0.8f)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(200)) + fadeOut(tween(160)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("Playlist name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        if (newName.isNotBlank()) {
                            onCreatePlaylist(newName.trim())
                            newName = ""
                        }
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("Create") }
            }
        }

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No playlists yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            playlists.forEach { playlist ->
                ListItem(
                    modifier = Modifier.clickable { onPlaylistSelected(playlist) },
                    headlineContent = { Text(playlist.name) },
                    supportingContent = {
                        Text(
                            "${playlist.songCount} song${if (playlist.songCount == 1) "" else "s"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.QueueMusic, null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SheetInfoPage(
    song: Song,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.padding(bottom = 32.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text(
                "Info",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }

        // Artwork + title
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 4.dp) {
                ArtworkImage(
                    uri = song.artworkUri,
                    contentDescription = song.album,
                    modifier = Modifier.size(72.dp),
                    cornerRadius = 14.dp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.displayArtist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
            if (song.album.isNotBlank())
                SongInfoRow("Album", song.album)
            if (song.year > 0)
                SongInfoRow("Year", song.year.toString())
            if (song.genre.isNotBlank())
                SongInfoRow("Genre", song.genre)
            if (song.albumArtist.isNotBlank() && song.albumArtist != song.artist)
                SongInfoRow("Album artist", song.albumArtist)
            if (song.trackNumber > 0) {
                val track = if (song.discNumber > 1)
                    "Disc ${song.discNumber}  ·  Track ${song.trackNumber}"
                else "Track ${song.trackNumber}"
                SongInfoRow("Track", track)
            }
            SongInfoRow("Duration", formatDurationLong(song.duration))
            SongInfoRow("File size", formatFileSize(song.size))
            if (song.bitrate > 0)
                SongInfoRow("Bitrate", "${song.bitrate} kbps")
            if (song.sampleRate > 0)
                SongInfoRow("Sample rate", formatSampleRate(song.sampleRate))
            val fmt = formatMimeType(song.mimeType)
            if (fmt.isNotBlank())
                SongInfoRow("Format", fmt)
            if (song.path.isNotBlank())
                SongInfoRow("Path", song.path, isPath = true)
        }
    }
}

@Composable
private fun SongInfoRow(
    label: String,
    value: String,
    isPath: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = if (isPath) Alignment.Top else Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(110.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
            maxLines = if (isPath) 4 else 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Dynamic background ───────────────────────────────────────────────────────

@Composable
private fun DynamicBackground(
    artworkUri: android.net.Uri?,
    isPlaying: Boolean,
    blurBackground: Boolean,
    blurStrength: Float,
) {
    if (!blurBackground) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }

    val targetBlur = (blurStrength * 200).dp

    val blurRadius by animateDpAsState(
        targetValue = if (isPlaying) targetBlur else (targetBlur * 0.75f),
        animationSpec = tween(800, easing = EaseOutCubic),
        label = "blur_radius",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.38f else 0.22f,
        animationSpec = tween(800),
        label = "bg_alpha",
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AsyncImage(
            model = artworkUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(blurRadius)
                .alpha(alpha),
            contentScale = ContentScale.Crop,
        )
    }
}

// ─── Lyrics overlay ──────────────────────────────────────────────────────────

@Composable
fun LyricsOverlay(
    lyrics: List<LyricLine>,
    activeLineIndex: Int,
    fontScale: Float,
    onSeek: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(activeLineIndex) {
        if (activeLineIndex >= 0) {
            listState.animateScrollToItem(activeLineIndex, scrollOffset = -200)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, "Close lyrics")
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 100.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                itemsIndexed(lyrics) { index, line ->
                    val isActive = index == activeLineIndex
                    val lineScale by animateFloatAsState(
                        targetValue = if (isActive) 1.05f else 1f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy),
                        label = "lyric_scale_$index",
                    )
                    val lineAlpha by animateFloatAsState(
                        targetValue = when {
                            isActive -> 1f
                            index < activeLineIndex -> 0.35f
                            else -> 0.55f
                        },
                        animationSpec = tween(300),
                        label = "lyric_alpha_$index",
                    )

                    val baseStyle = if (isActive) MaterialTheme.typography.headlineMedium
                    else MaterialTheme.typography.titleLarge
                    val scaledStyle = baseStyle.copy(fontSize = baseStyle.fontSize * fontScale)

                    Text(
                        text = line.text,
                        style = scaledStyle,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = lineAlpha),
                        modifier = Modifier
                            .scale(lineScale)
                            .clickable(enabled = line.timeMs > 0L) { onSeek(line.timeMs) },
                    )
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic  = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

private fun formatDurationLong(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "—"
    val df = DecimalFormat("#.##")
    val mb = bytes / 1_048_576.0
    return if (mb >= 1.0) "${df.format(mb)} MB" else "${df.format(bytes / 1024.0)} KB"
}

private fun formatSampleRate(hz: Int): String =
    if (hz >= 1000) "${DecimalFormat("#.#").format(hz / 1000.0)} kHz" else "$hz Hz"

private fun formatMimeType(mime: String): String = when (mime.lowercase()) {
    "audio/mpeg"               -> "MP3"
    "audio/flac", "audio/x-flac" -> "FLAC"
    "audio/ogg"                -> "OGG Vorbis"
    "audio/opus"               -> "Opus"
    "audio/aac"                -> "AAC"
    "audio/mp4", "audio/m4a"  -> "AAC / M4A"
    "audio/x-wav", "audio/wav" -> "WAV"
    "audio/x-aiff"             -> "AIFF"
    "audio/alac"               -> "ALAC"
    "audio/x-ms-wma"           -> "WMA"
    else                       -> mime.substringAfterLast('/').uppercase()
}