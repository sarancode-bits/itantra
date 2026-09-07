package com.itantra.core.speech

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class LatencyMetrics(
    val messageId: String = "",
    val pttReleasedMs: Long = 0,
    val sttCompletedMs: Long = 0,
    val transmitSentMs: Long = 0,
    val payloadReceivedMs: Long = 0,
    val ttsCompletedMs: Long = 0,
    val audioDurationMs: Long = 0
) {
    val sttLatencyMs: Long get() = if (sttCompletedMs > 0 && pttReleasedMs > 0) sttCompletedMs - pttReleasedMs else 0
    val transmitLatencyMs: Long get() = if (payloadReceivedMs > 0 && transmitSentMs > 0) payloadReceivedMs - transmitSentMs else 0
    val ttsGenerationMs: Long get() = if (ttsCompletedMs > 0 && payloadReceivedMs > 0) ttsCompletedMs - payloadReceivedMs else 0
    
    val rtf: Float get() = if (audioDurationMs > 0 && sttLatencyMs > 0) {
        sttLatencyMs.toFloat() / audioDurationMs.toFloat()
    } else 0f
}

@Singleton
class LatencyTracker @Inject constructor() {
    private val _metrics = MutableStateFlow<LatencyMetrics?>(null)
    val metrics: StateFlow<LatencyMetrics?> = _metrics.asStateFlow()

    private var currentMetrics = LatencyMetrics()

    fun recordPttReleased(durationMs: Long) {
        currentMetrics = LatencyMetrics(
            pttReleasedMs = System.currentTimeMillis(),
            audioDurationMs = durationMs
        )
        _metrics.value = currentMetrics
    }

    fun recordSttCompleted() {
        currentMetrics = currentMetrics.copy(sttCompletedMs = System.currentTimeMillis())
        _metrics.value = currentMetrics
    }

    fun recordTransmitSent(messageId: String) {
        currentMetrics = currentMetrics.copy(
            messageId = messageId,
            transmitSentMs = System.currentTimeMillis()
        )
        _metrics.value = currentMetrics
    }
    
    fun recordPayloadReceived(messageId: String) {
        currentMetrics = LatencyMetrics(
            messageId = messageId,
            payloadReceivedMs = System.currentTimeMillis()
        )
        _metrics.value = currentMetrics
    }
    
    fun recordTtsCompleted() {
        currentMetrics = currentMetrics.copy(ttsCompletedMs = System.currentTimeMillis())
        _metrics.value = currentMetrics
    }
}
