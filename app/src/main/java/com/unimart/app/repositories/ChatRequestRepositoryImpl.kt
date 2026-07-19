package com.unimart.app.repositories

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unimart.app.constants.ChatRequestStatus
import com.unimart.app.models.ChatRequest
import com.unimart.app.utils.FirestoreHelper
import com.unimart.app.utils.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ChatRequestRepositoryImpl : ChatRequestRepository {

    private val requestCollection = FirestoreHelper.getChatRequestsCollection()

    override suspend fun sendChatRequest(request: ChatRequest): Resource<Unit> {
        return try {
            // Rule: One Buyer + One Product = One Request.
            // Using productId_buyerId as document ID enforces this natively.
            val docId = "${request.productId}_${request.buyerId}"
            val docRef = requestCollection.document(docId)
            
            val existing = docRef.get().await()
            if (existing.exists()) {
                // If it already exists, we don't overwrite to avoid resetting status or createdAt
                return Resource.Success(Unit) 
            }
            
            docRef.set(request.copy(requestId = docId)).await()
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
