package com.itantra.core.transport

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

data class PeerInfo(
    val endpointId: String,
    val name: String,
    val rssi: Int = -50
)

sealed class ConnectionState {
    object Idle : ConnectionState()
    object Advertising : ConnectionState()
    object Discovering : ConnectionState()
    data class Connecting(val peerName: String) : ConnectionState()
    data class Connected(val endpointId: String, val peerName: String) : ConnectionState()
    object Disconnected : ConnectionState()
    data class RadiosOff(val missingRadios: List<String>) : ConnectionState()
    data class Error(val message: String) : ConnectionState()
}

interface P2pTransport {
    val connectionState: StateFlow<ConnectionState>
    val discoveredPeers: StateFlow<List<PeerInfo>>
    val incomingPayloads: SharedFlow<ByteArray>

    suspend fun startHosting(localName: String)
    suspend fun startScanning()
    suspend fun connectTo(peer: PeerInfo)
    suspend fun send(payload: ByteArray): Boolean
    suspend fun disconnect()
    suspend fun stop()
}
