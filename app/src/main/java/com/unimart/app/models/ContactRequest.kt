package com.unimart.app.models

import com.google.firebase.Timestamp
import com.unimart.app.constants.RequestStatus

/**
 * Data model for a Contact Request between a buyer and a seller
 */
data class ContactRequest(
    val requestId: String = "",
    val buyerId: String = "",
    val sellerId: String = "",
    val productId: String = "",
    val message: String = "",
    val status: String = RequestStatus.PENDING,
    val createdAt: Timestamp? = null
)
