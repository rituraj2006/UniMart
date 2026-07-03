package com.unimart.app.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.unimart.app.constants.ProductStatus
import com.unimart.app.models.Product
import com.unimart.app.models.User

class ProductRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val productsCollection = firestore.collection("Products")
    private val usersCollection = firestore.collection("Users")
    private val auth = FirebaseAuth.getInstance()

    fun getProductById(
        productId: String,
        onSuccess: (Product) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        productsCollection.document(productId).get()
            .addOnSuccessListener { document ->
                val product = document.toObject(Product::class.java)
                if (product != null) {
                    onSuccess(product)
                } else {
                    onFailure(Exception("Product not found"))
                }
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    fun updateProduct(
        product: Product,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        productsCollection.document(product.productId).set(product)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun markProductAsSold(
        productId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        productsCollection.document(productId).update("status", ProductStatus.SOLD)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun deleteProduct(
        productId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        productsCollection.document(productId).delete()
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    fun getCurrentUser(
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onFailure(Exception("User not logged in"))
        getUserById(uid, onSuccess, onFailure)
    }

    fun getUserById(
        uid: String,
        onSuccess: (User) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        usersCollection.document(uid).get()
            .addOnSuccessListener { document ->
                val user = document.toObject(User::class.java)
                if (user != null) {
                    onSuccess(user)
                } else {
                    onFailure(Exception("User profile not found"))
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getMyListings(
        onSuccess: (List<Product>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onFailure(Exception("User not logged in"))
        
        // Simplified query to avoid index issues. Fetching all user's products and sorting manually.
        productsCollection
            .whereEqualTo("sellerId", uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val products = querySnapshot.toObjects(Product::class.java)
                    .sortedByDescending { it.createdAt }
                onSuccess(products)
            }
            .addOnFailureListener { onFailure(it) }
    }

    fun getSellingCount(
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onFailure(Exception("User not logged in"))
        productsCollection
            .whereEqualTo("sellerId", uid)
            .whereEqualTo("status", ProductStatus.AVAILABLE)
            .get()
            .addOnSuccessListener { onSuccess(it.size()) }
            .addOnFailureListener { onFailure(it) }
    }

    fun getSoldCount(
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onFailure(Exception("User not logged in"))
        productsCollection
            .whereEqualTo("sellerId", uid)
            .whereEqualTo("status", ProductStatus.SOLD)
            .get()
            .addOnSuccessListener { onSuccess(it.size()) }
            .addOnFailureListener { onFailure(it) }
    }

    fun updateProfileImage(
        imageUrl: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onFailure(Exception("User not logged in"))
        usersCollection.document(uid).update("profileImage", imageUrl)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }
}
