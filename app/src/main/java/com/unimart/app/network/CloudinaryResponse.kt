package com.unimart.app.network

import com.google.gson.annotations.SerializedName

data class CloudinaryResponse(
    @SerializedName("secure_url")
    val secureUrl: String,
    @SerializedName("public_id")
    val publicId: String,
    @SerializedName("format")
    val format: String,
    @SerializedName("resource_type")
    val resourceType: String
)
