package com.unimart.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.unimart.app.models.Chat
import com.unimart.app.repositories.ChatRepositoryImpl
import com.unimart.app.utils.Resource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MessagesViewModel : ViewModel() {

    private val repository = ChatRepositoryImpl()

    fun getInbox(userId: String): LiveData<Resource<List<Chat>>> {
        return repository.getInbox(userId).asLiveData()
    }

    /**
     * Streams the total unread count for the given user across all chats.
     */
    fun getTotalUnreadCount(userId: String): LiveData<Int> {
        return repository.getInbox(userId).map { resource ->
            if (resource is Resource.Success) {
                resource.data.sumOf { chat -> chat.unreadCounts[userId] ?: 0 }
            } else 0
        }.asLiveData()
    }

    /**
     * Triggers a check for expired chats (7+ days inactive) and deletes them.
     */
    fun performCleanup(userId: String) {
        viewModelScope.launch {
            repository.performMaintenanceCleanup(userId)
        }
    }
}
