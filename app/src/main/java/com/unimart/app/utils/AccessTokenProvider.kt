package com.unimart.app.utils

import android.content.Context
import com.google.auth.oauth2.GoogleCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

object AccessTokenProvider {
    private const val MESSAGING_SCOPE = "https://www.googleapis.com/auth/firebase.messaging"

    suspend fun getAccessToken(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.assets.open("service-account.json")
            val credentials = GoogleCredentials.fromStream(inputStream)
                .createScoped(listOf(MESSAGING_SCOPE))
            credentials.refreshIfExpired()
            "Bearer ${credentials.accessToken.tokenValue}"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
