package com.unimart.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.constants.ProductStatus
import com.unimart.app.databinding.ItemMyListingBinding
import com.unimart.app.models.Product

class MyListingsAdapter(
    private val products: List<Product>,
    private val onProductClick: (Product) -> Unit
) : RecyclerView.Adapter<MyListingsAdapter.MyListingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyListingViewHolder {
        val binding = ItemMyListingBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MyListingViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MyListingViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    inner class MyListingViewHolder(private val binding: ItemMyListingBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.tvProductTitle.text = product.title
            
            // Format price to hide .00 decimals
            val price = product.price
            binding.tvProductPrice.text = if (price == price.toLong().toDouble()) {
                String.format(java.util.Locale.getDefault(), "₹%d", price.toLong())
            } else {
                String.format(java.util.Locale.getDefault(), "₹%.2f", price)
            }

            binding.chipStatus.text = product.status

            if (product.status == ProductStatus.SOLD) {
                // Using new theme colors
                binding.chipStatus.setChipBackgroundColorResource(R.color.unimart_surface_elevated)
                binding.chipStatus.setTextColor(binding.root.context.getColor(R.color.unimart_text_secondary))
            } else {
                binding.chipStatus.setChipBackgroundColorResource(R.color.unimart_primary)
                binding.chipStatus.setTextColor(binding.root.context.getColor(R.color.white))
            }

            val imageUrl = product.imageUrls.firstOrNull()
            Glide.with(binding.root.context)
                .load(imageUrl)
                .placeholder(R.drawable.ic_empty_box)
                .error(R.drawable.ic_empty_box)
                .centerCrop()
                .into(binding.ivProductImage)

            binding.root.setOnClickListener {
                onProductClick(product)
            }
        }
    }
}
