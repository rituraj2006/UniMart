package com.unimart.app.utils

import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.FirebaseFirestore

object FirestoreHelper {
    private val db = FirebaseFirestore.getInstance()

    fun getUsersCollection(): CollectionReference = db.collection("Users")
    fun getProductsCollection(): CollectionReference = db.collection("Products")
    fun getChatRequestsCollection(): CollectionReference = db.collection("ChatRequests")
    fun getChatsCollection(): CollectionReference = db.collection("Chats")
    
    fun getMessagesCollection(chatId: String): CollectionReference {
        return getChatsCollection().document(chatId).collection("Messages")
    }
}
