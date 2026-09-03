package com.itantra

import com.itantra.core.protocol.ItantraMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRepositoryTest {

    @Test
    fun testVoiceMessageEncodeAndDecode() {
        val voiceMsg = ItantraMessage.Voice(
            id = "msg_123",
            senderName = "Rescue Leader",
            text = "Need medical kit at sector 4",
            timestampMs = 1700000000000L
        )

        val bytes = voiceMsg.encodeToByteArray()
        assertTrue(bytes.isNotEmpty())

        val decoded = ItantraMessage.decodeFromByteArray(bytes)
        assertNotNull(decoded)
        assertTrue(decoded is ItantraMessage.Voice)
        val decodedVoice = decoded as ItantraMessage.Voice
        assertEquals("msg_123", decodedVoice.id)
        assertEquals("Rescue Leader", decodedVoice.senderName)
        assertEquals("Need medical kit at sector 4", decodedVoice.text)
    }

    @Test
    fun testSosMessageEncodeAndDecode() {
        val sosMsg = ItantraMessage.Sos(
            id = "sos_999",
            senderName = "Unit 7",
            note = "EMERGENCY SOS",
            timestampMs = 1700000000500L
        )

        val bytes = sosMsg.encodeToByteArray()
        val decoded = ItantraMessage.decodeFromByteArray(bytes) as? ItantraMessage.Sos

        assertNotNull(decoded)
        assertEquals("sos_999", decoded?.id)
        assertEquals("Unit 7", decoded?.senderName)
    }

    @Test
    fun testAckMessageEncodeAndDecode() {
        val ackMsg = ItantraMessage.Ack(forId = "msg_123")
        val bytes = ackMsg.encodeToByteArray()
        val decoded = ItantraMessage.decodeFromByteArray(bytes) as? ItantraMessage.Ack

        assertNotNull(decoded)
        assertEquals("msg_123", decoded?.forId)
    }
}
