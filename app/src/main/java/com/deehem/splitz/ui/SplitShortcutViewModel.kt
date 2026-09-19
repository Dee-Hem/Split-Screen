package com.deehem.splitz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.deehem.splitz.data.AppDatabase
import com.deehem.splitz.data.AppInfo
import com.deehem.splitz.data.AppRepository
import com.deehem.splitz.data.SplitShortcut
import com.deehem.splitz.utils.ShortcutUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SplitShortcutViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: AppRepository
    val allShortcuts: kotlinx.coroutines.flow.Flow<List<SplitShortcut>>

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        val dao = AppDatabase.getDatabase(application).splitShortcutDao()
        repository = AppRepository(application, dao)
        allShortcuts = repository.allShortcuts
        loadInstalledApps()
        
        viewModelScope.launch {
            allShortcuts.collect { list ->
                try {
                    ShortcutUtils.updateDynamicShortcuts(getApplication(), list)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _installedApps.value = repository.getInstalledApps()
            _isLoading.value = false
        }
    }

    fun saveShortcut(name: String, topPkg: String, bottomPkg: String, folder: String? = null, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            val normalizedFolder = folder?.trim()?.ifEmpty { null }
            val id = repository.insertShortcut(
                SplitShortcut(
                    name = name,
                    topPackage = topPkg,
                    bottomPackage = bottomPkg,
                    folder = normalizedFolder
                )
            )
            onComplete(id)
        }
    }

    fun deleteShortcut(shortcut: SplitShortcut) {
        viewModelScope.launch {
            repository.deleteShortcut(shortcut)
        }
    }

    fun updateShortcut(shortcut: SplitShortcut, newName: String, newFolder: String?) {
        viewModelScope.launch {
            val normalizedFolder = newFolder?.trim()?.ifEmpty { null }
            val updated = shortcut.copy(name = newName, folder = normalizedFolder)
            repository.insertShortcut(updated)
            ShortcutUtils.updatePinnedShortcut(getApplication(), updated)
        }
    }

    fun renameFolder(oldFolder: String, newFolder: String) {
        val trimmed = newFolder.trim()
        if (trimmed.isEmpty() || trimmed.equals(oldFolder, ignoreCase = true)) return
        viewModelScope.launch {
            repository.renameFolder(oldFolder, trimmed)
        }
    }

    fun deleteFolder(folder: String) {
        viewModelScope.launch {
            repository.deleteFolder(folder)
        }
    }
}
