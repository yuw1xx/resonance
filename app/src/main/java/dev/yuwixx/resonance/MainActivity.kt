package dev.yuwixx.resonance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dagger.hilt.android.AndroidEntryPoint
import dev.yuwixx.resonance.presentation.navigation.ResonanceNavGraph
import dev.yuwixx.resonance.presentation.viewmodel.PlayerViewModel
import dev.yuwixx.resonance.presentation.viewmodel.SettingsViewModel
import dev.yuwixx.resonance.ui.theme.ResonanceTheme
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install and immediately dismiss the splash screen so it never lingers.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val playerViewModel: PlayerViewModel = hiltViewModel()
            val settingsViewModel: SettingsViewModel = hiltViewModel()

            val dynamicColor by playerViewModel.dynamicColor.collectAsState()
            val darkThemePref by settingsViewModel.darkTheme.collectAsState()
            val systemDark = isSystemInDarkTheme()

            val useDark = when (darkThemePref) {
                "LIGHT" -> false
                "DARK"  -> true
                else    -> systemDark  // "SYSTEM"
            }

            ResonanceTheme(
                darkTheme = useDark,
                dynamicColorSeed = dynamicColor,
            ) {
                ResonanceNavGraph(playerViewModel = playerViewModel)
            }
        }
    }
}