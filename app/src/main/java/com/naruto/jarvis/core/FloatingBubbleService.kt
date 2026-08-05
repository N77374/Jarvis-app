package com.naruto.jarvis.core

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import kotlin.math.abs

/**
 * FloatingBubbleService
 * ---------------------------------------------------------------
 * A small always-on-top Jarvis indicator (like Messenger's chat
 * heads), draggable anywhere on screen, visible over every other
 * app. Tap it to start talking — no need to switch back to Jarvis
 * itself. Shows live status text as it works ("Opening WhatsApp…",
 * "Listening…", "Done.") so you can see what it's doing without
 * leaving whatever app you're in.
 *
 * Routes everything through NativeCommandRouter, NOT the WebView —
 * so this keeps working even if the main app screen was never
 * opened this session, or got killed in the background.
 */
class FloatingBubbleService : Service(), JarvisStateListener, VoiceCommandPipeline.Listener {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var statusLabel: TextView? = null
    private var pauseBtn: TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var isPaused = false

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIF_ID, buildNotification())
        JarvisStateManager.addListener(this)
        VoiceCommandPipeline.addListener(this)
        showBubble()
    }

    private fun showBubble() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                cornerRadius = 30f
                setColor(0xFF000000.toInt())
            }
        }

        // Tap-to-talk zone: dot + status label, fills remaining space
        val talkArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams((14 * density).toInt(), (14 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF45E8C9.toInt())
            }
        }
        val label = TextView(this).apply {
            text = "JARVIS — tap to talk"
            setTextColor(0xFFD6E0E3.toInt())
            textSize = 15f
            setPadding((16 * density).toInt(), 0, 0, 0)
        }
        statusLabel = label
        talkArea.addView(dot)
        talkArea.addView(label)
        talkArea.setOnClickListener { onBubbleTapped() }

        val pause = TextView(this).apply {
            text = "⏸"
            setTextColor(0xFFB7C1C5.toInt())
            textSize = 20f
            setPadding((18 * density).toInt(), 0, (18 * density).toInt(), 0)
            setOnClickListener { togglePause() }
        }
        pauseBtn = pause

        val close = TextView(this).apply {
            text = "✕"
            setTextColor(0xFFB7C1C5.toInt())
            textSize = 20f
            setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
            setOnClickListener { closeBubble() }
        }

        container.addView(talkArea)
        container.addView(pause)
        container.addView(close)

        // Sit just below the status bar, spanning the full screen width — fixed, not draggable.
        val statusBarHeight = resources.getIdentifier("status_bar_height", "dimen", "android")
            .let { if (it > 0) resources.getDimensionPixelSize(it) else (24 * density).toInt() }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = statusBarHeight + (8 * density).toInt()
        }

        bubbleView = container
        windowManager.addView(container, params)
    }

    private fun togglePause() {
        isPaused = !isPaused
        pauseBtn?.text = if (isPaused) "▶" else "⏸"
        updateStatus(if (isPaused) "Paused" else "Standby")
    }

    private fun closeBubble() {
        getSharedPreferences("jarvis_prefs", MODE_PRIVATE).edit()
            .putBoolean("bubble_enabled", false).apply()
        stopSelf()
    }

    private fun onBubbleTapped() {
        if (isPaused) return
        if (JarvisStateManager.currentState == JarvisState.SLEEP) JarvisStateManager.wake()
        val started = VoiceCommandPipeline.startManual(applicationContext)
        if (started) {
            updateStatus("Listening…")
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                VoiceCommandPipeline.stopManual()
            }, 6000)
        } else {
            updateStatus("Mic unavailable")
        }
    }

    private fun updateStatus(text: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            statusLabel?.text = text
        }
    }

    // ---------- JarvisStateListener ----------

    override fun onStateChanged(newState: JarvisState) {
        updateStatus(if (newState == JarvisState.ACTIVE) "Ready" else "Standby")
    }

    // ---------- VoiceCommandPipeline.Listener ----------

    override fun onTranscript(text: String) {
        updateStatus("Working…")
        NativeCommandRouter.route(applicationContext, text) { narration ->
            updateStatus(narration)
        }
    }

    override fun onNoSpeechDetected() {
        updateStatus("Didn't catch that")
    }

    private fun buildNotification(): Notification {
        val channelId = "jarvis_bubble_channel"
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(channelId, "Jarvis floating bubble", NotificationManager.IMPORTANCE_LOW)
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Jarvis")
            .setContentText("Floating bubble active — tap it anywhere to talk")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        bubbleView?.let { windowManager.removeView(it) }
        JarvisStateManager.removeListener(this)
        VoiceCommandPipeline.removeListener(this)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = null

    companion object {
        const val NOTIF_ID = 77
    }
}
