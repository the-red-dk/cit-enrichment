package com.daksha.cit.enrichment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {
    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileEmail: TextView
    private lateinit var txtProfileMeta: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var btnLogout: Button

    private var currentDepartment: String = ""
    private var currentSemester: String = ""
    private var currentPhone: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        txtProfileName = findViewById(R.id.txtProfileName)
        txtProfileEmail = findViewById(R.id.txtProfileEmail)
        txtProfileMeta = findViewById(R.id.txtProfileMeta)
        btnEditProfile = findViewById(R.id.btnEditProfile)
        btnLogout = findViewById(R.id.btnLogout)

        findViewById<View>(R.id.btnBackProfile).setOnClickListener {
            finish()
        }

        btnEditProfile.setOnClickListener {
            showEditProfileDialog()
        }

        btnLogout.setOnClickListener {
            if (BuildConfig.ENABLE_FIREBASE_AUTH) {
                FirebaseAuth.getInstance().signOut()
                startActivity(
                    Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            } else {
                startActivity(
                    Intent(this, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
            }
            finish()
        }
    }

    override fun onStart() {
        super.onStart()
        loadProfile()
    }

    private fun loadProfile() {
        if (!BuildConfig.ENABLE_FIREBASE_AUTH) {
            txtProfileName.text = getString(R.string.profile_guest_name)
            txtProfileEmail.text = getString(R.string.profile_guest_mode_only)
            txtProfileMeta.text = getString(R.string.profile_guest_mode_notice)
            btnEditProfile.isEnabled = false
            btnLogout.text = getString(R.string.action_back_to_home)
            btnLogout.setOnClickListener {
                startActivity(
                    Intent(this, HomeActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                )
                finish()
            }
            return
        }

        if (SessionManager.isGuestMode(this)) {
            txtProfileName.text = getString(R.string.profile_guest_name)
            txtProfileEmail.text = getString(R.string.profile_guest_email)
            txtProfileMeta.text = getString(R.string.profile_guest_meta)
            btnEditProfile.isEnabled = false
            btnLogout.text = getString(R.string.action_go_to_login)
            btnLogout.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            return
        }

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            txtProfileName.text = getString(R.string.profile_guest_name)
            txtProfileEmail.text = getString(R.string.profile_guest_email)
            txtProfileMeta.text = getString(R.string.profile_guest_meta)
            btnEditProfile.isEnabled = false
            btnLogout.text = getString(R.string.action_go_to_login)
            btnLogout.setOnClickListener {
                startActivity(Intent(this, MainActivity::class.java))
                finish()
            }
            return
        }

        btnEditProfile.isEnabled = true
        btnLogout.text = getString(R.string.action_logout)
        btnLogout.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            startActivity(
                Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
        }

        val fallbackName = user.email?.substringBefore("@")?.replaceFirstChar { ch -> ch.uppercase() }
            ?: getString(R.string.profile_guest_name)

        txtProfileName.text = fallbackName
        txtProfileEmail.text = user.email ?: getString(R.string.profile_guest_email)
        txtProfileMeta.text = getString(R.string.profile_meta_default)

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    return@addOnSuccessListener
                }

                val displayName = document.getString("displayName").orEmpty().ifBlank { fallbackName }
                val email = document.getString("email").orEmpty().ifBlank { user.email.orEmpty() }
                val department = document.getString("department").orEmpty()
                val semester = document.getString("semester").orEmpty()
                val phone = document.getString("phone").orEmpty()

                currentDepartment = department
                currentSemester = semester
                currentPhone = phone

                txtProfileName.text = displayName
                txtProfileEmail.text = email
                txtProfileMeta.text = listOf(department, semester, phone)
                    .filter { it.isNotBlank() }
                    .joinToString("  •  ")
                    .ifBlank { getString(R.string.profile_meta_default) }
            }
    }

    private fun showEditProfileDialog() {
        if (FirebaseAuth.getInstance().currentUser == null) {
            Toast.makeText(this, R.string.profile_guest_meta, Toast.LENGTH_SHORT).show()
            return
        }

        val formLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 0)
        }

        val inputDepartment = EditText(this).apply {
            hint = getString(R.string.label_department)
            setText(currentDepartment)
        }
        val inputSemester = EditText(this).apply {
            hint = getString(R.string.label_semester)
            setText(currentSemester)
        }
        val inputPhone = EditText(this).apply {
            hint = getString(R.string.label_phone)
            setText(currentPhone)
        }

        formLayout.addView(inputDepartment)
        formLayout.addView(inputSemester)
        formLayout.addView(inputPhone)

        AlertDialog.Builder(this)
            .setTitle(R.string.action_edit_profile)
            .setView(formLayout)
            .setPositiveButton(R.string.action_save) { _, _ ->
                saveProfileUpdates(
                    inputDepartment.text.toString().trim(),
                    inputSemester.text.toString().trim(),
                    inputPhone.text.toString().trim()
                )
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveProfileUpdates(department: String, semester: String, phone: String) {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        FirebaseFirestore.getInstance()
            .collection("users")
            .document(user.uid)
            .update(
                mapOf(
                    "department" to department,
                    "semester" to semester,
                    "phone" to phone
                )
            )
            .addOnSuccessListener {
                currentDepartment = department
                currentSemester = semester
                currentPhone = phone
                txtProfileMeta.text = listOf(department, semester, phone)
                    .filter { it.isNotBlank() }
                    .joinToString("  •  ")
                    .ifBlank { getString(R.string.profile_meta_default) }
                Toast.makeText(this, R.string.message_profile_updated, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(this, error.message ?: getString(R.string.error_profile_update_failed), Toast.LENGTH_LONG).show()
            }
    }
}
