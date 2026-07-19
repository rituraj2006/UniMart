package com.unimart.app.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface FcmApi {
    @POST("v1/projects/{projectId}/messages:send")
    suspend fun sendNotification(
        @Path("projectId") projectId: String,
        @Header("Authorization") bearerToken: String,
        @Body payload: FcmPayloadV1
    ): Response<ResponseBody>
}

data class FcmPayloadV1(
    val message: MessageData
)

data class MessageData(
    val token: String,
    val notification: NotificationData,
    val data: Map<String, String>
)

data class NotificationData(
    val title: String,
    val body: String
)
