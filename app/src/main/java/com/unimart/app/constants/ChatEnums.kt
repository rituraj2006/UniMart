package com.unimart.app.constants

enum class ChatRequestStatus {
    PENDING, ACCEPTED, REJECTED, CANCELLED
}

enum class ChatStatus {
    ACTIVE, CLOSED, EXPIRED
}

enum class MessageType {
    TEXT, IMAGE, SYSTEM
}

enum class PhoneSharingStatus {
    NONE, PENDING, APPROVED, REJECTED
}
