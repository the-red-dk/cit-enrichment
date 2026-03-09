package com.daksha.cit.enrichment

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore

class ClaimDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_claim_detail)

        findViewById<View>(R.id.btnBackClaimDetail).setOnClickListener {
            finish()
        }

        val claimId = intent.getStringExtra(EXTRA_CLAIM_ID).orEmpty()
        if (claimId.isBlank()) {
            finish()
            return
        }

        loadClaim(claimId)
    }

    private fun loadClaim(claimId: String) {
        val activeUserKey = SessionManager.getActiveUserKey(this)
        val txtClaimType = findViewById<TextView>(R.id.txtDetailClaimType)
        val txtTitle = findViewById<TextView>(R.id.txtDetailTitle)
        val txtStatus = findViewById<TextView>(R.id.txtDetailStatus)
        val txtAmount = findViewById<TextView>(R.id.txtDetailAmount)
        val txtDate = findViewById<TextView>(R.id.txtDetailDate)
        val txtDescription = findViewById<TextView>(R.id.txtDetailDescription)
        val txtDocuments = findViewById<TextView>(R.id.txtDetailDocuments)

        FirebaseFirestore.getInstance()
            .collection("claims")
            .document(claimId)
            .get()
            .addOnSuccessListener { document ->
                if (!document.exists()) {
                    txtDescription.text = getString(R.string.claims_load_failed)
                    return@addOnSuccessListener
                }

                if (document.getString("userKey") != activeUserKey) {
                    txtDescription.text = getString(R.string.error_claim_access_denied)
                    txtDocuments.text = getString(R.string.claim_detail_no_documents)
                    return@addOnSuccessListener
                }

                val claimType = document.getString("claimType").orEmpty().ifBlank { getString(R.string.claim_type_unknown) }
                val title = document.getString("title").orEmpty().ifBlank { getString(R.string.claim_title_unknown) }
                val status = document.getString("status").orEmpty().ifBlank { getString(R.string.claim_status_pending) }
                val amount = document.getString("amountPaid").orEmpty().ifBlank { "-" }
                val date = document.getString("claimDate").orEmpty().ifBlank {
                    (document.getTimestamp("submittedAt") ?: Timestamp.now()).toDate().toString()
                }
                val description = document.getString("remarks").orEmpty().ifBlank { getString(R.string.claim_detail_no_description) }
                val certificateUrl = document.getString("certificateUrl").orEmpty()
                val paymentUrl = document.getString("paymentUrl").orEmpty()
                val certificatePath = document.getString("certificateStoragePath").orEmpty()
                val paymentPath = document.getString("paymentStoragePath").orEmpty()
                val certificateName = document.getString("certificateFileName").orEmpty()
                val paymentName = document.getString("paymentFileName").orEmpty()

                txtClaimType.text = claimType
                txtTitle.text = title
                txtStatus.text = getString(R.string.claim_detail_status_value, status)
                txtAmount.text = getString(R.string.claim_detail_amount_value, amount)
                txtDate.text = getString(R.string.claim_detail_date_value, date)
                txtDescription.text = description
                txtDocuments.text = listOf(
                    formatDocumentLine(certificateName, certificateUrl, certificatePath),
                    formatDocumentLine(paymentName, paymentUrl, paymentPath)
                )
                    .filter { it.isNotBlank() }
                    .joinToString("\n\n")
                    .ifBlank { getString(R.string.claim_detail_no_documents) }
            }
            .addOnFailureListener {
                txtDescription.text = getString(R.string.claims_load_failed)
            }
    }

    private fun formatDocumentLine(fileName: String, url: String, storagePath: String): String {
        val resolvedLocation = url.ifBlank { storagePath }
        if (resolvedLocation.isBlank()) {
            return ""
        }

        return listOf(fileName, resolvedLocation)
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    companion object {
        const val EXTRA_CLAIM_ID = "extra_claim_id"
    }
}