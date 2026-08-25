package com.sameerasw.essentials.utils

import android.content.Context
import androidx.core.graphics.ColorUtils

object ThemeUtil {
    private const val PREFS = "schedule_prefs"
    private const val KEY_SYSTEM_COLOR = "theme_primary_color"   // synced dynamically from the phone's wallpaper
    private const val KEY_COLOR_MODE = "theme_color_mode"        // "system" or "custom"
    private const val KEY_CUSTOM_COLOR = "theme_custom_color"    // one of FIXED_PALETTE, chosen in Settings

    const val COLOR_MODE_SYSTEM = "system"
    const val COLOR_MODE_CUSTOM = "custom"

    /** 10 fixed Material You accent colors offered in Settings as an alternative to the dynamic system color. */
    val FIXED_PALETTE: List<Int> = listOf(
        0xFFD32F2F.toInt(), // Red
        0xFFF57C00.toInt(), // Orange
        0xFFFBC02D.toInt(), // Yellow
        0xFF689F38.toInt(), // Green
        0xFF00897B.toInt(), // Teal
        0xFF0097A7.toInt(), // Cyan
        0xFF1976D2.toInt(), // Blue
        0xFF5C6BC0.toInt(), // Indigo
        0xFF8E24AA.toInt(), // Purple
        0xFFD81B60.toInt()  // Pink
    )

    /** Keys to watch with a SharedPreferences listener if a screen needs to react live to theme changes. */
    val WATCHED_PREF_KEYS = setOf(KEY_COLOR_MODE, KEY_CUSTOM_COLOR, KEY_SYSTEM_COLOR)

    fun getColorMode(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_COLOR_MODE, COLOR_MODE_SYSTEM) ?: COLOR_MODE_SYSTEM
    }

    fun getCustomColor(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val color = prefs.getInt(KEY_CUSTOM_COLOR, -1)
        return if (color != -1) color else null
    }

    fun getSystemSyncedColor(context: Context): Int? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val color = prefs.getInt(KEY_SYSTEM_COLOR, -1)
        return if (color != -1) color else null
    }

    /**
     * Resolves the color that should actually drive the UI right now.
     * - "system": whatever dynamic color the phone last synced over (unchanged legacy behavior).
     * - "custom": one of the 10 fixed Material You colors picked in Settings.
     * Falls back to the system color if "custom" is selected but nothing was ever picked.
     */
    fun getThemeColor(context: Context): Int? {
        return when (getColorMode(context)) {
            COLOR_MODE_CUSTOM -> getCustomColor(context) ?: getSystemSyncedColor(context)
            else -> getSystemSyncedColor(context)
        }
    }

    fun setSystemColorMode(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_COLOR_MODE, COLOR_MODE_SYSTEM).apply()
    }

    fun setCustomColor(context: Context, color: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_COLOR_MODE, COLOR_MODE_CUSTOM)
            .putInt(KEY_CUSTOM_COLOR, color)
            .apply()
    }

    fun getTonedColor(baseColor: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor, hsl)

        // Darken for card background: Lightness around 25% for a nice visible toned look
        hsl[2] = 0.25f
        // Also slightly desaturate for a more premium "dark mode" feel
        hsl[1] = (hsl[1] * 0.8f).coerceIn(0f, 1f)

        return ColorUtils.HSLToColor(hsl)
    }

    fun getLightAccentColor(baseColor: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(baseColor, hsl)

        // Increase lightness for better visibility on dark backgrounds
        hsl[2] = (hsl[2] + 0.3f).coerceIn(0f, 1f)
        // Ensure some saturation remains
        hsl[1] = (hsl[1] + 0.2f).coerceIn(0f, 1f)

        return ColorUtils.HSLToColor(hsl)
    }

    fun getTimeCountdown(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = timestamp - now

        if (diff <= 0) return "now"

        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            days > 0 -> "in ${days}d"
            hours > 0 -> "in ${hours}h"
            else -> "in ${minutes}m"
        }
    }
}
