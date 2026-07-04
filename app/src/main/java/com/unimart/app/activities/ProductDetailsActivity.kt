package com.unimart.app.activities

import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.animation.OvershootInterpolator
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayoutMediator
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.unimart.app.R
import com.unimart.app.adapters.ProductImagesAdapter
import com.unimart.app.constants.ProductStatus
import com.unimart.app.constants.RequestStatus
import com.unimart.app.databinding.ActivityProductDetailsBinding
import com.unimart.app.models.ContactRequest
import com.unimart.app.models.Product
import com.unimart.app.models.User
import com.unimart.app.repositories.ContactRepository
import com.unimart.app.repositories.ProductRepository
import com.unimart.app.repositories.WishlistRepository
import java.util.Calendar
import java.util.Locale

class ProductDetailsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProductDetailsBinding
    private lateinit var viewModel: com.unimart.app.viewmodels.ProductDetailsViewModel
    private val productRepository = ProductRepository()
    private val contactRepository = ContactRepository()
    private val wishlistRepository = WishlistRepository()
    
    private var currentProduct: Product? = null
    private var sellerUser: User? = null
    private var currentRequest: ContactRequest? = null
    private var isWishlisted: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProductDetailsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        viewModel = ViewModelProvider(this)[com.unimart.app.viewmodels.ProductDetailsViewModel::class.java]

        observeViewModel()

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.appBarLayout.updatePadding(top = systemBars.top)
            binding.bottomActionLayout.updatePadding(bottom = systemBars.bottom)
            insets
        }

        val productId = intent.getStringExtra("PRODUCT_ID")
        if (productId.isNullOrEmpty()) {
            finish()
            return
        }

        setupToolbar()
        loadProductDetails(productId)
        checkWishlistStatus(productId)

        binding.ivWishlist.setOnClickListener {
            animateHeart()
            toggleWishlist(productId)
        }
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            binding.loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.nestedScrollView.visibility = if (isLoading) View.GONE else View.VISIBLE
            binding.bottomActionLayout.visibility = if (isLoading) View.GONE else {
                // Keep hidden if user is seller
                if (FirebaseAuth.getInstance().currentUser?.uid == currentProduct?.sellerId) View.GONE else View.VISIBLE
            }
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
    }

    private fun checkWishlistStatus(productId: String) {
        wishlistRepository.getWishlistedProductIds(
            onSuccess = { ids ->
                isWishlisted = ids.contains(productId)
                updateWishlistIcon()
            },
            onFailure = { }
        )
    }

    private fun updateWishlistIcon() {
        binding.ivWishlist.setImageResource(
            if (isWishlisted) R.drawable.ic_wishlist_filled else R.drawable.ic_wishlist_outline
        )
    }

    private fun toggleWishlist(productId: String) {
        wishlistRepository.toggleWishlist(productId, isWishlisted,
            onSuccess = { newState ->
                isWishlisted = newState
                updateWishlistIcon()
            },
            onFailure = {
                Toast.makeText(this, "Failed to update wishlist", Toast.LENGTH_SHORT).show()
            }
        )
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

    private fun loadProductDetails(productId: String) {
        viewModel.setLoading(true)
        productRepository.getProductById(productId,
            onSuccess = { product ->
                viewModel.setLoading(false)
                if (isFinishing) return@getProductById
                currentProduct = product
                populateUI(product)
                checkContactRequestStatus()
                invalidateOptionsMenu()
            },
            onFailure = {
                viewModel.setLoading(false)
                if (isFinishing) return@getProductById
                Toast.makeText(this, "Failed to load product", Toast.LENGTH_SHORT).show()
            },
        )
    }

    private fun checkContactRequestStatus() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val product = currentProduct ?: return
        
        if (currentUser.uid == product.sellerId) {
            binding.bottomActionLayout.visibility = View.GONE
            return
        }

        if (product.status == ProductStatus.SOLD) {
            binding.btnContactSeller.isEnabled = false
            binding.btnContactSeller.text = "Sold Out"
            return
        }

        binding.btnContactSeller.isEnabled = false
        contactRepository.getBuyerRequestForProduct(currentUser.uid, product.productId,
            onSuccess = { request ->
                if (isFinishing) return@getBuyerRequestForProduct
                currentRequest = request
                updateContactButtonUI()
            },
            onFailure = {
                if (isFinishing) return@getBuyerRequestForProduct
                Toast.makeText(this, "Something went wrong.", Toast.LENGTH_SHORT).show()
                binding.btnContactSeller.isEnabled = true
            }
        )
    }

    private fun updateContactButtonUI() {
        val request = currentRequest
        if (request == null) {
            binding.btnContactSeller.text = "Contact Seller"
            binding.btnContactSeller.isEnabled = true
            binding.btnContactSeller.setOnClickListener { showRequestContactDialog() }
        } else {
            when (request.status) {
                RequestStatus.PENDING -> {
                    binding.btnContactSeller.text = "Waiting for Approval"
                    binding.btnContactSeller.isEnabled = false
                }
                RequestStatus.ACCEPTED -> {
                    binding.btnContactSeller.text = "Chat on WhatsApp"
                    binding.btnContactSeller.isEnabled = true
                    binding.btnContactSeller.setOnClickListener { openWhatsApp() }
                }
                RequestStatus.REJECTED -> {
                    binding.btnContactSeller.text = "Request Rejected"
                    binding.btnContactSeller.isEnabled = false
                }
                RequestStatus.AUTO_REJECTED -> {
                    binding.btnContactSeller.text = "Product Sold"
                    binding.btnContactSeller.isEnabled = false
                }
            }
        }
    }

    private fun showRequestContactDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_request_contact, null)
        val etMessage = dialogView.findViewById<TextInputEditText>(R.id.etRequestMessage)
        etMessage.setText("Hi, I'm interested in your product. Is it still available?")

        AlertDialog.Builder(this)
            .setTitle("Contact Seller")
            .setView(dialogView)
            .setPositiveButton("Send Request") { _: DialogInterface, _: Int ->
                sendContactRequest(etMessage.text.toString().trim())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendContactRequest(message: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val product = currentProduct ?: return
        binding.btnContactSeller.isEnabled = false

        contactRepository.createContactRequest(currentUser.uid, product.sellerId, product.productId, message,
            onSuccess = {
                Snackbar.make(binding.root, "Request sent successfully.", Snackbar.LENGTH_SHORT).show()
                checkContactRequestStatus()
            },
            onFailure = {
                Toast.makeText(this, "Something went wrong.", Toast.LENGTH_SHORT).show()
                binding.btnContactSeller.isEnabled = true
            }
        )
    }

    private fun openWhatsApp() {
        val seller = sellerUser ?: return
        val product = currentProduct ?: return
        val phoneNumber = seller.whatsappNumber.ifEmpty { return Toast.makeText(this, "No number provided", Toast.LENGTH_SHORT).show() }
        val cleanNumber = phoneNumber.replace("+", "").replace(" ", "")
        val message = "Hi ${seller.name}, I'm interested in your product \"${product.title}\" on UniMart."
        val url = "https://wa.me/$cleanNumber?text=${java.net.URLEncoder.encode(message, "UTF-8")}"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun populateUI(product: Product) {
        binding.tvTitle.text = product.title
        val price = product.price
        binding.tvPrice.text = if (price == price.toLong().toDouble()) String.format(Locale.getDefault(), "₹%d", price.toLong()) else String.format(Locale.getDefault(), "₹%.2f", price)
        binding.tvDescription.text = product.description
        binding.chipCategory.text = product.category
        binding.chipCondition.text = product.condition
        binding.chipListingType.text = product.listingType
        if (product.listingType == "EXCHANGE") {
            binding.layoutLookingFor.visibility = View.VISIBLE
            binding.tvLookingForDescription.text = product.lookingFor
        } else {
            binding.layoutLookingFor.visibility = View.GONE
        }
        loadSellerInfo(product.sellerId)
        setupImageSlider(product.imageUrls)
    }

    private fun loadSellerInfo(sellerId: String) {
        productRepository.getUserById(sellerId,
            onSuccess = { user ->
                if (isFinishing) return@getUserById
                sellerUser = user
                binding.tvSellerName.text = user.name
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = user.joinedDate
                binding.tvMemberSince.text = "Member since ${calendar.get(Calendar.YEAR)}"
                Glide.with(this).load(user.profileImage.ifEmpty { null }).placeholder(R.drawable.ic_profile_placeholder).error(R.drawable.ic_profile_placeholder).circleCrop().into(binding.ivSellerProfile)
            },
            onFailure = { binding.tvSellerName.text = getString(R.string.campus_seller) }
        )
    }

    private fun setupImageSlider(imageUrls: List<String>) {
        binding.viewPagerImages.adapter = ProductImagesAdapter(imageUrls)
        TabLayoutMediator(binding.tabLayoutIndicators, binding.viewPagerImages) { _, _ -> }.attach()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        currentProduct?.let { if (it.sellerId == FirebaseAuth.getInstance().currentUser?.uid) { menuInflater.inflate(R.menu.product_details_menu, menu); if (it.status == ProductStatus.SOLD) menu?.findItem(R.id.menuSold)?.isVisible = false; return true } }
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menuShare -> shareProduct()
            R.id.menuEdit -> editProduct()
            R.id.menuSold -> confirmMarkAsSold()
            R.id.menuDelete -> confirmDelete()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun shareProduct() {
        currentProduct?.let { product ->
            val shareText = "Check out this product on UniMart!\n\nTitle: ${product.title}\nPrice: ₹${product.price}\nDescription: ${product.description}"
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, shareText) }, "Share Product"))
        }
    }

    private fun editProduct() {
        currentProduct?.let { startActivity(Intent(this, SellProductActivity::class.java).apply { putExtra("PRODUCT_ID", it.productId); putExtra("IS_EDIT_MODE", true) }) }
    }

    private fun confirmMarkAsSold() {
        AlertDialog.Builder(this).setTitle("Mark as Sold").setMessage("Confirm mark as sold?").setPositiveButton("Yes") { _, _ -> markAsSold() }.setNegativeButton("No", null).show()
    }

    private fun markAsSold() {
        currentProduct?.let { productRepository.markProductAsSold(it.productId, onSuccess = { contactRepository.autoRejectPendingRequests(it.productId, onSuccess = { finish() }, onFailure = { finish() }) }, onFailure = { }) }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this).setTitle("Delete Product").setMessage("Confirm delete?").setPositiveButton("Delete") { _, _ -> deleteProduct() }.setNegativeButton("Cancel", null).show()
    }

    private fun deleteProduct() {
        currentProduct?.let { productRepository.deleteProduct(it.productId, onSuccess = { finish() }, onFailure = { }) }
    }
}
