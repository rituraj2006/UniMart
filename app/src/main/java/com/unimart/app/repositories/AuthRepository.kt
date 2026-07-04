package com.unimart.app.repositories

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.unimart.app.models.User

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * Checks if a user with the same phone number already exists in Firestore.
     */
    fun checkDuplicatePhone(phone: String, onResult: (Boolean) -> Unit, onFailure: (Exception) -> Unit) {
        firestore.collection("Users")
            .whereEqualTo("whatsappNumber", phone)
            .get()
            .addOnSuccessListener { querySnapshot ->
                onResult(!querySnapshot.isEmpty)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    /**
     * Checks if a user with the same email already exists in Firestore.
     */
    fun checkEmailExists(email: String, onResult: (Boolean) -> Unit, onFailure: (Exception) -> Unit) {
        firestore.collection("Users")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { querySnapshot ->
                onResult(!querySnapshot.isEmpty)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    /**
     * Creates a new Firebase Authentication user.
     */
    fun signUp(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(task.result?.user, null)
                } else {
                    onComplete(null, task.exception)
                }
            }
    }

    /**
     * Saves user details to Firestore.
     */
    fun createUserProfile(user: User, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        firestore.collection("Users").document(user.uid).set(user)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { onFailure(it) }
    }

    /**
     * Deletes a Firebase Auth user (used for cleanup in case of Firestore failure).
     */
    fun deleteAuthUser(user: FirebaseUser, onComplete: () -> Unit) {
        user.delete().addOnCompleteListener { onComplete() }
    }

    /**
     * Sends a verification email to the user.
     */
    fun sendVerificationEmail(user: FirebaseUser, onComplete: (Boolean) -> Unit) {
        user.sendEmailVerification().addOnCompleteListener { onComplete(it.isSuccessful) }
    }

    /**
     * Signs in an existing user.
     */
    fun signIn(email: String, password: String, onComplete: (FirebaseUser?, Exception?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    onComplete(task.result?.user, null)
                } else {
                    onComplete(null, task.exception)
                }
            }
    }

    /**
     * Reloads user data to check for email verification status.
     */
    fun reloadUser(user: FirebaseUser, onComplete: () -> Unit) {
        user.reload().addOnCompleteListener { onComplete() }
    }

    /**
     * Sends a password reset email to the given email address.
     */
    fun sendPasswordResetEmail(email: String, onComplete: (Boolean, Exception?) -> Unit) {
        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
            onComplete(task.isSuccessful, task.exception)
        }
    }

    /**
     * Signs out the current user.
     */
    fun signOut() {
        auth.signOut()
    }
}
