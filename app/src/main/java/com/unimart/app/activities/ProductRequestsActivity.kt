package com.unimart.app.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.adapters.ContactRequestsAdapter
import com.unimart.app.constants.RequestStatus
import com.unimart.app.databinding.ActivityProductRequestsBinding
import com.unimart.app.models.ContactRequest
import com.unimart.app.models.Product
import com.unimart.app.repositories.ContactRepository
import com.unimart.app.repositories.ProductRepository
import java.util.Locale

class ProductRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductRequestsBinding
    private val contactRepository = ContactRepository()
    private val productRepository = ProductRepository()
    
    private val requestsList = mutableListOf<ContactRequest>()
    private lateinit var adapter: ContactRequestsAdapter
    
    private var productId: String? = null
    private var productTitle: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProductRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        productId = intent.getStringExtra("PRODUCT_ID")
        productTitle = intent.getStringExtra("PRODUCT_TITLE")

        if (productId == null) {
            finish()
            return
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(top = systemBars.top)
            insets
        }

        setupToolbar()
        setupRecyclerView()
        loadHeaderInfo()
        loadRequests()
    }

    private fun setupToolbar() {
        binding.toolbar.title = getString(R.string.product_requests_title, productTitle ?: "...")
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        adapter = ContactRequestsAdapter(
            requestsList,
            onAcceptClick = { request -> handleStatusUpdate(request, RequestStatus.ACCEPTED) },
            onRejectClick = { request -> handleStatusUpdate(request, RequestStatus.REJECTED) },
            onWhatsAppClick = { request -> openWhatsApp(request) }
        )
        binding.rvRequests.layoutManager = LinearLayoutManager(this)
        binding.rvRequests.adapter = adapter
    }

    private fun loadHeaderInfo() {
        productId?.let { id ->
            productRepository.getProductById(id,
                onSuccess = { product ->
                    populateHeader(product)
                },
                onFailure = {
                    // Error handled by tools:text
                }
            )
        }
    }

    private fun populateHeader(product: Product) {
        binding.tvHeaderProductTitle.text = product.title
        
        val priceStr = if (product.price == product.price.toLong().toDouble()) {
            String.format(Locale.getDefault(), "₹%d", product.price.toLong())
        } else {
            String.format(Locale.getDefault(), "₹%.2f", product.price)
        }
        binding.tvHeaderProductPrice.text = "$priceStr • ${product.status}"

        val imageUrl = product.imageUrls.firstOrNull()
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.ic_empty_box)
            .error(R.drawable.ic_empty_box)
            .centerCrop()
            .into(binding.ivHeaderProductImage)
    }

    private fun loadRequests() {
        productId?.let { id ->
            contactRepository.getRequestsByProductId(id,
                onSuccess = { list ->
                    requestsList.clear()
                    requestsList.addAll(list)
                    adapter.notifyDataSetChanged()
                    
                    val count = requestsList.size
                    binding.tvHeaderRequestCount.text = if (count == 1) "1 Request" else "$count Requests"

                    updateEmptyState(requestsList.isEmpty())
                },
                onFailure = { e ->
                    Toast.makeText(this@ProductRequestsActivity, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    private fun handleStatusUpdate(request: ContactRequest, newStatus: String) {
        contactRepository.updateRequestStatus(request.requestId, newStatus,
            onSuccess = {
                loadRequests() // Refresh list
            },
            onFailure = { e ->
                Toast.makeText(this@ProductRequestsActivity, "Update failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun openWhatsApp(request: ContactRequest) {
        productRepository.getUserById(request.buyerId,
            onSuccess = { buyer ->
                val phoneNumber = buyer.whatsappNumber
                if (phoneNumber.isEmpty()) {
                    Toast.makeText(this@ProductRequestsActivity, "Buyer hasn't provided a number.", Toast.LENGTH_SHORT).show()
                    return@getUserById
                }

                val cleanNumber = phoneNumber.replace("+", "").replace(" ", "")
                val message = "Hi ${buyer.name}, I accepted your contact request for \"$productTitle\" on UniMart."
                val encodedMessage = Uri.encode(message)
                val url = "https://wa.me/$cleanNumber?text=$encodedMessage"

                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(url)
                }

                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@ProductRequestsActivity, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show()
                }
            },
            onFailure = {
                Toast.makeText(this@ProductRequestsActivity, "Failed to get buyer details.", Toast.LENGTH_SHORT).show()
            }
        )
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
