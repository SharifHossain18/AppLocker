package com.applocker

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AppLockerAccessibilityService : AccessibilityService() {

    private var lastPackageName: String? = null

    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                synchronized(unlockedPackages) {
                    unlockedPackages.clear()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenOffReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenOffReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (e: Exception) {
            Log.e("AppLocker", "Failed to unregister screenOffReceiver", e)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }
        serviceInfo = info
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Ignore our own app, system UI, and common system launchers
        val skipPackages = setOf(
            "com.applocker",
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher2",
            "com.android.launcher3",
            "com.google.android.apps.nexuslauncher",
            "com.sec.android.app.launcher",      // Samsung
            "com.miui.home",                      // Xiaomi
            "com.oppo.launcher",                  // OPPO
            "com.realme.launcher",                // Realme
            "com.vivo.launcher"                   // Vivo
        )
        if (skipPackages.contains(packageName)) return

        lastPackageName = packageName

        val prefs: SharedPreferences = getSharedPreferences("applocker", Context.MODE_PRIVATE)
        val lockedApps = prefs.getStringSet("locked_apps", emptySet()) ?: emptySet()

        if (lockedApps.contains(packageName)) {
            synchronized(unlockedPackages) {
                // Bug 5 Fix: Check if the package was unlocked and if the unlock is still valid
                // (within the same session — session is reset on screen-off)
                if (unlockedPackages.contains(packageName)) {
                    // Already unlocked in this session
                    return
                }
            }

            Log.d("AppLocker", "Locked app detected: $packageName")
            val intent = Intent(this, LockScreenActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("target_package", packageName)
            }
            startActivity(intent)
        }
    }

    override fun onInterrupt() {}

    companion object {
        val unlockedPackages = HashSet<String>()

        fun isServiceEnabled(context: Context): Boolean {
            val expectedComponentName = ComponentName(context, AppLockerAccessibilityService::class.java)
            val enabledServicesSetting = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledService = ComponentName.unflattenFromString(componentNameString)
                if (enabledService != null && enabledService == expectedComponentName) {
                    return true
                }
            }
            return false
        }
    }
}
