package com.example.services

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class SplitScreenService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We only require the capability to invoke toggleSplitScreen
    }

    override fun onInterrupt() {
        // No interrupt handling necessary for simple toggling
    }

    companion object {
        @Volatile
        var instance: SplitScreenService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    fun toggleSplitScreen(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            return performGlobalAction(GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
        }
        return false
    }
}
