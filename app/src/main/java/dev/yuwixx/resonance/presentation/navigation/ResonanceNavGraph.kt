package dev.yuwixx.resonance.presentation.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import dev.yuwixx.resonance.presentation.screens.*
import dev.yuwixx.resonance.presentation.viewmodel.LibraryViewModel
import dev.yuwixx.resonance.presentation.viewmodel.PlayerViewModel
import dev.yuwixx.resonance.presentation.viewmodel.SettingsViewModel
import dev.yuwixx.resonance.presentation.components.MiniPlayer
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    data object Setup         : Screen("setup")
    data object Home          : Screen("home")
    data object Songs         : Screen("songs")
    data object Albums        : Screen("albums")
    data object Artists       : Screen("artists")
    data object Folders       : Screen("folders")
    data object Playlists     : Screen("playlists")
    data object LikedSongs    : Screen("liked_songs")
    data object Player        : Screen("player")
    data object Search        : Screen("search")
    data object Settings      : Screen("settings")
    data object Licenses      : Screen("licenses")
    data object NowPlayingQueue : Screen("now_playing_queue")
    data object LyricsEditor  : Screen("lyrics_editor")

    // NEW: Tag Editor Route
    data object TagEditor : Screen("tag_editor/{songId}") {
        fun createRoute(songId: Long) = "tag_editor/$songId"
    }

    data object AlbumDetail   : Screen("album_detail/{albumId}") {
        fun createRoute(albumId: Long) = "album_detail/$albumId"
    }
    data object ArtistDetail  : Screen("artist_detail/{artistName}") {
        fun createRoute(artistName: String) = "artist_detail/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
    }
    data object PlaylistDetail : Screen("playlist_detail/{playlistId}") {
        fun createRoute(playlistId: Long) = "playlist_detail/$playlistId"
    }
}

data class NavItem(
    val screen: Screen,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector = icon,
)

val navItems = listOf(
    NavItem(Screen.Home,       "Home",     Icons.Rounded.Home,                         Icons.Rounded.Home),
    NavItem(Screen.Songs,      "Songs",    Icons.Rounded.MusicNote,                    Icons.Rounded.MusicNote),
    NavItem(Screen.Albums,     "Albums",   Icons.Rounded.Album,                        Icons.Rounded.Album),
    NavItem(Screen.Artists,    "Artists",  Icons.Rounded.Person,                       Icons.Rounded.Person),
    NavItem(Screen.LikedSongs, "Liked",    Icons.Rounded.FavoriteBorder,               Icons.Rounded.Favorite),
    NavItem(Screen.Playlists,  "Playlists", Icons.AutoMirrored.Rounded.QueueMusic,     Icons.AutoMirrored.Rounded.QueueMusic),
)

// ─── Easing curves ────────────────────────────────────────────────────────────
private val EaseOutCubic = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
private val EaseInCubic  = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
@Composable
fun ResonanceNavGraph(
    playerViewModel: PlayerViewModel,
    receiveUri: android.net.Uri? = null,
    onReceiveDismiss: () -> Unit = {},
) {
    val navController = rememberNavController()
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val isFirstRun by libraryViewModel.prefs.isFirstRun.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    val currentSong by playerViewModel.currentSong.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val allPlaylists by libraryViewModel.allPlaylists.collectAsState()

    val miniPlayerStyle by playerViewModel.miniPlayerStyle.collectAsState()

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route
    val isPlayerRoute = currentRoute == Screen.Player.route
    val isSetupRoute = currentRoute == Screen.Setup.route

    val navigateToPlayer = { navController.navigate(Screen.Player.route) }

    LaunchedEffect(isFirstRun) {
        if (isFirstRun == true && currentRoute != Screen.Setup.route) {
            navController.navigate(Screen.Setup.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (!isSetupRoute) {
                Column {
                    AnimatedVisibility(
                        visible = currentSong != null && !isPlayerRoute,
                        enter = slideInVertically(
                            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)
                        ) { it } + fadeIn(tween(220)),
                        exit = slideOutVertically(tween(200, easing = EaseInCubic)) { it }
                                + fadeOut(tween(160)),
                    ) {
                        MiniPlayer(
                            playerViewModel = playerViewModel,
                            style = miniPlayerStyle,
                            onClick = navigateToPlayer,
                        )
                    }

                    if (!isPlayerRoute) {
                        NavigationBar(
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars,
                        ) {
                            navItems.forEach { item ->
                                val selected = currentDestination?.hierarchy
                                    ?.any { it.route == item.screen.route } == true
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            if (selected) item.selectedIcon else item.icon,
                                            contentDescription = item.label,
                                        )
                                    },
                                    label = { Text(item.label) },
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(item.screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding()),
            enterTransition = {
                slideInHorizontally(
                    spring(dampingRatio = 0.85f, stiffness = 400f)
                ) { it / 3 } + fadeIn(tween(220))
            },
            exitTransition = {
                fadeOut(tween(180))
            },
            popEnterTransition = {
                fadeIn(tween(220))
            },
            popExitTransition = {
                slideOutHorizontally(
                    spring(dampingRatio = 0.85f, stiffness = 400f)
                ) { it / 3 } + fadeOut(tween(180))
            },
        ) {
            composable(Screen.Setup.route) {
                SetupScreen(onComplete = {
                    scope.launch {
                        libraryViewModel.prefs.setFirstRunCompleted()
                        libraryViewModel.syncLibrary(force = true)
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Setup.route) { inclusive = true }
                        }
                    }
                })
            }

            composable(Screen.Home.route) {
                HomeScreen(
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                    onNavigateTo = { navController.navigate(it.route) },
                )
            }

            composable(Screen.Songs.route) {
                SongsScreen(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onSearchClick = { navController.navigate(Screen.Search.route) },
                    onNavigateToPlayer = navigateToPlayer,
                    onNavigateToTagEditor = { id -> navController.navigate(Screen.TagEditor.createRoute(id)) }
                )
            }

            composable(Screen.Albums.route) {
                AlbumsScreen(
                    libraryViewModel = libraryViewModel,
                    onAlbumClick = { album -> navController.navigate(Screen.AlbumDetail.createRoute(album.id)) },
                    onSearchClick = { navController.navigate(Screen.Search.route) }
                )
            }

            composable(Screen.Artists.route) {
                ArtistsScreen(
                    libraryViewModel = libraryViewModel,
                    onArtistClick = { artist -> navController.navigate(Screen.ArtistDetail.createRoute(artist.name)) },
                    onSearchClick = { navController.navigate(Screen.Search.route) }
                )
            }

            composable(Screen.Folders.route) {
                FoldersScreen(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.Playlists.route) {
                PlaylistsScreen(
                    libraryViewModel = libraryViewModel,
                    onPlaylistClick = { playlist -> navController.navigate(Screen.PlaylistDetail.createRoute(playlist.id)) }
                )
            }

            composable(Screen.LikedSongs.route) {
                LikedSongsScreen(
                    playerViewModel = playerViewModel,
                    libraryViewModel = libraryViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = navigateToPlayer,
                )
            }

            // ── Player — expands up from the mini-player bar ─────────────────
            composable(
                route = Screen.Player.route,
                enterTransition = {
                    slideInVertically(
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = 340f,
                        ),
                    ) { (it * 0.92f).toInt() } +
                            fadeIn(tween(260, easing = EaseOutCubic))
                },
                exitTransition = {
                    fadeOut(tween(200, easing = EaseInCubic))
                },
                popEnterTransition = {
                    fadeIn(tween(180))
                },
                popExitTransition = {
                    slideOutVertically(
                        animationSpec = spring(
                            dampingRatio = 0.9f,
                            stiffness = 420f,
                        ),
                    ) { (it * 0.88f).toInt() } +
                            fadeOut(tween(240, easing = EaseInCubic))
                },
            ) {
                PlayerScreen(
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onQueueClick = { navController.navigate(Screen.NowPlayingQueue.route) },
                    onLyricsEdit = { navController.navigate(Screen.LyricsEditor.route) },
                    playlists = allPlaylists,
                    onAddToPlaylist = { playlist ->
                        playerViewModel.currentSong.value?.let { song ->
                            libraryViewModel.addSongsToPlaylist(playlist.id, listOf(song.id))
                        }
                    },
                    onCreatePlaylist = { name ->
                        libraryViewModel.createPlaylist(name)
                    },
                )
            }

            composable(Screen.Search.route) {
                SearchScreen(
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onAlbumClick = { album -> navController.navigate(Screen.AlbumDetail.createRoute(album.id)) },
                    onArtistClick = { artist -> navController.navigate(Screen.ArtistDetail.createRoute(artist.name)) },
                    onNavigateToPlayer = navigateToPlayer,
                )
            }

            // NEW: Tag Editor Route
            composable(
                route = Screen.TagEditor.route,
                arguments = listOf(navArgument("songId") { type = NavType.LongType })
            ) { backStackEntry ->
                val songId = backStackEntry.arguments?.getLong("songId") ?: return@composable
                TagEditorScreen(
                    songId = songId,
                    libraryViewModel = libraryViewModel,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.AlbumDetail.route,
                arguments = listOf(navArgument("albumId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val albumId = backStackEntry.arguments?.getLong("albumId") ?: return@composable
                AlbumDetailScreen(
                    albumId = albumId,
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = navigateToPlayer,
                )
            }

            composable(
                route = Screen.ArtistDetail.route,
                arguments = listOf(navArgument("artistName") { type = NavType.StringType }),
            ) { backStackEntry ->
                val artistName = java.net.URLDecoder.decode(
                    backStackEntry.arguments?.getString("artistName") ?: return@composable,
                    "UTF-8"
                )
                ArtistDetailScreen(
                    artistName = artistName,
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onAlbumClick = { album -> navController.navigate(Screen.AlbumDetail.createRoute(album.id)) },
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = navigateToPlayer,
                )
            }

            composable(
                route = Screen.PlaylistDetail.route,
                arguments = listOf(navArgument("playlistId") { type = NavType.LongType }),
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: return@composable
                PlaylistDetailScreen(
                    playlistId = playlistId,
                    libraryViewModel = libraryViewModel,
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                    onNavigateToPlayer = navigateToPlayer,
                )
            }

            composable(Screen.NowPlayingQueue.route) {
                QueueScreen(
                    playerViewModel = playerViewModel,
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Screen.Settings.route) {
                val settingsViewModel: SettingsViewModel = hiltViewModel()
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToLicenses = { navController.navigate(Screen.Licenses.route) },
                    libraryViewModel = libraryViewModel,
                    settingsViewModel = settingsViewModel,
                )
            }

            composable(Screen.Licenses.route) {
                LicensesScreen(onBack = { navController.popBackStack() })
            }

            composable(Screen.LyricsEditor.route) {
                LyricsEditorScreen(
                    playerViewModel = playerViewModel,
                    lyricsRepository = playerViewModel.lyricsRepository,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        // ── Receive sheet — triggered by resonance://receive deep link ───────────
        receiveUri?.let { uri ->
            ReceiveSheet(
                uri       = uri,
                onDismiss = onReceiveDismiss,
                onPlayNow = { song ->
                    playerViewModel.play(listOf(song), 0)
                    navController.navigate(Screen.Player.route)
                },
            )
        }
    }
}