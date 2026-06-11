package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.AppInfo
import com.example.data.AppRepository
import com.example.data.SplitShortcut
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
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _installedApps.value = repository.getInstalledApps()
            _isLoading.value = false
        }
    }

    fun saveShortcut(name: String, topPkg: String, bottomPkg: String, onComplete: (Long) -> Unit) {
        viewModelScope.launch {
            val id = repository.insertShortcut(
                SplitShortcut(name = name, topPackage = topPkg, bottomPackage = bottomPkg)
            )
            onComplete(id)
        }
    }

    fun deleteShortcut(shortcut: SplitShortcut) {
        viewModelScope.launch {
            repository.deleteShortcut(shortcut)
        }
    }
}
