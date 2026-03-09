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
            if (BuildConfig.ENABLE_FIREBASE_AUTH) {
                val destination = if (SessionManager.hasSignedInUser()) {
                    HomeActivity::class.java
                } else {
                    MainActivity::class.java
                }
                startActivity(Intent(this, destination))
            } else {
                SessionManager.enableGuestMode(this)
                startActivity(Intent(this, HomeActivity::class.java))
            }
            finish()
        }, 1500L)
    }
}
