package com.lbc.breadcrumb.capture

import android.app.Activity
import android.content.pm.PackageManager

object SourceHelper {
    fun getSource(activity: Activity): Pair<String?, String?> {
        val referrer = activity.referrer ?: return Pair(null, null)
        if (referrer.scheme != "android-app") return Pair(null, null)
        
        val packageName = referrer.host ?: return Pair(null, null)
        if (packageName == "com.android.systemui") return Pair(null, null)

        var label: String? = null
        try {
            val pm = activity.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            label = pm.getApplicationLabel(appInfo).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // ignore
        }

        return Pair(packageName, label)
    }
}
