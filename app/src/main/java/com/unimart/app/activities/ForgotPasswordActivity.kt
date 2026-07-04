package com.unimart.app.activities

import android.os.Bundle
import android.os.CountDownTimer
import android.util.Patterns
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.unimart.app.R
import com.unimart.app.viewmodels.AuthViewModel

class ForgotPasswordActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var btnSendLink: MaterialButton
    private lateinit var loadingIndicator: CircularProgressIndicator
    private lateinit var tvBackToLogin: TextView
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar

    private val viewModel: AuthViewModel by viewModels()

    private var resendTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        initViews()
        observeViewModel()

        toolbar.setNavigationOnClickListener { finish() }
        tvBackToLogin.setOnClickListener { finish() }
        btnSendLink.setOnClickListener { validateAndSend() }
    }

    private fun initViews() {
        etEmail = findViewById(R.id.etEmail)
        btnSendLink = findViewById(R.id.btnSendLink)
        loadingIndicator = findViewById(R.id.loadingIndicator)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)
        toolbar = findViewById(R.id.toolbar)
    }

    private fun observeViewModel() {
        viewModel.isLoading.observe(this) { isLoading ->
            btnSendLink.isEnabled = !isLoading
            etEmail.isEnabled = !isLoading
            loadingIndicator.visibility = if (isLoading) View.VISIBLE else View.GONE
            btnSendLink.text = if (isLoading) "Sending..." else "Send Reset Link"
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                if (it.contains("No account found", ignoreCase = true)) {
                    etEmail.error = it
                    etEmail.requestFocus()
                } else {
                    com.google.android.material.snackbar.Snackbar.make(findViewById(android.R.id.content), it, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show()
                }
                viewModel.clearError()
            }
        }

        viewModel.passwordResetSent.observe(this) { success ->
            if (success) {
                showSuccessDialog(etEmail.text.toString().trim())
            }
        }
    }

    private fun validateAndSend() {
        val email = etEmail.text.toString().trim()

        if (email.isEmpty()) {
            etEmail.error = "Email is required."
            etEmail.requestFocus()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.error = "Please enter a valid email address."
            etEmail.requestFocus()
            return
        }

        viewModel.sendPasswordReset(email)
    }

    private fun showSuccessDialog(email: String) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Password Reset Email Sent")
            .setMessage("We've sent a password reset link to:\n\n$email\n\nPlease check your Inbox. If you don't see the email, check your Spam folder.")
            .setPositiveButton("Back to Login") { _, _ -> finish() }
            .setNeutralButton("Resend Email") { _, _ -> viewModel.sendPasswordReset(email) }
            .setCancelable(false)
            .create()

        dialog.show()

        // Anti-spam logic for Resend button
        val resendButton = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        resendButton.isEnabled = false

        resendTimer?.cancel()
        resendTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                resendButton.text = "Resend in ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                resendButton.isEnabled = true
                resendButton.text = "Resend Email"
            }
        }.start()
    }

    override fun onDestroy() {
        resendTimer?.cancel()
        super.onDestroy()
    }
}
