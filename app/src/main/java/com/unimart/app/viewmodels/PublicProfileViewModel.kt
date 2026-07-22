package com.unimart.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unimart.app.models.User
import com.unimart.app.repositories.UserRepository
import com.unimart.app.repositories.ProductRepository
import com.unimart.app.utils.Resource
import kotlinx.coroutines.launch

class PublicProfileViewModel : ViewModel() {
    private val userRepository = UserRepository()
    private val productRepository = ProductRepository()

    private val _profile = MutableLiveData<Resource<User>>()
    val profile: LiveData<Resource<User>> = _profile

    private val _listingsCount = MutableLiveData<Int>()
    val listingsCount: LiveData<Int> = _listingsCount

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _profile.value = Resource.Loading
            _profile.value = userRepository.getPublicProfile(userId)
            
            // Fetch real active listing count
            productRepository.getSellingCountById(userId,
                onSuccess = { count: Int ->
                    _listingsCount.value = count
                },
                onFailure = { 
                    _listingsCount.value = 0
                }
            )
        }
    }
}
