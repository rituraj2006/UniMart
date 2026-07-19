package com.unimart.app.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.unimart.app.adapters.SellerRequestAdapter
import com.unimart.app.constants.ChatStatus
import com.unimart.app.databinding.ActivityContactRequestsBinding
import com.unimart.app.models.Chat
import com.unimart.app.models.ChatRequest
import com.unimart.app.utils.Resource
import com.unimart.app.viewmodels.SellerRequestsViewModel
import kotlinx.coroutines.launch

class ContactRequestsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactRequestsBinding
    private val viewModel: SellerRequestsViewModel by viewModels()
    private val productRepository = com.unimart.app.repositories.ProductRepository()
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    private var currentUser: com.unimart.app.models.User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadCurrentUser()
        observeRequests()
        observeActions()
    }

    private fun loadCurrentUser() {
        productRepository.getUserById(currentUserId,
            onSuccess = { user -> currentUser = user },
            onFailure = { }
        )
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun observeRequests() {
        viewModel.getPendingRequests(currentUserId).observe(this) { resource: Resource<List<ChatRequest>> ->
            when (resource) {
                is Resource.Loading -> binding.loadingIndicator.visibility = View.VISIBLE
                is Resource.Success -> {
                    binding.loadingIndicator.visibility = View.GONE
                    updateUI(resource.data)
                }
                is Resource.Failure -> {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(this, "Error: ${resource.exception.message}", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun observeActions() {
        lifecycleScope.launch {
            viewModel.actionStatus.collect { resource ->
                if (resource != null) {
                    when (resource) {
                        is Resource.Loading -> binding.loadingIndicator.visibility = View.VISIBLE
                        is Resource.Success<String> -> {
                            binding.loadingIndicator.visibility = View.GONE
                            if (resource.data != "REJECTED") {
                                Toast.makeText(this@ContactRequestsActivity, "Chat Created!", Toast.LENGTH_SHORT).show()
                            }
                            viewModel.clearActionStatus()
                        }
                        is Resource.Failure -> {
                            binding.loadingIndicator.visibility = View.GONE
                            Toast.makeText(this@ContactRequestsActivity, resource.exception.message, Toast.LENGTH_LONG).show()
                            viewModel.clearActionStatus()
                        }
                        else -> {}
                    }
                }
            }
        }
    }

    private fun updateUI(requests: List<ChatRequest>) {
        if (requests.isEmpty()) {
            binding.rvRequests.visibility = View.GONE
            binding.llEmptyState.visibility = View.VISIBLE
            binding.tvFooter.visibility = View.GONE
        } else {
            binding.llEmptyState.visibility = View.GONE
            binding.rvRequests.visibility = View.VISIBLE
            binding.tvFooter.visibility = View.VISIBLE
            binding.rvRequests.layoutManager = LinearLayoutManager(this)
            binding.rvRequests.adapter = SellerRequestAdapter(
                requests,
                onAcceptClick = { req -> accept(req) },
                onRejectClick = { req -> viewModel.rejectRequest(req.requestId) }
            )
        }
    }

    private fun accept(request: ChatRequest) {
        val seller = currentUser ?: return
        val chatMetadata = Chat(
            productId = request.productId,
            buyerId = request.buyerId,
            sellerId = request.sellerId,
            participants = listOf(request.buyerId, request.sellerId),
            chatStatus = ChatStatus.ACTIVE,
            buyerName = request.buyerName,
            buyerImage = request.buyerImage,
            sellerName = seller.name,
            sellerImage = seller.profileImage,
            title = request.productTitle,
            price = request.productPrice,
            thumbnail = request.productImage
        )
        viewModel.acceptRequest(request, chatMetadata)
    }
}
