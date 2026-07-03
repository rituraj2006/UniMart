package com.unimart.app.activities

import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.unimart.app.R

class SignupActivity : AppCompatActivity() {

    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var etWhatsApp: TextInputEditText

    private lateinit var btnSignup: MaterialButton
    private lateinit var tvLogin: TextView

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        initViews()

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        tvLogin.setOnClickListener {
            // Just finish to go back to existing LoginActivity
            finish()
        }

        btnSignup.setOnClickListener {
            registerUser()
        }
    }

    private fun initViews() {
        etFullName = findViewById(R.id.etFullName)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        etWhatsApp = findViewById(R.id.etWhatsApp)

        btnSignup = findViewById(R.id.btnSignup)
        tvLogin = findViewById(R.id.tvLogin)
    }

    private fun registerUser() {

        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()
        val whatsapp = etWhatsApp.text.toString().trim()

        when {
            fullName.isEmpty() -> {
                etFullName.error = getString(R.string.err_full_name)
                etFullName.requestFocus()
                return
            }

            email.isEmpty() -> {
                etEmail.error = getString(R.string.err_email)
                etEmail.requestFocus()
                return
            }

            password.isEmpty() -> {
                etPassword.error = getString(R.string.err_password)
                etPassword.requestFocus()
                return
            }

            password.length < 6 -> {
                etPassword.error = getString(R.string.err_password_length)
                etPassword.requestFocus()
                return
            }

            confirmPassword.isEmpty() -> {
                etConfirmPassword.error = getString(R.string.err_confirm_password)
                etConfirmPassword.requestFocus()
                return
            }

            password != confirmPassword -> {
                etConfirmPassword.error = getString(R.string.err_password_mismatch)
                etConfirmPassword.requestFocus()
                return
            }

            whatsapp.isEmpty() -> {
                etWhatsApp.error = getString(R.string.err_whatsapp)
                etWhatsApp.requestFocus()
                return
            }
        }

        btnSignup.isEnabled = false
        btnSignup.text = getString(R.string.creating)

        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {

                    val firebaseUser = auth.currentUser

                    if (firebaseUser != null) {

                        val userData = hashMapOf(
                            "uid" to firebaseUser.uid,
                            "name" to fullName,
                            "email" to email,
                            "whatsappNumber" to whatsapp,
                            "profileImage" to "",
                            "joinedDate" to System.currentTimeMillis()
                        )

                        firestore.collection("Users")
                            .document(firebaseUser.uid)
                            .set(userData)
                            .addOnSuccessListener {

                                btnSignup.isEnabled = true
                                btnSignup.text = getString(R.string.create_account)

                                Toast.makeText(
                                    this,
                                    "Account created successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()

                                // Sign out user after registration to force manual login
                                auth.signOut()

                                // Finish this activity to go back to LoginActivity
                                finish()
                            }
                            .addOnFailureListener {

                                btnSignup.isEnabled = true
                                btnSignup.text = getString(R.string.create_account)

                                Toast.makeText(
                                    this,
                                    "Failed to save user profile.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }

                    }

                } else {

                    btnSignup.isEnabled = true
                    btnSignup.text = getString(R.string.create_account)

                    Toast.makeText(
                        this,
                        task.exception?.localizedMessage ?: "Signup failed",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }
}
