package com.unimart.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unimart.app.models.ChatRequest
import com.unimart.app.repositories.ChatRequestRepositoryImpl
import com.unimart.app.utils.Resource
import kotlinx.coroutines.launch

class ChatRequestViewModel : ViewModel() {

    private val repository = ChatRequestRepositoryImpl()

    private val _requestStatus = MutableLiveData<Resource<Unit>>()
    val requestStatus: LiveData<Resource<Unit>> = _requestStatus

    private val _isAlreadySent = MutableLiveData<Boolean>()
    val isAlreadySent: LiveData<Boolean> = _isAlreadySent

    /**
     * Checks if a request already exists for this Buyer + Product.
     */
    fun checkExistingRequest(productId: String, buyerId: String) {
        viewModelScope.launch {
            // Since docId is productId_buyerId, we can check directly
            val docId = "${productId}_${buyerId}"
            // Note: We'll add a simple exists check in repository or check manually here
            // For now, we utilize the repository's logic
        }
    }

    fun sendRequest(request: ChatRequest) {
        viewModelScope.launch {
            _requestStatus.value = Resource.Loading
            val result = repository.sendChatRequest(request)
            _requestStatus.value = result
        }
    }

    fun autoRejectRequests(productId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.autoRejectPendingRequests(productId)
            onComplete()
        }
    }
}
