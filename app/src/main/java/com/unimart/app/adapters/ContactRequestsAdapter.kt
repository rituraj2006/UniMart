package com.unimart.app.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.constants.RequestStatus
import com.unimart.app.databinding.ItemContactRequestBinding
import com.unimart.app.models.ContactRequest
import com.unimart.app.repositories.ProductRepository
import java.text.SimpleDateFormat
import java.util.Locale

class ContactRequestsAdapter(
    private val requests: List<ContactRequest>,
    private val onAcceptClick: (ContactRequest) -> Unit,
    private val onRejectClick: (ContactRequest) -> Unit,
    private val onWhatsAppClick: (ContactRequest) -> Unit
) : RecyclerView.Adapter<ContactRequestsAdapter.RequestViewHolder>() {

    private val productRepository = ProductRepository()
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RequestViewHolder {
        val binding = ItemContactRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RequestViewHolder, position: Int) {
        holder.bind(requests[position])
    }

    override fun getItemCount(): Int = requests.size

    inner class RequestViewHolder(private val binding: ItemContactRequestBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(request: ContactRequest) {
            binding.tvMessage.text = request.message
            
            // Format Date
            request.createdAt?.let {
                val dateStr = dateFormat.format(it.toDate())
                binding.tvProductPriceStatus.text = "Requested on $dateStr"
            }

            // Status Badge Formatting
            binding.chipStatus.text = when(request.status) {
                RequestStatus.PENDING -> "[PENDING]"
                RequestStatus.ACCEPTED -> "ACCEPTED"
                RequestStatus.REJECTED -> "REJECTED"
                RequestStatus.AUTO_REJECTED -> "PRODUCT SOLD"
                else -> request.status
            }

            // Fetch buyer info
            productRepository.getUserById(request.buyerId,
                onSuccess = { user ->
                    binding.tvBuyerName.text = user.name
                    
                    Glide.with(binding.root.context)
                        .load(user.profileImage.ifEmpty { null })
                        .placeholder(R.drawable.ic_profile_placeholder)
                        .error(R.drawable.ic_profile_placeholder)
                        .circleCrop()
                        .into(binding.ivBuyerProfile)
                },
                onFailure = {
                    binding.tvBuyerName.text = "Unknown User"
                }
            )

            // UI Actions Logic
            when (request.status) {
                RequestStatus.PENDING -> {
                    binding.layoutActions.visibility = View.VISIBLE
                    binding.btnAccept.visibility = View.VISIBLE
                    binding.btnReject.visibility = View.VISIBLE
                    binding.btnAccept.text = "Accept"
                    binding.btnAccept.setOnClickListener { onAcceptClick(request) }
                    binding.btnReject.setOnClickListener { onRejectClick(request) }
                }
                RequestStatus.ACCEPTED -> {
                    binding.layoutActions.visibility = View.VISIBLE
                    binding.btnAccept.visibility = View.VISIBLE
                    binding.btnReject.visibility = View.GONE
                    
                    // Style as green outlined button
                    binding.btnAccept.text = "Chat on WhatsApp"
                    binding.btnAccept.setIconResource(android.R.drawable.ic_menu_send)
                    binding.btnAccept.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    binding.btnAccept.setStrokeColorResource(R.color.unimart_primary)
                    binding.btnAccept.strokeWidth = (1 * binding.root.context.resources.displayMetrics.density).toInt()
                    binding.btnAccept.setTextColor(binding.root.context.getColor(R.color.unimart_primary))
                    binding.btnAccept.setIconTintResource(R.color.unimart_primary)

                    binding.btnAccept.setOnClickListener { onWhatsAppClick(request) }
                }
                else -> {
                    binding.layoutActions.visibility = View.GONE
                }
            }
        }
    }
}
