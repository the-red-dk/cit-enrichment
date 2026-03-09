package com.daksha.cit.enrichment

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class RegisterActivity : AppCompatActivity() {
    private lateinit var inputRegEmail: TextInputEditText
    private lateinit var inputRegPassword: TextInputEditText
    private lateinit var inputRegConfirm: TextInputEditText
    private lateinit var btnRegisterSubmit: Button
    private lateinit var btnGoLogin: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        inputRegEmail = findViewById(R.id.inputRegEmail)
        inputRegPassword = findViewById(R.id.inputRegPassword)
        inputRegConfirm = findViewById(R.id.inputRegConfirm)
        btnRegisterSubmit = findViewById(R.id.btnRegisterSubmit)
        btnGoLogin = findViewById(R.id.btnGoLogin)

        inputRegConfirm.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                registerUser()
                true
            } else {
                false
            }
        }

        btnRegisterSubmit.setOnClickListener {
            registerUser()
        }

        btnGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    private fun registerUser() {
        val email = inputRegEmail.text?.toString()?.trim().orEmpty()
        val password = inputRegPassword.text?.toString().orEmpty()
        val confirmPassword = inputRegConfirm.text?.toString().orEmpty()

        when {
            email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() -> {
                Toast.makeText(this, R.string.error_fill_required_fields, Toast.LENGTH_SHORT).show()
                return
            }

            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show()
                return
            }

            password.length < 6 -> {
                Toast.makeText(this, R.string.error_password_too_short, Toast.LENGTH_SHORT).show()
                return
            }

            password != confirmPassword -> {
                Toast.makeText(this, R.string.error_password_mismatch, Toast.LENGTH_SHORT).show()
                return
            }
        }

        setLoading(true)

        FirebaseAuth.getInstance()
            .createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                SessionManager.clearGuestMode(this)
                val user = result.user
                if (user == null) {
                    setLoading(false)
                    Toast.makeText(this, R.string.error_register_failed, Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val displayName = email.substringBefore("@").replaceFirstChar { ch -> ch.uppercase() }
                val userData = hashMapOf(
                    "uid" to user.uid,
                    "email" to email,
                    "displayName" to displayName,
                    "department" to "",
                    "semester" to "",
                    "phone" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "lastLoginAt" to FieldValue.serverTimestamp()
                )

                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(user.uid)
                    .set(userData)
                    .addOnSuccessListener {
                        navigateToHome()
                    }
                    .addOnFailureListener { error ->
                        setLoading(false)
                        Toast.makeText(this, error.message ?: getString(R.string.error_profile_save_failed), Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { error ->
                setLoading(false)
                Toast.makeText(
                    this,
                    SessionManager.getAuthErrorMessage(this, error.message, R.string.error_register_failed),
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
        btnRegisterSubmit.isEnabled = !isLoading
        btnGoLogin.isEnabled = !isLoading
        btnRegisterSubmit.text = getString(if (isLoading) R.string.label_creating_account else R.string.action_register)
    }
}
