package com.unimart.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.unimart.app.constants.MessageType
import com.unimart.app.models.Message
import com.unimart.app.network.CloudinaryRepository
import com.unimart.app.repositories.ChatRepositoryImpl
import com.unimart.app.utils.Resource
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val repository = ChatRepositoryImpl()
    private val userRepository = com.unimart.app.repositories.UserRepository()

    private val _sendMessageStatus = MutableLiveData<Resource<Unit>>()
    val sendMessageStatus: LiveData<Resource<Unit>> = _sendMessageStatus

    private val _isBlocked = MutableLiveData<Boolean>()
    val isBlocked: LiveData<Boolean> = _isBlocked

    fun checkBlockStatus(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            _isBlocked.value = userRepository.isUserBlocked(currentUserId, targetUserId)
        }
    }

    fun blockUser(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            val result = userRepository.blockUser(currentUserId, targetUserId)
            if (result is Resource.Success) _isBlocked.value = true
        }
    }

    fun unblockUser(currentUserId: String, targetUserId: String) {
        viewModelScope.launch {
            val result = userRepository.unblockUser(currentUserId, targetUserId)
            if (result is Resource.Success) _isBlocked.value = false
        }
    }

    fun getMessages(chatId: String): LiveData<Resource<List<Message>>> {
        return repository.getChatMessages(chatId).asLiveData()
    }

    fun sendMessage(chatId: String, message: Message) {
        viewModelScope.launch {
            _sendMessageStatus.value = Resource.Loading
            val result = repository.sendMessage(chatId, message)
            _sendMessageStatus.value = result
        }
    }

    fun uploadImageAndSendMessage(
        chatId: String,
        senderId: String,
        uri: android.net.Uri,
        cloudinaryRepository: CloudinaryRepository
    ) {
        viewModelScope.launch {
            _sendMessageStatus.value = Resource.Loading
            val secureUrl = cloudinaryRepository.uploadImage(uri)
            if (secureUrl != null) {
                val message = Message(
                    senderId = senderId,
                    type = MessageType.IMAGE,
                    content = secureUrl,
                    timestamp = Timestamp.now()
                )
                val result = repository.sendMessage(chatId, message)
                _sendMessageStatus.value = result
            } else {
                _sendMessageStatus.value = Resource.Failure(Exception("Failed to upload image."))
            }
        }
    }

    fun markAsRead(chatId: String, userId: String) {
        viewModelScope.launch {
            repository.markAsRead(chatId, userId)
        }
    }

    fun setChatActive(chatId: String, userId: String, isActive: Boolean) {
        viewModelScope.launch {
            repository.setChatActiveStatus(chatId, userId, isActive)
        }
    }

    fun updatePhoneSharing(chatId: String, status: com.unimart.app.constants.PhoneSharingStatus) {
        viewModelScope.launch {
            repository.updatePhoneSharing(chatId, status)
        }
    }

    fun markProductAsSold(productId: String) {
        viewModelScope.launch {
            repository.markChatsAsProductSold(productId)
        }
    }
}
