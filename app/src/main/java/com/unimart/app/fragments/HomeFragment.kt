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
    private lateinit var categoryAdapter: CategoryAdapter
    private var productsListener: com.google.firebase.firestore.ListenerRegistration? = null

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
        setupSearch()
        setupSwipeRefresh()
        loadWishlist()
        loadProducts()
        loadBlockedUsers()
        loadCurrentUserProfile()
        
        observeViewModel()
    }

    private fun loadBlockedUsers() {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            viewModel.loadBlockedUsers(uid)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setColorSchemeResources(R.color.unimart_primary)
        binding.swipeRefreshLayout.setProgressBackgroundColorSchemeResource(R.color.unimart_surface_elevated)
        binding.swipeRefreshLayout.setOnRefreshListener {
            // 1. Reset search and category in ViewModel
            viewModel.resetFilters()

            // 2. Clear UI Search Field (will trigger TextWatcher, but resetFilters handles it)
            binding.etSearch.text?.clear()

            // 3. Reset Category UI
            if (::categoryAdapter.isInitialized) {
                categoryAdapter.resetSelection()
            }

            // 4. Reload from Firestore
            loadProducts()
        }
    }

    override fun onResume() {
        super.onResume()
        loadWishlist()
        loadProducts()
        loadCurrentUserProfile()
    }

    override fun onPause() {
        super.onPause()
        productsListener?.remove()
        productsListener = null
    }

    private fun observeViewModel() {
        viewModel.filteredProducts.observe(viewLifecycleOwner) { filteredList ->
            if (::productAdapter.isInitialized) {
                productAdapter.updateProducts(filteredList)
                updateEmptyState(filteredList.isEmpty())
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingIndicator.visibility = if (isLoading && !binding.swipeRefreshLayout.isRefreshing) View.VISIBLE else View.GONE
            binding.rvProducts.visibility = if (isLoading && !binding.swipeRefreshLayout.isRefreshing) View.GONE else View.VISIBLE
            
            if (!isLoading) {
                binding.swipeRefreshLayout.isRefreshing = false
            }

            // Optionally hide empty state while loading
            if (isLoading) binding.llEmptyState.visibility = View.GONE
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""
                viewModel.searchProducts(query)
                // If user starts searching, we may need to reload without the 20-item limit
                loadProducts()
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
        categoryAdapter = CategoryAdapter(categoryList) { category ->
            viewModel.selectCategory(category.name)
            // Reload without the 20-item limit when a category is selected
            loadProducts()
        }
        binding.rvCategories.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        binding.rvCategories.adapter = categoryAdapter
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
        val isFilterActive = viewModel.isFilterActive()
        productsListener?.remove()
        viewModel.setLoading(true)
        
        val db = FirebaseFirestore.getInstance()
        
        // Use a query that only relies on the default 'createdAt' index
        var query = db.collection("Products")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        // Apply a reasonable buffer limit to ensure we find enough AVAILABLE items 
        // without requiring a composite index.
        if (!isFilterActive) {
            query = query.limit(50) 
        }

        productsListener = query.addSnapshotListener { querySnapshot, e ->
            viewModel.setLoading(false)
            if (_binding == null) return@addSnapshotListener
            
            if (e != null) {
                context?.let { Toast.makeText(it, "Error: ${e.localizedMessage}", Toast.LENGTH_LONG).show() }
                return@addSnapshotListener
            }

            // Filter for AVAILABLE status locally in Kotlin to avoid Index requirements
            var newProducts = querySnapshot?.toObjects(Product::class.java)
                ?.filter { it.status == "AVAILABLE" } ?: emptyList()
            
            // Apply the 20-item limit for the Home screen
            if (!isFilterActive) {
                newProducts = newProducts.take(20)
            }
            
            productList.clear()
            productList.addAll(newProducts)
            
            viewModel.setAllProducts(newProducts)
            updateEmptyState(productList.isEmpty())
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
