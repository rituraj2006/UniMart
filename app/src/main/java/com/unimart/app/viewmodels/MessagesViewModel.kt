package com.unimart.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.unimart.app.models.Chat
import com.unimart.app.repositories.ChatRepositoryImpl
import com.unimart.app.utils.Resource

class MessagesViewModel : ViewModel() {

    private val repository = ChatRepositoryImpl()

    fun getInbox(userId: String): LiveData<Resource<List<Chat>>> {
        return repository.getInbox(userId).asLiveData()
    }
}
