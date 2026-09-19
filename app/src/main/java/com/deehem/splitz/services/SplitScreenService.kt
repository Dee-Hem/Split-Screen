package com.deehem.splitz.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

class SplitScreenService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Only invoked when split-screen toggle is requested
    }

    override fun onInterrupt() {
        // Interrupt handling
    }

    companion object {
        private const val TAG = "SplitzService"

        @Volatile
        var instance: SplitScreenService? = null
            private set

        /**
         * Checks whether the user has toggled the service ON in Android Settings.
         */
        fun isSettingsEnabled(context: Context): Boolean {
            val expectedComponentName = "${context.packageName}/${SplitScreenService::class.java.name}"
            val enabledServicesSetting = android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServicesSetting)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                if (componentNameString.equals(expectedComponentName, ignoreCase = true)) {
                    return true
                }
            }
            return false
        }

        /**
         * Checks whether the service is actively registered, connected, and bound in the
         * AccessibilityManager framework. This detects when Settings says ON, but the service
         * process was killed or unbound by Android.
         */
        fun isServiceLive(context: Context): Boolean {
            if (instance != null) return true
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val expectedComponentName = "${context.packageName}/${SplitScreenService::class.java.name}"
            return enabledServices.any { serviceInfo ->
                val id = serviceInfo.id
                val clsName = serviceInfo.resolveInfo?.serviceInfo?.name
                id?.equals(expectedComponentName, ignoreCase = true) == true ||
                clsName?.equals(SplitScreenService::class.java.name, ignoreCase = true) == true
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "SplitScreenService onCreate: Service process initialized.")
        instance = this
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "SplitScreenService onServiceConnected: Service successfully bound to system. Reconnection confirmed.")
        ServiceRecoveryHelper.onServiceConnected(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "SplitScreenService onDestroy: Service process destroyed / killed by Android system.")
        if (instance == this) {
            instance = null
        }
        ServiceRecoveryHelper.onServiceDisconnected(this)
    }

    fun toggleSplitScreen(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        }
        return false
    }
}
