package com.unimart.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unimart.app.models.Product
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val allProducts = mutableListOf<Product>()
    private val _filteredProducts = MutableLiveData<List<Product>>()
    val filteredProducts: LiveData<List<Product>> = _filteredProducts

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private var currentSearchQuery = ""
    private var selectedCategory: String? = null

    /**
     * Returns true if any filter (search or category) is currently active.
     */
    fun isFilterActive(): Boolean = currentSearchQuery.isNotEmpty() || selectedCategory != null

    /**
     * Updates loading state
     */
    fun setLoading(isLoading: Boolean) {
        _isLoading.value = isLoading
    }

    /**
     * Initializes the master list. Called once when data is loaded from Repository.
     */
    fun setAllProducts(products: List<Product>) {
        allProducts.clear()
        allProducts.addAll(products)
        applyFilters()
    }

    /**
     * Updates the search query and applies all filters.
     */
    fun searchProducts(query: String) {
        currentSearchQuery = query.trim().lowercase()
        applyFilters()
    }

    /**
     * Updates the selected category and applies all filters.
     */
    fun selectCategory(category: String?) {
        selectedCategory = if (category == "All") null else category
        applyFilters()
    }

    /**
     * Resets all search and category filters.
     */
    fun resetFilters() {
        currentSearchQuery = ""
        selectedCategory = null
        applyFilters()
    }

    /**
     * Combines category and search filters and updates the LiveData.
     */
    private val userRepository = com.unimart.app.repositories.UserRepository()
    private val blockedUserIds = mutableSetOf<String>()

    fun loadBlockedUsers(currentUserId: String) {
        viewModelScope.launch {
            blockedUserIds.clear()
            blockedUserIds.addAll(userRepository.getBlockedUserIds(currentUserId))
            applyFilters()
        }
    }

    private fun applyFilters() {
        var result = allProducts.toList()

        // 1. Filter by Blocked Users
        result = result.filter { !blockedUserIds.contains(it.sellerId) }

        // 2. Filter by Category
        selectedCategory?.let { category ->
            result = result.filter { it.category == category }
        }

        // 3. Filter by Search Query
        if (currentSearchQuery.isNotEmpty()) {
            result = result.filter { product ->
                product.title.lowercase().contains(currentSearchQuery) ||
                product.category.lowercase().contains(currentSearchQuery)
            }
        }

        _filteredProducts.value = result
    }
}
