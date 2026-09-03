package com.itantra.core.protocol

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
sealed class ItantraMessage {
    @Serializable
    data class Voice(
        val id: String,
        val senderName: String,
        val text: String,
        val timestampMs: Long
    ) : ItantraMessage()

    @Serializable
    data class Sos(
        val id: String,
        val senderName: String,
        val note: String? = null,
        val timestampMs: Long
    ) : ItantraMessage()

    @Serializable
    data class Ack(
        val forId: String
    ) : ItantraMessage()

    @Serializable
    data class Presence(
        val deviceName: String,
        val batteryPct: Int? = null
    ) : ItantraMessage()

    fun encodeToByteArray(): ByteArray {
        return Json.encodeToString(this).toByteArray(Charsets.UTF_8)
    }

    companion object {
        fun decodeFromByteArray(bytes: ByteArray): ItantraMessage? {
            return try {
                val jsonString = String(bytes, Charsets.UTF_8)
                Json.decodeFromString<ItantraMessage>(jsonString)
            } catch (e: Exception) {
                null
            }
        }
    }
}
