package com.unimart.app.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.unimart.app.models.Chat
import com.unimart.app.models.ChatRequest
import com.unimart.app.repositories.ChatRepositoryImpl
import com.unimart.app.repositories.ChatRequestRepositoryImpl
import com.unimart.app.utils.Resource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SellerRequestsViewModel : ViewModel() {

    private val chatRepository = ChatRepositoryImpl()
    private val requestRepository = ChatRequestRepositoryImpl()

    private val _actionStatus = MutableStateFlow<Resource<String>?>(null)
    val actionStatus: StateFlow<Resource<String>?> = _actionStatus

    fun getPendingRequests(sellerId: String) = 
        requestRepository.getPendingRequestsForSeller(sellerId).asLiveData()

    fun acceptRequest(request: ChatRequest, chatMetadata: Chat) {
        viewModelScope.launch {
            _actionStatus.value = Resource.Loading
            val result = chatRepository.acceptChatRequest(request, chatMetadata)
            _actionStatus.value = result
        }
    }

    fun rejectRequest(requestId: String) {
        viewModelScope.launch {
            _actionStatus.value = Resource.Loading
            val result = requestRepository.rejectChatRequest(requestId)
            // Mapping Resource<Unit> to Resource<String> for consistent UI state
            _actionStatus.value = when(result) {
                is Resource.Success -> Resource.Success("REJECTED")
                is Resource.Failure -> Resource.Failure(result.exception)
                else -> Resource.Loading
            }
        }
    }

    fun clearActionStatus() {
        _actionStatus.value = null
    }
}
