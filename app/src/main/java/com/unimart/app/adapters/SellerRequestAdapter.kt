package com.unimart.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.databinding.ItemSellerRequestBinding
import com.unimart.app.models.ChatRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class SellerRequestAdapter(
    private val requests: List<ChatRequest>,
    private val onAcceptClick: (ChatRequest) -> Unit,
    private val onRejectClick: (ChatRequest) -> Unit
) : RecyclerView.Adapter<SellerRequestAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemSellerRequestBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount() = requests.size

    inner class ViewHolder(private val binding: ItemSellerRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: ChatRequest) {
            binding.tvBuyerName.text = request.buyerName.ifEmpty { "Unknown User" }
            binding.tvProductTitle.text = request.productTitle
            binding.tvProductPrice.text = "₹${request.productPrice.toInt()}"
            binding.tvTime.text = formatTimestamp(request.createdAt)

            // Buyer Profile Image
            Glide.with(binding.root.context)
                .load(request.buyerImage.ifEmpty { null })
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .circleCrop()
                .into(binding.ivBuyerImage)

            // Product Thumbnail
            Glide.with(binding.root.context)
                .load(request.productImage.ifEmpty { null })
                .placeholder(R.drawable.ic_search)
                .into(binding.ivProductImage)

            // Fetch live data to ensure accuracy (as per requirements)
            val productRepo = com.unimart.app.repositories.ProductRepository()
            
            // 1. Live User Data
            productRepo.getUserById(request.buyerId,
                onSuccess = { user ->
                    binding.tvBuyerName.text = user.name
                    Glide.with(binding.root.context)
                        .load(user.profileImage.ifEmpty { null })
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .into(binding.ivBuyerImage)
                },
                onFailure = { }
            )

            // 2. Live Product Data (Fix for "price shows 0")
            productRepo.getProductById(request.productId,
                onSuccess = { product ->
                    val priceStr = if (product.price == product.price.toLong().toDouble()) "₹${product.price.toLong()}" else "₹${product.price}"
                    binding.tvProductPrice.text = priceStr
                    binding.tvProductTitle.text = product.title
                    
                    Glide.with(binding.root.context)
                        .load(product.imageUrls.firstOrNull())
                        .placeholder(R.drawable.ic_search)
                        .into(binding.ivProductImage)
                },
                onFailure = {
                    // Fallback to data stored in request if product fetch fails
                    val priceStr = if (request.productPrice == request.productPrice.toLong().toDouble()) "₹${request.productPrice.toLong()}" else "₹${request.productPrice}"
                    binding.tvProductPrice.text = priceStr
                }
            )

            binding.btnAccept.setOnClickListener { onAcceptClick(request) }
            binding.btnReject.setOnClickListener { onRejectClick(request) }
        }

        private fun formatTimestamp(timestamp: com.google.firebase.Timestamp): String {
            val date = timestamp.toDate()
            val now = Calendar.getInstance()
            val chatDate = Calendar.getInstance().apply { time = date }

            val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(date)

            return when {
                isSameDay(now, chatDate) -> "Today • $timeStr"
                isYesterday(now, chatDate) -> "Yesterday • $timeStr"
                else -> SimpleDateFormat("MMM dd • h:mm a", Locale.getDefault()).format(date)
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
}
