package com.unimart.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.databinding.ItemInboxChatBinding
import com.unimart.app.models.Chat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MessagesAdapter(
    private val currentUserId: String,
    private val onChatClick: (Chat) -> Unit
) : ListAdapter<Chat, MessagesAdapter.ViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemInboxChatBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemInboxChatBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(chat: Chat) {
            val isBuyer = currentUserId == chat.buyerId
            var otherUserName = if (isBuyer) chat.sellerName else chat.buyerName
            var otherUserImage = if (isBuyer) chat.sellerImage else chat.buyerImage
            val otherUserId = if (isBuyer) chat.sellerId else chat.buyerId

            binding.tvOtherUserName.text = otherUserName.ifEmpty { "User" }
            
            // Set initial Title + Price from snapshot
            updateTitleAndPrice(chat.title, chat.price)
            
            binding.tvLastMessage.text = chat.lastMessagePreview.ifEmpty { "Start a conversation" }
            binding.tvTime.text = formatTimestamp(chat.lastTimestamp)

            // Unread Badge Logic
            val unreadCount = chat.unreadCounts[currentUserId] ?: 0
            if (unreadCount > 0) {
                binding.tvUnreadBadge.text = if (unreadCount > 99) "99+" else unreadCount.toString()
                binding.tvUnreadBadge.visibility = View.VISIBLE
                binding.tvLastMessage.setTextColor(binding.root.context.getColor(R.color.unimart_primary))
                binding.tvLastMessage.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                binding.tvUnreadBadge.visibility = View.GONE
                binding.tvLastMessage.setTextColor(binding.root.context.getColor(R.color.unimart_text_secondary))
                binding.tvLastMessage.setTypeface(null, android.graphics.Typeface.NORMAL)
            }

            Glide.with(binding.root.context)
                .load(chat.thumbnail)
                .placeholder(R.drawable.ic_search)
                .into(binding.ivProductThumbnail)

            Glide.with(binding.root.context)
                .load(otherUserImage.ifEmpty { null })
                .placeholder(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.ivOtherUserImage)

            // --- Real-time Data Sync for Accuracy ---
            val productRepo = com.unimart.app.repositories.ProductRepository()
            
            // 1. Fetch live other user info if name is missing or for total accuracy
            productRepo.getUserById(otherUserId,
                onSuccess = { user ->
                    binding.tvOtherUserName.text = user.name
                    Glide.with(binding.root.context)
                        .load(user.profileImage.ifEmpty { null })
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .into(binding.ivOtherUserImage)
                },
                onFailure = { }
            )

            // 2. Fetch live product info to fix the "price shows 0" issue
            productRepo.getProductById(chat.productId,
                onSuccess = { product ->
                    updateTitleAndPrice(product.title, product.price)
                    Glide.with(binding.root.context)
                        .load(product.imageUrls.firstOrNull())
                        .placeholder(R.drawable.ic_search)
                        .into(binding.ivProductThumbnail)
                },
                onFailure = { }
            )

            binding.root.setOnClickListener { onChatClick(chat) }
        }

        private fun updateTitleAndPrice(title: String, price: Double) {
            val priceStr = if (price == price.toLong().toDouble()) "₹${price.toLong()}" else "₹$price"
            binding.tvProductTitle.text = "$title • $priceStr"
        }

        private fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
            val date = timestamp.toDate()
            val now = Calendar.getInstance()
            val chatDate = Calendar.getInstance().apply { time = date }

            return when {
                isSameDay(now, chatDate) -> {
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)
                }
                isYesterday(now, chatDate) -> {
                    "Yesterday"
                }
                else -> {
                    SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(date)
                }
            }
        }

        private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
            return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                    cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
        }

        private fun isYesterday(now: Calendar, chatDate: Calendar): Boolean {
            val yesterday = now.clone() as Calendar
            yesterday.add(Calendar.DAY_OF_YEAR, -1)
            return isSameDay(yesterday, chatDate)
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Chat>() {
        override fun areItemsTheSame(oldItem: Chat, newItem: Chat): Boolean =
            oldItem.chatId == newItem.chatId

        override fun areContentsTheSame(oldItem: Chat, newItem: Chat): Boolean =
            oldItem == newItem
    }
}
