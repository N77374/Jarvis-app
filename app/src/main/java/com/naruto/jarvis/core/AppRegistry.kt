package com.naruto.jarvis.core

import android.content.Context
import android.content.Intent

/**
 * AppRegistry
 * ---------------------------------------------------------------
 * Replaces the old hardcoded "whatsapp -> com.whatsapp" style map.
 * Instead, this queries Android directly for every launchable app
 * currently installed, builds a name -> package lookup once, and
 * matches spoken app names against real installed app labels —
 * so "open X" works for anything on the phone, no registration
 * needed, and it stays current automatically as apps are installed
 * or removed.
 */
object AppRegistry {

    private var cache: Map<String, String>? = null

    private fun installedApps(context: Context): Map<String, String> {
        cache?.let { return it }
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = pm.queryIntentActivities(intent, 0)

        val map = mutableMapOf<String, String>()
        for (r in resolved) {
            val label = r.loadLabel(pm).toString().lowercase().trim()
            val pkg = r.activityInfo.packageName
            if (label.isNotBlank()) map[label] = pkg
        }
        cache = map
        return map
    }

    /** Call this if the user installs/uninstalls apps while Jarvis is running. */
    fun refresh(context: Context) {
        cache = null
        installedApps(context)
    }

    /**
     * Finds the best-matching installed app's package name for a spoken
     * name — tries an exact label match first, then falls back to a
     * substring match in either direction (so "whats app" still matches
     * "WhatsApp", and "chrome" matches "Google Chrome").
     */
    fun findPackage(context: Context, spokenName: String): String? {
        val name = spokenName.lowercase().trim()
        if (name.isBlank()) return null

        val apps = installedApps(context)
        apps[name]?.let { return it }

        return apps.entries.firstOrNull { (label, _) ->
            label.contains(name) || name.contains(label)
        }?.value
    }
}
