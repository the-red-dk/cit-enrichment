package com.daksha.cit.enrichment

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            val destination = if (BuildConfig.ENABLE_FIREBASE_AUTH && SessionManager.hasSignedInUser()) {
                HomeActivity::class.java
            } else {
                MainActivity::class.java
            }

            startActivity(Intent(this, destination))
            finish()
        }, 1500L)
    }
}
