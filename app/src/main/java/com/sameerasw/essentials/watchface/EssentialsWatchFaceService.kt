package com.sameerasw.essentials.watchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.res.ResourcesCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.utils.ThemeUtil
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.graphics.RectF

class EssentialsWatchFaceService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return EssentialsEngine()
    }

    inner class EssentialsEngine : WallpaperService.Engine(), SensorEventListener {

        private val updateTimeHandler = EngineHandler(this)
        private var timeZoneReceiverRegistered = false
        private var sharedPrefs: SharedPreferences? = null

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_primary_color") {
                updateThemeColor()
                draw()
            }
        }

        private val timeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                calendar.timeZone = TimeZone.getDefault()
                draw()
            }
        }

        private var isAmbient = false
        private var isVisibleState = false
        private lateinit var calendar: Calendar
        private lateinit var textPaint: Paint
        private lateinit var trackPaint: Paint
        private lateinit var progressPaint: Paint
        private var customTypeface: Typeface? = null

        private var sensorManager: SensorManager? = null
        private var stepSensor: Sensor? = null
        private var initialSteps = -1
        private var currentSteps = 0
        private val dailyStepGoal = 10000

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            calendar = Calendar.getInstance()

            try {
                customTypeface = ResourcesCompat.getFont(this@EssentialsWatchFaceService, R.font.google_sans_flex)
            } catch (_: Exception) {
                customTypeface = Typeface.DEFAULT
            }

            textPaint = Paint().apply {
                color = getClockColor()
                typeface = customTypeface ?: Typeface.DEFAULT
                // Variable font settings: 'ROND' 100 (rounded), 'wdth' 150 (wider), 'wght' 100 (thinner)
                fontVariationSettings = "'ROND' 100.0, 'wdth' 150.0, 'wght' 100.0"
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            trackPaint = Paint().apply {
                color = getTrackColor()
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }

            progressPaint = Paint().apply {
                color = getClockColor()
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                isAntiAlias = true
            }

            sharedPrefs = getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            sharedPrefs?.registerOnSharedPreferenceChangeListener(prefListener)

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                ?: sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        }

        private fun getClockColor(): Int {
            val themeColor = ThemeUtil.getThemeColor(this@EssentialsWatchFaceService)
            return themeColor?.let { ThemeUtil.getLightAccentColor(it) } ?: 0xFFB39DDB.toInt()
        }

        private fun getTrackColor(): Int {
            val themeColor = ThemeUtil.getThemeColor(this@EssentialsWatchFaceService) ?: 0xFF6750A4.toInt()
            return ThemeUtil.getTonedColor(themeColor)
        }

        private fun updateThemeColor() {
            val color = getClockColor()
            textPaint.color = color
            progressPaint.color = color
            trackPaint.color = getTrackColor()
        }

        override fun onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            unregisterReceiver()
            unregisterStepSensor()
            sharedPrefs?.unregisterOnSharedPreferenceChangeListener(prefListener)
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible

            if (visible) {
                currentSteps = getSavedDailySteps()
                updateThemeColor()
                registerReceiver()
                registerStepSensor()
                calendar.timeZone = TimeZone.getDefault()
                draw()
            } else {
                unregisterReceiver()
                unregisterStepSensor()
            }

            updateTimer()
        }

        private fun registerReceiver() {
            if (timeZoneReceiverRegistered) return
            timeZoneReceiverRegistered = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED).apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_BATTERY_CHANGED)
            }
            this@EssentialsWatchFaceService.registerReceiver(timeZoneReceiver, filter)
        }

        private fun unregisterReceiver() {
            if (!timeZoneReceiverRegistered) return
            timeZoneReceiverRegistered = false
            try {
                this@EssentialsWatchFaceService.unregisterReceiver(timeZoneReceiver)
            } catch (_: Exception) { }
        }

        private fun registerStepSensor() {
            stepSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }

        private fun unregisterStepSensor() {
            sensorManager?.unregisterListener(this)
        }

        private var stepCountPrefs: SharedPreferences? = null

        private fun getStepPrefs(): SharedPreferences {
            return stepCountPrefs ?: getSharedPreferences("watchface_steps_prefs", Context.MODE_PRIVATE).also {
                stepCountPrefs = it
            }
        }

        private fun getSavedDailySteps(): Int {
            val prefs = getStepPrefs()
            val savedDay = prefs.getInt("step_day_of_year", -1)
            val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            if (savedDay != currentDay) {
                return 0
            }
            return prefs.getInt("daily_step_count", 0)
        }

        override fun onSensorChanged(event: SensorEvent?) {
            val sensor = event?.sensor ?: return
            val prefs = getStepPrefs()
            val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
            val savedDay = prefs.getInt("step_day_of_year", -1)

            if (sensor.type == Sensor.TYPE_STEP_COUNTER && event.values.isNotEmpty()) {
                val sensorSteps = event.values[0].toInt()
                var baseline = prefs.getInt("step_baseline", -1)
                if (savedDay != currentDay || baseline < 0 || sensorSteps < baseline) {
                    baseline = sensorSteps
                    prefs.edit()
                        .putInt("step_day_of_year", currentDay)
                        .putInt("step_baseline", baseline)
                        .putInt("daily_step_count", 0)
                        .apply()
                }
                val calculated = (sensorSteps - baseline).coerceAtLeast(0)
                currentSteps = calculated
                prefs.edit().putInt("daily_step_count", currentSteps).apply()
                draw()
            } else if (sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                if (savedDay != currentDay) {
                    currentSteps = 0
                    prefs.edit()
                        .putInt("step_day_of_year", currentDay)
                        .putInt("daily_step_count", 0)
                        .apply()
                }
                currentSteps++
                prefs.edit().putInt("daily_step_count", currentSteps).apply()
                draw()
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            currentSteps = getSavedDailySteps()
            val textSize = height * 0.28f
            textPaint.textSize = textSize
            val strokeW = height * 0.015f
            trackPaint.strokeWidth = strokeW
            progressPaint.strokeWidth = strokeW
            draw()
        }

        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            draw()
        }

        fun draw() {
            val holder = surfaceHolder ?: return
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                if (canvas != null) {
                    drawWatchFace(canvas)
                }
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) { }
                }
            }
        }

        private fun drawWatchFace(canvas: Canvas) {
            val now = System.currentTimeMillis()
            calendar.timeInMillis = now

            val rawHour = calendar.get(Calendar.HOUR)
            val hourInt = if (rawHour == 0) 12 else rawHour
            val minuteInt = calendar.get(Calendar.MINUTE)

            val hourText = String.format(Locale.getDefault(), "%02d", hourInt)
            val minuteText = String.format(Locale.getDefault(), "%02d", minuteInt)

            canvas.drawColor(Color.BLACK)

            val width = canvas.width.toFloat()
            val height = canvas.height.toFloat()
            val centerX = width / 2f
            val centerY = height / 2f

            if (textPaint.textSize == 0f) {
                textPaint.textSize = height * 0.28f
            }

            // Draw Edge Dials
            val strokeW = height * 0.015f
            trackPaint.strokeWidth = strokeW
            progressPaint.strokeWidth = strokeW

            val dialPadding = strokeW * 1.5f
            val dialRect = RectF(dialPadding, dialPadding, width - dialPadding, height - dialPadding)
            val arcSpan = 70f // Span angle in degrees for each half dial

            // 1. Left Dial: Battery Level (Sweep centered on Left: 180° - 35° to 180° + 35°)
            val leftStartAngle = 180f - (arcSpan / 2f)
            val batteryLevel = getWatchBatteryLevel()
            val batterySweep = (batteryLevel / 100f).coerceIn(0f, 1f) * arcSpan

            // Draw Left Track & Progress
            canvas.drawArc(dialRect, leftStartAngle, arcSpan, false, trackPaint)
            if (batterySweep > 0f) {
                canvas.drawArc(dialRect, leftStartAngle, batterySweep, false, progressPaint)
            }

            // 2. Right Dial: Steps Count (Sweep centered on Right: 0° - 35° to 0° + 35°)
            val rightStartAngle = 0f - (arcSpan / 2f)
            val stepFraction = (currentSteps.toFloat() / dailyStepGoal.toFloat()).coerceIn(0f, 1f)
            val stepsSweep = stepFraction * arcSpan

            // Draw Right Track & Progress
            canvas.drawArc(dialRect, rightStartAngle, arcSpan, false, trackPaint)
            if (stepsSweep > 0f) {
                canvas.drawArc(dialRect, rightStartAngle, stepsSweep, false, progressPaint)
            }

            // Draw Centered Clock Text
            val textBounds = Rect()
            textPaint.getTextBounds("88", 0, 2, textBounds)
            val textHeight = textBounds.height().toFloat()

            val lineSpacing = 7f
            val hourY = centerY - lineSpacing
            val minuteY = centerY + textHeight + lineSpacing

            canvas.drawText(hourText, centerX, hourY, textPaint)
            canvas.drawText(minuteText, centerX, minuteY, textPaint)
        }

        private fun getWatchBatteryLevel(): Int {
            return try {
                val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            } catch (_: Exception) {
                100
            }
        }

        private fun updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME)
            }
        }

        private fun shouldTimerBeRunning(): Boolean {
            return isVisibleState && !isAmbient
        }

        fun handleUpdateTimeMessage() {
            draw()
            if (shouldTimerBeRunning()) {
                val timeMs = System.currentTimeMillis()
                val delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS)
                updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs)
            }
        }
    }

    companion object {
        private const val MSG_UPDATE_TIME = 0
        private const val INTERACTIVE_UPDATE_RATE_MS = 1000L
    }

    private class EngineHandler(reference: EssentialsEngine) : Handler(Looper.getMainLooper()) {
        private val weakReference: WeakReference<EssentialsEngine> = WeakReference(reference)

        override fun handleMessage(msg: Message) {
            val engine = weakReference.get()
            if (engine != null && msg.what == MSG_UPDATE_TIME) {
                engine.handleUpdateTimeMessage()
            }
        }
    }
}
