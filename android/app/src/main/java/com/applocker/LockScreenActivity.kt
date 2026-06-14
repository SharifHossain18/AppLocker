package com.applocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.MessageDigest

class LockScreenActivity : FragmentActivity() {

    private var pinBuffer = StringBuilder()
    private var targetPackage: String? = null
    private var pinLength = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on and show over lockscreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            )
        }

        setContentView(R.layout.lock_screen)

        targetPackage = intent.getStringExtra("target_package")

        // Bug 3 Fix: Read the stored PIN length to know when auto-verify should trigger
        val prefs = getSharedPreferences("applocker", Context.MODE_PRIVATE)
        pinLength = prefs.getInt("pin_length", 4).coerceIn(4, 6)

        setupPinPad()

        // Bug 2 Fix: Try biometric unlock first if available
        tryBiometricUnlock()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        targetPackage = intent.getStringExtra("target_package")
        pinBuffer.clear()
        updatePinDots()
    }

    // Bug 2 Fix: Full biometric implementation using AndroidX BiometricPrompt
    private fun tryBiometricUnlock() {
        val biometricManager = BiometricManager.from(this)
        val canAuthenticate = biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
        )

        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            // No biometric available, fall through to PIN only
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                grantAccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // User cancelled or biometric error — they can still use PIN
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                // Wrong biometric — stay on screen, let them try again or use PIN
            }
        }

        val prompt = BiometricPrompt(this, executor, callback)
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock App")
            .setSubtitle(targetPackage ?: "Protected by AppLocker")
            .setDescription("Use your fingerprint or face to unlock")
            .setNegativeButtonText("Use PIN instead")
            .build()

        prompt.authenticate(promptInfo)
    }

    private fun setupPinPad() {
        val buttons = listOf(
            R.id.btn_0 to "0", R.id.btn_1 to "1", R.id.btn_2 to "2",
            R.id.btn_3 to "3", R.id.btn_4 to "4", R.id.btn_5 to "5",
            R.id.btn_6 to "6", R.id.btn_7 to "7", R.id.btn_8 to "8",
            R.id.btn_9 to "9"
        )

        for ((id, digit) in buttons) {
            findViewById<Button>(id).setOnClickListener {
                // Bug 3 Fix: Allow up to the actual stored PIN length (4-6)
                if (pinBuffer.length < pinLength) {
                    pinBuffer.append(digit)
                    updatePinDots()
                    // Auto-verify only when buffer matches the exact saved PIN length
                    if (pinBuffer.length == pinLength) {
                        verifyPin()
                    }
                }
            }
        }

        findViewById<Button>(R.id.btn_backspace).setOnClickListener {
            if (pinBuffer.isNotEmpty()) {
                pinBuffer.deleteCharAt(pinBuffer.length - 1)
                updatePinDots()
            }
        }
    }

    private fun updatePinDots() {
        val dots = findViewById<TextView>(R.id.pin_dots)
        val filled = "●".repeat(pinBuffer.length)
        val empty = "○".repeat(pinLength - pinBuffer.length)
        dots.text = filled + empty
    }

    private fun verifyPin() {
        val prefs: SharedPreferences = getSharedPreferences("applocker", Context.MODE_PRIVATE)
        val storedHash = prefs.getString("pin_hash", null) ?: run {
            Toast.makeText(this, "PIN not set. Configure in AppLocker app.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val inputHash = hashPin(pinBuffer.toString())

        if (inputHash == storedHash) {
            grantAccess()
        } else {
            pinBuffer.clear()
            updatePinDots()
            Toast.makeText(this, "Wrong PIN — try again", Toast.LENGTH_SHORT).show()
        }
    }

    // Bug 5 Fix: Centralized unlock method — adds to unlockedPackages safely
    private fun grantAccess() {
        targetPackage?.let { pkg ->
            synchronized(AppLockerAccessibilityService.unlockedPackages) {
                AppLockerAccessibilityService.unlockedPackages.add(pkg)
            }
        }
        finish()
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Block back button — user must authenticate to proceed
    }
}
