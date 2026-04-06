package com.daksha.cit.enrichment

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileActivity : AppCompatActivity() {
    private lateinit var txtProfileName: TextView
    private lateinit var txtProfileEmail: TextView
    private lateinit var txtProfileMeta: TextView
    private lateinit var btnEditProfile: Button
    private lateinit var btnLogout: Button
    private lateinit var txtCreditsSummary: TextView
    private lateinit var txtCreditsEmpty: TextView
    private lateinit var containerCreditsHistory: LinearLayout
    private lateinit var txtDevicesSummary: TextView
    private lateinit var containerDevices: LinearLayout

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
        txtCreditsSummary = findViewById(R.id.txtCreditsSummary)
        txtCreditsEmpty = findViewById(R.id.txtCreditsEmpty)
        containerCreditsHistory = findViewById(R.id.containerCreditsHistory)
        txtDevicesSummary = findViewById(R.id.txtDevicesSummary)
        containerDevices = findViewById(R.id.containerDevices)

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
        loadCreditsHistory()
        loadDeviceSessions()
    }

    private fun loadProfile() {
        if (!BuildConfig.ENABLE_FIREBASE_AUTH) {
            showGuestProfileState(
                emailText = R.string.profile_guest_mode_only,
                metaText = R.string.profile_guest_mode_notice,
                logoutText = R.string.action_back_to_home,
                logoutAction = {
                    startActivity(
                        Intent(this, HomeActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                    )
                    finish()
                }
            )
            return
        }

        if (SessionManager.isGuestMode(this)) {
            showGuestProfileState(
                emailText = R.string.profile_guest_email,
                metaText = R.string.profile_guest_meta,
                logoutText = R.string.action_go_to_login,
                logoutAction = {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            )
            return
        }

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            showGuestProfileState(
                emailText = R.string.profile_guest_email,
                metaText = R.string.profile_guest_meta,
                logoutText = R.string.action_go_to_login,
                logoutAction = {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            )
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
                    currentDepartment = ""
                    currentSemester = ""
                    currentPhone = ""
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
            .addOnFailureListener {
                currentDepartment = ""
                currentSemester = ""
                currentPhone = ""
                txtProfileName.text = fallbackName
                txtProfileEmail.text = user.email ?: getString(R.string.profile_guest_email)
                txtProfileMeta.text = getString(R.string.profile_meta_default)
            }
    }

    private fun loadCreditsHistory() {
        if (SessionManager.isGuestMode(this)) {
            renderCreditHistory(
                LocalClaimStore.getClaims(this)
                    .filter { isCreditsEligibleClaim(it.claimType) }
                    .map {
                        CreditHistoryItem(
                            title = it.title,
                            credits = it.nptelCredits,
                            claimDate = it.claimDate,
                            status = it.status
                        )
                    }
            )
            return
        }

        val userKey = SessionManager.getActiveUserKey(this)
        FirebaseFirestore.getInstance()
            .collection("claims")
            .whereEqualTo("userKey", userKey)
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener { snapshot ->
                val items = snapshot.documents
                    .mapNotNull { document ->
                        val claimType = document.getString("claimType").orEmpty()
                        if (!isCreditsEligibleClaim(claimType)) {
                            return@mapNotNull null
                        }

                        CreditHistoryItem(
                            title = document.getString("title").orEmpty(),
                            credits = (document.getLong("nptelCredits") ?: 0L).toInt(),
                            claimDate = document.getString("claimDate").orEmpty(),
                            status = document.getString("status").orEmpty()
                        )
                    }
                renderCreditHistory(items)
            }
            .addOnFailureListener {
                txtCreditsSummary.text = getString(R.string.profile_credits_summary, 0, 0)
                txtCreditsEmpty.visibility = View.VISIBLE
                containerCreditsHistory.removeAllViews()
            }
    }

    private fun renderCreditHistory(items: List<CreditHistoryItem>) {
        val validItems = items.filter { it.credits > 0 }
        val totalCredits = validItems.sumOf { it.credits }

        txtCreditsSummary.text = getString(R.string.profile_credits_summary, totalCredits, validItems.size)
        containerCreditsHistory.removeAllViews()

        if (validItems.isEmpty()) {
            txtCreditsEmpty.visibility = View.VISIBLE
            return
        }

        txtCreditsEmpty.visibility = View.GONE
        for (item in validItems) {
            val card = layoutInflater.inflate(R.layout.item_credit_history, containerCreditsHistory, false)
            val txtCredits = card.findViewById<TextView>(R.id.txtCreditValue)
            val txtTitle = card.findViewById<TextView>(R.id.txtCreditTitle)
            val txtMeta = card.findViewById<TextView>(R.id.txtCreditMeta)

            txtCredits.text = getString(R.string.profile_credits_value, item.credits)
            txtTitle.text = item.title.ifBlank { getString(R.string.claim_title_unknown) }
            txtMeta.text = getString(
                R.string.profile_credits_meta,
                item.claimDate.ifBlank { getString(R.string.claim_date_unknown) },
                item.status.ifBlank { getString(R.string.claim_status_pending) }
            )

            containerCreditsHistory.addView(card)
        }
    }

    private fun loadDeviceSessions() {
        if (SessionManager.isGuestMode(this)) {
            txtDevicesSummary.text = getString(R.string.profile_devices_guest_summary)
            containerDevices.removeAllViews()
            containerDevices.addView(createDeviceItem(getString(R.string.profile_this_device), getString(R.string.profile_devices_local_only)))
            return
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            txtDevicesSummary.text = getString(R.string.profile_devices_unavailable)
            containerDevices.removeAllViews()
            return
        }

        registerCurrentDeviceSession(user.uid)

        FirebaseFirestore.getInstance()
            .collection("user_sessions")
            .whereEqualTo("userId", user.uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val sessions = snapshot.documents.map { document ->
                    DeviceSessionItem(
                        deviceName = document.getString("deviceName").orEmpty(),
                        model = document.getString("model").orEmpty(),
                        lastSeenMillis = document.getTimestamp("lastSeenAt")?.toDate()?.time ?: 0L,
                        deviceId = document.getString("deviceId").orEmpty()
                    )
                }.sortedByDescending { it.lastSeenMillis }

                txtDevicesSummary.text = getString(R.string.profile_devices_summary, sessions.size)
                containerDevices.removeAllViews()
                val currentDeviceId = getCurrentDeviceKey()

                sessions.forEach { session ->
                    val title = if (session.deviceId == currentDeviceId) {
                        getString(R.string.profile_this_device)
                    } else {
                        session.deviceName.ifBlank { session.model.ifBlank { getString(R.string.profile_device_unknown) } }
                    }
                    val subtitle = if (session.lastSeenMillis > 0L) {
                        getString(R.string.profile_device_last_seen, dateTimeFormatter.format(Date(session.lastSeenMillis)))
                    } else {
                        getString(R.string.profile_device_last_seen, getString(R.string.claim_date_unknown))
                    }
                    containerDevices.addView(createDeviceItem(title, subtitle))
                }
            }
            .addOnFailureListener {
                txtDevicesSummary.text = getString(R.string.profile_devices_unavailable)
                containerDevices.removeAllViews()
            }
    }

    private fun registerCurrentDeviceSession(userId: String) {
        val deviceId = getCurrentDeviceKey()
        val payload = mapOf(
            "userId" to userId,
            "deviceId" to deviceId,
            "deviceName" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "model" to Build.MODEL,
            "platform" to "Android",
            "lastSeenAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )

        FirebaseFirestore.getInstance()
            .collection("user_sessions")
            .document("${userId}_$deviceId")
            .set(payload, SetOptions.merge())
    }

    private fun createDeviceItem(title: String, subtitle: String): View {
        val card = layoutInflater.inflate(R.layout.item_device_session, containerDevices, false)
        card.findViewById<TextView>(R.id.txtDeviceTitle).text = title
        card.findViewById<TextView>(R.id.txtDeviceSubtitle).text = subtitle
        return card
    }

    private fun isCreditsEligibleClaim(claimType: String): Boolean {
        val normalized = claimType.lowercase(Locale.getDefault())
        return normalized.contains("nptel") || normalized.contains("course")
    }

    private fun getCurrentDeviceKey(): String {
        return Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
            .ifBlank { Build.MODEL + Build.BRAND }
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
            .set(
                mapOf(
                    "department" to department,
                    "semester" to semester,
                    "phone" to phone
                ),
                SetOptions.merge()
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

    private fun showGuestProfileState(
        emailText: Int,
        metaText: Int,
        logoutText: Int,
        logoutAction: () -> Unit
    ) {
        currentDepartment = ""
        currentSemester = ""
        currentPhone = ""
        txtProfileName.text = getString(R.string.profile_guest_name)
        txtProfileEmail.text = getString(emailText)
        txtProfileMeta.text = getString(metaText)
        btnEditProfile.isEnabled = false
        btnLogout.text = getString(logoutText)
        btnLogout.setOnClickListener { logoutAction() }
    }

    private data class CreditHistoryItem(
        val title: String,
        val credits: Int,
        val claimDate: String,
        val status: String
    )

    private data class DeviceSessionItem(
        val deviceName: String,
        val model: String,
        val lastSeenMillis: Long,
        val deviceId: String
    )

    companion object {
        private val dateTimeFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    }
}
