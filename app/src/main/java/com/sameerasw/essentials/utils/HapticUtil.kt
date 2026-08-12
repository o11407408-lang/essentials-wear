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

    fun performStrongDoubleTap(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        val timings = longArrayOf(0, 150, 100, 150)
        val amplitudes = intArrayOf(0, 255, 0, 255)
        
        if (vibrator.hasVibrator()) {
            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        }
    }

    fun performStrongDoubleTap(view: View) {
        performStrongDoubleTap(view.context)
    }

    fun startRingingVibration(context: Context): Vibrator? {
        return try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            if (vibrator.hasVibrator()) {
                val timings = longArrayOf(0, 1000, 1000)
                val amplitudes = intArrayOf(0, 255, 0)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, 0)
                vibrator.vibrate(effect)
                vibrator
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun stopRingingVibration(vibrator: Vibrator?) {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {}
    }
}
