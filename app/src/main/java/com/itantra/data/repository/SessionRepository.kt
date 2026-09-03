package com.itantra.data.repository

import com.itantra.core.alert.SosAlertPlayer
import com.itantra.core.protocol.ItantraMessage
import com.itantra.core.speech.SpeechToText
import com.itantra.core.speech.SttState
import com.itantra.core.speech.TextToSpeechEngine
import com.itantra.core.transport.ConnectionState
import com.itantra.core.transport.P2pTransport
import com.itantra.core.transport.PeerInfo
import com.itantra.data.db.MessageDao
import com.itantra.data.db.MessageEntity
import com.itantra.data.db.PeerDao
import com.itantra.data.db.PeerEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

enum class DeliveryStatus {
    Sending, Sent, Delivered, Failed
}

data class TranscriptEntry(
    val id: String,
    val senderName: String,
    val text: String,
    val timestampMs: Long,
    val deliveryStatus: DeliveryStatus,
    val isOwn: Boolean
)

@Singleton
class SessionRepository @Inject constructor(
    val transport: P2pTransport,
    val sttEngine: SpeechToText,
    val ttsEngine: TextToSpeechEngine,
    val sosAlertPlayer: SosAlertPlayer,
    private val messageDao: MessageDao,
    private val peerDao: PeerDao
) {
    private val scope = CoroutineScope(Dispatchers.IO)

    val connectionState: StateFlow<ConnectionState> = transport.connectionState
    val discoveredPeers: StateFlow<List<PeerInfo>> = transport.discoveredPeers

    private val _sosSenderName = MutableStateFlow<String?>(null)
    val sosSenderName: StateFlow<String?> = _sosSenderName.asStateFlow()

    private val _peerBatteryPct = MutableStateFlow<Int?>(null)
    val peerBatteryPct: StateFlow<Int?> = _peerBatteryPct.asStateFlow()

    val transcript: Flow<List<TranscriptEntry>> = messageDao.getAllMessages().map { entities ->
        entities.map { entity ->
            TranscriptEntry(
                id = entity.id,
                senderName = entity.senderName,
                text = entity.text,
                timestampMs = entity.timestampMs,
                deliveryStatus = try { DeliveryStatus.valueOf(entity.deliveryStatus) } catch (e: Exception) { DeliveryStatus.Sent },
                isOwn = entity.isOwn
            )
        }
    }

    init {
        // Listen to incoming network payloads
        scope.launch {
            try {
                transport.incomingPayloads.collect { bytes ->
                    try {
                        handleIncomingPayload(bytes)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Listen to STT results
        scope.launch {
            try {
                sttEngine.state.collect { state ->
                    if (state is SttState.Result) {
                        try {
                            sendVoiceMessage(state.text)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Update database when peer connects
        scope.launch {
            try {
                connectionState.collect { state ->
                    if (state is ConnectionState.Connected) {
                        try {
                            peerDao.insertPeer(
                                PeerEntity(
                                    endpointId = state.endpointId,
                                    name = state.peerName,
                                    lastConnectedMs = System.currentTimeMillis()
                                )
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun handleIncomingPayload(bytes: ByteArray) {
        val message = ItantraMessage.decodeFromByteArray(bytes) ?: return
        when (message) {
            is ItantraMessage.Voice -> {
                val entity = MessageEntity(
                    id = message.id,
                    senderName = message.senderName,
                    text = message.text,
                    timestampMs = message.timestampMs,
                    deliveryStatus = DeliveryStatus.Delivered.name,
                    isOwn = false
                )
                messageDao.insertMessage(entity)
                // Speak out loud automatically (walkie-talkie mode)
                ttsEngine.speak(message.text)
                // Send Ack back
                val ack = ItantraMessage.Ack(forId = message.id)
                transport.send(ack.encodeToByteArray())
            }
            is ItantraMessage.Sos -> {
                _sosSenderName.value = message.senderName
                sosAlertPlayer.start()
                // Send Ack back
                val ack = ItantraMessage.Ack(forId = message.id)
                transport.send(ack.encodeToByteArray())
            }
            is ItantraMessage.Ack -> {
                messageDao.updateDeliveryStatus(message.forId, DeliveryStatus.Delivered.name)
            }
            is ItantraMessage.Presence -> {
                _peerBatteryPct.value = message.batteryPct
            }
        }
    }

    fun sendVoiceMessage(text: String, localSenderName: String = "Me") {
        scope.launch {
            val msgId = "msg_${System.currentTimeMillis()}_${(1000..9999).random()}"
            val entity = MessageEntity(
                id = msgId,
                senderName = localSenderName,
                text = text,
                timestampMs = System.currentTimeMillis(),
                deliveryStatus = DeliveryStatus.Sending.name,
                isOwn = true
            )
            messageDao.insertMessage(entity)

            val payloadMsg = ItantraMessage.Voice(
                id = msgId,
                senderName = localSenderName,
                text = text,
                timestampMs = entity.timestampMs
            )
            val success = transport.send(payloadMsg.encodeToByteArray())
            val newStatus = if (success) DeliveryStatus.Sent else DeliveryStatus.Failed
            messageDao.updateDeliveryStatus(msgId, newStatus.name)

            // Speak locally for feedback
            ttsEngine.speak(text)
        }
    }

    fun retrySendMessage(entry: TranscriptEntry) {
        scope.launch {
            messageDao.updateDeliveryStatus(entry.id, DeliveryStatus.Sending.name)
            val payloadMsg = ItantraMessage.Voice(
                id = entry.id,
                senderName = entry.senderName,
                text = entry.text,
                timestampMs = entry.timestampMs
            )
            val success = transport.send(payloadMsg.encodeToByteArray())
            val newStatus = if (success) DeliveryStatus.Sent else DeliveryStatus.Failed
            messageDao.updateDeliveryStatus(entry.id, newStatus.name)
        }
    }

    fun triggerSos(localSenderName: String = "Me") {
        scope.launch {
            val sosMsg = ItantraMessage.Sos(
                id = "sos_${System.currentTimeMillis()}",
                senderName = localSenderName,
                note = "EMERGENCY SOS",
                timestampMs = System.currentTimeMillis()
            )
            transport.send(sosMsg.encodeToByteArray())
            _sosSenderName.value = localSenderName
            sosAlertPlayer.start()
        }
    }

    fun cancelSos() {
        sosAlertPlayer.stop()
        _sosSenderName.value = null
    }

    suspend fun clearHistory() {
        messageDao.clearAll()
    }
}
