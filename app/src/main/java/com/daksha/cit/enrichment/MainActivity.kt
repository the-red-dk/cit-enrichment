package com.daksha.cit.enrichment

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnLogin = findViewById<View>(R.id.btnLogin)
        val btnRegister = findViewById<View>(R.id.btnRegister)

        if (BuildConfig.ENABLE_FIREBASE_AUTH) {
            btnLogin.visibility = View.VISIBLE
            btnRegister.visibility = View.VISIBLE

            findViewById<Button>(R.id.btnLogin).setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
            }

            findViewById<Button>(R.id.btnRegister).setOnClickListener {
                startActivity(Intent(this, RegisterActivity::class.java))
            }
        } else {
            btnLogin.visibility = View.GONE
            btnRegister.visibility = View.GONE
        }

        findViewById<Button>(R.id.btnContinue).setOnClickListener {
            SessionManager.enableGuestMode(this)
            if (BuildConfig.ENABLE_FIREBASE_AUTH) {
                SessionManager.ensureBackendSession(
                    context = this,
                    onReady = {
                        startActivity(Intent(this, HomeActivity::class.java))
                    },
                    onFailure = { message ->
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                )
            } else {
                startActivity(Intent(this, HomeActivity::class.java))
            }
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
