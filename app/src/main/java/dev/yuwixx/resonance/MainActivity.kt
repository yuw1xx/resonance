package dev.yuwixx.resonance

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import dev.yuwixx.resonance.presentation.navigation.ResonanceNavGraph
import dev.yuwixx.resonance.presentation.viewmodel.PlayerViewModel
import dev.yuwixx.resonance.presentation.viewmodel.SettingsViewModel
import dev.yuwixx.resonance.ui.theme.ResonanceTheme
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val receiveUri = MutableStateFlow<Uri?>(null)

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        receiveUri.value = intent?.data

        setContent {
            val playerViewModel: PlayerViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()

            val dynamicColor by playerViewModel.dynamicColor.collectAsState()
            val darkThemePref by settingsViewModel.darkTheme.collectAsState()
            val systemDark = isSystemInDarkTheme()
            val uri by receiveUri.collectAsState()

            val useDark = when (darkThemePref) {
                "LIGHT" -> false
                "DARK"  -> true
                else    -> systemDark
            }

            // Update Check Flow
            val updateState by settingsViewModel.updateState.collectAsState()
            val context = LocalContext.current

            LaunchedEffect(Unit) {
                val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                val versionName = pkgInfo.versionName ?: "1.0.0"
                settingsViewModel.checkForUpdates(versionName, isManual = false)
            }

            when (val state = updateState) {
                is SettingsViewModel.UpdateState.Available -> {
                    AlertDialog(
                        onDismissRequest = { settingsViewModel.dismissUpdate() },
                        icon = { Icon(Icons.Rounded.NewReleases, null, tint = MaterialTheme.colorScheme.primary) },
                        title = { Text("Update Available") },
                        text = {
                            Column {
                                Text("Version ${state.release.tagName} is available!", fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                Text(state.release.body ?: "Bug fixes and performance improvements.", style = MaterialTheme.typography.bodySmall)
                            }
                        },
                        confirmButton = {
                            Button(onClick = { settingsViewModel.downloadUpdate(context, state.assetUrl) }) {
                                Text("Download")
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { settingsViewModel.dismissUpdate() }) { Text("Later") }
                        }
                    )
                }
                is SettingsViewModel.UpdateState.Downloading -> {
                    AlertDialog(
                        onDismissRequest = {}, // Lock dialog
                        title = { Text("Downloading Update") },
                        text = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth().height(8.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text("${(state.progress * 100).toInt()}%")
                            }
                        },
                        confirmButton = {}
                    )
                }
                is SettingsViewModel.UpdateState.ReadyToInstall -> {
                    AlertDialog(
                        onDismissRequest = { settingsViewModel.dismissUpdate() },
                        title = { Text("Download Complete") },
                        text = { Text("The update is ready to be installed.") },
                        confirmButton = {
                            Button(onClick = {
                                settingsViewModel.dismissUpdate()
                                val apkUri = androidx.core.content.FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.provider",
                                    state.apkFile
                                )
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }) { Text("Install") }
                        },
                        dismissButton = {
                            TextButton(onClick = { settingsViewModel.dismissUpdate() }) { Text("Cancel") }
                        }
                    )
                }
                is SettingsViewModel.UpdateState.Checking -> {
                    if (state.isManual) {
                        AlertDialog(
                            onDismissRequest = {},
                            title = { Text("Checking for updates...") },
                            text = {
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            },
                            confirmButton = {}
                        )
                    }
                }
                is SettingsViewModel.UpdateState.UpToDate -> {
                    AlertDialog(
                        onDismissRequest = { settingsViewModel.dismissUpdate() },
                        title = { Text("Up to date") },
                        text = { Text("You are on the latest version of Resonance.") },
                        confirmButton = { TextButton(onClick = { settingsViewModel.dismissUpdate() }) { Text("OK") } }
                    )
                }
                is SettingsViewModel.UpdateState.Error -> {
                    AlertDialog(
                        onDismissRequest = { settingsViewModel.dismissUpdate() },
                        title = { Text("Update Error") },
                        text = { Text(state.message) },
                        confirmButton = { TextButton(onClick = { settingsViewModel.dismissUpdate() }) { Text("OK") } }
                    )
                }
                else -> {}
            }

            ResonanceTheme(
                darkTheme = useDark,
                dynamicColorSeed = dynamicColor,
            ) {
                ResonanceNavGraph(
                    playerViewModel = playerViewModel,
                    receiveUri = uri,
                    onReceiveDismiss = { receiveUri.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { receiveUri.value = it }
    }
}