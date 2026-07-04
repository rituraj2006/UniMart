package com.unimart.app.fragments

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unimart.app.R
import com.unimart.app.activities.ProductDetailsActivity
import com.unimart.app.activities.SellProductActivity
import com.unimart.app.adapters.CategoryAdapter
import com.unimart.app.adapters.ProductAdapter
import com.unimart.app.constants.Categories
import com.unimart.app.databinding.FragmentHomeBinding
import com.unimart.app.models.Category
import com.unimart.app.models.Product
import com.unimart.app.repositories.ProductRepository
import com.unimart.app.repositories.WishlistRepository
import com.unimart.app.viewmodels.HomeViewModel

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: HomeViewModel
    private val repository = ProductRepository()
    private val wishlistRepository = WishlistRepository()
    
    private val productList = mutableListOf<Product>()
    private var wishlistedIds = setOf<String>()
    private lateinit var productAdapter: ProductAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[HomeViewModel::class.java]

        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbarHome) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        setupCategories()
        setupRecyclerView()
        setupClickListeners()
        setupSearch()
        loadWishlist()
        loadProducts()
        loadCurrentUserProfile()
        
        observeViewModel()
    }

    override fun onResume() {
        super.onResume()
        loadWishlist()
        loadProducts()
        loadCurrentUserProfile()
    }

    private fun observeViewModel() {
        viewModel.filteredProducts.observe(viewLifecycleOwner) { filteredList ->
            if (::productAdapter.isInitialized) {
                productAdapter.updateProducts(filteredList)
                updateEmptyState(filteredList.isEmpty())
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.rvProducts.visibility = if (isLoading) View.GONE else View.VISIBLE
            // Optionally hide empty state while loading
            if (isLoading) binding.llEmptyState.visibility = View.GONE
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                viewModel.searchProducts(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun loadWishlist() {
        wishlistRepository.getWishlistedProductIds(
            onSuccess = { ids ->
                wishlistedIds = ids
                if (::productAdapter.isInitialized) {
                    productAdapter.updateWishlist(wishlistedIds)
                }
            },
            onFailure = { }
        )
    }

    private fun setupCategories() {
        val categoryList = listOf(
            Category(Categories.BOOKS, R.drawable.ic_search),
            Category(Categories.ELECTRONICS, R.drawable.ic_search),
            Category(Categories.FURNITURE, R.drawable.ic_search),
            Category(Categories.CYCLES, R.drawable.ic_search),
            Category(Categories.PHONES, R.drawable.ic_search),
            Category(Categories.HOSTEL_ESSENTIALS, R.drawable.ic_search),
            Category(Categories.NOTES, R.drawable.ic_search),
            Category(Categories.ACCESSORIES, R.drawable.ic_search),
            Category(Categories.OTHERS, R.drawable.ic_search)
        )
        binding.rvCategories.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCategories.adapter = CategoryAdapter(categoryList) { category ->
            viewModel.selectCategory(category.name)
        }
    }

    private fun setupRecyclerView() {
        productAdapter = ProductAdapter(
            productList,
            wishlistedIds,
            onProductClick = { product ->
                val intent = Intent(context, ProductDetailsActivity::class.java)
                intent.putExtra("PRODUCT_ID", product.productId)
                startActivity(intent)
            },
            onWishlistClick = { product, isSaved ->
                toggleWishlist(product.productId, isSaved)
            }
        )
        binding.rvProducts.layoutManager = GridLayoutManager(context, 2)
        binding.rvProducts.adapter = productAdapter
    }

    private fun toggleWishlist(productId: String, isSaved: Boolean) {
        wishlistRepository.toggleWishlist(productId, isSaved,
            onSuccess = { _ -> loadWishlist() },
            onFailure = { Toast.makeText(context, "Failed to update wishlist", Toast.LENGTH_SHORT).show() }
        )
    }

    private fun loadProducts() {
        viewModel.setLoading(true)
        val db = FirebaseFirestore.getInstance()
        db.collection("Products")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                viewModel.setLoading(false)
                if (_binding == null) return@addOnSuccessListener
                
                val newProducts = mutableListOf<Product>()
                for (doc in querySnapshot) {
                    try {
                        val product = doc.toObject(Product::class.java)
                        if (product.status == "AVAILABLE") {
                            newProducts.add(product)
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }
                
                productList.clear()
                productList.addAll(newProducts)
                
                // Initialize the ViewModel with the loaded products for in-memory searching
                viewModel.setAllProducts(newProducts)
                
                updateEmptyState(productList.isEmpty())
            }
            .addOnFailureListener { e ->
                viewModel.setLoading(false)
                if (_binding == null) return@addOnFailureListener
                context?.let { Toast.makeText(it, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
            }
    }

    private fun loadCurrentUserProfile() {
        repository.getCurrentUser(
            onSuccess = { user ->
                if (_binding == null) return@getCurrentUser
                Glide.with(this)
                    .load(user.profileImage.ifEmpty { null })
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(binding.ivProfile)
            },
            onFailure = { }
        )
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (_binding == null) return
        if (isEmpty) {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvProducts.visibility = View.GONE
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvProducts.visibility = View.VISIBLE
        }
    }

    private fun setupClickListeners() {
        binding.fabSell.setOnClickListener {
            startActivity(Intent(context, SellProductActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
