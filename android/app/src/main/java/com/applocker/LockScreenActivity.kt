package com.applocker

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import java.security.MessageDigest

class LockScreenActivity : Activity() {

    private var pinBuffer = StringBuilder()
    private var targetPackage: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        setContentView(R.layout.lock_screen)

        targetPackage = intent.getStringExtra("target_package")

        findViewById<TextView>(R.id.pin_prompt).apply {
            setOnClickListener { /* prevent dismiss */ }
        }

        setupPinPad()
    }

    private fun setupPinPad() {
        val pinDots = findViewById<TextView>(R.id.pin_dots)

        val buttons = listOf(
            R.id.btn_0 to "0", R.id.btn_1 to "1", R.id.btn_2 to "2",
            R.id.btn_3 to "3", R.id.btn_4 to "4", R.id.btn_5 to "5",
            R.id.btn_6 to "6", R.id.btn_7 to "7", R.id.btn_8 to "8",
            R.id.btn_9 to "9"
        )

        for ((id, digit) in buttons) {
            findViewById<Button>(id).setOnClickListener {
                if (pinBuffer.length < 4) {
                    pinBuffer.append(digit)
                    updatePinDots(pinDots)
                    if (pinBuffer.length == 4) {
                        verifyPin()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btn_backspace).setOnClickListener {
            if (pinBuffer.isNotEmpty()) {
                pinBuffer.deleteCharAt(pinBuffer.length - 1)
                updatePinDots(pinDots)
            }
        }
    }

    private fun updatePinDots(dots: TextView) {
        val display = pinBuffer.toString().padEnd(4, '○')
        dots.text = display.replaceRange(IntRange(0, pinBuffer.length.coerceAtMost(4) - 1)) {
            pinBuffer.toString().map { '●' }.joinToString("")
        }
    }

    private fun verifyPin() {
        val prefs: SharedPreferences = getSharedPreferences("applocker", Context.MODE_PRIVATE)
        val storedHash = prefs.getString("pin_hash", null) ?: run {
            Toast.makeText(this, "PIN not set. Configure in app.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val inputHash = hashPin(pinBuffer.toString())

        if (inputHash == storedHash) {
            finish()
        } else {
            pinBuffer.clear()
            findViewById<TextView>(R.id.pin_dots).text = "○○○○"
            Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onBackPressed() {
        // Block back button — user must enter PIN
    }
}
