package dev.yuwixx.resonance.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.yuwixx.resonance.data.network.GitHubApi
import dev.yuwixx.resonance.data.network.GitHubRelease
import dev.yuwixx.resonance.data.preferences.ResonancePreferences
import dev.yuwixx.resonance.data.repository.LastFmAuthState
import dev.yuwixx.resonance.data.repository.LastFmRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val lastFmRepository: LastFmRepository,
    private val prefs: ResonancePreferences,
    private val gitHubApi: GitHubApi,
    private val okHttpClient: OkHttpClient
) : ViewModel() {

    // ─── Updater Logic ────────────────────────────────────────────────────────

    sealed class UpdateState {
        data object Idle : UpdateState()
        data class Checking(val isManual: Boolean) : UpdateState()
        data object UpToDate : UpdateState()
        data class Available(val release: GitHubRelease, val assetUrl: String) : UpdateState()
        data class Downloading(val progress: Float) : UpdateState()
        data class ReadyToInstall(val apkFile: File) : UpdateState()
        data class Error(val message: String) : UpdateState()
    }

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    val updateFrequency: StateFlow<String> = prefs.updateFrequency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "DAILY")

    fun setUpdateFrequency(freq: String) {
        viewModelScope.launch { prefs.setUpdateFrequency(freq) }
    }

    fun dismissUpdate() {
        _updateState.value = UpdateState.Idle
    }

    fun checkForUpdates(currentVersion: String, isManual: Boolean = false) {
        viewModelScope.launch {
            if (!isManual) {
                val freq = prefs.updateFrequency.first()
                if (freq == "DISABLED") return@launch

                val lastCheck = prefs.lastUpdateCheck.first()
                val now = System.currentTimeMillis()
                val hoursSinceLast = (now - lastCheck) / (1000 * 60 * 60)

                val shouldCheck = when (freq) {
                    "LAUNCH" -> true
                    "DAILY"  -> hoursSinceLast >= 24
                    "WEEKLY" -> hoursSinceLast >= 168
                    else     -> false
                }
                if (!shouldCheck) return@launch
            }

            _updateState.value = UpdateState.Checking(isManual)
            try {
                val release = gitHubApi.getLatestRelease()
                prefs.setLastUpdateCheck(System.currentTimeMillis())

                val remoteVersion = release.tagName.removePrefix("v")
                val localVersion = currentVersion.removePrefix("v")

                if (isNewerVersion(localVersion, remoteVersion)) {
                    val apkAsset = release.assets.find { it.name.endsWith(".apk") }
                    if (apkAsset != null) {
                        _updateState.value = UpdateState.Available(release, apkAsset.browserDownloadUrl)
                    } else {
                        if (isManual) _updateState.value = UpdateState.Error("No APK found in the latest release.")
                        else _updateState.value = UpdateState.Idle
                    }
                } else {
                    if (isManual) _updateState.value = UpdateState.UpToDate
                    else _updateState.value = UpdateState.Idle
                }
            } catch (e: Exception) {
                if (isManual) _updateState.value = UpdateState.Error("Failed to check for updates. Check your connection.")
                else _updateState.value = UpdateState.Idle
            }
        }
    }

    private fun isNewerVersion(local: String, remote: String): Boolean {
        val lParts = local.split(".").mapNotNull { it.toIntOrNull() }
        val rParts = remote.split(".").mapNotNull { it.toIntOrNull() }
        val length = maxOf(lParts.size, rParts.size)
        for (i in 0 until length) {
            val l = lParts.getOrElse(i) { 0 }
            val r = rParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }

    fun downloadUpdate(context: Context, url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState.Downloading(0f)
            try {
                val request = Request.Builder().url(url).build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("HTTP ${response.code}")
                val body = response.body ?: throw Exception("Empty response body")

                val file = File(context.cacheDir, "resonance_update.apk")
                if (file.exists()) file.delete()

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (totalBytes > 0) {
                                _updateState.value = UpdateState.Downloading(downloadedBytes.toFloat() / totalBytes)
                            }
                        }
                    }
                }
                _updateState.value = UpdateState.ReadyToInstall(file)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "Download failed")
            }
        }
    }

    // ─── Settings logic ───────────────────────────────────────────────────────

    val lastFmAuthState: StateFlow<LastFmAuthState> = lastFmRepository.authState

    val darkTheme: StateFlow<String> = prefs.darkTheme
        .stateIn(viewModelScope, SharingStarted.Eagerly, "SYSTEM")

    val lastFmEnabled: StateFlow<Boolean> = lastFmRepository.isEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val lastFmNowPlaying: StateFlow<Boolean> = lastFmRepository.nowPlaying
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val lastFmOnlyWifi: StateFlow<Boolean> = lastFmRepository.onlyWifi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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