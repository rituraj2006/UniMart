package com.unimart.app.repositories

import com.google.firebase.Timestamp
import com.unimart.app.constants.PhoneSharingStatus
import com.unimart.app.models.Chat
import com.unimart.app.models.ChatRequest
import com.unimart.app.models.Message
import com.unimart.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    /**
     * Promotes a ChatRequest to an active Chat using an atomic transaction.
     * 
     * Final Business Rule: One Buyer + One Product = One Conversation.
     * Both the Request and the Chat use the deterministic ID: productId_buyerId.
     *
     * @return ChatId (format: productId_buyerId)
     */
    suspend fun acceptChatRequest(request: ChatRequest, chatMetadata: Chat): Resource<String>
    
    // Retrieval
    fun getInbox(userId: String): Flow<Resource<List<Chat>>>
    fun getChatMessages(chatId: String, limit: Long = 50): Flow<Resource<List<Message>>>
    suspend fun getMoreMessages(chatId: String, lastVisibleTimestamp: Timestamp, limit: Long = 50): Resource<List<Message>>
    
    // Messaging
    suspend fun sendMessage(chatId: String, message: Message): Resource<Unit>
    suspend fun updatePhoneSharing(chatId: String, status: PhoneSharingStatus): Resource<Unit>
    suspend fun markAsRead(chatId: String, userId: String): Resource<Unit>
    suspend fun markChatsAsProductSold(productId: String): Resource<Unit>
}
