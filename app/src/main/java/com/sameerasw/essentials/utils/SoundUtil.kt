package com.sameerasw.essentials.utils

import android.content.Context
import android.media.AudioManager
import android.media.MediaPlayer
import com.sameerasw.essentials.R

object SoundUtil {
    fun playNotificationSound(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
        val soundsEnabled = prefs.getBoolean("prefs_notification_sounds_enabled", true)
        if (!soundsEnabled) return

        try {
            val soundName = prefs.getString("selected_notification_sound", "carmen_nexus") ?: "carmen_nexus"
            val soundResId = when (soundName) {
                "google" -> R.raw.google
                "notification" -> R.raw.notification
                "dock" -> R.raw.dock
                else -> R.raw.carmen_nexus
            }
            val mediaPlayer = MediaPlayer.create(context, soundResId)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {}
    }
}
