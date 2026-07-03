package com.unimart.app.models

import com.unimart.app.constants.ProductStatus

data class Product(

    val productId: String = "",

    val sellerId: String = "",

    val title: String = "",

    val description: String = "",

    val price: Double = 0.0,

    val category: String = "",

    val condition: String = "",

    val listingType: String = "",

    val lookingFor: String = "",

    val imageUrls: List<String> = emptyList(),

    val status: String = ProductStatus.AVAILABLE,

    val createdAt: Long = System.currentTimeMillis()
)