package com.unimart.app.repositories

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unimart.app.constants.PhoneSharingStatus
import com.unimart.app.constants.MessageType
import com.unimart.app.models.Chat
import com.unimart.app.models.ChatRequest
import com.unimart.app.models.Message
import com.unimart.app.utils.FirestoreHelper
import com.unimart.app.utils.Resource
import com.unimart.app.network.FcmApi
import com.unimart.app.network.ProxyPayload
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.tasks.await

class ChatRepositoryImpl : ChatRepository {

    private val db = FirebaseFirestore.getInstance()
    private val chatCollection = FirestoreHelper.getChatsCollection()
    private val requestCollection = FirestoreHelper.getChatRequestsCollection()

    private val fcmApi: FcmApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://unimart-proxy.onrender.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FcmApi::class.java)
    }

    override suspend fun acceptChatRequest(request: ChatRequest, chatMetadata: Chat): Resource<String> {
        return try {
            val chatId = "${request.productId}_${request.buyerId}"
            
            db.runTransaction { transaction ->
                val requestRef = requestCollection.document(request.requestId)
                val chatRef = chatCollection.document(chatId)
                
                val requestSnapshot = transaction.get(requestRef)
                if (!requestSnapshot.exists()) {
                    throw Exception("Chat request no longer exists.")
                }
                
                transaction.set(chatRef, chatMetadata.copy(chatId = chatId))
                transaction.delete(requestRef)
                
                chatId
            }.await()
            Resource.Success(chatId)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override fun getInbox(userId: String): Flow<Resource<List<Chat>>> = callbackFlow {
        trySend(Resource.Loading)
        val listener = chatCollection
            .whereArrayContains("participants", userId)
            .orderBy("lastTimestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Failure(error))
                    return@addSnapshotListener
                }
                val chats = snapshot?.toObjects(Chat::class.java) ?: emptyList()
                trySend(Resource.Success(chats))
            }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    override fun getChatMessages(chatId: String, limit: Long): Flow<Resource<List<Message>>> = callbackFlow {
        trySend(Resource.Loading)
        val listener = FirestoreHelper.getMessagesCollection(chatId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Failure(error))
                    return@addSnapshotListener
                }
                // Sort ascending for the UI (Oldest -> Newest) after fetching the latest batch
                val messages = snapshot?.toObjects(Message::class.java)?.reversed() ?: emptyList()
                trySend(Resource.Success(messages))
            }
        awaitClose { listener.remove() }
    }.flowOn(Dispatchers.IO)

    override suspend fun getMoreMessages(
        chatId: String,
        lastVisibleTimestamp: Timestamp,
        limit: Long
    ): Resource<List<Message>> {
        return try {
            val snapshot = FirestoreHelper.getMessagesCollection(chatId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .startAfter(lastVisibleTimestamp)
                .limit(limit)
                .get()
                .await()
            val messages = snapshot.toObjects(Message::class.java)
            Resource.Success(messages)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override suspend fun sendMessage(chatId: String, message: Message): Resource<Unit> {
        return try {
            val batch = db.batch()
            val messageRef = FirestoreHelper.getMessagesCollection(chatId).document()
            val chatRef = chatCollection.document(chatId)
            
            batch.set(messageRef, message.copy(messageId = messageRef.id))
            
            val previewText = if (message.type == MessageType.IMAGE) "📷 Image" else message.content
            val updates = mutableMapOf<String, Any>(
                "lastMessagePreview" to previewText,
                "lastSenderId" to message.senderId,
                "lastTimestamp" to message.timestamp
            )

            // Logic: Increment unread count for the receiver ONLY if they are NOT active in the chat
            // CRITICAL: We MUST use Source.SERVER to ensure the other user actually sees the notification.
            val chatDoc = chatRef.get(com.google.firebase.firestore.Source.SERVER).await()
            @Suppress("UNCHECKED_CAST")
            val activeParticipants = chatDoc.get("activeParticipants") as? List<String> ?: emptyList()
            @Suppress("UNCHECKED_CAST")
            val participants = chatDoc.get("participants") as? List<String> ?: emptyList()
            val receiverId = participants.find { it != message.senderId }

            val shouldNotify = receiverId != null && !activeParticipants.contains(receiverId)

            if (shouldNotify) {
                updates["unreadCounts.$receiverId"] = FieldValue.increment(1)
            }

            batch.update(chatRef, updates)
            batch.commit().await()

            if (shouldNotify && receiverId != null) {
                try {
                    // Check if receiver has blocked the sender
                    val userRepo = com.unimart.app.repositories.UserRepository()
                    if (userRepo.isUserBlocked(receiverId, message.senderId)) {
                        return Resource.Success(Unit)
                    }

                    val receiverDoc = FirestoreHelper.getUsersCollection().document(receiverId).get().await()
                    val token = receiverDoc.getString("fcmToken")
                    val senderDoc = FirestoreHelper.getUsersCollection().document(message.senderId).get().await()
                    val senderName = senderDoc.getString("name") ?: "UniMart"

                    if (!token.isNullOrEmpty()) {
                        val payload = ProxyPayload(
                            token = token,
                            title = senderName,
                            body = previewText,
                            data = mapOf("type" to "MESSAGE", "chatId" to chatId)
                        )
                        fcmApi.sendNotification(payload)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override suspend fun updatePhoneSharing(chatId: String, status: PhoneSharingStatus): Resource<Unit> {
        return try {
            val batch = db.batch()
            val chatRef = chatCollection.document(chatId)
            val messageRef = FirestoreHelper.getMessagesCollection(chatId).document()
            
            batch.update(chatRef, "phoneSharingStatus", status)
            
            val systemText = when(status) {
                PhoneSharingStatus.PENDING -> "WhatsApp contact requested."
                PhoneSharingStatus.APPROVED -> "Seller approved WhatsApp contact."
                PhoneSharingStatus.REJECTED -> "Seller declined WhatsApp contact."
                else -> ""
            }
            
            if (systemText.isNotEmpty()) {
                val systemMessage = Message(
                    messageId = messageRef.id,
                    senderId = "SYSTEM",
                    type = MessageType.SYSTEM,
                    content = systemText,
                    timestamp = Timestamp.now()
                )
                batch.set(messageRef, systemMessage)
            }
            
            batch.commit().await()

            try {
                val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                val chatDoc = chatCollection.document(chatId).get(com.google.firebase.firestore.Source.SERVER).await()
                @Suppress("UNCHECKED_CAST")
                val participants = chatDoc.get("participants") as? List<String> ?: emptyList()
                @Suppress("UNCHECKED_CAST")
                val activeParticipants = chatDoc.get("activeParticipants") as? List<String> ?: emptyList()
                val receiverId = participants.find { it != currentUserId }

                if (receiverId != null && currentUserId != null && !activeParticipants.contains(receiverId)) {
                    val receiverDoc = FirestoreHelper.getUsersCollection().document(receiverId).get().await()
                    val token = receiverDoc.getString("fcmToken")
                    val senderDoc = FirestoreHelper.getUsersCollection().document(currentUserId).get().await()
                    val senderName = senderDoc.getString("name") ?: "UniMart"

                    if (!token.isNullOrEmpty()) {
                        val payload = ProxyPayload(
                            token = token,
                            title = senderName,
                            body = systemText,
                            data = mapOf("type" to "MESSAGE", "chatId" to chatId)
                        )
                        fcmApi.sendNotification(payload)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override suspend fun markAsRead(chatId: String, userId: String): Resource<Unit> {
        return try {
            chatCollection.document(chatId).update("unreadCounts.$userId", 0).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override suspend fun setChatActiveStatus(chatId: String, userId: String, isActive: Boolean): Resource<Unit> {
        return try {
            val update = if (isActive) {
                FieldValue.arrayUnion(userId)
            } else {
                FieldValue.arrayRemove(userId)
            }
            chatCollection.document(chatId).update("activeParticipants", update).await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override suspend fun markChatsAsProductSold(productId: String): Resource<Unit> {
        return try {
            val query = chatCollection
                .whereEqualTo("productId", productId)
                .whereEqualTo("productStatus", "AVAILABLE")
                .get()
                .await()

            if (!query.isEmpty) {
                val batch = db.batch()
                for (doc in query.documents) {
                    batch.update(doc.reference, "productStatus", "SOLD")
                }
                batch.commit().await()
            }
            Resource.Success(Unit)
        } catch (e: Exception) {
            Resource.Failure(e)
        }
    }

    override suspend fun performMaintenanceCleanup(userId: String): Resource<Unit> {
        return try {
            val now = System.currentTimeMillis()
            val cutoffDate = Timestamp(java.util.Date(now - 7 * 24 * 60 * 60 * 1000L))

            val expiredQuery = chatCollection
                .whereArrayContains("participants", userId)
                .whereLessThanOrEqualTo("lastTimestamp", cutoffDate)
                .get()
                .await()

            if (expiredQuery.isEmpty) return Resource.Success(Unit)

            val batch = db.batch()
            for (chatDoc in expiredQuery.documents) {
                val chatId = chatDoc.id
                val messagesQuery = FirestoreHelper.getMessagesCollection(chatId).get().await()
                for (msgDoc in messagesQuery.documents) {
                    batch.delete(msgDoc.reference)
                }
                batch.delete(chatDoc.reference)
            }

            batch.commit().await()
            Resource.Success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Failure(e)
        }
    }
}
