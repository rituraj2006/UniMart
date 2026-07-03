package com.unimart.app.models

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val whatsappNumber: String = "",
    val profileImage: String = "",
    val joinedDate: Long = 0L
)
