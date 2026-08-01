package com.naruto.jarvis.accessibility

import android.content.Context

/**
 * GridOverlayHolder
 * ---------------------------------------------------------------
 * GridOverlayManager needs a Context but we don't want every caller
 * (MainActivity, CommandRouter) constructing a new one. This keeps
 * a single instance around for the app's lifetime.
 */
object GridOverlayHolder {
    private var manager: GridOverlayManager? = null

    fun instance(context: Context): GridOverlayManager {
        return manager ?: GridOverlayManager(context.applicationContext).also { manager = it }
    }
}
