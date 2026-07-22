package com.unimart.app.activities

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.unimart.app.R
import com.unimart.app.adapters.SelectedImagesAdapter
import com.unimart.app.constants.ListingType
import com.unimart.app.constants.ProductStatus
import com.unimart.app.models.Product
import com.unimart.app.network.CloudinaryRepository
import com.unimart.app.repositories.ProductRepository
import com.unimart.app.viewmodels.SellProductViewModel
import kotlinx.coroutines.launch

class SellProductActivity : AppCompatActivity() {

    private val selectedImages = mutableListOf<Uri>()
    private var existingImageUrls = listOf<String>()
    private lateinit var imagesAdapter: SelectedImagesAdapter
    private lateinit var cloudinaryRepository: CloudinaryRepository
    private val productRepository = ProductRepository()
    private lateinit var viewModel: SellProductViewModel

    // UI Elements
    private lateinit var toolbar: MaterialToolbar
    private lateinit var cardImagePicker: MaterialCardView
    private lateinit var rvSelectedImages: RecyclerView
    private lateinit var etTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var actCategory: AutoCompleteTextView
    private lateinit var actCondition: AutoCompleteTextView
    private lateinit var toggleListingType: MaterialButtonToggleGroup
    private lateinit var tilPrice: TextInputLayout
    private lateinit var etPrice: TextInputEditText
    private lateinit var tilLookingFor: TextInputLayout
    private lateinit var etLookingFor: TextInputEditText
    private lateinit var btnPublish: MaterialButton
    private lateinit var loadingIndicator: CircularProgressIndicator

    private var isEditMode = false
    private var productId: String? = null
    private var currentProduct: Product? = null

    private val pickMultipleMedia = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(5)
    ) { uris ->
        if (uris.isNotEmpty()) {
            selectedImages.clear()
            selectedImages.addAll(uris)
            existingImageUrls = emptyList()
            imagesAdapter.notifyDataSetChanged()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sell_product)

        viewModel = ViewModelProvider(this)[SellProductViewModel::class.java]

        isEditMode = intent.getBooleanExtra("IS_EDIT_MODE", false)
        productId = intent.getStringExtra("PRODUCT_ID")

        initViews()
        observeViewModel()
        cloudinaryRepository = CloudinaryRepository(this)
        
        setupToolbar()
        setupDropdowns()
        setupListingTypeToggle()
        setupImagePicker()

        if (isEditMode && productId != null) {
            loadProductData(productId!!)
            btnPublish.text = "Update Listing"
            toolbar.title = "Edit Product"
        }

        btnPublish.setOnClickListener {
            validateAndPublish()
        }
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        cardImagePicker = findViewById(R.id.cardImagePicker)
        rvSelectedImages = findViewById(R.id.rvSelectedImages)
        etTitle = findViewById(R.id.etTitle)
        etDescription = findViewById(R.id.etDescription)
        actCategory = findViewById(R.id.actCategory)
        actCondition = findViewById(R.id.actCondition)
        toggleListingType = findViewById(R.id.toggleListingType)
        tilPrice = findViewById(R.id.tilPrice)
        etPrice = findViewById(R.id.etPrice)
        tilLookingFor = findViewById(R.id.tilLookingFor)
        etLookingFor = findViewById(R.id.etLookingFor)
        btnPublish = findViewById(R.id.btnPublish)
        loadingIndicator = findViewById(R.id.loadingIndicator)
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnPublish.isEnabled = !isLoading
            btnPublish.text = if (isLoading) "Uploading..." else {
                if (isEditMode) "Update Listing" else "Publish Listing"
            }
        }
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupDropdowns() {
        val categories = arrayOf(
            "Books", "Electronics", "Furniture", "Cycles",
            "Phones", "Hostel Essentials", "Notes", "Accessories", "Others"
        )
        val categoryAdapter = ArrayAdapter(this, R.layout.dropdown_item, categories)
        actCategory.setAdapter(categoryAdapter)

        val conditions = arrayOf("New", "Like New", "Good", "Fair")
        val conditionAdapter = ArrayAdapter(this, R.layout.dropdown_item, conditions)
        actCondition.setAdapter(conditionAdapter)
    }

    private fun setupListingTypeToggle() {
        toggleListingType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btnSell -> {
                        tilPrice.visibility = View.VISIBLE
                        tilLookingFor.visibility = View.GONE
                    }
                    R.id.btnExchange -> {
                        tilPrice.visibility = View.GONE
                        tilLookingFor.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun setupImagePicker() {
        imagesAdapter = SelectedImagesAdapter(selectedImages) { position ->
            selectedImages.removeAt(position)
            imagesAdapter.notifyItemRemoved(position)
            imagesAdapter.notifyItemRangeChanged(position, selectedImages.size)
        }

        rvSelectedImages.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvSelectedImages.adapter = imagesAdapter

        cardImagePicker.setOnClickListener {
            pickMultipleMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }

    private fun loadProductData(productId: String) {
        viewModel.setLoading(true)
        productRepository.getProductById(productId,
            onSuccess = { product ->
                currentProduct = product
                viewModel.setLoading(false)
                populateFields(product)
            },
            onFailure = {
                viewModel.setLoading(false)
                showToast("Failed to load product data")
                finish()
            }
        )
    }

    private fun populateFields(product: Product) {
        etTitle.setText(product.title)
        etDescription.setText(product.description)
        actCategory.setText(product.category, false)
        actCondition.setText(product.condition, false)
        
        if (product.listingType == ListingType.SELL) {
            toggleListingType.check(R.id.btnSell)
            etPrice.setText(product.price.toString())
        } else {
            toggleListingType.check(R.id.btnExchange)
            etLookingFor.setText(product.lookingFor)
        }

        existingImageUrls = product.imageUrls
        if (existingImageUrls.isNotEmpty()) {
            showToast("Current images will be kept unless you pick new ones.")
        }
    }

    private fun validateAndPublish() {
        val title = etTitle.text.toString().trim()
        val description = etDescription.text.toString().trim()
        val category = actCategory.text.toString().trim()
        val condition = actCondition.text.toString().trim()
        val isSell = toggleListingType.checkedButtonId == R.id.btnSell
        val priceText = etPrice.text.toString().trim()
        val lookingFor = etLookingFor.text.toString().trim()

        if (selectedImages.isEmpty() && existingImageUrls.isEmpty()) {
            showToast("Please select at least one image")
            return
        }
        if (title.isEmpty()) {
            etTitle.error = "Title required"
            return
        }
        if (description.isEmpty()) {
            etDescription.error = "Description required"
            return
        }
        if (category.isEmpty()) {
            showToast("Please select a category")
            return
        }
        if (condition.isEmpty()) {
            showToast("Please select condition")
            return
        }

        if (isSell && priceText.isEmpty()) {
            etPrice.error = "Price required"
            return
        }
        if (!isSell && lookingFor.isEmpty()) {
            etLookingFor.error = "Item name required"
            return
        }

        publishListing(title, description, category, condition, isSell, priceText, lookingFor)
    }

    private fun publishListing(
        title: String,
        description: String,
        category: String,
        condition: String,
        isSell: Boolean,
        priceText: String,
        lookingFor: String
    ) {
        viewModel.setLoading(true)

        lifecycleScope.launch {
            val finalImageUrls = mutableListOf<String>()
            
            // If new images selected, upload them.
            if (selectedImages.isNotEmpty()) {
                var uploadFailed = false
                for (uri in selectedImages) {
                    val url = cloudinaryRepository.uploadImage(uri)
                    if (url != null) {
                        finalImageUrls.add(url)
                    } else {
                        uploadFailed = true
                        break
                    }
                }
                if (uploadFailed) {
                    onFailure("Image upload failed. Please try again.")
                    return@launch
                }
            } else {
                // No new images, keep existing
                finalImageUrls.addAll(existingImageUrls)
            }

            saveProductToFirestore(
                title, description, category, condition,
                if (isSell) ListingType.SELL else ListingType.EXCHANGE,
                priceText.toDoubleOrNull() ?: 0.0,
                lookingFor,
                finalImageUrls
            )
        }
    }

    private fun saveProductToFirestore(
        title: String,
        description: String,
        category: String,
        condition: String,
        listingType: String,
        price: Double,
        lookingFor: String,
        imageUrls: List<String>
    ) {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        
        val product = Product(
            productId = productId ?: "", // Use existing ID if edit mode
            sellerId = userId,
            title = title,
            description = description,
            price = price,
            category = category,
            condition = condition,
            listingType = listingType,
            lookingFor = lookingFor,
            imageUrls = imageUrls,
            status = currentProduct?.status ?: ProductStatus.AVAILABLE,
            createdAt = currentProduct?.createdAt ?: System.currentTimeMillis()
        )

        if (isEditMode && productId != null) {
            productRepository.updateProduct(product,
                onSuccess = {
                    viewModel.setLoading(false)
                    showToast("Listing Updated Successfully")
                    finish()
                },
                onFailure = { e ->
                    onFailure("Update Error: ${e.message}")
                }
            )
        } else {
            db.collection("Products")
                .add(product)
                .addOnSuccessListener { documentReference ->
                    documentReference.update("productId", documentReference.id)
                        .addOnCompleteListener {
                            viewModel.setLoading(false)
                            showToast("Listing Published Successfully")
                            finish()
                        }
                }
                .addOnFailureListener { e ->
                    onFailure("Firestore Error: ${e.message}")
                }
        }
    }

    private fun onFailure(message: String) {
        viewModel.setLoading(false)
        showToast(message)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
