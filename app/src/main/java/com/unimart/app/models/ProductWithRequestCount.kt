package com.unimart.app.models

data class ProductWithRequestCount(
    val product: Product,
    val totalCount: Int,
    val pendingCount: Int
)
