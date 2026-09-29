package com.awlaunch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.awlaunch.data.AppInfo
import com.awlaunch.data.AppRepository
import com.awlaunch.data.FavoritesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class LauncherViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = AppRepository(app)
    private val prefs = FavoritesRepository(app)

    val apps: StateFlow<List<AppInfo>> =
        repo.observeApps().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val pinned: StateFlow<Set<String>> =
        prefs.observePinned().stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val hidden: StateFlow<Set<String>> =
        prefs.observeHidden().stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val names: StateFlow<Map<String, String>> =
        prefs.observeNames().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val groups: StateFlow<Map<String, Set<String>>> =
        prefs.observeGroups().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun togglePin(key: String) = prefs.togglePin(key)
    fun toggleHide(key: String) = prefs.toggleHide(key)
    fun setName(key: String, name: String?) = prefs.setName(key, name)
    fun addAppToGroup(appKey: String, group: String) = prefs.addAppToGroup(appKey, group)
    fun removeAppFromGroup(appKey: String, group: String) = prefs.removeAppFromGroup(appKey, group)

    fun renameGroup(oldName: String, newName: String) = prefs.renameGroup(oldName, newName)
    fun deleteGroup(name: String) = prefs.deleteGroup(name)

    fun launch(app: AppInfo, bounds: android.graphics.Rect? = null) = repo.launch(app, bounds)
    fun openInfo(app: AppInfo, bounds: android.graphics.Rect? = null) = repo.openAppInfo(app, bounds)
}
