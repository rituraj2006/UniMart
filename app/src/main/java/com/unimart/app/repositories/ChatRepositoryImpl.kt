package com.unimart.app.repositories

import com.google.firebase.Timestamp
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
import com.unimart.app.network.FcmPayloadV1
import com.unimart.app.network.MessageData
import com.unimart.app.network.NotificationData
import com.unimart.app.utils.AccessTokenProvider
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
    private val projectId = "unimart-6726a"

    private val fcmApi: FcmApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://fcm.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(FcmApi::class.java)
    }

    override suspend fun acceptChatRequest(request: ChatRequest, chatMetadata: Chat): Resource<String> {
        return try {
            // Final Rule: chatId must be productId_buyerId
            val chatId = "${request.productId}_${request.buyerId}"
            
            db.runTransaction { transaction ->
                val requestRef = requestCollection.document(request.requestId)
                val chatRef = chatCollection.document(chatId)
                
                // 1. Verify Request exists before promoting (Read-before-write)
                val requestSnapshot = transaction.get(requestRef)
                if (!requestSnapshot.exists()) {
                    throw Exception("Chat request no longer exists.")
                }
                
                // 2. Create Chat and Delete Request atomically
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
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Failure(error))
                    return@addSnapshotListener
                }
                val messages = snapshot?.toObjects(Message::class.java) ?: emptyList()
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
            
            // Update Chat metadata
            val previewText = if (message.type == MessageType.IMAGE) "📷 Image" else message.content
            val updates = mapOf(
                "lastMessagePreview" to previewText,
                "lastSenderId" to message.senderId,
                "lastTimestamp" to message.timestamp
            )
            batch.update(chatRef, updates)
            
            batch.commit().await()

            // --- New: Send Push Notification (App-to-App V1) ---
            try {
                val chatDoc = chatCollection.document(chatId).get().await()
                @Suppress("UNCHECKED_CAST")
                val participants = chatDoc.get("participants") as? List<String>
                val receiverId = participants?.find { it != message.senderId }

                if (receiverId != null) {
                    val receiverDoc = FirestoreHelper.getUsersCollection().document(receiverId).get().await()
                    val token = receiverDoc.getString("fcmToken")
                    
                    val senderDoc = FirestoreHelper.getUsersCollection().document(message.senderId).get().await()
                    val senderName = senderDoc.getString("name") ?: "UniMart"

                    if (!token.isNullOrEmpty()) {
                        // Use context from app to access assets
                        val context = com.google.firebase.FirebaseApp.getInstance().applicationContext
                        val bearerToken = AccessTokenProvider.getAccessToken(context)
                        
                        if (bearerToken != null) {
                            val bodyText = if (message.type == MessageType.IMAGE) "📷 Image" else message.content
                            val payload = FcmPayloadV1(
                                message = MessageData(
                                    token = token,
                                    notification = NotificationData(senderName, bodyText),
                                    data = mapOf("type" to "MESSAGE", "chatId" to chatId)
                                )
                            )
                            fcmApi.sendNotification(projectId, bearerToken, payload)
                        }
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

    override suspend fun updatePhoneSharing(chatId: String, status: PhoneSharingStatus): Resource<Unit> {
        return try {
            val batch = db.batch()
            val chatRef = chatCollection.document(chatId)
            val messageRef = FirestoreHelper.getMessagesCollection(chatId).document()
            
            // 1. Update Status
            batch.update(chatRef, "phoneSharingStatus", status)
            
            // 2. Insert System Message
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

            // --- New: Send Push Notification (App-to-App V1) ---
            try {
                val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
                val chatDoc = chatCollection.document(chatId).get().await()
                @Suppress("UNCHECKED_CAST")
                val participants = chatDoc.get("participants") as? List<String>
                val receiverId = participants?.find { it != currentUserId }

                if (receiverId != null && currentUserId != null) {
                    val receiverDoc = FirestoreHelper.getUsersCollection().document(receiverId).get().await()
                    val token = receiverDoc.getString("fcmToken")
                    
                    val senderDoc = FirestoreHelper.getUsersCollection().document(currentUserId).get().await()
                    val senderName = senderDoc.getString("name") ?: "UniMart"

                    if (!token.isNullOrEmpty()) {
                        // Use context from app to access assets
                        val context = com.google.firebase.FirebaseApp.getInstance().applicationContext
                        val bearerToken = AccessTokenProvider.getAccessToken(context)
                        
                        if (bearerToken != null) {
                            val bodyText = systemText
                            val payload = FcmPayloadV1(
                                message = MessageData(
                                    token = token,
                                    notification = NotificationData(senderName, bodyText),
                                    data = mapOf("type" to "MESSAGE", "chatId" to chatId)
                                )
                            )
                            fcmApi.sendNotification(projectId, bearerToken, payload)
                        }
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
        // Implementation for marking messages as read (version 1)
        return Resource.Success(Unit)
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
}
