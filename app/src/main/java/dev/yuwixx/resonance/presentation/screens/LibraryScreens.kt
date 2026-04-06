package dev.yuwixx.resonance.presentation.screens

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import dev.yuwixx.resonance.data.model.*
import dev.yuwixx.resonance.data.model.RepeatMode as AppRepeatMode
import dev.yuwixx.resonance.presentation.components.*
import dev.yuwixx.resonance.presentation.viewmodel.LibraryViewModel
import dev.yuwixx.resonance.presentation.viewmodel.PlayerViewModel
import dev.yuwixx.resonance.data.preferences.ResonancePreferences
import dev.yuwixx.resonance.presentation.navigation.Screen
import dev.yuwixx.resonance.ui.theme.PresetColors
import kotlinx.coroutines.launch

// ─── Songs Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SongsScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onSearchClick: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val songs by libraryViewModel.allSongs.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val isSyncing by libraryViewModel.isSyncing.collectAsState()
    val playlists by libraryViewModel.allPlaylists.collectAsState()

    var selectedSongs by remember { mutableStateOf(setOf<Song>()) }
    val isSelectionMode = selectedSongs.isNotEmpty()

    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var songForInfoSheet by remember { mutableStateOf<Song?>(null) }
    var showInfoSheetPlaylistPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text("${selectedSongs.size} selected") },
                    navigationIcon = {
                        IconButton(onClick = { selectedSongs = emptySet() }) {
                            Icon(Icons.Rounded.Close, "Cancel")
                        }
                    },
                    actions = {
                        // Select All
                        IconButton(onClick = { selectedSongs = songs.toSet() }) {
                            Icon(Icons.Rounded.SelectAll, "Select All")
                        }
                        IconButton(onClick = { showAddToPlaylistDialog = true }) {
                            Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to Playlist")
                        }
                        IconButton(onClick = {
                            selectedSongs.forEach { playerViewModel.addToQueueEnd(it) }
                            selectedSongs = emptySet()
                        }) {
                            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Add to Queue")
                        }
                        IconButton(onClick = {
                            selectedSongs.forEach { playerViewModel.addToQueueNext(it) }
                            selectedSongs = emptySet()
                        }) {
                            Icon(Icons.Rounded.SkipNext, "Play Next")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            } else {
                LargeTopAppBar(
                    title = { Text("Songs", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = onSearchClick) {
                            Icon(Icons.Rounded.Search, "Search")
                        }
                        IconButton(onClick = { libraryViewModel.syncLibrary(force = true) }) {
                            if (isSyncing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(Icons.Rounded.Refresh, "Refresh")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (songs.isEmpty() && !isSyncing) {
                EmptyLibraryView(onScan = { libraryViewModel.syncLibrary() })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(songs, key = { it.id }) { song ->
                        SongCard(
                            song = song,
                            isPlaying = currentSong?.id == song.id,
                            isSelected = selectedSongs.contains(song),
                            onClick = {
                                if (isSelectionMode) {
                                    selectedSongs = if (selectedSongs.contains(song)) {
                                        selectedSongs - song
                                    } else {
                                        selectedSongs + song
                                    }
                                } else {
                                    playerViewModel.play(songs, songs.indexOf(song))
                                    onNavigateToPlayer()
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    selectedSongs = setOf(song)
                                }
                            },
                            onLeadingClick = {
                                if (isSelectionMode) {
                                    selectedSongs = if (selectedSongs.contains(song)) {
                                        selectedSongs - song
                                    } else {
                                        selectedSongs + song
                                    }
                                } else {
                                    songForInfoSheet = song
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = playlists,
            onDismiss = { showAddToPlaylistDialog = false },
            onPlaylistSelected = { playlist ->
                libraryViewModel.addSongsToPlaylist(playlist.id, selectedSongs.map { it.id })
                selectedSongs = emptySet()
                showAddToPlaylistDialog = false
            },
            onCreatePlaylist = { name ->
                libraryViewModel.createPlaylist(name)
            }
        )
    }

    songForInfoSheet?.let { song ->
        SongInfoBottomSheet(
            song = song,
            onDismiss = {
                songForInfoSheet = null
                showInfoSheetPlaylistPicker = false
            },
            onPlayNext = { playerViewModel.addToQueueNext(song) },
            onAddToQueue = { playerViewModel.addToQueueEnd(song) },
            onAddToPlaylist = { showInfoSheetPlaylistPicker = true },
            playlists = playlists,
            showPlaylistPicker = showInfoSheetPlaylistPicker,
            onPlaylistSelected = { playlist ->
                libraryViewModel.addSongsToPlaylist(playlist.id, listOf(song.id))
                songForInfoSheet = null
                showInfoSheetPlaylistPicker = false
            }
        )
    }
}

@Composable
fun EmptyLibraryView(onScan: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.LibraryMusic,
            null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No songs found",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onScan) {
            Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(8.dp))
            Text("Scan for Music")
        }
    }
}

// ─── Albums Screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    libraryViewModel: LibraryViewModel,
    onAlbumClick: (Album) -> Unit,
    onSearchClick: () -> Unit,
) {
    val albums by libraryViewModel.allAlbums.collectAsState()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Albums", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Rounded.Search, "Search")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        }
    ) { padding ->
        if (albums.isEmpty()) {
            EmptyLibraryView(onScan = { libraryViewModel.syncLibrary() })
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = 80.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums, key = { it.id }) { album ->
                    AlbumCard(album = album, onClick = { onAlbumClick(album) })
                }
            }
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        ArtworkImage(
            uri = album.artworkUri,
            contentDescription = album.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            cornerRadius = 16.dp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Artists Screen ───────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    libraryViewModel: LibraryViewModel,
    onArtistClick: (Artist) -> Unit,
    onSearchClick: () -> Unit,
) {
    val artists by libraryViewModel.allArtists.collectAsState()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Artists", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Rounded.Search, "Search")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        }
    ) { padding ->
        if (artists.isEmpty()) {
            EmptyLibraryView(onScan = { libraryViewModel.syncLibrary() })
        } else {
            val fetchImages by libraryViewModel.prefs.fetchArtistImages.collectAsState(initial = true)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(), bottom = 80.dp
                ),
            ) {
                items(artists, key = { it.name }) { artist ->
                    var imageUrl by remember(artist.name) { mutableStateOf<String?>(null) }
                    LaunchedEffect(artist.name, fetchImages) {
                        imageUrl = if (fetchImages) libraryViewModel.getArtistArtworkUrl(artist.name) else null
                    }
                    ListItem(
                        modifier = Modifier.clickable { onArtistClick(artist) },
                        headlineContent = { Text(artist.name, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text("${artist.songCount} songs · ${artist.albumCount} albums") },
                        leadingContent = {
                            if (fetchImages && imageUrl != null) {
                                coil.compose.AsyncImage(
                                    model = imageUrl,
                                    contentDescription = artist.name,
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape),
                                )
                            } else {
                                Surface(
                                    modifier = Modifier.size(52.dp),
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                ) {
                                    Icon(
                                        Icons.Rounded.Person,
                                        null,
                                        modifier = Modifier.padding(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
}

// ─── Playlists Screen ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    libraryViewModel: LibraryViewModel,
    onPlaylistClick: (Playlist) -> Unit,
) {
    val playlists by libraryViewModel.allPlaylists.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Playlists", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Rounded.Add, "New Playlist")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd, null, modifier = Modifier.size(72.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("No playlists yet", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(24.dp))
                    Button(onClick = { showCreateDialog = true }) {
                        Text("Create Playlist")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(
                    start = 12.dp, end = 12.dp, top = padding.calculateTopPadding(), bottom = 80.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist) })
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name: String ->
                libraryViewModel.createPlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun PlaylistCard(playlist: Playlist, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = "${playlist.songCount} songs",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ─── Detail Screen Components ─────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumId: Long,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val albums by libraryViewModel.allAlbums.collectAsState()
    val allSongs by libraryViewModel.allSongs.collectAsState()

    val album = remember(albums, albumId) { albums.find { it.id == albumId } }
    val albumSongs = remember(allSongs, album) {
        allSongs.filter { it.albumId == albumId }.sortedBy { it.trackNumber }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { padding ->
        album?.let {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                item {
                    AlbumHeader(album = it, songs = albumSongs) {
                        playerViewModel.play(albumSongs, 0)
                        onNavigateToPlayer()
                    }
                }
                itemsIndexed(albumSongs) { index, song ->
                    SongCard(
                        song = song,
                        onClick = {
                            playerViewModel.play(albumSongs, index)
                            onNavigateToPlayer()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AlbumHeader(album: Album, songs: List<Song>, onPlayClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ArtworkImage(
            uri = album.artworkUri,
            contentDescription = album.title,
            modifier = Modifier
                .size(240.dp)
                .shadow(16.dp, RoundedCornerShape(24.dp)),
            cornerRadius = 24.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = album.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${songs.size} songs · ${album.year.takeIf { it > 0 } ?: ""}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPlayClick,
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Play")
        }
    }
}

// ─── Artist Detail Screen ─────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    artistName: String,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onAlbumClick: (Album) -> Unit,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val artists by libraryViewModel.allArtists.collectAsState()
    val albums by libraryViewModel.allAlbums.collectAsState()
    val allSongs by libraryViewModel.allSongs.collectAsState()

    val artist = remember(artists, artistName) { artists.find { it.name == artistName } }
    val artistAlbums = remember(albums, artistName) { albums.filter { it.artist == artistName } }
    val artistSongs = remember(allSongs, artistName) { allSongs.filter { it.artist == artistName } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { padding ->
        artist?.let {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 80.dp),
            ) {
                item {
                    ArtistHeader(artist = it, songs = artistSongs, libraryViewModel = libraryViewModel) {
                        playerViewModel.play(artistSongs, 0)
                        onNavigateToPlayer()
                    }
                }

                if (artistAlbums.isNotEmpty()) {
                    item {
                        Text(
                            "Albums",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(artistAlbums) { album ->
                                Column(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clickable { onAlbumClick(album) }
                                ) {
                                    ArtworkImage(
                                        uri = album.artworkUri,
                                        contentDescription = album.title,
                                        modifier = Modifier.size(140.dp),
                                        cornerRadius = 16.dp,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        album.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        "Songs",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                itemsIndexed(artistSongs) { index, song ->
                    SongCard(
                        song = song,
                        onClick = {
                            playerViewModel.play(artistSongs, index)
                            onNavigateToPlayer()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistHeader(artist: Artist, songs: List<Song>, libraryViewModel: LibraryViewModel, onPlayClick: () -> Unit) {
    val fetchImages by libraryViewModel.prefs.fetchArtistImages.collectAsState(initial = true)
    var imageUrl by remember(artist.name) { mutableStateOf<String?>(null) }

    LaunchedEffect(artist.name, fetchImages) {
        if (fetchImages) {
            imageUrl = libraryViewModel.getArtistArtworkUrl(artist.name)
        } else {
            imageUrl = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (fetchImages && imageUrl != null) {
            coil.compose.AsyncImage(
                model = imageUrl,
                contentDescription = artist.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier
                    .size(160.dp)
                    .clip(CircleShape)
                    .shadow(8.dp, CircleShape),
            )
        } else {
            Surface(
                modifier = Modifier.size(160.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Rounded.Person,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = artist.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${artist.albumCount} albums · ${songs.size} songs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPlayClick,
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Play All")
        }
    }
}

// ─── Playlist Detail Screen ───────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val playlists by libraryViewModel.allPlaylists.collectAsState()
    val allSongs by libraryViewModel.allSongs.collectAsState()

    val playlist = remember(playlists, playlistId) { playlists.find { it.id == playlistId } }
    val playlistSongs: List<Song> = remember(allSongs, playlist) {
        playlist?.songs ?: emptyList()
    }

    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Rounded.Delete, "Delete Playlist")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        }
    ) { padding ->
        playlist?.let {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 80.dp),
            ) {
                item {
                    PlaylistHeader(playlist = it, songs = playlistSongs) {
                        if (playlistSongs.isNotEmpty()) {
                            playerViewModel.play(playlistSongs, 0)
                            onNavigateToPlayer()
                        }
                    }
                }
                itemsIndexed(playlistSongs) { index, song ->
                    SongCard(
                        song = song,
                        onClick = {
                            playerViewModel.play(playlistSongs, index)
                            onNavigateToPlayer()
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                libraryViewModel.removeSongsFromPlaylist(playlistId, listOf(song.id))
                            }) {
                                Icon(Icons.Rounded.RemoveCircleOutline, "Remove")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete this playlist?") },
            confirmButton = {
                TextButton(onClick = {
                    libraryViewModel.deletePlaylist(playlistId)
                    showDeleteDialog = false
                    onBack()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PlaylistHeader(playlist: Playlist, songs: List<Song>, onPlayClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(200.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "${songs.size} songs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onPlayClick,
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = RoundedCornerShape(16.dp),
            enabled = songs.isNotEmpty()
        ) {
            Icon(Icons.Rounded.PlayArrow, null)
            Spacer(Modifier.width(8.dp))
            Text("Play All")
        }
    }
}

// ─── Queue Screen ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun QueueScreen(
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val queue by playerViewModel.queue.collectAsState()
    val currentSongIndex by playerViewModel.currentQueueIndex.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playing Queue", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { playerViewModel.clearQueue() }) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                }
            )
        }
    ) { padding ->
        if (queue.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Queue is empty", style = MaterialTheme.typography.titleMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding,
            ) {
                itemsIndexed(queue) { index, song ->
                    val isCurrent = index == currentSongIndex
                    SongCard(
                        song = song,
                        isPlaying = isCurrent,
                        onClick = { playerViewModel.play(queue, index) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { playerViewModel.removeFromQueue(index) }) {
                                    Icon(Icons.Rounded.Close, "Remove")
                                }
                                Icon(
                                    Icons.Rounded.DragHandle,
                                    "Reorder",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── Settings Screen ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, libraryViewModel: LibraryViewModel) {
    val scope = rememberCoroutineScope()
    val prefs = libraryViewModel.prefs

    val dynamicColor by prefs.dynamicColorEnabled.collectAsState(initial = true)
    val presetColorInt by prefs.presetColor.collectAsState(initial = null)

    val showWaveform by prefs.showWaveformSeekbar.collectAsState(initial = true)
    val gapless by prefs.gaplessEnabled.collectAsState(initial = true)
    val skipSilence by prefs.skipSilence.collectAsState(initial = false)
    val crossfadeMs by prefs.crossfadeDurationMs.collectAsState(initial = 0)
    val replayGainMode by prefs.replayGainMode.collectAsState(initial = "TRACK")
    val fetchArtistImages by prefs.fetchArtistImages.collectAsState(initial = true)
    val isSyncing by libraryViewModel.isSyncing.collectAsState()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item { SettingsGroupHeader("Appearance") }
            item {
                SettingsToggle(
                    title = "System Dynamic Color",
                    subtitle = "Use Android 12+ wallpaper colors",
                    checked = dynamicColor,
                    onToggle = { scope.launch { prefs.setDynamicColorEnabled(it) } }
                )
            }

            if (!dynamicColor) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            "Theme Preset",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PresetColors.forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (presetColorInt == color.toArgb()) 3.dp else 0.dp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            scope.launch { prefs.setPresetColor(color.toArgb()) }
                                        }
                                )
                            }
                        }
                    }
                }
            }

            item {
                SettingsToggle(
                    title = "Waveform Seekbar",
                    subtitle = "Show audio waveform in player",
                    checked = showWaveform,
                    onToggle = { scope.launch { prefs.setShowWaveformSeekbar(it) } }
                )
            }

            item { SettingsGroupHeader("Library") }
            item {
                SettingsToggle(
                    title = "Fetch Artist Images",
                    subtitle = "Download artist photos from Deezer",
                    checked = fetchArtistImages,
                    onToggle = { scope.launch { prefs.setFetchArtistImages(it) } }
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { libraryViewModel.syncLibrary() },
                    headlineContent = { Text("Scan for Music") },
                    supportingContent = { Text("Search device for new audio files") },
                    trailingContent = {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(Icons.Rounded.Refresh, null)
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )
            }

            item { SettingsGroupHeader("Playback") }
            item {
                SettingsToggle(
                    title = "Gapless Playback",
                    checked = gapless,
                    onToggle = { scope.launch { prefs.setGaplessEnabled(it) } }
                )
            }
            item {
                SettingsToggle(
                    title = "Skip Silence",
                    checked = skipSilence,
                    onToggle = { scope.launch { prefs.setSkipSilence(it) } }
                )
            }
            item {
                SettingsSlider(
                    title = "Crossfade Duration",
                    subtitle = "${crossfadeMs}ms",
                    value = crossfadeMs.toFloat(),
                    range = 0f..10000f,
                    onValueChange = { scope.launch { prefs.setCrossfadeDuration(it.toInt()) } }
                )
            }

            item { SettingsGroupHeader("Audio") }
            item {
                SettingsItem(
                    title = "ReplayGain Mode",
                    subtitle = replayGainMode,
                    onClick = {
                        val next = when(replayGainMode) {
                            "TRACK" -> "ALBUM"
                            "ALBUM" -> "OFF"
                            else -> "TRACK"
                        }
                        scope.launch { prefs.setReplayGainMode(next) }
                    }
                )
            }

            item { SettingsGroupHeader("About") }
            item { SettingsItem("Resonance", "v1.2.0 · Expressive Edition") }
        }
    }
}

@Composable
private fun SettingsGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsItem(title: String, subtitle: String, onClick: () -> Unit = {}) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable { onToggle(!checked) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun SettingsSlider(
    title: String,
    subtitle: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Column {
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = range,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            LazyColumn {
                item {
                    ListItem(
                        modifier = Modifier.clickable { showCreateDialog = true },
                        headlineContent = { Text("New Playlist...", color = MaterialTheme.colorScheme.primary) },
                        leadingContent = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) }
                    )
                }
                items(playlists) { playlist ->
                    ListItem(
                        modifier = Modifier.clickable { onPlaylistSelected(playlist) },
                        headlineContent = { Text(playlist.name) },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoBottomSheet(
    song: Song,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    playlists: List<Playlist>,
    showPlaylistPicker: Boolean,
    onPlaylistSelected: (Playlist) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (showPlaylistPicker) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                Text(
                    "Add to Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
                LazyColumn {
                    items(playlists) { playlist ->
                        ListItem(
                            modifier = Modifier.clickable { onPlaylistSelected(playlist) },
                            headlineContent = { Text(playlist.name) },
                            leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) }
                        )
                    }
                }
            }
        } else {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ListItem(
                    headlineContent = { Text(song.title, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(song.displayArtist) },
                    leadingContent = {
                        ArtworkImage(
                            uri = song.artworkUri,
                            contentDescription = song.album,
                            modifier = Modifier.size(48.dp),
                            cornerRadius = 8.dp
                        )
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ListItem(
                    modifier = Modifier.clickable {
                        onPlayNext()
                        onDismiss()
                    },
                    headlineContent = { Text("Play Next") },
                    leadingContent = { Icon(Icons.Rounded.SkipNext, null) }
                )
                ListItem(
                    modifier = Modifier.clickable {
                        onAddToQueue()
                        onDismiss()
                    },
                    headlineContent = { Text("Add to Queue") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null) }
                )
                ListItem(
                    modifier = Modifier.clickable { onAddToPlaylist() },
                    headlineContent = { Text("Add to Playlist") },
                    leadingContent = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) }
                )
            }
        }
    }
}

// ─── Liked Songs Screen ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedSongsScreen(
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val allSongs by libraryViewModel.allSongs.collectAsState()
    val likedIds by playerViewModel.likedSongIds.collectAsState(initial = emptyList())

    // Preserve liked order (most-recently-liked first), matching only loaded songs
    val likedSongs: List<Song> = remember(allSongs, likedIds) {
        val songMap = allSongs.associateBy { it.id }
        likedIds.mapNotNull { songMap[it] }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Liked Songs", fontWeight = FontWeight.Bold)
                        if (likedSongs.isNotEmpty()) {
                            Text(
                                "${likedSongs.size} songs",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
                actions = {
                    if (likedSongs.isNotEmpty()) {
                        IconButton(onClick = {
                            playerViewModel.play(likedSongs, 0)
                            onNavigateToPlayer()
                        }) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                "Play all",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (likedSongs.isEmpty()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val pulse = rememberInfiniteTransition(label = "heart_pulse")
                    val heartScale by pulse.animateFloat(
                        initialValue = 1f,
                        targetValue = 1.12f,
                        animationSpec = infiniteRepeatable(tween(800), androidx.compose.animation.core.RepeatMode.Reverse),
                        label = "heart_scale",
                    )
                    Icon(
                        Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .graphicsLayer { scaleX = heartScale; scaleY = heartScale },
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No liked songs yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap ♥ in the player to like a song",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = padding,
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(likedSongs, key = { _, s -> s.id }) { index, song ->
                    val animatedAlpha by animateFloatAsState(
                        targetValue = 1f,
                        animationSpec = tween(300, delayMillis = (index * 30).coerceAtMost(300)),
                        label = "item_alpha_$index",
                    )
                    SongCard(
                        song = song,
                        modifier = Modifier
                            .animateItem()
                            .alpha(animatedAlpha),
                        onClick = {
                            playerViewModel.play(likedSongs, index)
                            onNavigateToPlayer()
                        },
                    )
                }
            }
        }
    }
}

// ─── Search Screen ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    libraryViewModel: LibraryViewModel,
    playerViewModel: PlayerViewModel,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val allSongs by libraryViewModel.allSongs.collectAsState()
    val allAlbums by libraryViewModel.allAlbums.collectAsState()
    val allArtists by libraryViewModel.allArtists.collectAsState()

    var query by remember { mutableStateOf("") }
    val trimmed = query.trim()

    val filteredSongs = remember(trimmed, allSongs) {
        if (trimmed.isBlank()) emptyList()
        else allSongs.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                    it.artist.contains(trimmed, ignoreCase = true) ||
                    it.album.contains(trimmed, ignoreCase = true)
        }
    }
    val filteredAlbums = remember(trimmed, allAlbums) {
        if (trimmed.isBlank()) emptyList()
        else allAlbums.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                    it.artist.contains(trimmed, ignoreCase = true)
        }
    }
    val filteredArtists = remember(trimmed, allArtists) {
        if (trimmed.isBlank()) emptyList()
        else allArtists.filter { it.name.contains(trimmed, ignoreCase = true) }
    }

    val hasResults = filteredSongs.isNotEmpty() || filteredAlbums.isNotEmpty() || filteredArtists.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search songs, albums, artists…") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null)
                        },
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear")
                                }
                            }
                        },
                    )
                },
            )
        }
    ) { padding ->
        when {
            query.isBlank() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Search your library",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            !hasResults -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Rounded.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No results for \"$trimmed\"",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = padding,
                ) {
                    // ── Artists ──────────────────────────────────────────────
                    if (filteredArtists.isNotEmpty()) {
                        item {
                            Text(
                                "Artists",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(filteredArtists, key = { it.name }) { artist ->
                            ListItem(
                                headlineContent = { Text(artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        "${artist.albumCount} album${if (artist.albumCount != 1) "s" else ""} · " +
                                                "${artist.songCount} song${if (artist.songCount != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(48.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Rounded.Person, contentDescription = null)
                                        }
                                    }
                                },
                                modifier = Modifier.clickable { onArtistClick(artist) },
                            )
                        }
                    }

                    // ── Albums ───────────────────────────────────────────────
                    if (filteredAlbums.isNotEmpty()) {
                        item {
                            Text(
                                "Albums",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        items(filteredAlbums, key = { it.id }) { album ->
                            ListItem(
                                headlineContent = { Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text(
                                        "${album.artist} · ${album.songCount} song${if (album.songCount != 1) "s" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    ArtworkImage(
                                        uri = album.artworkUri,
                                        contentDescription = album.title,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(6.dp)),
                                    )
                                },
                                modifier = Modifier.clickable { onAlbumClick(album) },
                            )
                        }
                    }

                    // ── Songs ────────────────────────────────────────────────
                    if (filteredSongs.isNotEmpty()) {
                        item {
                            Text(
                                "Songs",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        itemsIndexed(filteredSongs, key = { _, s -> s.id }) { index, song ->
                            SongCard(
                                song = song,
                                onClick = {
                                    playerViewModel.play(filteredSongs, index)
                                    onNavigateToPlayer()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}