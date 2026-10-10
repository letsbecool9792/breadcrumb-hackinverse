package com.lbc.breadcrumb.capture

import android.app.Activity
import android.content.pm.PackageManager

object SourceHelper {

    /**
     * The app that shared to [activity], as (package, name), resolved now: an
     * app uninstalled later has no name to read. [Sources] decides what counts.
     */
    fun getSource(activity: Activity): Pair<String?, String?> {
        val referrer = activity.referrer ?: return Pair(null, null)
        if (referrer.scheme != "android-app") return Pair(null, null)
        val packageName = referrer.host ?: return Pair(null, null)

        val label = try {
            val pm = activity.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
        return Sources.of(packageName, label, activity.packageName)
    }
}
