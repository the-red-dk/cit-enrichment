package com.daksha.cit.enrichment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        SessionManager.clearGuestMode(this)

        val btnLogin = findViewById<View>(R.id.btnLogin)
        val btnRegister = findViewById<View>(R.id.btnRegister)
        btnLogin.visibility = View.VISIBLE
        btnRegister.visibility = View.VISIBLE

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()

        if (BuildConfig.ENABLE_FIREBASE_AUTH && SessionManager.hasSignedInUser()) {
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}
