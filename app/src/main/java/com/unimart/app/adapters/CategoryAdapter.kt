package com.unimart.app.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.unimart.app.R
import com.unimart.app.databinding.ItemCategoryBinding
import com.unimart.app.models.Category

class CategoryAdapter(
    private val categories: List<Category>,
    private val onCategoryClick: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder>() {

    private var selectedPosition = -1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoryViewHolder {
        val binding = ItemCategoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CategoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CategoryViewHolder, position: Int) {
        val category = categories[position]
        holder.bind(category, position == selectedPosition)

        holder.itemView.setOnClickListener {
            val previousPosition = selectedPosition
            selectedPosition = if (selectedPosition == position) -1 else position
            
            notifyItemChanged(previousPosition)
            notifyItemChanged(selectedPosition)
            
            // If deselected, passing "All" logic is handled in ViewModel or by passing null
            if (selectedPosition == -1) {
                onCategoryClick(Category("All", 0))
            } else {
                onCategoryClick(category)
            }
        }
    }

    override fun getItemCount(): Int = categories.size

    class CategoryViewHolder(private val binding: ItemCategoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(category: Category, isSelected: Boolean) {
            binding.tvCategoryName.text = category.name
            binding.ivCategoryIcon.setImageResource(category.iconResId)

            if (isSelected) {
                binding.root.setStrokeColor(binding.root.context.getColorStateList(R.color.unimart_primary))
                binding.root.strokeWidth = 2.coerceAtLeast(1) // Visual indicator
            } else {
                binding.root.strokeWidth = 0
            }
        }
    }
}
