package com.unimart.app.models

import com.google.firebase.Timestamp
import com.unimart.app.constants.ChatRequestStatus

data class ChatRequest(
    val requestId: String = "",
    val productId: String = "",
    val sellerId: String = "",
    val buyerId: String = "",
    val buyerName: String = "",
    val buyerImage: String = "",
    val productTitle: String = "",
    val productImage: String = "",
    val productPrice: Double = 0.0,
    val status: ChatRequestStatus = ChatRequestStatus.PENDING,
    val createdAt: Timestamp = Timestamp.now()
)
