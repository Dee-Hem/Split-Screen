package com.example.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.example.MainActivity
import com.example.data.SplitShortcut

object ShortcutUtils {

    fun createPinnedShortcut(context: Context, shortcut: SplitShortcut) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(context, "Requires Android 8.0 or above for pin shortcuts", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)

            if (shortcutManager != null && shortcutManager.isRequestPinShortcutSupported) {
                val intent = Intent(context, MainActivity::class.java).apply {
                    action = "LAUNCH_SPLIT"
                    putExtra("topPackage", shortcut.topPackage)
                    putExtra("bottomPackage", shortcut.bottomPackage)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }

                // Create a unique ID for the shortcut
                val shortcutId = "split_shortcut_${shortcut.id}"

                // Try to use modern custom split logo, or fallback gracefully
                val icon = try {
                    Icon.createWithResource(context, com.example.R.drawable.split_screen_logo_1780993093646)
                } catch (e: Exception) {
                    Icon.createWithResource(context, android.R.drawable.ic_menu_share)
                }

                val shortcutInfo = ShortcutInfo.Builder(context, shortcutId)
                    .setShortLabel(shortcut.name)
                    .setLongLabel("Split: ${shortcut.name}")
                    .setIcon(icon)
                    .setIntent(intent)
                    .build()

                val pinnedShortcutCallbackIntent = shortcutManager.createShortcutResultIntent(shortcutInfo)

                val successCallback = PendingIntent.getBroadcast(
                    context, 0, pinnedShortcutCallbackIntent, PendingIntent.FLAG_IMMUTABLE
                )

                shortcutManager.requestPinShortcut(shortcutInfo, successCallback.intentSender)
                Toast.makeText(context, "Requesting home screen shortcut placement...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Shortcut pinning not supported by your launcher", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
