package com.unimart.app.models

import com.google.firebase.Timestamp

data class BlockedUser(
    val blockedUserId: String = "",
    val blockedAt: Timestamp = Timestamp.now()
)
