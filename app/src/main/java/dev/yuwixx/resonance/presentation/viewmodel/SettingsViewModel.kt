package dev.yuwixx.resonance.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yuwixx.resonance.data.preferences.ResonancePreferences
import dev.yuwixx.resonance.data.repository.LastFmAuthState
import dev.yuwixx.resonance.data.repository.LastFmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val lastFmRepository: LastFmRepository,
    private val prefs: ResonancePreferences,
) : ViewModel() {

    // Auth state comes directly from the repository
    val lastFmAuthState: StateFlow<LastFmAuthState> = lastFmRepository.authState

    // Dark theme preference — "SYSTEM" | "LIGHT" | "DARK"
    val darkTheme: StateFlow<String> = prefs.darkTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")

    // Last.fm toggle/option flows — read from the repository's own DataStore
    val lastFmEnabled: StateFlow<Boolean> = lastFmRepository.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastFmNowPlaying: StateFlow<Boolean> = lastFmRepository.nowPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lastFmOnlyWifi: StateFlow<Boolean> = lastFmRepository.onlyWifi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Scrobble threshold options — persisted in ResonancePreferences
    val lastFmScrobblePct: StateFlow<Float> = prefs.lastFmScrobblePercent
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.5f)

    val lastFmScrobbleMinSecs: StateFlow<Int> = prefs.lastFmScrobbleMinSecs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 30)

    val lastFmOfflineQueue: StateFlow<Boolean> = prefs.lastFmScrobbleOffline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setLastFmScrobblePct(v: Float) {
        viewModelScope.launch { prefs.setLastFmScrobblePercent(v) }
    }

    fun setLastFmScrobbleMinSecs(v: Int) {
        viewModelScope.launch { prefs.setLastFmScrobbleMinSecs(v) }
    }

    fun setLastFmOfflineQueue(v: Boolean) {
        viewModelScope.launch { prefs.setLastFmScrobbleOffline(v) }
    }

    private val _pendingScrobbles = MutableStateFlow(0)
    val pendingScrobbles: StateFlow<Int> = _pendingScrobbles.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                _pendingScrobbles.value = lastFmRepository.pendingScrobbleCount
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    fun lastFmLogin(username: String, password: String) {
        viewModelScope.launch { lastFmRepository.authenticate(username, password) }
    }

    fun lastFmLogout() {
        viewModelScope.launch { lastFmRepository.logout() }
    }

    fun setLastFmEnabled(v: Boolean) {
        viewModelScope.launch { lastFmRepository.setEnabled(v) }
    }

    fun setLastFmNowPlaying(v: Boolean) {
        viewModelScope.launch { lastFmRepository.setNowPlaying(v) }
    }

    fun setLastFmOnlyWifi(v: Boolean) {
        viewModelScope.launch { lastFmRepository.setOnlyWifi(v) }
    }
}