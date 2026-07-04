package com.unimart.app.fragments

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.unimart.app.R
import com.unimart.app.activities.LoginActivity
import com.unimart.app.activities.ProductDetailsActivity
import com.unimart.app.adapters.MyListingsAdapter
import com.unimart.app.databinding.FragmentProfileBinding
import com.unimart.app.models.Product
import com.unimart.app.network.CloudinaryRepository
import com.unimart.app.repositories.ContactRepository
import com.unimart.app.repositories.ProductRepository
import com.unimart.app.repositories.WishlistRepository
import com.unimart.app.viewmodels.ProfileViewModel
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProfileViewModel
    private val repository = ProductRepository()
    private val contactRepository = ContactRepository()
    private val wishlistRepository = WishlistRepository()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var cloudinaryRepository: CloudinaryRepository

    private val myListings = mutableListOf<Product>()
    private lateinit var adapter: MyListingsAdapter

    private val pickMedia = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            uploadProfileImage(uri)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(this)[ProfileViewModel::class.java]

        cloudinaryRepository = CloudinaryRepository(requireContext())

        // Handle Window Insets
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.headerLayout.updatePadding(top = systemBars.top)
            insets
        }

        observeViewModel()
        setupRecyclerView()
        loadUserProfile()
        loadStatistics()
        loadMyListings()

        binding.ivProfileImage.setOnClickListener {
            showImageOptions()
        }

        binding.cardRequests.setOnClickListener {
            val intent = Intent(requireContext(), com.unimart.app.activities.ContactRequestsActivity::class.java)
            startActivity(intent)
        }

        binding.tvWishlistCount.setOnClickListener {
            startActivity(Intent(requireContext(), com.unimart.app.activities.WishlistActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            logoutUser()
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.nestedScrollView.visibility = if (isLoading) View.GONE else View.VISIBLE
        }
    }

    private fun setupRecyclerView() {
        adapter = MyListingsAdapter(myListings) { product ->
            val intent = Intent(context, ProductDetailsActivity::class.java)
            intent.putExtra("PRODUCT_ID", product.productId)
            startActivity(intent)
        }
        binding.rvMyListings.layoutManager = LinearLayoutManager(context)
        binding.rvMyListings.adapter = adapter
    }

    private fun showImageOptions() {
        ProfileImageBottomSheet(
            onChangePhoto = {
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemovePhoto = {
                removeProfileImage()
            }
        ).show(childFragmentManager, ProfileImageBottomSheet.TAG)
    }

    private fun uploadProfileImage(uri: Uri) {
        setLoading(true)
        lifecycleScope.launch {
            val secureUrl = cloudinaryRepository.uploadImage(uri)
            if (secureUrl != null) {
                repository.updateProfileImage(secureUrl,
                    onSuccess = {
                        setLoading(false)
                        loadUserProfile() // Refresh UI
                        showSnackbar("Profile updated successfully")
                    },
                    onFailure = {
                        setLoading(false)
                        showSnackbar("Failed to update Firestore")
                    }
                )
            } else {
                setLoading(false)
                showSnackbar("Failed to upload to Cloudinary. Check internet.")
            }
        }
    }

    private fun removeProfileImage() {
        setLoading(true)
        repository.updateProfileImage("",
            onSuccess = {
                setLoading(false)
                loadUserProfile()
                showSnackbar("Profile image removed")
            },
            onFailure = {
                setLoading(false)
                showSnackbar("Failed to remove image")
            }
        )
    }

    private fun setLoading(isLoading: Boolean) {
        if (_binding == null) return
        binding.pbProfileImage.visibility = if (isLoading) View.VISIBLE else View.GONE
        binding.ivProfileImage.isEnabled = !isLoading
    }

    private fun showSnackbar(message: String) {
        view?.let { Snackbar.make(it, message, Snackbar.LENGTH_SHORT).show() }
    }

    private fun loadUserProfile() {
        viewModel.setLoading(true)
        repository.getCurrentUser(
            onSuccess = { user ->
                viewModel.setLoading(false)
                if (_binding == null) return@getCurrentUser
                binding.tvUserName.text = user.name
                binding.tvUserEmail.text = user.email
                binding.tvUserWhatsApp.text = user.whatsappNumber

                Glide.with(this)
                    .load(user.profileImage.ifEmpty { null })
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .error(R.drawable.ic_profile_placeholder)
                    .circleCrop()
                    .into(binding.ivProfileImage)
            },
            onFailure = {
                viewModel.setLoading(false)
                context?.let {
                    Toast.makeText(it, "Failed to load profile", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun loadStatistics() {
        repository.getSellingCount(
            onSuccess = { count ->
                if (_binding == null) return@getSellingCount
                binding.tvSellingCount.text = count.toString()
            },
            onFailure = { /* Silent fail */ }
        )

        repository.getSoldCount(
            onSuccess = { count ->
                if (_binding == null) return@getSoldCount
                binding.tvSoldCount.text = count.toString()
            },
            onFailure = { /* Silent fail */ }
        )

        wishlistRepository.getWishlistedProductIds(
            onSuccess = { ids ->
                if (_binding == null) return@getWishlistedProductIds
                binding.tvWishlistCount.text = ids.size.toString()
            },
            onFailure = { /* Silent fail */ }
        )

        val uid = auth.currentUser?.uid
        if (uid != null) {
            contactRepository.getPendingRequestsCount(uid,
                onSuccess = { count ->
                    if (_binding == null) return@getPendingRequestsCount
                    if (count > 0) {
                        binding.tvPendingBadge.text = count.toString()
                        binding.tvPendingBadge.visibility = View.VISIBLE
                    } else {
                        binding.tvPendingBadge.visibility = View.GONE
                    }
                },
                onFailure = { /* Silent fail */ }
            )
        }
    }

    private fun loadMyListings() {
        repository.getMyListings(
            onSuccess = { products ->
                if (_binding == null) return@getMyListings
                myListings.clear()
                myListings.addAll(products)
                adapter.notifyDataSetChanged()
            },
            onFailure = {
                context?.let {
                    Toast.makeText(it, "Failed to load your listings", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
