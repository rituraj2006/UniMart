package com.unimart.app.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.unimart.app.models.User
import com.unimart.app.repositories.AuthRepository

class AuthViewModel : ViewModel() {

    private val repository = AuthRepository()

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _signupSuccess = MutableLiveData<Boolean>()
    val signupSuccess: LiveData<Boolean> = _signupSuccess

    private val _loginSuccess = MutableLiveData<FirebaseUser?>()
    val loginSuccess: LiveData<FirebaseUser?> = _loginSuccess

    private val _verificationEmailSent = MutableLiveData<Boolean>()
    val verificationEmailSent: LiveData<Boolean> = _verificationEmailSent

    private val _passwordResetSent = MutableLiveData<Boolean>()
    val passwordResetSent: LiveData<Boolean> = _passwordResetSent

    /**
     * Re-ordered signup flow to prevent Permission Denied errors while checking duplicate data.
     * 1. Create Auth Account (Handles Duplicate Email)
     * 2. Check Firestore for Duplicate Phone (Now Authenticated)
     * 3. Finalize Profile or Cleanup
     */
    fun signUp(name: String, email: String, phone: String, password: String) {
        _isLoading.value = true
        _error.value = null

        // Step 1: Create Firebase Auth account first (handles duplicate email natively)
        repository.signUp(email, password) { firebaseUser, exception ->
            if (firebaseUser != null) {
                
                // User is now Authenticated. Now we can safely query Firestore for duplicate phone.
                repository.checkDuplicatePhone(phone, { phoneExists ->
                    if (phoneExists) {
                        // Duplicate phone found. Cleanup the auth account.
                        repository.deleteAuthUser(firebaseUser) {
                            _isLoading.value = false
                            _error.value = "This phone number is already registered."
                        }
                    } else {
                        // Step 2: Phone is unique. Create Firestore profile.
                        val user = User(
                            uid = firebaseUser.uid,
                            name = name,
                            email = email,
                            whatsappNumber = phone,
                            joinedDate = System.currentTimeMillis()
                        )
                        repository.createUserProfile(user, {
                            // Step 3: Send verification email
                            repository.sendVerificationEmail(firebaseUser) {
                                repository.signOut()
                                _isLoading.value = false
                                _signupSuccess.value = true
                            }
                        }, { profileException ->
                            // Cleanup if profile creation fails
                            repository.deleteAuthUser(firebaseUser) {
                                _isLoading.value = false
                                _error.value = "Account creation failed. Please try again."
                            }
                        })
                    }
                }, { phoneCheckError ->
                    // Cleanup if we can't even check the phone (e.g. security rules fail)
                    repository.deleteAuthUser(firebaseUser) {
                        _isLoading.value = false
                        _error.value = phoneCheckError.localizedMessage ?: "Verification failed."
                    }
                })

            } else {
                // Handle Auth failures (like duplicate email)
                _isLoading.value = false
                if (exception is FirebaseAuthUserCollisionException) {
                    _error.value = "This email is already registered. Please sign in instead."
                } else {
                    _error.value = exception?.localizedMessage ?: "Signup failed."
                }
            }
        }
    }

    fun signIn(email: String, password: String) {
        _isLoading.value = true
        _error.value = null

        repository.signIn(email, password) { firebaseUser, exception ->
            if (firebaseUser != null) {
                repository.reloadUser(firebaseUser) {
                    _isLoading.value = false
                    if (firebaseUser.isEmailVerified) {
                        _loginSuccess.value = firebaseUser
                    } else {
                        _loginSuccess.value = null // Means unverified
                    }
                }
            } else {
                _isLoading.value = false
                val errorMessage = when (exception) {
                    is com.google.firebase.auth.FirebaseAuthInvalidUserException,
                    is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException -> 
                        "Check your email or password, it's incorrect."
                    else -> exception?.localizedMessage ?: "Login failed. Please check your credentials."
                }
                _error.value = errorMessage
            }
        }
    }

    fun resendVerification(user: FirebaseUser) {
        repository.sendVerificationEmail(user) {
            _verificationEmailSent.value = it
        }
    }

    /**
     * Triggers the password reset process.
     */
    fun sendPasswordReset(email: String) {
        _isLoading.value = true
        _error.value = null

        // Step 1: Check if email exists in Firestore
        // Note: This requires Firestore "Users" collection to have "read" permissions for unauthenticated users.
        repository.checkEmailExists(email, { exists ->
            if (exists) {
                // Step 2: Proceed with reset
                repository.sendPasswordResetEmail(email) { success, exception ->
                    _isLoading.value = false
                    if (success) {
                        _passwordResetSent.value = true
                    } else {
                        _error.value = exception?.localizedMessage ?: "Unable to send reset email. Please try again."
                    }
                }
            } else {
                _isLoading.value = false
                _error.value = "No account found with this email address."
            }
        }, { exception ->
            // If Firestore check fails (e.g. Permission Denied), fallback to standard Firebase reset
            // to ensure the user isn't blocked by database security rules.
            repository.sendPasswordResetEmail(email) { success, authException ->
                _isLoading.value = false
                if (success) {
                    _passwordResetSent.value = true
                } else {
                    // If both fail, show the most relevant error
                    val errorMessage = when {
                        authException?.message?.contains("user-not-found") == true ->
                            "No account found with this email address."
                        else -> exception.localizedMessage ?: "Verification failed. Please check your connection."
                    }
                    _error.value = errorMessage
                }
            }
        })
    }

    fun clearError() {
        _error.value = null
    }
}
