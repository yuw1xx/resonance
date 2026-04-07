package dev.yuwixx.resonance.presentation.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yuwixx.resonance.data.model.Song
import dev.yuwixx.resonance.data.repository.MusicRepository
import dev.yuwixx.resonance.data.service.NearbyShareManager
import dev.yuwixx.resonance.data.service.ShareTransferManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ShareViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val nearbyManager: NearbyShareManager,
    private val transferManager: ShareTransferManager,
    private val musicRepository: MusicRepository,
) : ViewModel() {

    val nearbyState     = nearbyManager.state
    val nearbyDevices   = nearbyManager.nearbyDevices
    val localName       = nearbyManager.localName
    val incomingRequest = nearbyManager.incomingRequest
    val incomingFile    = nearbyManager.incomingFile

    fun startScanning(activity: Activity? = null) = nearbyManager.startScanning(activity)
    fun stopScanning()                = nearbyManager.stopScanning()
    fun connectTo(endpointId: String) = nearbyManager.connectTo(endpointId)
    fun disconnectNearby()            = nearbyManager.disconnect()
    fun clearIncoming()               = nearbyManager.clearIncoming()

    fun sendViaNearby(endpointId: String) {
        val song = _selectedSong.value ?: return
        nearbyManager.sendSong(song, endpointId)
    }

    fun acceptNearbyRequest(endpointId: String) = nearbyManager.acceptRequest(endpointId)
    fun rejectNearbyRequest(endpointId: String) = nearbyManager.rejectRequest(endpointId)

    val transferState = transferManager.state

    fun prepareTransfer(song: Song) {
        viewModelScope.launch {
            nearbyManager.stopScanning()
            delay(500)
            transferManager.prepareTransfer(song)
        }
    }
    fun cancelTransfer()            = transferManager.cancel()
    fun dismissNoWifi()             = transferManager.dismissNoWifi()

    private val _selectedSong = MutableStateFlow<Song?>(null)
    val selectedSong: StateFlow<Song?> = _selectedSong.asStateFlow()

    fun preselectSong(song: Song?) { if (_selectedSong.value == null) _selectedSong.value = song }
    fun selectSong(song: Song)     { _selectedSong.value = song }

    val allSongs: StateFlow<List<Song>> = musicRepository.allSongs
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    override fun onCleared() {
        super.onCleared()
        nearbyManager.stopScanning()
        transferManager.cancel()
    }
}