package com.unimart.app.activities

import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.unimart.app.R
import com.unimart.app.viewmodels.AuthViewModel

class SignupActivity : AppCompatActivity() {

    private lateinit var etFullName: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var etWhatsApp: TextInputEditText

    private lateinit var btnSignup: MaterialButton
    private lateinit var tvLogin: TextView
    private lateinit var loadingIndicator: CircularProgressIndicator

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        initViews()
        observeViewModel()

        tvLogin.setOnClickListener {
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
        loadingIndicator = findViewById(R.id.loadingIndicator)
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            btnSignup.isEnabled = !isLoading
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnSignup.text = if (isLoading) getString(R.string.creating) else getString(R.string.create_account)
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.signupSuccess.observe(this) { success ->
            if (success) {
                showVerificationDialog(etEmail.text.toString().trim())
            }
        }
    }

    private fun showVerificationDialog(email: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("📧 Verify Your Email")
            .setMessage("We've sent a verification email to:\n\n$email\n\nPlease verify your email before logging in to UniMart.\n\nIf you don't receive the email within 1–2 minutes:\n\n• Check your Inbox\n• Check your Spam/Junk folder\n• If you still don't receive it, you can use \"Resend Verification Email\" from the Login screen.\n\nAfter verifying your email, return to the Login screen and sign in.")
            .setPositiveButton("Go to Login") { _, _ ->
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun registerUser() {
        val fullName = etFullName.text.toString().trim()
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        val confirmPassword = etConfirmPassword.text.toString().trim()
        val whatsapp = etWhatsApp.text.toString().trim()

        if (fullName.isEmpty()) {
            etFullName.error = getString(R.string.err_full_name)
            etFullName.requestFocus()
            return
        }

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = getString(R.string.err_email)
            etEmail.requestFocus()
            return
        }

        if (password.length < 6) {
            etPassword.error = getString(R.string.err_password_length)
            etPassword.requestFocus()
            return
        }

        if (password != confirmPassword) {
            etConfirmPassword.error = getString(R.string.err_password_mismatch)
            etConfirmPassword.requestFocus()
            return
        }

        if (whatsapp.length != 10) {
            etWhatsApp.error = getString(R.string.err_whatsapp)
            etWhatsApp.requestFocus()
            return
        }

        viewModel.signUp(fullName, email, whatsapp, password)
    }
}
