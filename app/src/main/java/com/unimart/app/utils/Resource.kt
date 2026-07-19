package com.unimart.app.utils

sealed class Resource<out T> {
    data class Success<out T>(val data: T) : Resource<T>()
    data class Failure(val exception: Exception, val errorCode: Int? = null) : Resource<Nothing>()
    object Loading : Resource<Nothing>()
    object Empty : Resource<Nothing>()
}
