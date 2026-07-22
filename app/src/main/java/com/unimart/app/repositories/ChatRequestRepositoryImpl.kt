package com.unimart.app.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unimart.app.constants.ChatRequestStatus
import com.unimart.app.models.ChatRequest
import com.unimart.app.utils.FirestoreHelper
import com.unimart.app.utils.Resource
import com.unimart.app.network.FcmApi
import com.unimart.app.network.ProxyPayload
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRequestRepositoryImpl : ChatRequestRepository {

    private val requestCollection = FirestoreHelper.getChatRequestsCollection()

    private val fcmApi: FcmApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://unimart-proxy.onrender.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FcmApi::class.java)
    }

    override suspend fun sendChatRequest(request: ChatRequest): Resource<Unit> {
        return try {
            // Rule: One Buyer + One Product = One Request/Conversation.
            val docId = "${request.productId}_${request.buyerId}"
            
            // 1. Check if a pending request already exists
            val existingRequest = requestCollection.document(docId).get().await()
            if (existingRequest.exists()) {
                return Resource.Success(Unit) 
            }

            // 2. Check if an active chat already exists for this pair
            val chatsCollection = FirestoreHelper.getChatsCollection()
            val existingChat = chatsCollection.document(docId).get().await()
            if (existingChat.exists()) {
                return Resource.Success(Unit)
            }
            
            // No request or chat exists, proceed to create request
            requestCollection.document(docId).set(request.copy(requestId = docId)).await()

            // --- New: Notify Seller ---
            try {
                val sellerDoc = FirestoreHelper.getUsersCollection().document(request.sellerId).get().await()
                val token = sellerDoc.getString("fcmToken")
                if (!token.isNullOrEmpty()) {
                    val payload = ProxyPayload(
                        token = token,
                        title = "New Chat Request",
                        body = "${request.buyerName} wants to chat about \"${request.productTitle}\"",
                        data = mapOf("type" to "CHAT_REQUEST", "productId" to request.productId)
                    )
                    fcmApi.sendNotification(payload)
                }
            } catch (e: Exception) { e.printStackTrace() }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override suspend fun cancelChatRequest(requestId: String): Resource<Unit> {
        return try {
            requestCollection.document(requestId).delete().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override suspend fun rejectChatRequest(requestId: String): Resource<Unit> {
        return try {
            requestCollection.document(requestId)
                .update("status", ChatRequestStatus.REJECTED)
                .await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override suspend fun autoRejectPendingRequests(productId: String): Resource<Unit> {
        return try {
            val query = requestCollection
                .whereEqualTo("productId", productId)
                .whereEqualTo("status", ChatRequestStatus.PENDING)
                .get()
                .await()
            
            if (!query.isEmpty) {
                val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                val batch = db.batch()
                for (doc in query.documents) {
                    batch.update(doc.reference, "status", ChatRequestStatus.REJECTED)
                }
                batch.commit().await()
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override fun getPendingRequestsForSeller(sellerId: String): Flow<Resource<List<ChatRequest>>> = callbackFlow {
        trySend(Resource.Loading)
        val listener = requestCollection
            .whereEqualTo("sellerId", sellerId)
            .whereEqualTo("status", ChatRequestStatus.PENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Failure(error))
                    return@addSnapshotListener
                }
                // Sort locally to prevent "Missing Index" errors
                val requests = snapshot?.toObjects(ChatRequest::class.java)
                    ?.sortedByDescending { it.createdAt } ?: emptyList()

                trySend(Resource.Success(requests))
            }
        awaitClose { listener.remove() }
    }
}
