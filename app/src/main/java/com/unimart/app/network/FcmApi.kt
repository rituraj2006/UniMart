package com.unimart.app.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface FcmApi {
    @POST("send-notification")
    suspend fun sendNotification(
        @Body payload: ProxyPayload
    ): Response<ResponseBody>
}

data class ProxyPayload(
    val token: String,
    val title: String,
    val body: String,
    val data: Map<String, String>
)
