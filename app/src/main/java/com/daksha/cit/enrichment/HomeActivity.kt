package com.daksha.cit.enrichment

import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class HomeActivity : AppCompatActivity() {
    private lateinit var txtHomeSubtitle: TextView
    private lateinit var txtHomeCardAmount: TextView
    private lateinit var txtHomeCardHint: TextView
    private var dashboardListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        txtHomeSubtitle = findViewById(R.id.txtHomeSubtitle)
        txtHomeCardAmount = findViewById(R.id.txtHomeCardAmount)
        txtHomeCardHint = findViewById(R.id.txtHomeCardHint)

        findViewById<Button>(R.id.btnPolicyInfo).setOnClickListener {
            startActivity(Intent(this, PolicyInfoActivity::class.java))
        }

        findViewById<Button>(R.id.btnApply).setOnClickListener {
            startActivity(Intent(this, ApplyActivity::class.java))
        }

        findViewById<Button>(R.id.btnTrack3).setOnClickListener {
            startActivity(Intent(this, ClaimStatusActivity::class.java))
        }

        findViewById<Button>(R.id.btnNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationsActivity::class.java))
        }

        findViewById<Button>(R.id.btnProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<View>(R.id.btnAppInfo).setOnClickListener {
            showAppInfoDialog()
        }

    }

    override fun onStart() {
        super.onStart()
        if (BuildConfig.ENABLE_FIREBASE_AUTH && !SessionManager.hasSignedInUser()) {
            SessionManager.clearGuestMode(this)
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        if (SessionManager.isGuestMode(this)) {
            bindLocalDashboardSummary()
            return
        }

        if (BuildConfig.ENABLE_FIREBASE_AUTH) {
            SessionManager.ensureBackendSession(
                context = this,
                onReady = {
                    bindDashboardSummary()
                },
                onFailure = {
                    txtHomeCardAmount.text = getString(R.string.home_claims_unavailable)
                    txtHomeCardHint.text = it
                }
            )
        } else {
            bindDashboardSummary()
        }
    }

    override fun onStop() {
        dashboardListener?.remove()
        dashboardListener = null
        super.onStop()
    }

    private fun bindDashboardSummary() {
        txtHomeSubtitle.text = if (SessionManager.isGuestMode(this)) {
            getString(R.string.home_subtitle)
        } else {
            getString(R.string.home_subtitle_user, SessionManager.getDisplayName(this))
        }

        dashboardListener?.remove()
        dashboardListener = FirebaseFirestore.getInstance()
            .collection("claims")
            .whereEqualTo("userKey", SessionManager.getActiveUserKey(this))
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || snapshot.isEmpty) {
                    txtHomeCardAmount.text = getString(R.string.home_claims_unavailable)
                    txtHomeCardHint.text = getString(R.string.home_claims_hint)
                    return@addSnapshotListener
                }

                val totalClaims = snapshot.size()
                val pendingClaims = snapshot.documents.count {
                    (it.getString("status") ?: getString(R.string.claim_status_pending)).equals(
                        getString(R.string.claim_status_pending),
                        ignoreCase = true
                    )
                }

                txtHomeCardAmount.text = resources.getQuantityString(R.plurals.home_claim_count, totalClaims, totalClaims)
                txtHomeCardHint.text = resources.getQuantityString(R.plurals.home_pending_count, pendingClaims, pendingClaims)
            }
    }

    private fun bindLocalDashboardSummary() {
        dashboardListener?.remove()
        dashboardListener = null

        txtHomeSubtitle.text = getString(R.string.home_subtitle)
        val claims = LocalClaimStore.getClaims(this)
        val totalClaims = claims.size
        val pendingClaims = claims.count {
            it.status.equals(getString(R.string.claim_status_pending), ignoreCase = true)
        }

        txtHomeCardAmount.text = resources.getQuantityString(R.plurals.home_claim_count, totalClaims, totalClaims)
        txtHomeCardHint.text = resources.getQuantityString(R.plurals.home_pending_count, pendingClaims, pendingClaims)
    }

    private fun showAppInfoDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_app_info, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
        dialog.show()
    }
}
