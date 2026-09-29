package com.awlaunch.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

class AppRepository(private val context: Context) {

    private val launcherApps: LauncherApps =
        context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps

    private val selfPackage = context.packageName

    fun observeApps(): Flow<List<AppInfo>> = callbackFlow {
        fun snapshot(): List<AppInfo> {
            val users: List<UserHandle> = launcherApps.profiles.ifEmpty { listOf(Process.myUserHandle()) }
            val all = mutableListOf<AppInfo>()
            for (user in users) {
                val activities = launcherApps.getActivityList(null, user)
                for (a in activities) {
                    if (a.applicationInfo.packageName == selfPackage) continue
                    all += AppInfo(
                        label = a.label.toString(),
                        packageName = a.applicationInfo.packageName,
                        activityName = a.componentName.className,
                        user = user,
                        icon = a.getBadgedIcon(0)
                    )
                }
            }
            return all.sortedBy { it.label.lowercase() }
        }

        trySend(snapshot())

        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { trySend(snapshot()) }
            override fun onPackageAdded(packageName: String, user: UserHandle) { trySend(snapshot()) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { trySend(snapshot()) }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(snapshot()) }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) { trySend(snapshot()) }
        }
        val mainHandler = Handler(Looper.getMainLooper())
        launcherApps.registerCallback(callback, mainHandler)
        awaitClose { launcherApps.unregisterCallback(callback) }
    }.flowOn(Dispatchers.IO)

    fun launch(app: AppInfo, sourceBounds: android.graphics.Rect? = null) {
        val component = ComponentName(app.packageName, app.activityName)
        launcherApps.startMainActivity(component, app.user, sourceBounds, null)
    }

    fun openAppInfo(app: AppInfo, sourceBounds: android.graphics.Rect? = null) {
        launcherApps.startAppDetailsActivity(
            ComponentName(app.packageName, app.activityName),
            app.user,
            sourceBounds,
            null
        )
    }
}
