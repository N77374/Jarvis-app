package com.naruto.jarvis.accessibility

import android.content.Context
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * GridOverlayManager
 * ---------------------------------------------------------------
 * Requires "Display over other apps" permission (SYSTEM_ALERT_WINDOW).
 * When an on-screen element has no readable label, this draws a
 * numbered badge over every clickable bounding box so the user can
 * say "tap 4" instead of a name Jarvis can't determine.
 */
class GridOverlayManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: FrameLayout? = null

    fun show(accessibilityService: JarvisAccessibilityService) {
        hide() // clear any previous grid first

        val container = FrameLayout(context)
        val bounds = accessibilityService.getClickableBoundsForGrid()

        bounds.forEachIndexed { index, rect ->
            val badge = TextView(context).apply {
                text = (index + 1).toString()
                setBackgroundColor(0xAA1B2E2A.toInt())
                setTextColor(0xFF7CFFCB.toInt())
                textSize = 14f
                setPadding(12, 6, 12, 6)
            }
            val params = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            params.leftMargin = rect.left
            params.topMargin = rect.top
            params.gravity = Gravity.TOP or Gravity.START
            container.addView(badge, params)
        }

        val windowParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, // needs SYSTEM_ALERT_WINDOW
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(container, windowParams)
        overlayView = container
    }

    fun hide() {
        overlayView?.let { windowManager.removeView(it) }
        overlayView = null
    }
}
