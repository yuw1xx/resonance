package dev.yuwixx.resonance.presentation.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.yuwixx.resonance.data.model.SmartQueueReason
import dev.yuwixx.resonance.presentation.components.*
import dev.yuwixx.resonance.presentation.navigation.Screen
import dev.yuwixx.resonance.presentation.viewmodel.LibraryViewModel
import dev.yuwixx.resonance.presentation.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onNavigateTo: (Screen) -> Unit,
) {
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val mostPlayed by libraryViewModel.mostPlayed.collectAsState()
    val isLoadingSmartQueue by playerViewModel.isLoadingSmartQueue.collectAsState()
    val smartQueueError by playerViewModel.smartQueueError.collectAsState()

    // Show snackbar on smart queue error
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(smartQueueError) {
        val error = smartQueueError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        playerViewModel.clearSmartQueueError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Resonance",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    IconButton(onClick = { onNavigateTo(Screen.Search) }) {
                        Icon(Icons.Rounded.Search, "Search")
                    }
                    IconButton(onClick = { onNavigateTo(Screen.Settings) }) {
                        Icon(Icons.Rounded.Settings, "Settings")
                    }
                },
                // FIX: Use surfaceContainer instead of background.
                // background is indistinguishable from the screen surface and loses M3 elevation
                // semantics. surfaceContainer is the correct M3 token for TopAppBar.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            // Now playing card — animated enter/exit so it doesn't just pop in/out
            item {
                // FIX: Wrap in AnimatedVisibility so the card slides/fades in when a song
                // starts playing, rather than abruptly appearing.
                AnimatedVisibility(
                    visible = currentSong != null,
                    enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                    exit = fadeOut(tween(200)) + shrinkVertically(tween(200)),
                ) {
                    currentSong?.let { song ->
                        ResumeCard(
                            song = song,
                            // FIX: Was hardcoded to `false`. Now correctly passed from state.
                            isPlaying = isPlaying,
                            onClick = { onNavigateTo(Screen.Player) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // Section: Most played
            if (mostPlayed.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Most Played",
                        icon = Icons.AutoMirrored.Rounded.TrendingUp,
                    )
                }
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(mostPlayed.take(10)) { song ->
                            CompactArtworkCard(
                                title = song.title,
                                subtitle = song.displayArtist,
                                artworkUri = song.artworkUri,
                                onClick = {
                                    playerViewModel.play(mostPlayed, mostPlayed.indexOf(song))
                                },
                            )
                        }
                    }
                }
            }

            // Section: Smart Queues
            item {
                SectionHeader(
                    title = "Create a Mix",
                    icon = Icons.Rounded.AutoAwesome,
                    // Show loading spinner in header while queue is building
                    isLoading = isLoadingSmartQueue,
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        // FIX: Use AssistChip instead of SuggestionChip.
                        // M3 spec: SuggestionChip is for autocomplete suggestions.
                        // AssistChip is the correct component for user-triggered shortcut actions.
                        SmartQueueChip("Related", Icons.Rounded.Link, isLoadingSmartQueue) {
                            playerViewModel.loadSmartQueue(SmartQueueReason.RELATED_BY_HISTORY)
                        }
                    }
                    item {
                        SmartQueueChip("Lost Memories", Icons.Rounded.History, isLoadingSmartQueue) {
                            playerViewModel.loadSmartQueue(SmartQueueReason.LOST_MEMORIES)
                        }
                    }
                    item {
                        SmartQueueChip("Same Era", Icons.Rounded.DateRange, isLoadingSmartQueue) {
                            playerViewModel.loadSmartQueue(SmartQueueReason.SIMILAR_RELEASE_DATE)
                        }
                    }
                    item {
                        SmartQueueChip("By Genre", Icons.Rounded.Category, isLoadingSmartQueue) {
                            playerViewModel.loadSmartQueue(SmartQueueReason.SAME_GENRE)
                        }
                    }
                    item {
                        SmartQueueChip("Top Tracks", Icons.Rounded.StarRate, isLoadingSmartQueue) {
                            playerViewModel.loadSmartQueue(SmartQueueReason.MOST_PLAYED)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResumeCard(
    song: dev.yuwixx.resonance.data.model.Song,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtworkImage(
                uri = song.artworkUri,
                contentDescription = song.album,
                modifier = Modifier.size(64.dp),
                cornerRadius = 16.dp,
                isAnimating = isPlaying,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Now Playing",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
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
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // FIX: Show a spinner in the section header while the smart queue loads,
        // so chips don't appear to silently do nothing.
        AnimatedVisibility(visible = isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

@Composable
private fun CompactArtworkCard(
    title: String,
    subtitle: String,
    artworkUri: Any?,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(160.dp),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column {
            // FIX: Clip the image to the card's top corners so it doesn't bleed into the
            // rounded shape. Previously cornerRadius = 0.dp caused a visual glitch where the
            // image appeared to overflow the card's rounded border.
            ArtworkImage(
                uri = artworkUri,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(
                        androidx.compose.foundation.shape.RoundedCornerShape(
                            topStart = 12.dp,
                            topEnd = 12.dp,
                        )
                    ),
                cornerRadius = 0.dp,
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun SmartQueueChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    // FIX: Use AssistChip instead of SuggestionChip.
    // Per M3 spec: SuggestionChip = autocomplete/search suggestions (passive).
    // AssistChip = user-triggered shortcut actions (active) — which is exactly what these are.
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
        shape = MaterialTheme.shapes.medium,
        // Dim chips while a queue is already loading to prevent double-taps
        enabled = !isLoading,
    )
}