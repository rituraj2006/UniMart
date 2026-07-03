package com.unimart.app.repositories

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.WriteBatch
import com.unimart.app.constants.RequestStatus
import com.unimart.app.models.ContactRequest

/**
 * Repository to handle all Firestore logic for Contact Requests
 */
class ContactRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val contactRequestsCollection = firestore.collection("ContactRequests")

    /**
     * Creates a new contact request from a buyer to a seller
     */
    fun createContactRequest(
        buyerId: String,
        sellerId: String,
        productId: String,
        message: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val requestId = contactRequestsCollection.document().id

        val requestData = hashMapOf(
            "requestId" to requestId,
            "buyerId" to buyerId,
            "sellerId" to sellerId,
            "productId" to productId,
            "message" to message,
            "status" to RequestStatus.PENDING,
            "createdAt" to FieldValue.serverTimestamp()
        )

        contactRequestsCollection.document(requestId)
            .set(requestData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Retrieves all incoming requests for a seller, sorted locally to avoid Index errors
     */
    fun getIncomingRequests(
        sellerId: String,
        onSuccess: (List<ContactRequest>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        contactRequestsCollection
            .whereEqualTo("sellerId", sellerId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                // Sort locally by createdAt (Oldest first)
                val requests = querySnapshot.toObjects(ContactRequest::class.java)
                    .sortedBy { it.createdAt }
                onSuccess(requests)
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Retrieves all requests for a specific product, sorted locally
     */
    fun getRequestsByProductId(
        productId: String,
        onSuccess: (List<ContactRequest>) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        contactRequestsCollection
            .whereEqualTo("productId", productId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                // Sort locally by createdAt (Oldest first)
                val requests = querySnapshot.toObjects(ContactRequest::class.java)
                    .sortedBy { it.createdAt }
                onSuccess(requests)
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Updates the status of an existing contact request (e.g. ACCEPTED, REJECTED)
     */
    fun updateRequestStatus(
        requestId: String,
        status: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        contactRequestsCollection.document(requestId)
            .update("status", status)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Checks if a buyer has already requested contact for a specific product
     */
    fun hasAlreadyRequested(
        buyerId: String,
        productId: String,
        onResult: (Boolean) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        contactRequestsCollection
            .whereEqualTo("buyerId", buyerId)
            .whereEqualTo("productId", productId)
            .get()
            .addOnSuccessListener { querySnapshot ->
                onResult(!querySnapshot.isEmpty)
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Retrieves a specific contact request for a buyer and product to determine status
     */
    fun getBuyerRequestForProduct(
        buyerId: String,
        productId: String,
        onSuccess: (ContactRequest?) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        contactRequestsCollection
            .whereEqualTo("buyerId", buyerId)
            .whereEqualTo("productId", productId)
            .limit(1)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (!querySnapshot.isEmpty) {
                    val request = querySnapshot.documents[0].toObject(ContactRequest::class.java)
                    onSuccess(request)
                } else {
                    onSuccess(null)
                }
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Automatically rejects all pending requests for a specific product using a WriteBatch
     */
    fun autoRejectPendingRequests(
        productId: String,
        onSuccess: () -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        contactRequestsCollection
            .whereEqualTo("productId", productId)
            .whereEqualTo("status", RequestStatus.PENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                if (querySnapshot.isEmpty) {
                    onSuccess()
                    return@addOnSuccessListener
                }

                val batch = firestore.batch()
                for (document in querySnapshot.documents) {
                    batch.update(document.reference, "status", RequestStatus.AUTO_REJECTED)
                }

                batch.commit()
                    .addOnSuccessListener { onSuccess() }
                    .addOnFailureListener { onFailure(it) }
            }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Counts only the pending requests for a specific seller
     */
    fun getPendingRequestsCount(
        sellerId: String,
        onSuccess: (Int) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        contactRequestsCollection
            .whereEqualTo("sellerId", sellerId)
            .whereEqualTo("status", RequestStatus.PENDING)
            .get()
            .addOnSuccessListener { querySnapshot ->
                onSuccess(querySnapshot.size())
            }
            .addOnFailureListener { onFailure(it) }
    }
}
