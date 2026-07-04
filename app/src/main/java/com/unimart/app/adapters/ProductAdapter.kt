package com.unimart.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.databinding.ItemProductBinding
import com.unimart.app.models.Product

class ProductAdapter(
    private var products: List<Product>,
    private var wishlistedIds: Set<String> = emptySet(),
    private val onProductClick: (Product) -> Unit,
    private val onWishlistClick: (Product, Boolean) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    fun updateProducts(newList: List<Product>) {
        this.products = newList
        notifyDataSetChanged()
    }

    fun updateWishlist(newIds: Set<String>) {
        this.wishlistedIds = newIds
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(products[position])
    }

    override fun getItemCount(): Int = products.size

    inner class ProductViewHolder(private val binding: ItemProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: Product) {
            binding.tvProductTitle.text = product.title
            
            val price = product.price
            binding.tvProductPrice.text = if (price == price.toLong().toDouble()) {
                String.format(java.util.Locale.getDefault(), "₹%d", price.toLong())
            } else {
                String.format(java.util.Locale.getDefault(), "₹%.2f", price)
            }

            binding.tvProductCategory.text = product.category
            binding.tvProductCondition.text = product.condition

            // Handle Wishlist State
            val isSaved = wishlistedIds.contains(product.productId)
            binding.ivWishlist.setImageResource(
                if (isSaved) R.drawable.ic_wishlist_filled else R.drawable.ic_wishlist_outline
            )

            binding.ivWishlist.setOnClickListener {
                animateHeart()
                onWishlistClick(product, isSaved)
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

        private fun animateHeart() {
            binding.ivWishlist.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(100)
                .setInterpolator(OvershootInterpolator())
                .withEndAction {
                    binding.ivWishlist.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }
}
