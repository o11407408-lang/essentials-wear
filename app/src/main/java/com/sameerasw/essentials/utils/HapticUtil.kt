package com.sameerasw.essentials.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

object HapticUtil {
    private const val IGNORE_GLOBAL = HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING

    fun performUIHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP, IGNORE_GLOBAL)
    }

    fun performLightHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, IGNORE_GLOBAL)
    }
    
    fun performSubtleTick(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK, IGNORE_GLOBAL)
    }

    fun performPageSwitchHaptic(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, IGNORE_GLOBAL)
    }

    fun performStrongDoubleTap(view: View) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = view.context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            view.context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val timings = longArrayOf(0, 150, 100, 150)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } else {
            // Fallback to haptic if vibrator isn't available or fails
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, IGNORE_GLOBAL)
        }
    }
}
