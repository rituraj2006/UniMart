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
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.unimart.app.adapters.ProductRequestsOverviewAdapter
import com.unimart.app.databinding.ActivityContactRequestsBinding
import com.unimart.app.models.ProductWithRequestCount
import com.unimart.app.repositories.ContactRepository
import com.unimart.app.repositories.ProductRepository
import com.unimart.app.viewmodels.ContactRequestsViewModel

class ContactRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactRequestsBinding
    private lateinit var viewModel: ContactRequestsViewModel
    private val contactRepository = ContactRepository()
    private val productRepository = ProductRepository()
    private val productOverviewList = mutableListOf<ProductWithRequestCount>()
    private lateinit var adapter: ProductRequestsOverviewAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityContactRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[ContactRequestsViewModel::class.java]

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        observeViewModel()
        setupToolbar()
        setupRecyclerView()
        loadProductOverview()
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.rvRequests.visibility = if (isLoading) View.GONE else View.VISIBLE
            if (isLoading) {
                binding.llEmptyState.visibility = View.GONE
                binding.tvSubtitle.visibility = View.GONE
            } else {
                binding.tvSubtitle.visibility = View.VISIBLE
            }
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ProductRequestsOverviewAdapter(productOverviewList) { item ->
            val intent = Intent(this, ProductRequestsActivity::class.java).apply {
                putExtra("PRODUCT_ID", item.product.productId)
                putExtra("PRODUCT_TITLE", item.product.title)
            }
            startActivity(intent)
        }
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = adapter
    }

    private fun loadProductOverview() {
        val currentSellerUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        viewModel.setLoading(true)
        contactRepository.getIncomingRequests(currentSellerUid,
            onSuccess = { allRequests ->
                if (allRequests.isEmpty()) {
                    viewModel.setLoading(false)
                    updateEmptyState(true)
                    return@getIncomingRequests
                }

                // Group requests by productId
                val groupedRequests = allRequests.groupBy { it.productId }
                val productIds = groupedRequests.keys.toList()
                
                fetchProductsAndCounts(productIds, groupedRequests)
            },
            onFailure = { e ->
                viewModel.setLoading(false)
                Toast.makeText(this, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun fetchProductsAndCounts(
        productIds: List<String>,
        groupedRequests: Map<String, List<com.unimart.app.models.ContactRequest>>
    ) {
        val tempOverviewList = mutableListOf<ProductWithRequestCount>()
        var fetchedCount = 0

        for (productId in productIds) {
            productRepository.getProductById(productId,
                onSuccess = { product ->
                    val requests = groupedRequests[productId] ?: emptyList()
                    val totalCount = requests.size
                    val pendingCount = requests.count { it.status == com.unimart.app.constants.RequestStatus.PENDING }
                    
                    tempOverviewList.add(ProductWithRequestCount(product, totalCount, pendingCount))
                    
                    fetchedCount++
                    if (fetchedCount == productIds.size) {
                        finalizeList(tempOverviewList)
                    }
                },
                onFailure = {
                    fetchedCount++
                    if (fetchedCount == productIds.size) {
                        finalizeList(tempOverviewList)
                    }
                }
            )
        }
    }

    private fun finalizeList(list: List<ProductWithRequestCount>) {
        viewModel.setLoading(false)
        productOverviewList.clear()
        productOverviewList.addAll(list.sortedByDescending { it.product.createdAt })
        adapter.notifyDataSetChanged()
        updateEmptyState(productOverviewList.isEmpty())
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        if (isEmpty) {
            binding.llEmptyState.visibility = View.VISIBLE
            binding.rvRequests.visibility = View.GONE
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvRequests.visibility = View.VISIBLE
        }
    }
}
