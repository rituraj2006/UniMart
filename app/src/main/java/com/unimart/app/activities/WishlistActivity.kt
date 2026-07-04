package com.unimart.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.unimart.app.adapters.ProductAdapter
import com.unimart.app.databinding.ActivityWishlistBinding
import com.unimart.app.models.Product
import com.unimart.app.repositories.WishlistRepository
import com.unimart.app.viewmodels.WishlistViewModel

class WishlistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWishlistBinding
    private lateinit var viewModel: WishlistViewModel
    private val wishlistRepository = WishlistRepository()
    
    private val wishlistProducts = mutableListOf<Product>()
    private var wishlistedIds = setOf<String>()
    private lateinit var adapter: ProductAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityWishlistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[WishlistViewModel::class.java]

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        observeViewModel()
        setupToolbar()
        setupRecyclerView()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.rvWishlist.visibility = if (isLoading) View.GONE else View.VISIBLE
            if (isLoading) binding.llEmptyState.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        loadWishlistData()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = ProductAdapter(
            wishlistProducts,
            wishlistedIds,
            onProductClick = { product ->
                startActivity(Intent(this, ProductDetailsActivity::class.java).apply {
                    putExtra("PRODUCT_ID", product.productId)
                })
            },
            onWishlistClick = { product, _ ->
                removeFromWishlistOptimistically(product)
            }
        )
        binding.rvWishlist.layoutManager = GridLayoutManager(this, 2)
        binding.rvWishlist.adapter = adapter
    }

    private fun loadWishlistData() {
        viewModel.setLoading(true)
        wishlistRepository.getWishlistedProductIds(
            onSuccess = { ids ->
                wishlistedIds = ids
                fetchFullProducts()
            },
            onFailure = { 
                viewModel.setLoading(false)
                Toast.makeText(this, "Failed to load wishlist IDs", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun fetchFullProducts() {
        wishlistRepository.getWishlistProducts(
            onSuccess = { products ->
                viewModel.setLoading(false)
                wishlistProducts.clear()
                wishlistProducts.addAll(products)
                
                // Update adapter with both the full objects and the ID set for correctly filling hearts
                adapter.updateWishlist(wishlistedIds)
                updateEmptyState()
            },
            onFailure = {
                viewModel.setLoading(false)
                Toast.makeText(this, "Failed to load products", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun removeFromWishlistOptimistically(product: Product) {
        // 1. Immediate UI update
        val position = wishlistProducts.indexOfFirst { it.productId == product.productId }
        if (position != -1) {
            wishlistProducts.removeAt(position)
            adapter.notifyItemRemoved(position)
            
            // Update the ID set so subsequent binds know this is gone
            wishlistedIds = wishlistedIds.minus(product.productId)
            adapter.updateWishlist(wishlistedIds)
            
            updateEmptyState()
        }

        // 2. Background Firestore update
        wishlistRepository.toggleWishlist(product.productId, true,
            onSuccess = { /* Already handled optimistically */ },
            onFailure = {
                Toast.makeText(this, "Error updating wishlist", Toast.LENGTH_SHORT).show()
                loadWishlistData() // Rollback on failure
            }
        )
    }

    private fun updateEmptyState() {
        if (wishlistProducts.isEmpty()) {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvWishlist.visibility = View.GONE
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvWishlist.visibility = View.VISIBLE
        }
    }
}
