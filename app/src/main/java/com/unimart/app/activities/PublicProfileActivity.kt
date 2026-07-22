package com.unimart.app.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.unimart.app.R
import com.unimart.app.databinding.ActivityPublicProfileBinding
import com.unimart.app.models.User
import com.unimart.app.utils.Resource
import com.unimart.app.viewmodels.PublicProfileViewModel

class PublicProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPublicProfileBinding
    private val viewModel: PublicProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPublicProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val userId = intent.getStringExtra("USER_ID")
        if (userId == null) {
            finish()
            return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        observeViewModel()
        viewModel.loadProfile(userId)
    }

    private fun observeViewModel() {
        viewModel.profile.observe(this) { resource ->
            when (resource) {
                is Resource.Loading -> binding.loadingIndicator.visibility = View.VISIBLE
                is Resource.Success -> {
                    binding.loadingIndicator.visibility = View.GONE
                    bindProfile(resource.data)
                }
                is Resource.Failure -> {
                    binding.loadingIndicator.visibility = View.GONE
                    Toast.makeText(this, "Profile not found", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        viewModel.listingsCount.observe(this) { count ->
            binding.tvActiveListingsCount.text = count.toString()
        }
    }

    private fun bindProfile(user: User) {
        binding.tvUserName.text = user.name
        Glide.with(this)
            .load(user.profileImage.ifEmpty { null })
            .placeholder(R.drawable.ic_profile_placeholder)
            .circleCrop()
            .into(binding.ivProfileImage)
    }
}
