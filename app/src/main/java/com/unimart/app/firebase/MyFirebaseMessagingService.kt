package com.unimart.app.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.unimart.app.MainActivity
import com.unimart.app.R
import com.unimart.app.repositories.AuthRepository

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Update token in Firestore if user is logged in
        AuthRepository().updateFcmToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"]
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"]
        val type = remoteMessage.data["type"]
        val chatId = remoteMessage.data["chatId"]
        val productId = remoteMessage.data["productId"]

        if (title != null && body != null) {
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
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("type", type)
            putExtra("chatId", chatId)
            putExtra("productId", productId)
            putExtra("fromNotification", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = when (type) {
            "CHAT_REQUEST" -> "chat_requests_channel"
            "MESSAGE" -> "messages_channel"
            else -> "general_channel"
        }

        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_messages) // Ensure this icon exists or use a generic one
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelName = when (type) {
                "CHAT_REQUEST" -> "Chat Requests"
                "MESSAGE" -> "Messages"
                else -> "General"
            }
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
