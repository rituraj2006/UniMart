package com.unimart.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.databinding.ItemProductRequestOverviewBinding
import com.unimart.app.models.ProductWithRequestCount
import java.util.Locale

class ProductRequestsOverviewAdapter(
    private val products: List<ProductWithRequestCount>,
    private val onProductClick: (ProductWithRequestCount) -> Unit
) : RecyclerView.Adapter<ProductRequestsOverviewAdapter.OverviewViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OverviewViewHolder {
        val binding = ItemProductRequestOverviewBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OverviewViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OverviewViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    inner class OverviewViewHolder(private val binding: ItemProductRequestOverviewBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ProductWithRequestCount) {
            val product = item.product
            binding.tvProductTitle.text = product.title
            
            val priceStr = if (product.price == product.price.toLong().toDouble()) {
                String.format(Locale.getDefault(), "₹%d", product.price.toLong())
            } else {
                String.format(Locale.getDefault(), "₹%.2f", product.price)
            }
            binding.tvProductPrice.text = priceStr

            binding.chipStatus.text = product.status
            
            val context = binding.root.context
            if (item.pendingCount > 0) {
                binding.tvRequestCount.text = context.getString(R.string.request_pending_badge, item.pendingCount)
                binding.tvRequestCount.setTextColor(context.getColor(R.color.unimart_warning))
            } else {
                binding.tvRequestCount.text = context.getString(R.string.all_requests_handled)
                binding.tvRequestCount.setTextColor(context.getColor(R.color.unimart_primary))
            }

            val imageUrl = product.imageUrls.firstOrNull()
            Glide.with(binding.root.context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_empty_box)
                .error(R.drawable.ic_empty_box)
                .centerCrop()
                .into(binding.ivProductImage)

            binding.root.setOnClickListener {
                onProductClick(item)
            }
        }
    }
}
