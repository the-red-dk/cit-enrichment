package com.daksha.cit.enrichment

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity

class PolicyInfoActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_policy_info)

        findViewById<View>(R.id.btnBackPolicy).setOnClickListener {
            finish()
        }
    }
}
