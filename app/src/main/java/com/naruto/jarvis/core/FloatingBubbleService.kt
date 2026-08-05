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

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20, 14, 14, 14)
            background = GradientDrawable().apply {
                cornerRadius = 44f
                setColor(0xE60D1113.toInt())
            }
        }

        // Draggable, tap-to-talk zone: dot + status label
        val dragArea = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(20, 20)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF45E8C9.toInt())
            }
        }
        val label = TextView(this).apply {
            text = "Jarvis"
            setTextColor(0xFFD6E0E3.toInt())
            textSize = 12f
            setPadding(18, 0, 14, 0)
        }
        statusLabel = label
        dragArea.addView(dot)
        dragArea.addView(label)

        // Pause/resume — greys the bubble out and stops it acting on taps, without fully closing it.
        val pause = TextView(this).apply {
            text = "⏸"
            setTextColor(0xFF9AA7AC.toInt())
            textSize = 14f
            setPadding(14, 0, 14, 0)
            setOnClickListener { togglePause() }
        }
        pauseBtn = pause

        // Close — stops the service entirely, no need to reopen the app to turn it off.
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(0xFF9AA7AC.toInt())
            textSize = 14f
            setPadding(6, 0, 6, 0)
            setOnClickListener { closeBubble() }
        }

        container.addView(dragArea)
        container.addView(pause)
        container.addView(close)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 220
        }

        // Drag to move; a small-movement release counts as a tap-to-talk instead.
        // Only the dragArea (dot+label) responds to this — the pause/close buttons
        // handle their own taps independently, so they're never accidentally dragged.
        var startX = 0; var startY = 0
        var touchStartX = 0f; var touchStartY = 0f

        dragArea.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params!!.x; startY = params!!.y
                    touchStartX = event.rawX; touchStartY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params!!.x = startX + (event.rawX - touchStartX).toInt()
                    params!!.y = startY + (event.rawY - touchStartY).toInt()
                    windowManager.updateViewLayout(container, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (abs(event.rawX - touchStartX) < 12 && abs(event.rawY - touchStartY) < 12) {
                        onBubbleTapped()
                    }
                    true
                }
                else -> false
            }
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
