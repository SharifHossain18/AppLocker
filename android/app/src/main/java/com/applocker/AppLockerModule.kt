package com.applocker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.facebook.react.bridge.*
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AppLockerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    companion object {
        private const val KEY_ALIAS = "applocker_pin_key"
        private const val PREFS_NAME = "applocker"
        private const val PIN_HASH_KEY = "pin_hash"
        private const val PIN_SALT_KEY = "pin_salt"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 16
    }

    override fun getName(): String = "AppLockerModule"

    @ReactMethod
    fun getInstalledApps(promise: Promise) {
        try {
            val pm = reactApplicationContext.packageManager
            val intent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val apps = mutableListOf<WritableMap>()
            val resolved = pm.queryIntentActivities(intent, 0)

            for (ri in resolved) {
                val pkg = ri.activityInfo.packageName
                val label = ri.loadLabel(pm).toString()
                val iconResId = ri.activityInfo.icon

                val app = Arguments.createMap().apply {
                    putString("packageName", pkg)
                    putString("name", label)
                    putInt("iconResId", iconResId)
                }
                if (!pkg.startsWith("com.android") && !pkg.startsWith("android")) {
                    apps.add(app)
                }
            }

            promise.resolve(Arguments.fromList(apps))
        } catch (e: Exception) {
            promise.reject("GET_APPS_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun lockApp(packageName: String, promise: Promise) {
        try {
            val prefs = getPrefs()
            val locked = prefs.getStringSet("locked_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            locked.add(packageName)
            prefs.edit().putStringSet("locked_apps", locked).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("LOCK_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun unlockApp(packageName: String, promise: Promise) {
        try {
            val prefs = getPrefs()
            val locked = prefs.getStringSet("locked_apps", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
            locked.remove(packageName)
            prefs.edit().putStringSet("locked_apps", locked).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("UNLOCK_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun getLockedApps(promise: Promise) {
        try {
            val prefs = getPrefs()
            val locked = prefs.getStringSet("locked_apps", emptySet()) ?: emptySet()
            promise.resolve(Arguments.fromList(locked.toList()))
        } catch (e: Exception) {
            promise.reject("GET_LOCKED_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun setPin(pin: String, promise: Promise) {
        try {
            if (pin.length < 4) {
                promise.reject("PIN_TOO_SHORT", "PIN must be at least 4 digits")
                return
            }
            val salt = generateSalt()
            val hash = hashPinWithSalt(pin, salt)
            val prefs = getPrefs()
            prefs.edit()
                .putString(PIN_HASH_KEY, hash)
                .putString(PIN_SALT_KEY, Base64.encodeToString(salt, Base64.NO_WRAP))
                .apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("PIN_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun verifyPin(pin: String, promise: Promise) {
        try {
            val prefs = getPrefs()
            val storedHash = prefs.getString(PIN_HASH_KEY, null)
            val storedSaltB64 = prefs.getString(PIN_SALT_KEY, null)
            if (storedHash == null || storedSaltB64 == null) {
                promise.resolve(false)
                return
            }
            val salt = Base64.decode(storedSaltB64, Base64.NO_WRAP)
            val inputHash = hashPinWithSalt(pin, salt)
            promise.resolve(inputHash == storedHash)
        } catch (e: Exception) {
            promise.reject("PIN_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun hasPin(promise: Promise) {
        try {
            val prefs = getPrefs()
            val hash = prefs.getString(PIN_HASH_KEY, null)
            promise.resolve(hash != null)
        } catch (e: Exception) {
            promise.reject("PIN_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun isAccessibilityServiceEnabled(promise: Promise) {
        try {
            promise.resolve(AppLockerAccessibilityService.isServiceEnabled(reactApplicationContext))
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    @ReactMethod
    fun openAccessibilitySettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactApplicationContext.startActivity(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SETTINGS_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun openAppOverlaySettings(promise: Promise) {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            reactApplicationContext.startActivity(intent)
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("SETTINGS_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun isBiometricAvailable(promise: Promise) {
        try {
            val biometricManager = reactApplicationContext.getSystemService(android.hardware.biometrics.BiometricManager::class.java)
            val result = when (biometricManager.canAuthenticate()) {
                android.hardware.biometrics.BiometricManager.BIOMETRIC_SUCCESS -> true
                else -> false
            }
            promise.resolve(result)
        } catch (e: Exception) {
            promise.resolve(false)
        }
    }

    private fun getPrefs(): SharedPreferences {
        return reactApplicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun generateSalt(): ByteArray {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return salt
    }

    private fun hashPinWithSalt(pin: String, salt: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val bytes = digest.digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
