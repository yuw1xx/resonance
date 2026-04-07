package dev.yuwixx.resonance.data.service

import android.app.Activity
import android.content.Context
import android.location.LocationManager
import android.os.Build
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.yuwixx.resonance.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyShareManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    companion object {
        const val SERVICE_ID = "dev.yuwixx.resonance.nearby"
        val STRATEGY: Strategy = Strategy.P2P_STAR
    }

    // Base client backed by ApplicationContext – used for payload ops (send/disconnect).
    private val appClient: ConnectionsClient = Nearby.getConnectionsClient(context)

    // During a scan this is either an Activity-backed client (supports NFC) or appClient.
    private var client: ConnectionsClient = appClient

    // ─── Discovered device ────────────────────────────────────────────────────

    data class NearbyDevice(
        val endpointId: String,
        val name: String,
    )

    private val _nearbyDevices = MutableStateFlow<List<NearbyDevice>>(emptyList())
    val nearbyDevices: StateFlow<List<NearbyDevice>> = _nearbyDevices.asStateFlow()

    // ─── State machine ────────────────────────────────────────────────────────

    sealed class NearbyState {
        data object Idle : NearbyState()
        data object Scanning : NearbyState()
        data class Connecting(val endpointId: String) : NearbyState()
        data class Connected(val endpointId: String, val deviceName: String) : NearbyState()
        data object AwaitingAcceptance : NearbyState()
        data class Sending(val progress: Float) : NearbyState()
        data object SendSuccess : NearbyState()
        data object Rejected : NearbyState()
        data class Error(val message: String) : NearbyState()
        data object LocationDisabled : NearbyState()
    }

    private val _state = MutableStateFlow<NearbyState>(NearbyState.Idle)
    val state: StateFlow<NearbyState> = _state.asStateFlow()

    // ─── Incoming Handshake & File ────────────────────────────────────────────

    data class IncomingRequest(
        val endpointId: String,
        val senderName: String,
        val songTitle: String,
        val songArtist: String,
    )

    data class IncomingFile(
        val senderName: String,
        val songTitle: String,
        val songArtist: String,
        val file: File,
    )

    private val _incomingRequest = MutableStateFlow<IncomingRequest?>(null)
    val incomingRequest: StateFlow<IncomingRequest?> = _incomingRequest.asStateFlow()

    private val _incomingFile = MutableStateFlow<IncomingFile?>(null)
    val incomingFile: StateFlow<IncomingFile?> = _incomingFile.asStateFlow()

    // ─── Local device identity ────────────────────────────────────────────────

    val localName: String = Build.MODEL

    // ─── Payload tracking ─────────────────────────────────────────────────────

    private val pendingSongsToSend = mutableMapOf<String, Song>()
    private var filePayloadId: Long? = null

    // ─── Public API ───────────────────────────────────────────────────────────

    fun startScanning(activity: Activity? = null) {
        if (_state.value != NearbyState.Idle) return

        if (!isLocationEnabled()) {
            _state.value = NearbyState.LocationDisabled
            return
        }

        // Use an Activity-backed client when available so the Nearby SDK can
        // attach its NfcDispatcher and discover peers over NFC as well as BT/Wi-Fi.
        client = if (activity != null) Nearby.getConnectionsClient(activity) else appClient

        _nearbyDevices.value = emptyList()
        _state.value = NearbyState.Scanning

        val strategy = STRATEGY

        val advOpts = AdvertisingOptions.Builder()
            .setStrategy(strategy)
            .build()
        client.startAdvertising(localName, SERVICE_ID, connectionLifecycle, advOpts)
            .addOnFailureListener { _state.value = NearbyState.Error(it.message ?: "Advertising failed") }

        val disOpts = DiscoveryOptions.Builder()
            .setStrategy(strategy)
            .build()
        client.startDiscovery(SERVICE_ID, endpointDiscovery, disOpts)
            .addOnFailureListener { _state.value = NearbyState.Error(it.message ?: "Discovery failed") }
    }

    fun stopScanning() {
        _state.value = NearbyState.Idle
        client.stopAdvertising()
        client.stopDiscovery()
        client.stopAllEndpoints()
        client = appClient          // release any Activity reference
        _nearbyDevices.value = emptyList()
        pendingSongsToSend.clear()
    }

    fun connectTo(endpointId: String) {
        _state.value = NearbyState.Connecting(endpointId)
        client.requestConnection(localName, endpointId, connectionLifecycle)
            .addOnFailureListener { _state.value = NearbyState.Error(it.message ?: "Connection request failed") }
    }

    /** Initiates the transfer by asking the receiver for permission first. */
    fun sendSong(song: Song, endpointId: String) {
        val file = File(song.path)
        if (!file.exists()) {
            _state.value = NearbyState.Error("Audio file not found at ${song.path}")
            return
        }

        pendingSongsToSend[endpointId] = song
        val meta = "META|||${song.title}|||${song.artist}"
        client.sendPayload(endpointId, Payload.fromBytes(meta.toByteArray()))
        _state.value = NearbyState.AwaitingAcceptance
    }

    fun acceptRequest(endpointId: String) {
        client.sendPayload(endpointId, Payload.fromBytes("ACCEPT".toByteArray()))
        _incomingRequest.value = null
    }

    fun rejectRequest(endpointId: String) {
        client.sendPayload(endpointId, Payload.fromBytes("REJECT".toByteArray()))
        _incomingRequest.value = null
    }

    fun disconnect() {
        client.stopAllEndpoints()
        _state.value = NearbyState.Idle
        _nearbyDevices.value = emptyList()
        pendingSongsToSend.clear()
        filePayloadId = null
    }

    fun clearIncoming() {
        _incomingFile.value = null
        _incomingRequest.value = null
    }

    // ─── Endpoint discovery callbacks ─────────────────────────────────────────

    private val endpointDiscovery = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // Ignore our own advertisement reflected back to us
            if (info.endpointName == localName) return
            val device = NearbyDevice(endpointId, info.endpointName)
            _nearbyDevices.value = _nearbyDevices.value
                .filterNot { it.endpointId == endpointId } + device
        }

        override fun onEndpointLost(endpointId: String) {
            _nearbyDevices.value = _nearbyDevices.value.filterNot { it.endpointId == endpointId }
        }
    }

    // ─── Connection lifecycle callbacks ───────────────────────────────────────

    private val connectionLifecycle = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                val name = _nearbyDevices.value.find { it.endpointId == endpointId }?.name ?: endpointId
                _state.value = NearbyState.Connected(endpointId, name)
            } else {
                _state.value = NearbyState.Error("Connection to device failed (${result.status.statusCode})")
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (_state.value !is NearbyState.Error) _state.value = NearbyState.Idle
        }
    }

    // ─── Payload callbacks ────────────────────────────────────────────────────

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            when (payload.type) {
                Payload.Type.BYTES -> {
                    val str = payload.asBytes()?.let { String(it) } ?: return
                    when {
                        str == "ACCEPT" -> {
                            val song = pendingSongsToSend[endpointId] ?: return
                            val filePayload = Payload.fromFile(File(song.path))
                            filePayloadId = filePayload.id
                            client.sendPayload(endpointId, filePayload)
                                .addOnFailureListener { _state.value = NearbyState.Error("Send failed") }
                            _state.value = NearbyState.Sending(0f)
                            pendingSongsToSend.remove(endpointId)
                        }
                        str == "REJECT" -> {
                            _state.value = NearbyState.Rejected
                            pendingSongsToSend.remove(endpointId)
                        }
                        str.startsWith("META|||") -> {
                            val parts = str.split("|||")
                            val senderName = _nearbyDevices.value.find { it.endpointId == endpointId }?.name ?: "Unknown"
                            _incomingRequest.value = IncomingRequest(
                                endpointId = endpointId,
                                senderName = senderName,
                                songTitle  = parts.getOrNull(1) ?: "Unknown",
                                songArtist = parts.getOrNull(2) ?: "Unknown"
                            )
                        }
                    }
                }
                Payload.Type.FILE -> {
                    val file = payload.asFile()?.asJavaFile() ?: return
                    // Assume if we receive a file, it's from the person who sent the request
                    val senderName = _nearbyDevices.value.find { it.endpointId == endpointId }?.name ?: "Unknown"
                    _incomingFile.value = IncomingFile(
                        senderName = senderName,
                        songTitle  = file.nameWithoutExtension,
                        songArtist = "Unknown artist",
                        file       = file,
                    )
                }
                else -> Unit
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
            if (update.payloadId != filePayloadId) return

            val progress = if (update.totalBytes > 0)
                update.bytesTransferred.toFloat() / update.totalBytes.toFloat()
            else 0f

            when (update.status) {
                PayloadTransferUpdate.Status.IN_PROGRESS -> _state.value = NearbyState.Sending(progress)
                PayloadTransferUpdate.Status.SUCCESS     -> _state.value = NearbyState.SendSuccess
                PayloadTransferUpdate.Status.FAILURE     -> _state.value = NearbyState.Error("Transfer failed")
                else                                     -> Unit
            }
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────────

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }
}