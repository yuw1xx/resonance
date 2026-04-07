package dev.yuwixx.resonance.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.yuwixx.resonance.data.repository.LastFmAuthState
import dev.yuwixx.resonance.presentation.viewmodel.LibraryViewModel
import dev.yuwixx.resonance.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToLicenses: () -> Unit,
    libraryViewModel: LibraryViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val scope = rememberCoroutineScope()
    val prefs = libraryViewModel.prefs
    val snackbarHostState = remember { SnackbarHostState() }

    // Updates
    val updateFreq by settingsViewModel.updateFrequency.collectAsState()

    // Appearance
    val dynamicColor by prefs.dynamicColorEnabled.collectAsState(initial = true)
    val presetColorInt by prefs.presetColor.collectAsState(initial = null)
    val darkTheme by prefs.darkTheme.collectAsState(initial = "SYSTEM")
    val cornerRadius by prefs.cornerRadius.collectAsState(initial = 28)
    val showWaveform by prefs.showWaveformSeekbar.collectAsState(initial = true)
    val blurBackground by prefs.blurArtworkBackground.collectAsState(initial = true)
    val blurStrength by prefs.blurStrength.collectAsState(initial = 0.3f)
    val artworkAnimation by prefs.artworkAnimation.collectAsState(initial = true)
    val hapticFeedback by prefs.hapticFeedback.collectAsState(initial = true)
    val showBitrateInfo by prefs.showBitrateInfo.collectAsState(initial = false)
    val albumGridCols by prefs.albumGridColumns.collectAsState(initial = 2)
    val miniPlayerStyle by prefs.miniPlayerStyle.collectAsState(initial = "CARD")
    val playerLayout by prefs.playerLayout.collectAsState(initial = "STANDARD")
    val showLyricsBtn by prefs.showLyricsButton.collectAsState(initial = true)
    val lyricsFontScale by prefs.lyricsFontScale.collectAsState(initial = 1.0f)

    // Playback
    val gapless by prefs.gaplessEnabled.collectAsState(initial = true)
    val skipSilence by prefs.skipSilence.collectAsState(initial = false)
    val crossfadeMs by prefs.crossfadeDurationMs.collectAsState(initial = 0)
    val playbackSpeed by prefs.playbackSpeed.collectAsState(initial = 1.0f)
    val playbackPitch by prefs.playbackPitch.collectAsState(initial = 1.0f)
    val resumeOnHeadphones by prefs.resumeOnHeadphones.collectAsState(initial = true)
    val pauseOnHeadphonesOut by prefs.pauseOnHeadphonesOut.collectAsState(initial = true)
    val duckAudio by prefs.duckAudioOnFocusLoss.collectAsState(initial = true)
    val smartShuffle by prefs.smartShuffleEnabled.collectAsState(initial = false)
    val volumeNorm by prefs.volumeNormalization.collectAsState(initial = false)

    // Audio
    val replayGainMode by prefs.replayGainMode.collectAsState(initial = "TRACK")
    val replayGainPreamp by prefs.replayGainPreampDb.collectAsState(initial = 0f)

    // Library
    val isSyncing by libraryViewModel.isSyncing.collectAsState()
    val minDurationMs by prefs.minTrackDurationMs.collectAsState(initial = 0L)
    val artistDelimiter by prefs.artistDelimiter.collectAsState(initial = ",;/&")
    val showArtworkList by prefs.showArtworkInList.collectAsState(initial = true)
    val groupByAlbumArtist by prefs.groupByAlbumArtist.collectAsState(initial = true)
    val showFilenameTitle by prefs.showFilenameAsTitle.collectAsState(initial = false)
    val ignoreArticles by prefs.ignoreArticles.collectAsState(initial = true)
    val autoScanHours by prefs.autoScanIntervalHours.collectAsState(initial = 0)

    // Notification
    val lockscreenArtwork by prefs.lockscreenArtwork.collectAsState(initial = true)
    val showSkipButtons by prefs.showSkipButtons.collectAsState(initial = true)

    // History
    val historyEnabled by prefs.historyEnabled.collectAsState(initial = true)
    val minListenSecs by prefs.minListenSeconds.collectAsState(initial = 30)
    val minListenPct by prefs.minListenPercentage.collectAsState(initial = 0.5f)
    val maxHistory by prefs.maxHistoryItems.collectAsState(initial = 1000)

    // Last.fm
    val lastFmEnabled by settingsViewModel.lastFmEnabled.collectAsState()
    val lastFmNowPlaying by settingsViewModel.lastFmNowPlaying.collectAsState()
    val lastFmOnlyWifi by settingsViewModel.lastFmOnlyWifi.collectAsState()
    val lastFmScrobblePct by settingsViewModel.lastFmScrobblePct.collectAsState()
    val lastFmScrobbleMinSecs by settingsViewModel.lastFmScrobbleMinSecs.collectAsState()
    val lastFmOfflineQueue by settingsViewModel.lastFmOfflineQueue.collectAsState()
    val lastFmAuthState by settingsViewModel.lastFmAuthState.collectAsState()
    val lastFmPending by settingsViewModel.pendingScrobbles.collectAsState()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {

            // ─── Updates ──────────────────────────────────────────────────────
            settingsSectionHeader("Updates", Icons.Rounded.SystemUpdate)

            item {
                var showDisableWarning by remember { mutableStateOf(false) }

                SegmentedSettingsItem(
                    title = "Check for Updates",
                    options = listOf("Launch" to "LAUNCH", "Daily" to "DAILY", "Weekly" to "WEEKLY", "Off" to "DISABLED"),
                    selected = updateFreq,
                    onSelect = {
                        if (it == "DISABLED") {
                            showDisableWarning = true
                        } else {
                            settingsViewModel.setUpdateFrequency(it)
                        }
                    },
                )

                if (showDisableWarning) {
                    AlertDialog(
                        onDismissRequest = { showDisableWarning = false },
                        icon = { Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error) },
                        title = { Text("Disable Updates?") },
                        text = { Text("It is highly recommended to keep update checks enabled so you don't miss bug fixes and new features.\n\nAre you sure you want to disable automatic checks?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    settingsViewModel.setUpdateFrequency("DISABLED")
                                    showDisableWarning = false
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Disable") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDisableWarning = false }) { Text("Keep Enabled") }
                        }
                    )
                }
            }

            item {
                val context = LocalContext.current
                val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionName = pkgInfo.versionName ?: "1.1"

                SettingsTextItem(
                    title = "Check Now",
                    subtitle = "Current version: v$versionName",
                    icon = Icons.Rounded.Update,
                    onClick = { settingsViewModel.checkForUpdates(versionName, isManual = true) }
                )
            }

            // ─── Appearance ───────────────────────────────────────────────────
            settingsSectionHeader("Appearance", Icons.Rounded.Palette)

            settingsToggle("System Dynamic Color", "Use Android 12+ wallpaper colours", dynamicColor) {
                scope.launch { prefs.setDynamicColorEnabled(it) }
            }

            if (!dynamicColor) {
                item {
                    ThemeColorPicker(
                        current = presetColorInt,
                        onPick = { scope.launch { prefs.setPresetColor(it) } }
                    )
                }
            }

            item {
                SegmentedSettingsItem(
                    title = "Dark Theme",
                    options = listOf("System" to "SYSTEM", "Light" to "LIGHT", "Dark" to "DARK"),
                    selected = darkTheme,
                    onSelect = { scope.launch { prefs.setDarkTheme(it) } },
                )
            }

            settingsToggle("Waveform Seekbar", "Material You 3 wave-style progress bar", showWaveform) {
                scope.launch { prefs.setShowWaveformSeekbar(it) }
            }
            settingsToggle("Blur Background", "Artwork-tinted blurred backdrop in player", blurBackground) {
                scope.launch { prefs.setBlurArtworkBackground(it) }
            }
            if (blurBackground) {
                item {
                    SettingsSliderItem(
                        title = "Blur Intensity",
                        value = blurStrength,
                        range = 0.1f..0.7f,
                        label = "${(blurStrength * 100).toInt()}%",
                        onValueChange = { scope.launch { prefs.setBlurStrength(it) } },
                    )
                }
            }
            settingsToggle("Artwork Animation", "Scale artwork on play/pause", artworkAnimation) {
                scope.launch { prefs.setArtworkAnimation(it) }
            }
            settingsToggle("Haptic Feedback", "Vibrate on seek and long-press", hapticFeedback) {
                scope.launch { prefs.setHapticFeedback(it) }
            }
            settingsToggle("Show Bitrate & Format", "Display audio quality badge in player", showBitrateInfo) {
                scope.launch { prefs.setShowBitrateInfo(it) }
            }
            item {
                SettingsSliderItem(
                    title = "Corner Radius",
                    value = cornerRadius.toFloat(),
                    range = 0f..40f,
                    label = "${cornerRadius}dp",
                    steps = 40,
                    onValueChange = { scope.launch { prefs.setCornerRadius(it.toInt()) } },
                )
            }
            item {
                SettingsSliderItem(
                    title = "Album Grid Columns",
                    value = albumGridCols.toFloat(),
                    range = 2f..4f,
                    label = "$albumGridCols columns",
                    steps = 1,
                    onValueChange = { scope.launch { prefs.setAlbumGridColumns(it.toInt()) } },
                )
            }

            // ─── Player ───────────────────────────────────────────────────────
            settingsSectionHeader("Player", Icons.Rounded.MusicNote)

            item {
                SegmentedSettingsItem(
                    title = "Player Layout",
                    options = listOf("Standard" to "STANDARD", "Big Artwork" to "ARTWORK_BIG", "Lyrics Focus" to "LYRICS_FOCUS"),
                    selected = playerLayout,
                    onSelect = { scope.launch { prefs.setPlayerLayout(it) } },
                )
            }
            item {
                SegmentedSettingsItem(
                    title = "Mini Player Style",
                    options = listOf("Compact" to "COMPACT", "Card" to "CARD", "Floating" to "FLOATING"),
                    selected = miniPlayerStyle,
                    onSelect = { scope.launch { prefs.setMiniPlayerStyle(it) } },
                )
            }
            settingsToggle("Show Lyrics Button", "Display lyrics shortcut in player footer", showLyricsBtn) {
                scope.launch { prefs.setShowLyricsButton(it) }
            }
            item {
                SettingsSliderItem(
                    title = "Lyrics Font Size",
                    value = lyricsFontScale,
                    range = 0.50f..1.75f,
                    label = "${(lyricsFontScale * 100).toInt()}%",
                    steps = 4,
                    onValueChange = { newValue ->
                        val step = 0.25f
                        val rounded = (newValue / step).roundToInt() * step
                        scope.launch { prefs.setLyricsFontScale(rounded.coerceIn(0.50f, 1.75f)) }
                    },
                )
            }

            // ─── Playback ─────────────────────────────────────────────────────
            settingsSectionHeader("Playback", Icons.Rounded.PlayCircle)

            settingsToggle("Gapless Playback", "Smooth transition between tracks", gapless) {
                scope.launch { prefs.setGaplessEnabled(it) }
            }
            settingsToggle("Skip Silence", "Automatically skip silent parts of tracks", skipSilence) {
                scope.launch { prefs.setSkipSilence(it) }
            }
            item {
                SettingsSliderItem(
                    title = "Crossfade Duration",
                    value = crossfadeMs.toFloat(),
                    range = 0f..10000f,
                    label = if (crossfadeMs == 0) "Disabled" else "${crossfadeMs / 1000}s",
                    steps = 10,
                    onValueChange = { newValue ->
                        val step = 1000f
                        val rounded = (newValue / step).roundToInt() * step
                        scope.launch { prefs.setCrossfadeDuration(rounded.toInt()) }
                    },
                )
            }
            item {
                SettingsSliderItem(
                    title = "Playback Speed",
                    value = playbackSpeed,
                    range = 0.5f..2.0f,
                    label = "${playbackSpeed}x",
                    steps = 6,
                    onValueChange = { newValue ->
                        val step = 0.25f
                        val rounded = (newValue / step).roundToInt() * step
                        scope.launch { prefs.setPlaybackSpeed(rounded.coerceIn(0.5f, 2.0f)) }
                    },
                )
            }
            item {
                SettingsSliderItem(
                    title = "Playback Pitch",
                    value = playbackPitch,
                    range = 0.5f..2.0f,
                    label = "${playbackPitch}x",
                    steps = 6,
                    onValueChange = { newValue ->
                        val step = 0.25f
                        val rounded = (newValue / step).roundToInt() * step
                        scope.launch { prefs.setPlaybackPitch(rounded.coerceIn(0.5f, 2.0f)) }
                    },
                )
            }
            settingsToggle("Resume on Headphones", "Continue playback when headphones are connected", resumeOnHeadphones) {
                scope.launch { prefs.setResumeOnHeadphones(it) }
            }
            settingsToggle("Pause on Disconnect", "Stop playback when headphones are removed", pauseOnHeadphonesOut) {
                scope.launch { prefs.setPauseOnHeadphonesOut(it) }
            }
            settingsToggle("Audio Ducking", "Lower volume when other apps play sound", duckAudio) {
                scope.launch { prefs.setDuckAudioOnFocusLoss(it) }
            }
            settingsToggle("Smart Shuffle", "Prioritise higher rated and recent tracks", smartShuffle) {
                scope.launch { prefs.setSmartShuffleEnabled(it) }
            }

            // ─── Audio ────────────────────────────────────────────────────────
            settingsSectionHeader("Audio", Icons.Rounded.GraphicEq)

            item {
                SegmentedSettingsItem(
                    title = "ReplayGain Mode",
                    options = listOf("Off" to "OFF", "Track" to "TRACK", "Album" to "ALBUM"),
                    selected = replayGainMode,
                    onSelect = { scope.launch { prefs.setReplayGainMode(it) } },
                )
            }
            item {
                SettingsSliderItem(
                    title = "ReplayGain Preamp",
                    value = replayGainPreamp,
                    range = -15f..15f,
                    label = when {
                        replayGainPreamp == 0f -> "0 dB"
                        replayGainPreamp > 0 -> "+${replayGainPreamp.toInt()} dB"
                        else -> "${replayGainPreamp.toInt()} dB"
                    },
                    steps = 30,
                    onValueChange = { scope.launch { prefs.setReplayGainPreamp(it) } },
                )
            }
            settingsToggle("Volume Normalisation", "Equalise loudness across all tracks", volumeNorm) {
                scope.launch { prefs.setVolumeNormalization(it) }
            }

            // ─── Library ──────────────────────────────────────────────────────
            settingsSectionHeader("Library", Icons.Rounded.LibraryMusic)

            item {
                ScanLibraryItem(isSyncing) {
                    libraryViewModel.syncLibrary()
                }
            }
            item {
                SettingsSliderItem(
                    title = "Minimum Track Duration",
                    value = (minDurationMs / 1000f),
                    range = 0f..300f,
                    label = if (minDurationMs == 0L) "No filter" else "${minDurationMs / 1000}s",
                    steps = 30,
                    onValueChange = { newValue ->
                        val step = 10f
                        val rounded = (newValue / step).roundToInt() * step
                        scope.launch { prefs.setMinTrackDuration((rounded.toLong() * 1000).coerceAtLeast(0)) }
                    },
                )
            }
            settingsToggle("Show Artwork in Lists", "Display album art thumbnails in song lists", showArtworkList) {
                scope.launch { prefs.setShowArtworkInList(it) }
            }
            settingsToggle("Group by Album Artist", "Use album artist for grouping (not track artist)", groupByAlbumArtist) {
                scope.launch { prefs.setGroupByAlbumArtist(it) }
            }
            settingsToggle("Show Filename as Title", "Fall back to filename when title tag is missing", showFilenameTitle) {
                scope.launch { prefs.setShowFilenameAsTitle(it) }
            }
            settingsToggle("Ignore Articles in Sort", "Sort \"The Beatles\" as \"Beatles\"", ignoreArticles) {
                scope.launch { prefs.setIgnoreArticles(it) }
            }
            item {
                var showDelimiterDialog by remember { mutableStateOf(false) }
                SettingsTextItem(
                    title = "Artist Delimiter",
                    subtitle = "Characters that split multi-artist tags: $artistDelimiter",
                    onClick = { showDelimiterDialog = true },
                )
                if (showDelimiterDialog) {
                    var delimiterInput by remember { mutableStateOf(artistDelimiter) }
                    AlertDialog(
                        onDismissRequest = { showDelimiterDialog = false },
                        title = { Text("Artist Delimiter") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Characters used to split multi-artist tags (e.g. \"Artist1, Artist2\"). " +
                                            "Enter all split characters with no spaces between them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedTextField(
                                    value = delimiterInput,
                                    onValueChange = { delimiterInput = it },
                                    label = { Text("Delimiter characters") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    scope.launch { prefs.setArtistDelimiter(delimiterInput) }
                                    showDelimiterDialog = false
                                },
                                enabled = delimiterInput.isNotBlank(),
                            ) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDelimiterDialog = false }) { Text("Cancel") }
                        },
                    )
                }
            }
            item {
                SegmentedSettingsItem(
                    title = "Auto Scan Interval",
                    options = listOf("Off" to "0", "1 hr" to "1", "6 hrs" to "6", "24 hrs" to "24"),
                    selected = autoScanHours.toString(),
                    onSelect = { scope.launch { prefs.setAutoScanIntervalHours(it.toInt()) } },
                )
            }

            // ─── Last.fm ──────────────────────────────────────────────────────
            settingsSectionHeader("Last.fm Scrobbling", Icons.Rounded.Radio)

            item {
                LastFmSection(
                    authState = lastFmAuthState,
                    enabled = lastFmEnabled,
                    nowPlayingEnabled = lastFmNowPlaying,
                    scrobblePct = lastFmScrobblePct,
                    scrobbleMinSecs = lastFmScrobbleMinSecs,
                    onlyOnWifi = lastFmOnlyWifi,
                    offlineQueue = lastFmOfflineQueue,
                    pendingScrobbles = lastFmPending,
                    onEnabledChange = { settingsViewModel.setLastFmEnabled(it) },
                    onLogin = { u, p -> settingsViewModel.lastFmLogin(u, p) },
                    onLogout = { settingsViewModel.lastFmLogout() },
                    onNowPlayingChange = { settingsViewModel.setLastFmNowPlaying(it) },
                    onScrobblePctChange = { settingsViewModel.setLastFmScrobblePct(it) },
                    onScrobbleMinSecsChange = { settingsViewModel.setLastFmScrobbleMinSecs(it) },
                    onOnlyOnWifiChange = { settingsViewModel.setLastFmOnlyWifi(it) },
                    onOfflineQueueChange = { settingsViewModel.setLastFmOfflineQueue(it) },
                    onSnackbar = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } },
                )
            }

            // ─── Notification ─────────────────────────────────────────────────
            settingsSectionHeader("Notification", Icons.Rounded.Notifications)

            settingsToggle("Show Artwork on Lock Screen", "Display album art on lock screen controls", lockscreenArtwork) {
                scope.launch { prefs.setLockscreenArtwork(it) }
            }
            settingsToggle("Show Skip Buttons", "Include previous/next in notification shade", showSkipButtons) {
                scope.launch { prefs.setShowSkipButtons(it) }
            }

            // ─── History ──────────────────────────────────────────────────────
            settingsSectionHeader("Play History", Icons.Rounded.History)

            settingsToggle("Track Play History", "Record which tracks you've listened to", historyEnabled) {
                scope.launch { prefs.setHistoryEnabled(it) }
            }
            if (historyEnabled) {
                item {
                    SettingsSliderItem(
                        title = "Minimum Listen Duration",
                        value = minListenSecs.toFloat(),
                        range = 10f..120f,
                        label = "${minListenSecs}s",
                        steps = 11,
                        onValueChange = { newValue ->
                            val step = 10f
                            val rounded = (newValue / step).roundToInt() * step
                            val finalSecs = rounded.toInt().coerceIn(10, 120)
                            scope.launch { prefs.setListenThresholds(finalSecs, minListenPct) }
                        },
                    )
                }
                item {
                    SettingsSliderItem(
                        title = "Minimum Listen Percentage",
                        value = minListenPct,
                        range = 0.1f..1.0f,
                        label = "${(minListenPct * 100).toInt()}%",
                        steps = 9,
                        onValueChange = { newValue ->
                            val step = 0.1f
                            val rounded = (newValue / step).roundToInt() * step
                            val finalPct = rounded.coerceIn(0.1f, 1.0f)
                            scope.launch { prefs.setListenThresholds(minListenSecs, finalPct) }
                        },
                    )
                }
                item {
                    SettingsSliderItem(
                        title = "Max History Items",
                        value = maxHistory.toFloat(),
                        range = 100f..5000f,
                        label = "$maxHistory items",
                        steps = 49,
                        onValueChange = { newValue ->
                            val step = 100f
                            val rounded = (newValue / step).roundToInt() * step
                            val final = rounded.toInt().coerceIn(100, 5000)
                            scope.launch { prefs.setMaxHistoryItems(final) }
                        },
                    )
                }
            }

            // ─── About ────────────────────────────────────────────────────────
            settingsSectionHeader("About", Icons.Rounded.Info)

            item {
                SettingsTextItem(
                    title = "Resonance",
                    subtitle = "v1.0.0",
                    icon = Icons.Rounded.MusicNote,
                )
            }
            item {
                val uriHandler = LocalUriHandler.current
                SettingsTextItem(
                    title = "GitHub Repository",
                    subtitle = "View source code and report issues",
                    icon = Icons.AutoMirrored.Rounded.OpenInNew,
                    trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { uriHandler.openUri("https://github.com/yuw1xx/resonance") },
                )
            }
            item {
                val uriHandler = LocalUriHandler.current
                SettingsTextItem(
                    title = "App License",
                    subtitle = "View Resonance's open source license",
                    icon = Icons.Rounded.Gavel,
                    trailingIcon = Icons.AutoMirrored.Rounded.OpenInNew,
                    onClick = { uriHandler.openUri("https://github.com/yuw1xx/resonance/blob/main/LICENSE") },
                )
            }
            item {
                SettingsTextItem(
                    title = "Third-Party Licenses",
                    subtitle = "Open source libraries used in this project",
                    icon = Icons.Rounded.Description,
                    onClick = onNavigateToLicenses,
                )
            }

            // ─── Danger Zone ──────────────────────────────────────────────────
            settingsSectionHeader("Data", Icons.Rounded.Storage)

            item {
                var showClearHistoryDialog by remember { mutableStateOf(false) }
                val isClearingHistory by libraryViewModel.isClearingHistory.collectAsState()

                SettingsTextItem(
                    title = "Clear Playback History",
                    subtitle = if (isClearingHistory) "Clearing…" else "Permanently delete all history records",
                    icon = Icons.Rounded.DeleteForever,
                    tint = MaterialTheme.colorScheme.error,
                    onClick = { if (!isClearingHistory) showClearHistoryDialog = true },
                )

                if (showClearHistoryDialog) {
                    AlertDialog(
                        onDismissRequest = { showClearHistoryDialog = false },
                        icon = {
                            Icon(
                                Icons.Rounded.DeleteForever,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        title = { Text("Clear Playback History?") },
                        text = {
                            Text(
                                "This will permanently delete all play counts and listen records. " +
                                        "Smart Queue, Most Played, and Lost Memories will be reset. " +
                                        "This cannot be undone.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showClearHistoryDialog = false
                                    libraryViewModel.clearHistory()
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Playback history cleared")
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) {
                                Text("Clear History")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showClearHistoryDialog = false }) {
                                Text("Cancel")
                            }
                        },
                    )
                }
            }
        }
    }
}

// ─── Last.fm Section ─────────────────────────────────────────────────────────

@Composable
private fun LastFmSection(
    authState: LastFmAuthState,
    enabled: Boolean,
    nowPlayingEnabled: Boolean,
    scrobblePct: Float,
    scrobbleMinSecs: Int,
    onlyOnWifi: Boolean,
    offlineQueue: Boolean,
    pendingScrobbles: Int,
    onEnabledChange: (Boolean) -> Unit,
    onLogin: (String, String) -> Unit,
    onLogout: () -> Unit,
    onNowPlayingChange: (Boolean) -> Unit,
    onScrobblePctChange: (Float) -> Unit,
    onScrobbleMinSecsChange: (Int) -> Unit,
    onOnlyOnWifiChange: (Boolean) -> Unit,
    onOfflineQueueChange: (Boolean) -> Unit,
    onSnackbar: (String) -> Unit,
) {
    var showLoginDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        // Auth card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = when (authState) {
                    is LastFmAuthState.Authenticated -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    is LastFmAuthState.Error         -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                    else                             -> MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    // Last.fm logo pill
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFD51007),
                        modifier = Modifier.padding(end = 12.dp),
                    ) {
                        Text(
                            "Last.fm",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }

                    when (authState) {
                        is LastFmAuthState.Authenticated -> {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    authState.username,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "${authState.playCount} scrobbles",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = enabled,
                                onCheckedChange = onEnabledChange,
                            )
                        }
                        is LastFmAuthState.Loading -> {
                            Text("Signing in…", modifier = Modifier.weight(1f))
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                        is LastFmAuthState.Error -> {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Sign-in failed", style = MaterialTheme.typography.titleSmall)
                                Text(authState.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                        LastFmAuthState.Idle -> {
                            Text(
                                "Not signed in",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                when (authState) {
                    is LastFmAuthState.Authenticated -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (pendingScrobbles > 0) {
                                AssistChip(
                                    onClick = { onSnackbar("$pendingScrobbles scrobbles pending sync") },
                                    label = { Text("$pendingScrobbles pending") },
                                    leadingIcon = { Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(16.dp)) },
                                )
                            }
                            OutlinedButton(onClick = onLogout, modifier = Modifier.weight(1f)) {
                                Text("Sign Out")
                            }
                        }
                    }
                    is LastFmAuthState.Error -> {
                        Button(onClick = { showLoginDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Try Again")
                        }
                    }
                    else -> {
                        Button(onClick = { showLoginDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Login, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Sign In to Last.fm")
                        }
                    }
                }
            }
        }

        // Scrobble options (only if authenticated + enabled)
        if (authState is LastFmAuthState.Authenticated && enabled) {
            Spacer(Modifier.height(12.dp))

            SettingsToggleCompact("Now Playing", "Send 'Now Playing' status when a track starts", nowPlayingEnabled, onNowPlayingChange)
            SettingsToggleCompact("Scrobble Only on Wi-Fi", "Save mobile data by queuing scrobbles until on Wi-Fi", onlyOnWifi, onOnlyOnWifiChange)
            SettingsToggleCompact("Offline Scrobble Queue", "Buffer scrobbles when offline and submit later", offlineQueue, onOfflineQueueChange)

            Spacer(Modifier.height(4.dp))

            SettingsSliderItem(
                title = "Scrobble After",
                value = scrobbleMinSecs.toFloat(),
                range = 10f..120f,
                label = "${scrobbleMinSecs}s",
                steps = 11,
                onValueChange = { onScrobbleMinSecsChange(it.toInt()) },
                compact = true,
            )
            SettingsSliderItem(
                title = "Scrobble at % of track",
                value = scrobblePct,
                range = 0.25f..1.0f,
                label = "${(scrobblePct * 100).toInt()}%",
                steps = 3,
                onValueChange = onScrobblePctChange,
                compact = true,
            )
        }
    }

    if (showLoginDialog) {
        LastFmLoginDialog(
            onDismiss = { showLoginDialog = false },
            onLogin = { u, p ->
                onLogin(u, p)
                showLoginDialog = false
            },
        )
    }
}

@Composable
private fun LastFmLoginDialog(
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFFD51007)) {
                Text("Last.fm", fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        },
        title = { Text("Sign In to Last.fm") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Your password is sent securely to Last.fm and never stored by Resonance. " +
                            "Only the session token is saved.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                )
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = if (showPassword) "Hide password" else "Show password",
                            )
                        }
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLogin(username.trim(), password) },
                enabled = username.isNotBlank() && password.isNotBlank(),
            ) {
                Text("Sign In")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ─── Reusable Settings Components ─────────────────────────────────────────────

@Composable
private fun ThemeColorPicker(
    current: Int?,
    onPick: (Int) -> Unit,
) {
    val colors = remember {
        listOf(
            Color(0xFF6750A4), Color(0xFF006874), Color(0xFF0B6299),
            Color(0xFF006E2C), Color(0xFF8B1A4A), Color(0xFFB1460E),
            Color(0xFF6B5E10), Color(0xFF4A548C),
        )
    }
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Accent Colour",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            colors.forEach { color ->
                val selected = current == color.toArgb()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .clickable { onPick(color.toArgb()) },
                ) {
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentedSettingsItem(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (label, value) ->
                SegmentedButton(
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    onClick = { onSelect(value) },
                    selected = selected == value,
                    label = { Text(label, fontSize = 13.sp) },
                )
            }
        }
    }
}

@Composable
private fun SettingsSliderItem(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    label: String,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    compact: Boolean = false,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = if (compact) 2.dp else 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps,
            modifier = Modifier.padding(top = 0.dp),
        )
    }
}

@Composable
private fun SettingsToggleCompact(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onToggle)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun SettingsTextItem(
    title: String,
    subtitle: String,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit = {},
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = icon?.let {
            { Icon(it, null, tint = tint, modifier = Modifier.size(24.dp)) }
        },
        headlineContent = { Text(title, color = if (tint != MaterialTheme.colorScheme.onSurfaceVariant) tint else Color.Unspecified) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = trailingIcon?.let {
            { Icon(it, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
private fun ScanLibraryItem(isSyncing: Boolean, onScan: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onScan),
        headlineContent = { Text("Scan for Music") },
        supportingContent = {
            AnimatedContent(
                targetState = isSyncing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "sync_text"
            ) { syncing ->
                Text(
                    if (syncing) "Scanning your device…" else "Search device for new audio files",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            AnimatedContent(
                targetState = isSyncing,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "sync_icon"
            ) { syncing ->
                if (syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Rounded.Refresh, null)
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private fun LazyListScope.settingsSectionHeader(title: String, icon: ImageVector) {
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )
    }
}

private fun LazyListScope.settingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    item {
        ListItem(
            modifier = Modifier.clickable { onToggle(!checked) },
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingContent = {
                Switch(checked = checked, onCheckedChange = onToggle)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
    }
}