package com.daksha.cit.enrichment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClaimStatusActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout
    private lateinit var progressBar: View
    private var claimsListener: ListenerRegistration? = null
    private val submittedAtFormatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_claim_status)

        container = findViewById(R.id.containerClaims)
        progressBar = findViewById(R.id.progressClaims)

        findViewById<View>(R.id.btnBackStatus).setOnClickListener {
            finish()
        }

    }

    override fun onStart() {
        super.onStart()
        if (SessionManager.isGuestMode(this)) {
            renderLocalClaims()
            return
        }

        if (BuildConfig.ENABLE_FIREBASE_AUTH) {
            SessionManager.ensureBackendSession(
                context = this,
                onReady = {
                    observeClaims()
                },
                onFailure = {
                    progressBar.visibility = View.GONE
                    container.removeAllViews()
                    val errorView = TextView(this)
                    errorView.text = it
                    container.addView(errorView)
                }
            )
        } else {
            observeClaims()
        }
    }

    override fun onStop() {
        claimsListener?.remove()
        claimsListener = null
        super.onStop()
    }

    private fun observeClaims() {
        progressBar.visibility = View.VISIBLE
        container.removeAllViews()
        claimsListener?.remove()

        claimsListener = FirebaseFirestore.getInstance()
            .collection("claims")
            .whereEqualTo("userKey", SessionManager.getActiveUserKey(this))
            .addSnapshotListener { snapshot, error ->
                progressBar.visibility = View.GONE
                if (error != null) {
                    renderLocalClaims()
                    return@addSnapshotListener
                }

                val safeSnapshot = snapshot ?: run {
                    renderLocalClaims()
                    return@addSnapshotListener
                }

                container.removeAllViews()
                if (safeSnapshot.isEmpty) {
                    renderLocalClaims()
                    return@addSnapshotListener
                }

                val documents = safeSnapshot.documents.sortedByDescending { doc ->
                    doc.getTimestamp("submittedAt")?.toDate()?.time
                        ?: doc.getLong("submittedAtEpochMs")
                        ?: 0L
                }

                for (doc in documents) {
                    val item = layoutInflater.inflate(R.layout.item_claim, container, false)
                    val title = item.findViewById<TextView>(R.id.claimTitle)
                    val subtitle = item.findViewById<TextView>(R.id.claimSubtitle)
                    val meta = item.findViewById<TextView>(R.id.claimMeta)

                    val claimType = doc.getString("claimType") ?: getString(R.string.claim_type_unknown)
                    val courseName = doc.getString("title") ?: doc.getString("courseName") ?: getString(R.string.claim_title_unknown)
                    val status = doc.getString("status") ?: getString(R.string.claim_status_pending)
                    val amount = doc.getString("amountPaid") ?: "-"
                    val examDate = doc.getString("examDate") ?: doc.getString("claimDate") ?: getString(R.string.claim_date_unknown)
                    val submittedAtDate = doc.getTimestamp("submittedAt")?.toDate()
                        ?: doc.getLong("submittedAtEpochMs")?.let { Date(it) }
                    val submittedAtText = formatSubmittedAt(submittedAtDate)

                    title.text = "$courseName ($claimType)"
                    subtitle.text = getString(R.string.claim_list_subtitle, status, amount)
                    meta.text = buildString {
                        append(getString(R.string.claim_list_exam_date, examDate))
                        append("\n")
                        append(getString(R.string.claim_list_submitted_at, submittedAtText))
                    }

                    item.setOnClickListener {
                        startActivity(Intent(this, ClaimDetailActivity::class.java).putExtra(ClaimDetailActivity.EXTRA_CLAIM_ID, doc.id))
                    }

                    container.addView(item)
                }
            }
    }

    private fun renderLocalClaims() {
        progressBar.visibility = View.GONE
        container.removeAllViews()
        claimsListener?.remove()
        claimsListener = null

        val claims = LocalClaimStore.getClaims(this)
        if (claims.isEmpty()) {
            val emptyView = TextView(this)
            emptyView.text = getString(R.string.claims_empty)
            container.addView(emptyView)
            return
        }

        for (claim in claims) {
            val item = layoutInflater.inflate(R.layout.item_claim, container, false)
            val title = item.findViewById<TextView>(R.id.claimTitle)
            val subtitle = item.findViewById<TextView>(R.id.claimSubtitle)
            val meta = item.findViewById<TextView>(R.id.claimMeta)

            title.text = "${claim.title} (${claim.claimType})"
            subtitle.text = getString(R.string.claim_list_subtitle, claim.status, claim.amountPaid.ifBlank { "-" })
            val examDate = claim.claimDate.ifBlank { getString(R.string.claim_date_unknown) }
            val submittedAtText = formatSubmittedAt(Date(claim.submittedAtEpochMs))
            meta.text = buildString {
                append(getString(R.string.claim_list_exam_date, examDate))
                append("\n")
                append(getString(R.string.claim_list_submitted_at, submittedAtText))
            }

            item.setOnClickListener {
                startActivity(Intent(this, ClaimDetailActivity::class.java).putExtra(ClaimDetailActivity.EXTRA_CLAIM_ID, claim.id))
            }

            container.addView(item)
        }
    }

    private fun formatSubmittedAt(date: Date?): String {
        return if (date == null) {
            getString(R.string.claim_date_unknown)
        } else {
            submittedAtFormatter.format(date)
        }
    }
}
