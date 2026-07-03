package com.unimart.app.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.unimart.app.models.Product
import com.unimart.app.models.WishlistItem

class WishlistRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val wishlistCollection = firestore.collection("Wishlist")
    private val productsCollection = firestore.collection("Products")
    private val auth = FirebaseAuth.getInstance()

    fun toggleWishlist(
        productId: String,
        isCurrentlyWishlisted: Boolean,
        onSuccess: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onFailure(Exception("User not logged in"))
        
        if (isCurrentlyWishlisted) {
            // Remove
            wishlistCollection
                .whereEqualTo("userId", uid)
                .whereEqualTo("productId", productId)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val batch = firestore.batch()
                    for (doc in querySnapshot.documents) {
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                        .addOnSuccessListener { onSuccess(false) }
                        .addOnFailureListener { onFailure(it) }
                }
                .addOnFailureListener { onFailure(it) }
        } else {
            // Add
            val id = wishlistCollection.document().id
            val item = WishlistItem(id, uid, productId)
            wishlistCollection.document(id).set(item)
                .addOnSuccessListener { onSuccess(true) }
                .addOnFailureListener { onFailure(it) }
        }
    }

    /**
     * Fetches only Product IDs for fast lookup in Home grid
     */
    fun getWishlistedProductIds(
        onSuccess: (Set<String>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onSuccess(emptySet())
        
        wishlistCollection
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val ids = querySnapshot.toObjects(WishlistItem::class.java)
                    .map { it.productId }
                    .toSet()
                onSuccess(ids)
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Fetches full Product objects for the Wishlist screen
     */
    fun getWishlistProducts(
        onSuccess: (List<Product>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return onFailure(Exception("User not logged in"))
        
        wishlistCollection
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { querySnapshot ->
                val productIds = querySnapshot.toObjects(WishlistItem::class.java)
                    .map { it.productId }
                
                if (productIds.isEmpty()) {
                    onSuccess(emptyList())
                    return@addOnSuccessListener
                }

                // Firestore 'in' query has a limit of 10 items. 
                // For a larger university app, we fetch all user items in chunks or handle manually.
                // Reusing ProductRepository for consistency.
                val products = mutableListOf<Product>()
                var fetchedCount = 0
                
                for (id in productIds) {
                    productsCollection.document(id).get()
                        .addOnSuccessListener { doc ->
                            val product = doc.toObject(Product::class.java)
                            if (product != null) {
                                products.add(product)
                            } else {
                                // Product was deleted from Firestore, remove the invalid wishlist reference
                                toggleWishlist(id, true, {}, {})
                            }

                            fetchedCount++
                            if (fetchedCount == productIds.size) {
                                onSuccess(products.sortedByDescending { it.createdAt })
                            }
                        }
                        .addOnFailureListener {
                            fetchedCount++
                            if (fetchedCount == productIds.size) {
                                onSuccess(products.sortedByDescending { it.createdAt })
                            }
                        }
                }
            }
            .addOnFailureListener { onFailure(it) }
    }
}
