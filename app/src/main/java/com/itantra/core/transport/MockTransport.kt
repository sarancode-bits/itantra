package com.itantra.core.transport

import com.itantra.core.protocol.ItantraMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
class MockTransport @Inject constructor() : P2pTransport {

    private val scope = CoroutineScope(Dispatchers.Default)

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredPeers = MutableStateFlow<List<PeerInfo>>(emptyList())
    override val discoveredPeers: StateFlow<List<PeerInfo>> = _discoveredPeers.asStateFlow()

    private val _incomingPayloads = MutableSharedFlow<ByteArray>()
    override val incomingPayloads: SharedFlow<ByteArray> = _incomingPayloads.asSharedFlow()

    private val mockPeer = PeerInfo(
        endpointId = "mock_endpoint_101",
        name = "Ravi's Emergency Device",
        rssi = -42
    )

    private val cannedResponses = listOf(
        "Copy that, I'm holding at Sector B.",
        "We have clear access to the medical station.",
        "Water supplies are secured. Moving to high ground.",
        "Road blocked near bridge, taking south detachment path."
    )
    private var cannedIndex = 0

    override suspend fun startHosting(localName: String) {
        _connectionState.value = ConnectionState.Advertising
    }

    override suspend fun startScanning() {
        _connectionState.value = ConnectionState.Discovering
        delay(1500) // realistic scanning delay
        _discoveredPeers.value = listOf(mockPeer)
    }

    override suspend fun connectTo(peer: PeerInfo) {
        _connectionState.value = ConnectionState.Connecting(peer.name)
        delay(1200) // realistic handshake delay
        _connectionState.value = ConnectionState.Connected(peer.endpointId, peer.name)
    }

    override suspend fun send(payload: ByteArray): Boolean {
        if (_connectionState.value !is ConnectionState.Connected) return false

        scope.launch {
            val message = ItantraMessage.decodeFromByteArray(payload)
            if (message is ItantraMessage.Voice) {
                // Send Ack first
                delay(300)
                val ack = ItantraMessage.Ack(forId = message.id)
                _incomingPayloads.emit(ack.encodeToByteArray())

                // Simulated echo response after 2 seconds
                delay(2000)
                val replyText = cannedResponses[cannedIndex % cannedResponses.size]
                cannedIndex++
                val replyMsg = ItantraMessage.Voice(
                    id = "reply_${System.currentTimeMillis()}",
                    senderName = mockPeer.name,
                    text = replyText,
                    timestampMs = System.currentTimeMillis()
                )
                _incomingPayloads.emit(replyMsg.encodeToByteArray())
            } else if (message is ItantraMessage.Sos) {
                // Ack for SOS
                delay(200)
                val ack = ItantraMessage.Ack(forId = message.id)
                _incomingPayloads.emit(ack.encodeToByteArray())
            }
        }
        return true
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionState.Disconnected
        _discoveredPeers.value = emptyList()
    }

    override suspend fun stop() {
        disconnect()
        _connectionState.value = ConnectionState.Idle
    }
}
