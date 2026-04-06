package com.daksha.cit.enrichment

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class ApplyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apply)

        findViewById<View>(R.id.btnBackApply).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.btnApplyNptel).setOnClickListener {
            startActivity(NptelFormActivity.createIntent(this, getString(R.string.apply_nptel), getString(R.string.nptel_title)))
        }

        val openClaimForm = View.OnClickListener { view ->
            val claimType = when (view.id) {
                R.id.btnApplyGate -> getString(R.string.apply_gate)
                R.id.btnApplyHackathon -> getString(R.string.apply_hackathon)
                R.id.btnApplySports -> getString(R.string.apply_sports)
                R.id.btnApplyPatent -> getString(R.string.apply_patent)
                R.id.btnApplyPublication -> getString(R.string.apply_publication)
                else -> getString(R.string.apply_conferences)
            }

            startActivity(NptelFormActivity.createIntent(this, claimType, getString(R.string.generic_claim_title, claimType)))
        }

        findViewById<View>(R.id.btnApplyGate).setOnClickListener(openClaimForm)
        findViewById<View>(R.id.btnApplyHackathon).setOnClickListener(openClaimForm)
        findViewById<View>(R.id.btnApplySports).setOnClickListener(openClaimForm)
        findViewById<View>(R.id.btnApplyPatent).setOnClickListener(openClaimForm)
        findViewById<View>(R.id.btnApplyPublication).setOnClickListener(openClaimForm)
        findViewById<View>(R.id.btnApplyConferences).setOnClickListener(openClaimForm)
    }
}
