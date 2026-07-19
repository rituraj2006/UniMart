package com.unimart.app.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.unimart.app.R
import com.unimart.app.adapters.ChatMessagesAdapter
import com.unimart.app.constants.MessageType
import com.unimart.app.constants.PhoneSharingStatus
import com.unimart.app.databinding.ActivityChatBinding
import com.unimart.app.models.Message
import com.unimart.app.network.CloudinaryRepository
import com.unimart.app.utils.ChatSessionManager
import com.unimart.app.utils.Resource
import com.unimart.app.viewmodels.ChatViewModel

class ChatActivity : AppCompatActivity() {

    private lateinit var binding: ActivityChatBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var cloudinaryRepository: CloudinaryRepository
    
    private lateinit var adapter: ChatMessagesAdapter
    private val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    
    private var chatId: String? = null
    private var otherUserName: String? = null
    private var productTitle: String? = null
    private var productThumbnail: String? = null

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            chatId?.let { id ->
                viewModel.uploadImageAndSendMessage(id, currentUserId, uri, cloudinaryRepository)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cloudinaryRepository = CloudinaryRepository(this)

        chatId = intent.getStringExtra("CHAT_ID")
        otherUserName = intent.getStringExtra("OTHER_USER_NAME")
        productTitle = intent.getStringExtra("PRODUCT_TITLE")
        productThumbnail = intent.getStringExtra("PRODUCT_THUMBNAIL")

        if (chatId == null) {
            finish()
            return
        }

        handleWindowInsets()
        setupUI()
        setupRecyclerView()
        observeMessages()
        observeSendStatus()
        observeChatStatus()
        
        viewModel.setChatActive(chatId!!, currentUserId, true)
        viewModel.markAsRead(chatId!!, currentUserId)
    }

    override fun onResume() {
        super.onResume()
        ChatSessionManager.activeChatId = chatId
        chatId?.let { viewModel.setChatActive(it, currentUserId, true) }
        chatId?.let { viewModel.markAsRead(it, currentUserId) }
    }

    override fun onPause() {
        super.onPause()
        ChatSessionManager.activeChatId = null
        chatId?.let { viewModel.setChatActive(it, currentUserId, false) }
    }

    private fun handleWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            binding.appBar.updatePadding(top = systemBars.top)
            
            val isKeyboardVisible = ime.bottom > 0
            val bottomInset = if (isKeyboardVisible) ime.bottom else systemBars.bottom
            binding.inputArea.updatePadding(bottom = bottomInset)
            
            if (isKeyboardVisible && adapter.itemCount > 0) {
                binding.rvMessages.post {
                    binding.rvMessages.scrollToPosition(adapter.itemCount - 1)
                }
            }
            
            insets
        }
    }

    private fun setupUI() {
        binding.tvOtherUserName.text = otherUserName
        binding.tvProductTitle.text = productTitle
        
        Glide.with(this)
            .load(productThumbnail)
            .placeholder(R.drawable.ic_search)
            .into(binding.ivProductThumbnail)

        binding.ivBack.setOnClickListener { finish() }

        binding.ivAttachImage.setOnClickListener {
            pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        
        binding.btnSend.setOnClickListener {
            val content = binding.etMessage.text.toString().trim()
            if (content.isNotEmpty()) {
                sendMessage(content)
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ChatMessagesAdapter(currentUserId) { imageUrl ->
            val intent = Intent(this, FullScreenImageActivity::class.java)
            intent.putExtra("IMAGE_URL", imageUrl)
            startActivity(intent)
        }
        val layoutManager = LinearLayoutManager(this)
        layoutManager.stackFromEnd = true
        binding.rvMessages.layoutManager = layoutManager
        binding.rvMessages.adapter = adapter
    }

    private fun observeMessages() {
        viewModel.getMessages(chatId!!).observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> {}
                is Resource.Success -> {
                    val messages = resource.data
                    binding.llEmptyChat.visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
                    adapter.submitList(messages) {
                        if (messages.isNotEmpty()) {
                            binding.rvMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }
                is Resource.Failure -> {
                    Toast.makeText(this, "Failed to load messages", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }
    }

    private fun observeSendStatus() {
        viewModel.sendMessageStatus.observe(this) { resource ->
            if (resource is Resource.Failure) {
                Toast.makeText(this, resource.exception.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun observeChatStatus() {
        chatId?.let { id ->
            com.unimart.app.utils.FirestoreHelper.getChatsCollection().document(id)
                .addSnapshotListener { snapshot, _ ->
                    val chat = snapshot?.toObject(com.unimart.app.models.Chat::class.java) ?: return@addSnapshotListener
                    
                    if (chat.productStatus == "SOLD") {
                        binding.inputArea.visibility = View.GONE
                    } else {
                        binding.inputArea.visibility = View.VISIBLE
                    }

                    updateWhatsAppUI(chat)
                }
        }
    }

    private fun updateWhatsAppUI(chat: com.unimart.app.models.Chat) {
        val isSeller = currentUserId == chat.sellerId
        val status = chat.phoneSharingStatus

        binding.cardWhatsAppBanner.visibility = View.VISIBLE
        binding.ivWhatsAppReject.visibility = View.GONE

        when (status) {
            PhoneSharingStatus.NONE, 
            PhoneSharingStatus.REJECTED -> {
                if (!isSeller) {
                    binding.tvWhatsAppStatus.text = "Connect on WhatsApp for faster replies?"
                    binding.btnWhatsAppAction.text = "Request WhatsApp"
                    binding.btnWhatsAppAction.visibility = View.VISIBLE
                    binding.btnWhatsAppAction.setOnClickListener {
                        confirmWhatsAppRequest()
                    }
                } else {
                    binding.cardWhatsAppBanner.visibility = View.GONE
                }
            }
            PhoneSharingStatus.PENDING -> {
                if (isSeller) {
                    binding.tvWhatsAppStatus.text = "Buyer wants to continue on WhatsApp."
                    binding.btnWhatsAppAction.text = "Approve"
                    binding.btnWhatsAppAction.visibility = View.VISIBLE
                    binding.ivWhatsAppReject.visibility = View.VISIBLE
                    
                    binding.btnWhatsAppAction.setOnClickListener {
                        viewModel.updatePhoneSharing(chat.chatId, PhoneSharingStatus.APPROVED)
                    }
                    binding.ivWhatsAppReject.setOnClickListener {
                        viewModel.updatePhoneSharing(chat.chatId, PhoneSharingStatus.REJECTED)
                    }
                } else {
                    binding.tvWhatsAppStatus.text = "WhatsApp request sent. Waiting for seller..."
                    binding.btnWhatsAppAction.visibility = View.GONE
                }
            }
            PhoneSharingStatus.APPROVED -> {
                binding.tvWhatsAppStatus.text = "WhatsApp contact shared successfully."
                binding.btnWhatsAppAction.text = "Open WhatsApp"
                binding.btnWhatsAppAction.visibility = View.VISIBLE
                binding.btnWhatsAppAction.setOnClickListener {
                    openWhatsAppConversation(chat.sellerId)
                }
            }
        }
    }

    private fun confirmWhatsAppRequest() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Request WhatsApp Contact?")
            .setMessage("This will notify the seller that you'd like to continue the conversation on WhatsApp.")
            .setPositiveButton("Request") { _, _ ->
                chatId?.let { id ->
                    viewModel.updatePhoneSharing(id, PhoneSharingStatus.PENDING)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openWhatsAppConversation(sellerId: String) {
        val productRepo = com.unimart.app.repositories.ProductRepository()
        productRepo.getUserById(sellerId,
            onSuccess = { user ->
                val phoneNumber = user.whatsappNumber
                if (phoneNumber.isNotEmpty()) {
                    val cleanNumber = phoneNumber.replace("+", "").replace(" ", "")
                    val message = "Hi ${user.name}, we're chatting on UniMart about \"$productTitle\"."
                    val encodedMessage = Uri.encode(message)
                    val url = "https://wa.me/$cleanNumber?text=$encodedMessage"
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "WhatsApp is not installed.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Seller hasn't provided a phone number.", Toast.LENGTH_SHORT).show()
                }
            },
            onFailure = {
                Toast.makeText(this, "Failed to retrieve seller contact.", Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun sendMessage(content: String) {
        val message = Message(
            senderId = currentUserId,
            type = MessageType.TEXT,
            content = content,
            timestamp = com.google.firebase.Timestamp.now()
        )
        binding.etMessage.text.clear()
        viewModel.sendMessage(chatId!!, message)
    }
}
