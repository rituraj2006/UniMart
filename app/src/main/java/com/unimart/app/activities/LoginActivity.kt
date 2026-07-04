package com.unimart.app.activities

import android.content.Intent
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.unimart.app.MainActivity
import com.unimart.app.R
import com.unimart.app.viewmodels.AuthViewModel

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: MaterialButton
    private lateinit var tvForgotPassword: TextView
    private lateinit var tvCreateAccount: TextView
    private lateinit var loadingIndicator: CircularProgressIndicator

    private val viewModel: AuthViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        initViews()
        observeViewModel()

        btnLogin.setOnClickListener {
            loginUser()
        }

        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvForgotPassword = findViewById(R.id.tvForgotPassword)
        tvCreateAccount = findViewById(R.id.tvCreateAccount)
        loadingIndicator = findViewById(R.id.loadingIndicator)
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            btnLogin.isEnabled = !isLoading
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnLogin.text = if (isLoading) getString(R.string.logging_in) else getString(R.string.login)
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }

        viewModel.loginSuccess.observe(this) { user ->
            if (user != null) {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            } else if (FirebaseAuth.getInstance().currentUser != null) {
                // User logged in but not verified
                showVerificationDialog(FirebaseAuth.getInstance().currentUser!!)
            }
        }

        viewModel.verificationEmailSent.observe(this) { success ->
            if (success) {
                Toast.makeText(this, "Verification email sent.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to send verification email.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showVerificationDialog(user: FirebaseUser) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Email Not Verified")
            .setMessage("Your email address has not been verified yet.\n\nPlease check your Inbox for the verification email. If you cannot find it, check your Spam/Junk folder.\n\nAfter verifying your email, tap \"Refresh Verification Status\". If needed, you can also resend the verification email.")
            .setPositiveButton("Refresh Verification Status") { _, _ ->
                viewModel.signIn(etEmail.text.toString(), etPassword.text.toString())
            }
            .setNeutralButton("Resend Verification Email") { _, _ ->
                viewModel.resendVerification(user)
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                FirebaseAuth.getInstance().signOut()
                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun loginUser() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = getString(R.string.err_email)
            etEmail.requestFocus()
            return
        }

        if (password.isEmpty()) {
            etPassword.error = getString(R.string.err_password)
            etPassword.requestFocus()
            return
        }

        viewModel.signIn(email, password)
    }
}
