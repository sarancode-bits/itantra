package com.itantra.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val senderName: String,
    val text: String,
    val timestampMs: Long,
    val deliveryStatus: String, // Sending, Sent, Delivered, Failed
    val isOwn: Boolean
)

@Entity(tableName = "peers")
data class PeerEntity(
    @PrimaryKey val endpointId: String,
    val name: String,
    val lastConnectedMs: Long
)
