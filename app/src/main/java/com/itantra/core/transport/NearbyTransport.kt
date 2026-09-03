package com.itantra.core.transport

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.itantra.core.radio.RadioStateMonitor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NearbyTransport @Inject constructor(
    @ApplicationContext private val context: Context,
    private val radioStateMonitor: RadioStateMonitor
) : P2pTransport {

    private val client: ConnectionsClient = Nearby.getConnectionsClient(context)
    private val SERVICE_ID = "com.itantra.SERVICE_ID"
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<PeerInfo>>(emptyList())
    override val discoveredPeers: StateFlow<List<PeerInfo>> = _discoveredPeers.asStateFlow()

    private val _incomingPayloads = MutableSharedFlow<ByteArray>()
    override val incomingPayloads: SharedFlow<ByteArray> = _incomingPayloads.asSharedFlow()

    private var activeEndpointId: String? = null

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            _connectionState.value = ConnectionState.Connecting(info.endpointName)
            // Auto accept emergency P2P connection
            client.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            if (result.status.isSuccess) {
                activeEndpointId = endpointId
                val peerName = _discoveredPeers.value.find { it.endpointId == endpointId }?.name ?: "Emergency Peer"
                _connectionState.value = ConnectionState.Connected(endpointId, peerName)
            } else {
                _connectionState.value = ConnectionState.Error("Connection declined or failed: ${result.status.statusMessage ?: "Unknown error"}")
            }
        }

        override fun onDisconnected(endpointId: String) {
            if (activeEndpointId == endpointId) {
                activeEndpointId = null
                _connectionState.value = ConnectionState.Disconnected
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val peer = PeerInfo(endpointId = endpointId, name = info.endpointName)
            val currentList = _discoveredPeers.value.toMutableList()
            if (currentList.none { it.endpointId == endpointId }) {
                currentList.add(peer)
                _discoveredPeers.value = currentList
            }
        }

        override fun onEndpointLost(endpointId: String) {
            val currentList = _discoveredPeers.value.filterNot { it.endpointId == endpointId }
            _discoveredPeers.value = currentList
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                payload.asBytes()?.let { bytes ->
                    scope.launch {
                        _incomingPayloads.emit(bytes)
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {}
    }

    override suspend fun startHosting(localName: String) {
        val radioState = radioStateMonitor.checkRadioState()
        if (!radioState.isWifiOn || !radioState.isBluetoothOn) {
            val missing = mutableListOf<String>()
            if (!radioState.isWifiOn) missing.add("Wi-Fi")
            if (!radioState.isBluetoothOn) missing.add("Bluetooth")
            _connectionState.value = ConnectionState.RadiosOff(missing)
            return
        }

        _connectionState.value = ConnectionState.Advertising
        try {
            val options = AdvertisingOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            client.startAdvertising(localName, SERVICE_ID, connectionLifecycleCallback, options)
                .addOnFailureListener { e ->
                    _connectionState.value = ConnectionState.Error("Failed to start host mode: ${e.localizedMessage ?: "Check radios"}")
                }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Missing permissions for P2P hosting. Grant Nearby Devices permission.")
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Host mode error: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    override suspend fun startScanning() {
        val radioState = radioStateMonitor.checkRadioState()
        if (!radioState.isWifiOn || !radioState.isBluetoothOn) {
            val missing = mutableListOf<String>()
            if (!radioState.isWifiOn) missing.add("Wi-Fi")
            if (!radioState.isBluetoothOn) missing.add("Bluetooth")
            _connectionState.value = ConnectionState.RadiosOff(missing)
            return
        }

        _connectionState.value = ConnectionState.Discovering
        try {
            val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_CLUSTER).build()
            client.startDiscovery(SERVICE_ID, endpointDiscoveryCallback, options)
                .addOnFailureListener { e ->
                    _connectionState.value = ConnectionState.Error("Failed to start scanning: ${e.localizedMessage ?: "Check radios"}")
                }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Missing permissions for P2P scanning. Grant Nearby Devices permission.")
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Scan error: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    override suspend fun connectTo(peer: PeerInfo) {
        try {
            client.requestConnection(peer.name, peer.endpointId, connectionLifecycleCallback)
                .addOnFailureListener { e ->
                    _connectionState.value = ConnectionState.Error("Connection request failed: ${e.localizedMessage ?: "Try again"}")
                }
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.Error("Missing permissions for P2P connection.")
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.Error("Connection error: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    override suspend fun send(payload: ByteArray): Boolean {
        val target = activeEndpointId ?: return false
        return try {
            client.sendPayload(target, Payload.fromBytes(payload))
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    override suspend fun disconnect() {
        try {
            activeEndpointId?.let { client.disconnectFromEndpoint(it) }
            activeEndpointId = null
            client.stopAdvertising()
            client.stopDiscovery()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _connectionState.value = ConnectionState.Disconnected
    }

    override suspend fun stop() {
        disconnect()
        try {
            client.stopAllEndpoints()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _connectionState.value = ConnectionState.Idle
    }
}
