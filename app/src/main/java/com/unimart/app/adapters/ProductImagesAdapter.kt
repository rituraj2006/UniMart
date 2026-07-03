package com.unimart.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.databinding.ItemProductImageBinding

class ProductImagesAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<ProductImagesAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ItemProductImageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(imageUrls[position])
    }

    override fun getItemCount(): Int = imageUrls.size

    inner class ImageViewHolder(private val binding: ItemProductImageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(url: String) {
            Glide.with(binding.imageProduct)
                .load(url)
                .placeholder(R.drawable.ic_empty_box)
                .error(R.drawable.ic_empty_box)
                .centerCrop()
                .into(binding.imageProduct)
        }
    }
}
