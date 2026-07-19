package com.unimart.app.models

import com.google.firebase.Timestamp
import com.unimart.app.constants.ChatStatus
import com.unimart.app.constants.PhoneSharingStatus

data class Chat(
    val chatId: String = "",
    val productId: String = "",
    val buyerId: String = "",
    val sellerId: String = "",
    val participants: List<String> = emptyList(),
    val chatStatus: ChatStatus = ChatStatus.ACTIVE,
    val phoneSharingStatus: PhoneSharingStatus = PhoneSharingStatus.NONE,
    val lastMessagePreview: String = "",
    val lastSenderId: String = "",
    val lastTimestamp: Timestamp = Timestamp.now(),

    // Other User Info (Denormalized for Inbox)
    val buyerName: String = "",
    val buyerImage: String = "",
    val sellerName: String = "",
    val sellerImage: String = "",
    
    // Denormalized Product Data
    val title: String = "",
    val price: Double = 0.0,
    val thumbnail: String = "",
    val productStatus: String = "AVAILABLE" // Using String to match existing ProductStatus.kt if needed
)
