package com.unimart.app.network

import android.content.Context
import android.net.Uri
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.FileOutputStream

class CloudinaryRepository(private val context: Context) {

    private val cloudName = "k1l954wy" // Replace with your actual cloud name if different
    private val uploadPreset = "unimart_upload"

    suspend fun uploadImage(imageUri: Uri): String? {
        return try {
            val file = uriToFile(context, imageUri) ?: return null
            val requestFile = file.asRequestBody("image/*".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
            val preset = uploadPreset.toRequestBody("text/plain".toMediaTypeOrNull())

            val response = RetrofitClient.cloudinaryApi.uploadImage(cloudName, body, preset)

            if (response.isSuccessful) {
                response.body()?.secureUrl
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun uriToFile(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val file = File(context.cacheDir, "upload_image_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
