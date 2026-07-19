package com.unimart.app.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

object NotificationHelper {

    const val CHANNEL_MESSAGES = "messages_channel"
    const val CHANNEL_CHAT_REQUESTS = "chat_requests_channel"
    const val CHANNEL_GENERAL = "general_channel"

    fun initNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val channels = listOf(
                createChannel(
                    CHANNEL_MESSAGES,
                    "Messages",
                    "Notifications for new chat messages",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                createChannel(
                    CHANNEL_CHAT_REQUESTS,
                    "Chat Requests",
                    "Notifications for new and accepted chat requests",
                    NotificationManager.IMPORTANCE_HIGH
                ),
                createChannel(
                    CHANNEL_GENERAL,
                    "General",
                    "General app notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )

            notificationManager.createNotificationChannels(channels)
        }
    }

    private fun createChannel(id: String, name: String, description: String, importance: Int): NotificationChannel {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel(id, name, importance).apply {
                this.description = description
                setShowBadge(true)
                enableVibration(true)
                // Set default notification sound
                val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
                setSound(soundUri, audioAttributes)
            }
        } else {
            throw IllegalStateException("Should not be called on versions below Oreo")
        }
    }
}
