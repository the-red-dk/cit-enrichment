package com.daksha.cit.enrichment

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class NotificationsActivity : AppCompatActivity() {
    private lateinit var containerNotifications: LinearLayout
    private lateinit var progressNotifications: ProgressBar
    private var notificationsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        containerNotifications = findViewById(R.id.containerNotifications)
        progressNotifications = findViewById(R.id.progressNotifications)

        findViewById<View>(R.id.btnBackNotifications).setOnClickListener {
            finish()
        }

    }

    override fun onStart() {
        super.onStart()
        if (SessionManager.isGuestMode(this)) {
            renderLocalNotifications()
            return
        }

        if (BuildConfig.ENABLE_FIREBASE_AUTH) {
            SessionManager.ensureBackendSession(
                context = this,
                onReady = {
                    observeNotifications()
                },
                onFailure = {
                    progressNotifications.visibility = View.GONE
                    containerNotifications.removeAllViews()
                    val errorView = TextView(this)
                    errorView.text = it
                    containerNotifications.addView(errorView)
                }
            )
        } else {
            observeNotifications()
        }
    }

    override fun onStop() {
        notificationsListener?.remove()
        notificationsListener = null
        super.onStop()
    }

    private fun observeNotifications() {
        progressNotifications.visibility = View.VISIBLE
        containerNotifications.removeAllViews()
        notificationsListener?.remove()

        notificationsListener = FirebaseFirestore.getInstance()
            .collection("claims")
            .whereEqualTo("userKey", SessionManager.getActiveUserKey(this))
            .addSnapshotListener { snapshot, error ->
                progressNotifications.visibility = View.GONE

                if (error != null) {
                    renderLocalNotifications()
                    return@addSnapshotListener
                }

                val safeSnapshot = snapshot ?: run {
                    renderLocalNotifications()
                    return@addSnapshotListener
                }

                containerNotifications.removeAllViews()

                if (safeSnapshot.isEmpty) {
                    renderLocalNotifications()
                    return@addSnapshotListener
                }

                val recentClaims = safeSnapshot.documents.sortedByDescending {
                    it.getTimestamp("submittedAt") ?: Timestamp.now()
                }.take(5)

                for (doc in recentClaims) {
                    val item = layoutInflater.inflate(R.layout.item_claim, containerNotifications, false)
                    val title = item.findViewById<TextView>(R.id.claimTitle)
                    val subtitle = item.findViewById<TextView>(R.id.claimSubtitle)
                    val claimType = doc.getString("claimType") ?: getString(R.string.claim_type_unknown)
                    val status = doc.getString("status") ?: getString(R.string.claim_status_pending)

                    title.text = getString(R.string.notification_claim_title, claimType)
                    subtitle.text = getString(R.string.notification_claim_subtitle, status)
                    containerNotifications.addView(item)
                }
            }
    }

    private fun renderLocalNotifications() {
        progressNotifications.visibility = View.GONE
        containerNotifications.removeAllViews()
        notificationsListener?.remove()
        notificationsListener = null

        val claims = LocalClaimStore.getClaims(this).take(5)
        if (claims.isEmpty()) {
            val emptyView = TextView(this)
            emptyView.text = getString(R.string.notifications_empty_logged)
            containerNotifications.addView(emptyView)
            return
        }

        for (claim in claims) {
            val item = layoutInflater.inflate(R.layout.item_claim, containerNotifications, false)
            val title = item.findViewById<TextView>(R.id.claimTitle)
            val subtitle = item.findViewById<TextView>(R.id.claimSubtitle)

            title.text = getString(R.string.notification_claim_title, claim.claimType.ifBlank { getString(R.string.claim_type_unknown) })
            subtitle.text = getString(R.string.notification_claim_subtitle, claim.status.ifBlank { getString(R.string.claim_status_pending) })
            containerNotifications.addView(item)
        }
    }
}
