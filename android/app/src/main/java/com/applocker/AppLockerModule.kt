package com.applocker

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Settings
import com.facebook.react.bridge.*
import java.security.MessageDigest

class AppLockerModule(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

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
                // Filter system launcher
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
            val hash = hashPin(pin)
            getPrefs().edit().putString("pin_hash", hash).apply()
            promise.resolve(true)
        } catch (e: Exception) {
            promise.reject("PIN_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun verifyPin(pin: String, promise: Promise) {
        try {
            val storedHash = getPrefs().getString("pin_hash", null)
            if (storedHash == null) {
                promise.resolve(false)
                return
            }
            promise.resolve(hashPin(pin) == storedHash)
        } catch (e: Exception) {
            promise.reject("PIN_ERROR", e.message, e)
        }
    }

    @ReactMethod
    fun hasPin(promise: Promise) {
        try {
            val hash = getPrefs().getString("pin_hash", null)
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

    private fun getPrefs(): SharedPreferences {
        return reactApplicationContext.getSharedPreferences("applocker", Context.MODE_PRIVATE)
    }

    private fun hashPin(pin: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
