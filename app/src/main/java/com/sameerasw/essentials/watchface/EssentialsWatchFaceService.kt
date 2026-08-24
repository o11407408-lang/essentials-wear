package com.sameerasw.essentials.watchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.utils.ThemeUtil
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

class EssentialsWatchFaceService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return EssentialsEngine()
    }

    inner class EssentialsEngine : WallpaperService.Engine(), SensorEventListener {

        private val updateTimeHandler = EngineHandler(this)
        private var timeZoneReceiverRegistered = false
        private var sharedPrefs: SharedPreferences? = null

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_primary_color" || key == "phone_battery_level") {
                updateThemeColor()
                draw()
            }
        }

        private val timeZoneReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_TIMEZONE_CHANGED -> calendar.timeZone = TimeZone.getDefault()
                    Intent.ACTION_SCREEN_OFF -> {
                        isAmbient = true
                        unregisterSensors()
                    }
                    Intent.ACTION_SCREEN_ON -> {
                        isAmbient = false
                        if (isVisibleState) {
                            registerSensors()
                        }
                    }
                }
                draw()
                updateTimer()
            }
        }

        private var isAmbient = false
        private var isVisibleState = false
        private lateinit var calendar: Calendar
        private lateinit var textPaint: Paint
        private lateinit var datePaint: Paint
        private lateinit var sideValuePaint: Paint
        private lateinit var trackPaint: Paint
        private lateinit var progressPaint: Paint
        private var customTypeface: Typeface? = null

        private var watchIconDrawable: Drawable? = null
        private var mobileIconDrawable: Drawable? = null
        private var heartIconDrawable: Drawable? = null
        private var stepsIconDrawable: Drawable? = null

        private var sensorManager: SensorManager? = null
        private var heartRateSensor: Sensor? = null
        private var stepSensor: Sensor? = null
        private var currentHeartRate: Int = 0
        private var currentSteps: Int = 0
        private var lastHeartRateUpdateMs: Long = 0L

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
                fontVariationSettings = "'ROND' 100.0, 'wdth' 150.0, 'wght' 200.0"
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            datePaint = Paint().apply {
                color = Color.WHITE
                typeface = customTypeface ?: Typeface.DEFAULT
                fontVariationSettings = "'ROND' 100.0, 'wdth' 100.0, 'wght' 400.0"
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }

            sideValuePaint = Paint().apply {
                color = Color.WHITE
                typeface = customTypeface ?: Typeface.DEFAULT
                fontVariationSettings = "'ROND' 100.0, 'wdth' 100.0, 'wght' 400.0"
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

            watchIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_watch_24)?.mutate()
            mobileIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_mobile_24)?.mutate()
            heartIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_favorite_24)?.mutate()
            stepsIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_steps_24)?.mutate()

            sharedPrefs = getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            sharedPrefs?.registerOnSharedPreferenceChangeListener(prefListener)

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
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
            unregisterSensors()
            sharedPrefs?.unregisterOnSharedPreferenceChangeListener(prefListener)
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible

            if (visible && !isAmbient) {
                currentSteps = getSavedDailySteps()
                updateThemeColor()
                registerReceiver()
                registerSensors()
                calendar.timeZone = TimeZone.getDefault()
                draw()
            } else {
                unregisterSensors()
                if (!visible) {
                    unregisterReceiver()
                }
            }

            updateTimer()
        }

        override fun onCommand(
            action: String?,
            x: Int,
            y: Int,
            z: Int,
            extras: android.os.Bundle?,
            resultRequested: Boolean
        ): android.os.Bundle? {
            if (action == "android.wallpaper.ambient" || action == "com.google.android.wearable.action.AMBIENT_UPDATE") {
                val ambient = extras?.getBoolean("ambient_mode", false) ?: false
                if (isAmbient != ambient) {
                    isAmbient = ambient
                    if (isAmbient) {
                        unregisterSensors()
                    } else if (isVisibleState) {
                        registerSensors()
                    }
                    draw()
                    updateTimer()
                }
            }
            return super.onCommand(action, x, y, z, extras, resultRequested)
        }

        private fun registerReceiver() {
            if (timeZoneReceiverRegistered) return
            timeZoneReceiverRegistered = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED).apply {
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
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

        private fun registerSensors() {
            if (!isVisibleState || isAmbient) return
            if (sensorManager == null) {
                sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            }
            if (heartRateSensor == null) {
                heartRateSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_HEART_RATE)
            }
            if (stepSensor == null) {
                stepSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
                    ?: sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
            }
            heartRateSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            }
            stepSensor?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
            }
        }

        private fun unregisterSensors() {
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
            val now = System.currentTimeMillis()

            if (sensor.type == Sensor.TYPE_HEART_RATE && event.values.isNotEmpty()) {
                val hr = event.values[0].toInt()
                if (hr > 0 && (now - lastHeartRateUpdateMs >= 4000L || currentHeartRate == 0)) {
                    currentHeartRate = hr
                    lastHeartRateUpdateMs = now
                    draw()
                }
            } else if (sensor.type == Sensor.TYPE_STEP_COUNTER && event.values.isNotEmpty()) {
                val totalSteps = event.values[0].toInt()
                val prefs = getStepPrefs()
                val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                val savedDay = prefs.getInt("step_day_of_year", -1)
                var baseline = prefs.getInt("step_baseline", -1)

                if (savedDay != currentDay || baseline < 0 || totalSteps < baseline) {
                    baseline = totalSteps
                    currentSteps = 0
                    prefs.edit()
                        .putInt("step_day_of_year", currentDay)
                        .putInt("step_baseline", baseline)
                        .putInt("daily_step_count", 0)
                        .apply()
                } else {
                    currentSteps = totalSteps - baseline
                    prefs.edit()
                        .putInt("step_day_of_year", currentDay)
                        .putInt("daily_step_count", currentSteps)
                        .apply()
                }
                draw()
            } else if (sensor.type == Sensor.TYPE_STEP_DETECTOR) {
                val prefs = getStepPrefs()
                val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
                val savedDay = prefs.getInt("step_day_of_year", -1)
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
            val textSize = height * 0.25f
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
                textPaint.textSize = height * 0.25f
            }

            // Set font weight based on Ambient/AOD mode (200 in interactive, 50 in AOD)
            textPaint.fontVariationSettings = if (isAmbient) {
                "'ROND' 100.0, 'wdth' 150.0, 'wght' 50.0"
            } else {
                "'ROND' 100.0, 'wdth' 150.0, 'wght' 200.0"
            }

            if (!isAmbient) {
                val strokeW = height * 0.015f
                trackPaint.strokeWidth = strokeW
                progressPaint.strokeWidth = strokeW

                val dialPadding = strokeW * 1.5f
                val dialRect = RectF(dialPadding, dialPadding, width - dialPadding, height - dialPadding)
                val radius = (width - 2f * dialPadding) / 2f

                val iconSize = (height * 0.085f).toInt()
                val iconInsetRadius = radius - (iconSize * 0.45f)

                val leftIconAngleRad = Math.toRadians(111.0)
                val leftIconCenterX = centerX + iconInsetRadius * cos(leftIconAngleRad).toFloat()
                val leftIconCenterY = centerY + iconInsetRadius * sin(leftIconAngleRad).toFloat()
                drawTintedDrawable(canvas, watchIconDrawable, leftIconCenterX, leftIconCenterY, iconSize, Color.WHITE, 111f - 90f)

                val rightIconAngleRad = Math.toRadians(69.0)
                val rightIconCenterX = centerX + iconInsetRadius * cos(rightIconAngleRad).toFloat()
                val rightIconCenterY = centerY + iconInsetRadius * sin(rightIconAngleRad).toFloat()
                drawTintedDrawable(canvas, mobileIconDrawable, rightIconCenterX, rightIconCenterY, iconSize, Color.WHITE, 69f - 90f)

                val leftStartAngle = 120f
                val arcSpan = 30f
                val watchBattery = getWatchBatteryLevel()
                val watchSweep = (watchBattery / 100f).coerceIn(0f, 1f) * arcSpan

                canvas.drawArc(dialRect, leftStartAngle, arcSpan, false, trackPaint)
                if (watchSweep > 0f) {
                    canvas.drawArc(dialRect, leftStartAngle, watchSweep, false, progressPaint)
                }

                val rightStartAngle = 60f
                val phoneBattery = getPhoneBatteryLevel()
                val phoneSweep = -((phoneBattery / 100f).coerceIn(0f, 1f) * arcSpan)

                canvas.drawArc(dialRect, rightStartAngle, -arcSpan, false, trackPaint)
                if (phoneBattery > 0) {
                    canvas.drawArc(dialRect, rightStartAngle, phoneSweep, false, progressPaint)
                }

                // Draw Curved Date Text at bottom center
                val dateText = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(calendar.time)
                datePaint.color = Color.WHITE
                datePaint.textSize = height * 0.070f
                val dateRadius = height * 0.37f
                val datePathRect = RectF(
                    centerX - dateRadius,
                    centerY - dateRadius,
                    centerX + dateRadius,
                    centerY + dateRadius
                )
                val datePath = Path().apply {
                    addArc(datePathRect, 140f, -100f)
                }
                canvas.drawTextOnPath(dateText, datePath, 0f, 0f, datePaint)

                val sideIconSize = (height * 0.075f).toInt()
                sideValuePaint.textSize = height * 0.055f
                val accentColor = getClockColor()

                val leftSideCenterX = width * 0.16f
                val sideIconY = centerY - (height * 0.025f)
                val sideTextY = centerY + (height * 0.045f)

                drawTintedDrawable(canvas, heartIconDrawable, leftSideCenterX, sideIconY, sideIconSize, accentColor)
                val hrText = if (currentHeartRate > 0) currentHeartRate.toString() else "--"
                canvas.drawText(hrText, leftSideCenterX, sideTextY, sideValuePaint)

                val rightSideCenterX = width * 0.84f
                drawTintedDrawable(canvas, stepsIconDrawable, rightSideCenterX, sideIconY, sideIconSize, accentColor)
                val stepsText = currentSteps.toString()
                canvas.drawText(stepsText, rightSideCenterX, sideTextY, sideValuePaint)
            }

            // Draw Centered Clock Text
            val textBounds = Rect()
            textPaint.getTextBounds("88", 0, 2, textBounds)
            val textHeight = textBounds.height().toFloat()

            val lineSpacing = 6f
            val hourY = centerY - lineSpacing
            val minuteY = centerY + textHeight + lineSpacing

            canvas.drawText(hourText, centerX, hourY, textPaint)
            canvas.drawText(minuteText, centerX, minuteY, textPaint)
        }

        private fun drawTintedDrawable(
            canvas: Canvas,
            drawable: Drawable?,
            cx: Float,
            cy: Float,
            size: Int,
            color: Int,
            rotationDegrees: Float = 0f
        ) {
            drawable ?: return
            drawable.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            val half = size / 2
            drawable.setBounds(-half, -half, half, half)
            canvas.save()
            canvas.translate(cx, cy)
            if (rotationDegrees != 0f) {
                canvas.rotate(rotationDegrees)
            }
            drawable.draw(canvas)
            canvas.restore()
        }

        private fun getWatchBatteryLevel(): Int {
            return try {
                val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100
            } catch (_: Exception) {
                100
            }
        }

        private fun getPhoneBatteryLevel(): Int {
            val prefs = getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            val level = prefs.getInt("phone_battery_level", -1)
            return if (level >= 0) level else 0
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
