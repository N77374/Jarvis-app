package com.naruto.jarvis.core

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * SystemControlManager
 * ---------------------------------------------------------------
 * Controls the pieces of the OS that a normal (non-system) Android
 * app is actually allowed to touch. Two honest limits, by OS design
 * (not something more code can work around):
 *
 *  - Wi-Fi / mobile data / Airplane Mode: Android blocked silent
 *    third-party toggling of these starting with Android 10, for
 *    every app including Google's own Assistant. We can open the
 *    quick-settings panel for a one-tap toggle, but not flip it
 *    with zero taps.
 *  - Force-closing another app: no public API exists for one app
 *    to kill another. We can send the phone Home (backing out of
 *    whatever's open), which is what "close X" does here.
 */
object SystemControlManager {

    // ---------- flashlight ----------

    fun setFlashlight(context: Context, on: Boolean): Boolean {
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val camId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return false
            cm.setTorchMode(camId, on)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- volume ----------

    fun setVolumePercent(context: Context, percent: Int): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (max * (percent.coerceIn(0, 100) / 100f)).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun adjustVolume(context: Context, raise: Boolean): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
                0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    fun setMuted(context: Context, muted: Boolean): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (muted) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- screen brightness (needs WRITE_SETTINGS, granted via system screen) ----------

    fun canWriteSettings(context: Context): Boolean = Settings.System.canWrite(context)

    fun requestWriteSettingsPermission(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun setBrightnessPercent(context: Context, percent: Int): Boolean {
        if (!canWriteSettings(context)) return false
        return try {
            val value = (255 * (percent.coerceIn(0, 100) / 100f)).toInt()
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            Settings.System.putInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, value)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- Bluetooth ----------

    @Suppress("DEPRECATION")
    fun setBluetooth(on: Boolean): Boolean {
        return try {
            val adapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            if (on) adapter.enable() else adapter.disable()
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- Do Not Disturb (needs Notification Policy Access, granted via system screen) ----------

    fun canAccessNotificationPolicy(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    fun requestNotificationPolicyAccess(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun setDoNotDisturb(context: Context, on: Boolean): Boolean {
        if (!canAccessNotificationPolicy(context)) return false
        return try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.setInterruptionFilter(
                if (on) android.app.NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else android.app.NotificationManager.INTERRUPTION_FILTER_ALL
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- Wi-Fi (Android blocks silent toggling — best we can do is open the panel) ----------

    fun openWifiPanel(context: Context) {
        val intent = Intent(Settings.Panel.ACTION_WIFI)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // ---------- auto-rotate (needs WRITE_SETTINGS, same as brightness) ----------

    fun setAutoRotate(context: Context, on: Boolean): Boolean {
        if (!canWriteSettings(context)) return false
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.ACCELEROMETER_ROTATION,
                if (on) 1 else 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- ringer mode: normal / vibrate / silent ----------

    fun setRingerMode(context: Context, mode: String): Boolean {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            if (mode != "normal" && !canAccessNotificationPolicy(context)) return false
            am.ringerMode = when (mode) {
                "silent" -> AudioManager.RINGER_MODE_SILENT
                "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
