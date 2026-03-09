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
                R.id.btnApplyHackathon -> getString(R.string.apply_hackathon)
                R.id.btnApplySports -> getString(R.string.apply_sports)
                else -> getString(R.string.apply_other)
            }

            startActivity(NptelFormActivity.createIntent(this, claimType, getString(R.string.generic_claim_title, claimType)))
        }

        findViewById<View>(R.id.btnApplyHackathon).setOnClickListener(openClaimForm)
        findViewById<View>(R.id.btnApplySports).setOnClickListener(openClaimForm)
        findViewById<View>(R.id.btnApplyOther).setOnClickListener(openClaimForm)
    }
}
