package com.unimart.app.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.unimart.app.MainActivity
import com.unimart.app.R
import com.unimart.app.repositories.AuthRepository
import com.unimart.app.utils.ChatSessionManager
import com.unimart.app.utils.NotificationHelper

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Update token in Firestore if user is logged in
        AuthRepository().updateFcmToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCM", "Message received from: ${remoteMessage.from}")

        // Now reading title and body exclusively from data block for reliability
        val title = remoteMessage.data["title"]
        val body = remoteMessage.data["body"]
        val type = remoteMessage.data["type"]
        val chatId = remoteMessage.data["chatId"]
        val productId = remoteMessage.data["productId"]
        
        Log.d("FCM", "Title: $title, Body: $body, Type: $type")

        // Fix: Don't show system notification if the user is already in this specific chat
        if (chatId != null && chatId == ChatSessionManager.activeChatId) {
            Log.d("FCM", "User is active in this chat. Notification suppressed.")
            return
        }

        if (!title.isNullOrEmpty() && !body.isNullOrEmpty()) {
            showNotification(title, body, type, chatId, productId)
        }
    }

    private fun showNotification(
        title: String,
        body: String,
        type: String?,
        chatId: String?,
        productId: String?
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("type", type)
            putExtra("chatId", chatId)
            putExtra("productId", productId)
            putExtra("fromNotification", true)
        }

        // Use a unique requestCode to prevent overwriting pending intents
        val requestCode = System.currentTimeMillis().toInt()
        val pendingIntent = PendingIntent.getActivity(
            this, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = when (type) {
            "CHAT_REQUEST" -> NotificationHelper.CHANNEL_CHAT_REQUESTS
            "MESSAGE" -> NotificationHelper.CHANNEL_MESSAGES
            else -> NotificationHelper.CHANNEL_GENERAL
        }

        // Use a high-priority builder configuration
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_messages) 
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setPriority(NotificationCompat.PRIORITY_MAX) 
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setContentIntent(pendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false) // Ensure it's dismissible
            .setOnlyAlertOnce(false) // Re-alert for new messages

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Ensure channels are created (fallback)
        NotificationHelper.initNotificationChannels(this)

        notificationManager.notify(requestCode, notificationBuilder.build())
    }
}
