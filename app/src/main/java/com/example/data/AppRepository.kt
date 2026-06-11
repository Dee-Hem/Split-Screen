package com.example.data

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppInfo(
    val packageName: String,
    val label: String,
    val icon: Drawable? = null
)

class AppRepository(private val context: Context, private val dao: SplitShortcutDao) {

    val allShortcuts = dao.getAllShortcuts()

    suspend fun getInstalledApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
        mainIntent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        
        val resolvedInfos = pm.queryIntentActivities(mainIntent, 0)
        resolvedInfos.map {
            AppInfo(
                packageName = it.activityInfo.packageName,
                label = it.loadLabel(pm).toString(),
                icon = it.loadIcon(pm)
            )
        }.sortedBy { it.label }
    }

    suspend fun insertShortcut(shortcut: SplitShortcut): Long {
        return dao.insertShortcut(shortcut)
    }

    suspend fun deleteShortcut(shortcut: SplitShortcut) {
        dao.deleteShortcut(shortcut)
    }

    suspend fun getShortcutById(id: Long) = dao.getShortcutById(id)
}
