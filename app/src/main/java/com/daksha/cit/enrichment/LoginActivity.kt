package com.daksha.cit.enrichment

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {
    private lateinit var inputEmail: TextInputEditText
    private lateinit var inputPassword: TextInputEditText
    private lateinit var btnLoginSubmit: Button
    private lateinit var btnGoRegister: Button
    private lateinit var btnForgotPassword: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        inputEmail = findViewById(R.id.inputEmail)
        inputPassword = findViewById(R.id.inputPassword)
        btnLoginSubmit = findViewById(R.id.btnLoginSubmit)
        btnGoRegister = findViewById(R.id.btnGoRegister)
        btnForgotPassword = findViewById(R.id.btnForgotPassword)

        findViewById<View>(R.id.btnBackLogin).setOnClickListener {
            finish()
        }

        inputPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                loginUser()
                true
            } else {
                false
            }
        }

        btnLoginSubmit.setOnClickListener {
            loginUser()
        }

        btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }

        btnForgotPassword.setOnClickListener {
            sendPasswordReset()
        }
    }

    private fun sendPasswordReset() {
        val email = inputEmail.text?.toString()?.trim().orEmpty()

        if (email.isEmpty()) {
            Toast.makeText(this, R.string.error_reset_email_required, Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show()
            return
        }

        setLoading(true)

        FirebaseAuth.getInstance()
            .sendPasswordResetEmail(email)
            .addOnSuccessListener {
                setLoading(false)
                Toast.makeText(this, R.string.message_password_reset_sent, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { error ->
                setLoading(false)
                Toast.makeText(
                    this,
                    SessionManager.getAuthErrorMessage(this, error.message, R.string.error_password_reset_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun loginUser() {
        val email = inputEmail.text?.toString()?.trim().orEmpty()
        val password = inputPassword.text?.toString().orEmpty()

        when {
            email.isEmpty() || password.isEmpty() -> {
                Toast.makeText(this, R.string.error_fill_required_fields, Toast.LENGTH_SHORT).show()
                return
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show()
                return
            }
        }

        setLoading(true)

        FirebaseAuth.getInstance()
            .signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                SessionManager.clearGuestMode(this)
                val user = result.user
                if (user != null) {
                    FirebaseFirestore.getInstance()
                        .collection("users")
                        .document(user.uid)
                        .set(
                            hashMapOf(
                                "uid" to user.uid,
                                "email" to (user.email ?: email),
                                "displayName" to ((user.email ?: email).substringBefore("@").replaceFirstChar { ch -> ch.uppercase() }),
                                "lastLoginAt" to FieldValue.serverTimestamp()
                            ),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                }

                navigateToHome()
            }
            .addOnFailureListener { error ->
                setLoading(false)
                Toast.makeText(
                    this,
                    SessionManager.getAuthErrorMessage(this, error.message, R.string.error_login_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun navigateToHome() {
        setLoading(false)
        startActivity(
            Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        finish()
    }

    private fun setLoading(isLoading: Boolean) {
        btnLoginSubmit.isEnabled = !isLoading
        btnGoRegister.isEnabled = !isLoading
        btnForgotPassword.isEnabled = !isLoading
        btnLoginSubmit.text = getString(if (isLoading) R.string.label_signing_in else R.string.action_login)
    }
}
