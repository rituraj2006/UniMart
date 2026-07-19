package com.unimart.app.models

import com.google.firebase.Timestamp
import com.unimart.app.constants.MessageType

data class Message(
    val messageId: String = "",
    val senderId: String = "",
    val type: MessageType = MessageType.TEXT,
    val content: String = "",
    val timestamp: Timestamp = Timestamp.now(),
    val readAt: Timestamp? = null,
    val metadata: Map<String, String>? = null
)
