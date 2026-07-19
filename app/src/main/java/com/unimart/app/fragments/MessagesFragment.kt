package com.unimart.app.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.unimart.app.adapters.MessagesAdapter
import com.unimart.app.databinding.FragmentMessagesBinding
import com.unimart.app.utils.Resource
import com.unimart.app.viewmodels.MessagesViewModel

/**
 * The Messages screen displays ONLY accepted conversations.
 * It observes the "Chats" collection in real-time.
 */
class MessagesFragment : Fragment() {

    private var _binding: FragmentMessagesBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: MessagesViewModel by viewModels()
    private lateinit var adapter: MessagesAdapter
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMessagesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupRecyclerView()
        observeConversations()
        
        // Automated Maintenance: Delete chats with no activity for 7 days
        viewModel.performCleanup(currentUserId)
    }

    private fun setupRecyclerView() {
        adapter = MessagesAdapter(currentUserId) { chat ->
            val intent = android.content.Intent(requireContext(), com.unimart.app.activities.ChatActivity::class.java).apply {
                putExtra("CHAT_ID", chat.chatId)
                putExtra("OTHER_USER_NAME", if (currentUserId == chat.buyerId) chat.sellerName else chat.buyerName)
                putExtra("PRODUCT_TITLE", chat.title)
                putExtra("PRODUCT_THUMBNAIL", chat.thumbnail)
            }
            startActivity(intent)
        }
        binding.rvInbox.layoutManager = LinearLayoutManager(context)
        binding.rvInbox.adapter = adapter
    }

    private fun observeConversations() {
        viewModel.getInbox(currentUserId).observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> {
                    binding.loadingIndicator.visibility = View.VISIBLE
                    binding.rvInbox.visibility = View.GONE
                    binding.llEmptyState.visibility = View.GONE
                }
                is Resource.Success -> {
                    binding.loadingIndicator.visibility = View.GONE
                    val chats = resource.data
                    if (chats.isEmpty()) {
                        binding.rvInbox.visibility = View.GONE
                        binding.llEmptyState.visibility = View.VISIBLE
                    } else {
                        binding.llEmptyState.visibility = View.GONE
                        binding.rvInbox.visibility = View.VISIBLE
                        adapter.submitList(chats)
                    }
                }
                is Resource.Failure -> {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(context, "Error: ${resource.exception.message}", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
