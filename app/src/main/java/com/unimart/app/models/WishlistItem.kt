package com.unimart.app.models

data class WishlistItem(
    val wishlistId: String = "",
    val userId: String = "",
    val productId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
