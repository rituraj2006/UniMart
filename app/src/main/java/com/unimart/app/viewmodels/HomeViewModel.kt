package com.unimart.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.unimart.app.models.Product

class HomeViewModel : ViewModel() {

    private val allProducts = mutableListOf<Product>()
    private val _filteredProducts = MutableLiveData<List<Product>>()
    val filteredProducts: LiveData<List<Product>> = _filteredProducts

    /**
     * Initializes the master list. Called once when data is loaded from Repository.
     */
    fun setAllProducts(products: List<Product>) {
        allProducts.clear()
        allProducts.addAll(products)
        _filteredProducts.value = products
    }

    /**
     * Filters the master list in-memory based on title or category.
     */
    fun searchProducts(query: String) {
        val trimmedQuery = query.trim().lowercase()

        if (trimmedQuery.isEmpty()) {
            _filteredProducts.value = allProducts
            return
        }

        val filtered = allProducts.filter { product ->
            product.title.lowercase().contains(trimmedQuery) ||
            product.category.lowercase().contains(trimmedQuery)
        }
        
        _filteredProducts.value = filtered
    }
}
