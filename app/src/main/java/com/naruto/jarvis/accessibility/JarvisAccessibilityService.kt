package com.naruto.jarvis.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * JarvisAccessibilityService
 * ---------------------------------------------------------------
 * This is the ONLY component that can actually see/touch other
 * apps' UI. It must be manually enabled by the user in:
 *   Settings -> Accessibility -> Jarvis -> On
 * (Android will not let an app self-grant this — by design, since
 * it's a powerful permission. There is no programmatic workaround;
 * this is the same requirement Google Voice Access itself has.)
 */
class JarvisAccessibilityService : AccessibilityService() {

    companion object {
        // Lets other classes (CommandRouter) reach the live instance
        // to call inspect/click/launch without re-binding.
        var instance: JarvisAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to react to every event — inspect_screen_ui()
        // is called on-demand when a command needs the current UI tree.
    }

    override fun onInterrupt() {}

    /** UI Tree Inspection ------------------------------------------------ */

    data class UiElement(
        val text: String?,
        val contentDescription: String?,
        val isClickable: Boolean,
        val bounds: Rect,
        val node: AccessibilityNodeInfo
    )

    /**
     * inspect_screen_ui()
     * Walks the current window's accessibility tree and returns every
     * visible element with a label, description, clickability, and
     * bounding box — the same raw material Google Voice Access uses
     * to build its labeled/grid overlay.
     */
    fun inspectScreenUi(): List<UiElement> {
        val root = rootInActiveWindow ?: return emptyList()
        val results = mutableListOf<UiElement>()
        collectNodes(root, results)
        return results
    }

    private fun collectNodes(node: AccessibilityNodeInfo, out: MutableList<UiElement>) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (node.text != null || node.contentDescription != null || node.isClickable) {
            out.add(
                UiElement(
                    text = node.text?.toString(),
                    contentDescription = node.contentDescription?.toString(),
                    isClickable = node.isClickable,
                    bounds = bounds,
                    node = node
                )
            )
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectNodes(it, out) }
        }
    }

    /** Element Interaction ------------------------------------------------ */

    /**
     * clickElement(label)
     * Finds the best-matching visible element by text or content
     * description and fires ACTION_CLICK directly on it — no pixel
     * coordinates involved, so it survives different screen sizes
     * and minor layout shifts.
     */
    fun clickElement(label: String): Boolean {
        val target = inspectScreenUi().firstOrNull { el ->
            el.isClickable && (
                el.text?.contains(label, ignoreCase = true) == true ||
                el.contentDescription?.contains(label, ignoreCase = true) == true
            )
        }
        return target?.node?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
    }

    /** Direct App Launch --------------------------------------------------- */

    /**
     * launchApp(packageName)
     * Uses the system launcher intent directly — this bypasses the
     * home screen entirely, unlike simulating a tap on an icon.
     */
    fun launchApp(packageName: String): Boolean {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        return true
    }

    /**
     * closeCurrentApp()
     * HONEST LIMIT: there is no public Android API letting one app
     * force-kill another — Android intentionally blocks this for
     * security (otherwise any app could kill any other app). This
     * sends the system Home action instead, which backs fully out of
     * whatever's currently open — the practical equivalent of
     * "close it" from the user's point of view, even though the
     * app's process may still exist backgrounded until Android
     * reclaims that memory on its own.
     */
    fun closeCurrentApp(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }

    /** Grid Fallback ("Tap 4") --------------------------------------------- */

    /**
     * When an element has no text/description (icon-only buttons,
     * custom-drawn UI), GridOverlayManager numbers every clickable
     * bounding box on screen so the user can say "tap 4".
     * See GridOverlayManager.kt for the overlay + click-by-index logic.
     */
    fun getClickableBoundsForGrid(): List<Rect> =
        inspectScreenUi().filter { it.isClickable }.map { it.bounds }

    fun clickByGridIndex(index: Int): Boolean {
        val clickables = inspectScreenUi().filter { it.isClickable }
        val target = clickables.getOrNull(index) ?: return false
        return target.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }
}
