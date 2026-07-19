package com.unimart.app.repositories

import com.unimart.app.models.ChatRequest
import com.unimart.app.utils.Resource
import kotlinx.coroutines.flow.Flow

interface ChatRequestRepository {
    suspend fun sendChatRequest(request: ChatRequest): Resource<Unit>
    suspend fun cancelChatRequest(requestId: String): Resource<Unit>
    suspend fun rejectChatRequest(requestId: String): Resource<Unit>
    suspend fun autoRejectPendingRequests(productId: String): Resource<Unit>
    fun getPendingRequestsForSeller(sellerId: String): Flow<Resource<List<ChatRequest>>>
}
