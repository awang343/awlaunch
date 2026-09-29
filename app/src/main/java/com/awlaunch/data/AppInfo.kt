package com.awlaunch.data

import android.graphics.drawable.Drawable
import android.os.UserHandle

data class AppInfo(
    val label: String,
    val packageName: String,
    val activityName: String,
    val user: UserHandle,
    val icon: Drawable
) {
    val key: String get() = "$packageName/$activityName@${user.hashCode()}"
}
