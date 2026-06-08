package com.applocker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest
import java.util.concurrent.Executor

class LockScreenActivity : FragmentActivity() {

    private var pinBuffer = StringBuilder()
    private var targetPackage: String? = null
    private var biometricPrompt: BiometricPrompt? = null
    private var biometricExecutor: Executor = ContextCompat.getMainExecutor(this)

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
        setupBiometricPrompt()
        tryBiometricAuth()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPackage = intent.getStringExtra("target_package")
        pinBuffer.clear()
        findViewById<TextView>(R.id.pin_dots).text = "○○○○"
        tryBiometricAuth()
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

    private fun setupBiometricPrompt() {
        biometricPrompt = BiometricPrompt(this, biometricExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                runOnUiThread { onBiometricSuccess() }
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                runOnUiThread { onBiometricError() }
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                runOnUiThread { onBiometricFailed() }
            }
        })
    }

    private fun tryBiometricAuth() {
        val prefs = getSharedPreferences("applocker", Context.MODE_PRIVATE)
        val hasPin = prefs.contains("pin_hash")
        val biometricManager = BiometricManager.from(this)

        if (hasPin && biometricManager.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS) {
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("AppLocker")
                .setSubtitle("Authenticate to unlock")
                .setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build()
            biometricPrompt?.authenticate(promptInfo)
        } else {
            findViewById<TextView>(R.id.pin_prompt).text = "Enter PIN to unlock"
        }
    }

    private fun onBiometricSuccess() {
        targetPackage?.let {
            synchronized(AppLockerAccessibilityService.unlockedPackages) {
                AppLockerAccessibilityService.unlockedPackages.add(it)
            }
        }
        finish()
    }

    private fun onBiometricError() {
        findViewById<TextView>(R.id.pin_prompt).text = "Biometric failed — Enter PIN"
    }

    private fun onBiometricFailed() {
        findViewById<TextView>(R.id.pin_prompt).text = "Not recognized — Enter PIN"
    }

    private fun updatePinDots(dots: TextView) {
        val filled = pinBuffer.toString().map { '●' }.joinToString("")
        val display = filled.padEnd(4, '○')
        dots.text = display
    }

    private fun verifyPin() {
        val prefs: SharedPreferences = getSharedPreferences("applocker", Context.MODE_PRIVATE)
        val storedHash = prefs.getString("pin_hash", null) ?: run {
            Toast.makeText(this, "PIN not set. Configure in app.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        val storedSaltB64 = prefs.getString("pin_salt", null) ?: run {
            Toast.makeText(this, "PIN data corrupted. Reset PIN in app.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
        val inputHash = hashPinWithSalt(pinBuffer.toString(), salt)

        if (inputHash == storedHash) {
            targetPackage?.let {
                synchronized(AppLockerAccessibilityService.unlockedPackages) {
                    AppLockerAccessibilityService.unlockedPackages.add(it)
                }
            }
            finish()
        } else {
            pinBuffer.clear()
            findViewById<TextView>(R.id.pin_dots).text = "○○○○"
            Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hashPinWithSalt(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val bytes = digest.digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    override fun onBackPressed() {
        // Block back button — user must enter PIN
    }
}
