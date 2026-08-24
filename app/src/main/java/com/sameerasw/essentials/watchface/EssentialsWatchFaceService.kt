package com.sameerasw.essentials.watchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
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
import android.os.PowerManager
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import com.sameerasw.essentials.R
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.utils.ThemeUtil
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

class EssentialsWatchFaceService : WallpaperService() {

    private data class TopScheduleInfo(
        val text: String,
        val isMeeting: Boolean,
        val remainingMinutes: Long? = null
    )

    override fun onCreateEngine(): Engine {
        return EssentialsEngine()
    }

    inner class EssentialsEngine : WallpaperService.Engine(), SensorEventListener {

        private val updateTimeHandler = EngineHandler(this)
        private var timeZoneReceiverRegistered = false
        private var sharedPrefs: SharedPreferences? = null

        private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_primary_color" || key == "phone_battery_level" || key == "synced_calendar_events" || key?.startsWith("watchface_") == true) {
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
        private lateinit var topEventPaint: Paint
        private lateinit var sideValuePaint: Paint
        private lateinit var circleOutlinePaint: Paint
        private lateinit var trackPaint: Paint
        private lateinit var progressPaint: Paint
        private lateinit var bgGradientPaint: Paint
        private var customTypeface: Typeface? = null

        private var watchIconDrawable: Drawable? = null
        private var mobileIconDrawable: Drawable? = null
        private var heartIconDrawable: Drawable? = null
        private var stepsIconDrawable: Drawable? = null
        private var distanceIconDrawable: Drawable? = null
        private var fireIconDrawable: Drawable? = null
        private var notifIconDrawable: Drawable? = null
        private var musicIconDrawable: Drawable? = null
        private var travelIconDrawable: Drawable? = null
        private var soundIconDrawable: Drawable? = null
        private var flashlightIconDrawable: Drawable? = null
        private var vibrateIconDrawable: Drawable? = null
        private var muteIconDrawable: Drawable? = null
        private var calendarIconDrawable: Drawable? = null
        private var alarmIconDrawable: Drawable? = null

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

            topEventPaint = Paint().apply {
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

            circleOutlinePaint = Paint().apply {
                color = getTrackColor()
                style = Paint.Style.STROKE
                isAntiAlias = true
            }

            bgGradientPaint = Paint().apply {
                isAntiAlias = true
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
            distanceIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_distance_24)?.mutate()
            fireIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_local_fire_department_24)?.mutate()
            notifIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_mobile_text_2_24)?.mutate()
            musicIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_music_note_24)?.mutate()
            travelIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_directions_bus_24)?.mutate()
            soundIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_volume_up_24)?.mutate()
            flashlightIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_flashlight_on_24)?.mutate()
            vibrateIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_mobile_vibrate_24)?.mutate()
            muteIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_volume_off_24)?.mutate()
            calendarIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_calendar_today_24)?.mutate()
            alarmIconDrawable = ContextCompat.getDrawable(this@EssentialsWatchFaceService, R.drawable.rounded_alarm_24)?.mutate()

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
            val trackColor = getTrackColor()
            trackPaint.color = trackColor
            circleOutlinePaint.color = trackColor
        }

        override fun onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            unregisterReceiver()
            unregisterSensors()
            sharedPrefs?.unregisterOnSharedPreferenceChangeListener(prefListener)
            super.onDestroy()
        }

        private fun checkInteractiveState(): Boolean {
            return try {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                pm?.isInteractive ?: true
            } catch (_: Exception) {
                true
            }
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible

            val interactive = checkInteractiveState()
            isAmbient = !interactive

            if (visible && interactive) {
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
                draw()
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
            if (action == "android.wallpaper.ambient" ||
                action == "com.google.android.wearable.action.AMBIENT_UPDATE" ||
                action == "com.google.android.wearable.action.AMBIENT_MODE_CHANGED" ||
                action == "com.google.android.wearable.watchface.action.AMBIENT_UPDATE"
            ) {
                val ambient = extras?.getBoolean("ambient_mode", false)
                    ?: extras?.getBoolean("ambient", false)
                    ?: !checkInteractiveState()
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

        private fun sendPhoneMessage(path: String, data: ByteArray = byteArrayOf()) {
            val nodeClient = com.google.android.gms.wearable.Wearable.getNodeClient(this@EssentialsWatchFaceService)
            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                val messageClient = com.google.android.gms.wearable.Wearable.getMessageClient(this@EssentialsWatchFaceService)
                for (node in nodes) {
                    messageClient.sendMessage(node.id, path, data)
                }
            }
        }

        private fun openYourAndroidScreen() {
            try {
                val intent = Intent(this@EssentialsWatchFaceService, com.sameerasw.essentials.presentation.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(com.sameerasw.essentials.presentation.MainActivity.EXTRA_NAVIGATE_TO, com.sameerasw.essentials.presentation.MainActivity.NAV_YOUR_ANDROID)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("EssentialsWatchFace", "Failed to launch Your Android screen", e)
            }
        }

        private fun openScheduleScreen() {
            try {
                val intent = Intent(this@EssentialsWatchFaceService, com.sameerasw.essentials.presentation.MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(com.sameerasw.essentials.presentation.MainActivity.EXTRA_NAVIGATE_TO, com.sameerasw.essentials.presentation.MainActivity.NAV_SCHEDULE)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("EssentialsWatchFace", "Failed to launch Schedule screen", e)
            }
        }

        private fun openAlarmApp() {
            try {
                val alarmIntent = Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(alarmIntent)
            } catch (_: Exception) {
                try {
                    // Fallback to Clock/DeskClock package intents on Wear OS
                    val packageNames = listOf(
                        "com.google.android.deskclock",
                        "com.samsung.android.watch.watchclock",
                        "com.google.android.wearable.deskclock"
                    )
                    var launched = false
                    for (pkg in packageNames) {
                        val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                        if (launchIntent != null) {
                            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(launchIntent)
                            launched = true
                            break
                        }
                    }
                    if (!launched) {
                        openScheduleScreen()
                    }
                } catch (e: Exception) {
                    Log.e("EssentialsWatchFace", "Failed to open alarm app", e)
                }
            }
        }

        private fun openHealthApp() {
            val healthPackages = listOf(
                "com.google.android.apps.fitness", // Google Fit
                "com.google.android.wearable.fit",
                "com.google.android.wearable.healthservices",
                "com.samsung.android.health.ring",
                "com.sec.android.app.shealth", // Samsung Health
                "com.fitbit.FitbitMobile" // Fitbit on Pixel Watch
            )
            var launched = false
            for (pkg in healthPackages) {
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(pkg)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        launched = true
                        break
                    }
                } catch (_: Exception) { }
            }
            if (!launched) {
                try {
                    // Generic health action
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(intent)
                } catch (_: Exception) { }
            }
        }

        private fun handleComplicationTap(compType: String) {
            val prefs = sharedPrefs
            when (compType) {
                "DYNAMIC" -> {
                    val flashlightOn = prefs?.getBoolean("phone_flashlight_on", false) ?: false
                    val travelActive = prefs?.getBoolean("phone_travel_active", false) ?: false
                    val syncLocationReachedEnabled = prefs?.getBoolean("phone_watch_sync_location_reached_enabled", true) ?: true
                    val ringerMode = prefs?.getInt("phone_ringer_mode", 2) ?: 2

                    if (flashlightOn) {
                        // Toggle flashlight off
                        HapticUtil.performClick(this@EssentialsWatchFaceService)
                        sendPhoneMessage("/toggle_flashlight")
                    } else if (travelActive && syncLocationReachedEnabled) {
                        // Open Your Android screen
                        HapticUtil.performClick(this@EssentialsWatchFaceService)
                        openYourAndroidScreen()
                    } else if (ringerMode != 2) {
                        // Ringer is Silent (0) or Vibrate (1) -> switch to Sound (2)
                        HapticUtil.performClick(this@EssentialsWatchFaceService)
                        sendPhoneMessage("/toggle_sound_mode")
                    } else {
                        // None of the above -> open Your Android directly
                        HapticUtil.performClick(this@EssentialsWatchFaceService)
                        openYourAndroidScreen()
                    }
                }
                "HEART_RATE", "STEPS", "DISTANCE", "CALORIES" -> {
                    HapticUtil.performClick(this@EssentialsWatchFaceService)
                    openHealthApp()
                }
                "TRAVEL", "SOUND_MODE", "NOTIFICATIONS", "NOW_PLAYING", "PHONE_BATTERY" -> {
                    HapticUtil.performClick(this@EssentialsWatchFaceService)
                    openYourAndroidScreen()
                }
                else -> {
                    HapticUtil.performClick(this@EssentialsWatchFaceService)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent?) {
            super.onTouchEvent(event)
            if (event?.action == MotionEvent.ACTION_UP && !isAmbient) {
                val touchX = event.x
                val touchY = event.y

                val prefs = sharedPrefs
                val width = 454f
                val height = 454f
                val centerX = width / 2f
                val centerY = height / 2f

                // 1. Check Top Info (At a Glance meeting / next alarm) Tap
                val showUpcomingEvents = prefs?.getBoolean("watchface_show_upcoming_events", true) ?: true
                if (showUpcomingEvents && touchY < centerY - (height * 0.22f)) {
                    val topInfo = getUpcomingMeetingOrAlarm()
                    if (topInfo != null) {
                        HapticUtil.performClick(this@EssentialsWatchFaceService)
                        if (topInfo.isMeeting) {
                            openScheduleScreen()
                        } else {
                            openAlarmApp()
                        }
                        return
                    }
                }

                // 2. Check Side Complications Tap
                val showComplications = prefs?.getBoolean("watchface_show_complications", true) ?: true
                if (showComplications) {
                    val circleRadius = height * 0.14f // touch target radius

                    val leftCenterX = width * 0.12f
                    val rightCenterX = width * 0.88f

                    val distLeft = Math.hypot((touchX - leftCenterX).toDouble(), (touchY - centerY).toDouble()).toFloat()
                    val distRight = Math.hypot((touchX - rightCenterX).toDouble(), (touchY - centerY).toDouble()).toFloat()

                    val leftComplicationType = prefs?.getString("watchface_left_complication", "DYNAMIC") ?: "DYNAMIC"
                    val rightComplicationType = prefs?.getString("watchface_right_complication", "STEPS") ?: "STEPS"

                    if (distLeft <= circleRadius) {
                        handleComplicationTap(leftComplicationType)
                        return
                    } else if (distRight <= circleRadius) {
                        handleComplicationTap(rightComplicationType)
                        return
                    }
                }
            }
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

            val prefs = sharedPrefs
            val hideBattery = prefs?.getBoolean("watchface_hide_battery", false) ?: false
            val hideDeviceIcons = prefs?.getBoolean("watchface_hide_device_icons", false) ?: false
            val showComplications = prefs?.getBoolean("watchface_show_complications", true) ?: true
            val complicationOutline = prefs?.getBoolean("watchface_complication_outline", true) ?: true
            val leftComplicationType = prefs?.getString("watchface_left_complication", "HEART_RATE") ?: "HEART_RATE"
            val rightComplicationType = prefs?.getString("watchface_right_complication", "STEPS") ?: "STEPS"
            val showUpcomingEvents = prefs?.getBoolean("watchface_show_upcoming_events", true) ?: true
            val showGlow = prefs?.getBoolean("watchface_show_glow", true) ?: true

            val topInfo = if (!isAmbient && showUpcomingEvents) getUpcomingMeetingOrAlarm() else null

            if (!isAmbient && showGlow && topInfo != null && topInfo.remainingMinutes != null && topInfo.remainingMinutes <= 120L) {
                val mins = topInfo.remainingMinutes.coerceAtLeast(0L)
                val proximityFactor = ((120L - mins).toFloat() / 105f).coerceIn(0f, 1f)

                if (proximityFactor > 0f) {
                    val accent = getClockColor()
                    val darkFactor = 0.60f
                    val darkRed = (Color.red(accent) * darkFactor).toInt()
                    val darkGreen = (Color.green(accent) * darkFactor).toInt()
                    val darkBlue = (Color.blue(accent) * darkFactor).toInt()

                    val topAlpha = (40 + (190 * proximityFactor)).toInt().coerceIn(0, 255)
                    val midAlpha = (20 + (100 * proximityFactor)).toInt().coerceIn(0, 255)
                    val gradientHeight = height * (0.35f + (0.50f * proximityFactor))

                    val topAlphaColor = Color.argb(topAlpha, darkRed, darkGreen, darkBlue)
                    val midAlphaColor = Color.argb(midAlpha, darkRed, darkGreen, darkBlue)

                    bgGradientPaint.shader = LinearGradient(
                        centerX,
                        0f,
                        centerX,
                        gradientHeight,
                        intArrayOf(topAlphaColor, midAlphaColor, Color.TRANSPARENT),
                        floatArrayOf(0f, 0.40f, 1f),
                        Shader.TileMode.CLAMP
                    )
                    canvas.drawRect(0f, 0f, width, height, bgGradientPaint)
                }
            }

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

                if (!hideBattery) {
                    val arcSpan = if (hideDeviceIcons) 42f else 30f
                    val leftStartAngle = if (hideDeviceIcons) 108f else 120f
                    val rightStartAngle = if (hideDeviceIcons) 72f else 60f

                    if (!hideDeviceIcons) {
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
                    }

                    val watchBattery = getWatchBatteryLevel()
                    val watchSweep = (watchBattery / 100f).coerceIn(0f, 1f) * arcSpan

                    canvas.drawArc(dialRect, leftStartAngle, arcSpan, false, trackPaint)
                    if (watchSweep > 0f) {
                        canvas.drawArc(dialRect, leftStartAngle, watchSweep, false, progressPaint)
                    }

                    val phoneBattery = getPhoneBatteryLevel()
                    val phoneSweep = -((phoneBattery / 100f).coerceIn(0f, 1f) * arcSpan)

                    canvas.drawArc(dialRect, rightStartAngle, -arcSpan, false, trackPaint)
                    if (phoneBattery > 0) {
                        canvas.drawArc(dialRect, rightStartAngle, phoneSweep, false, progressPaint)
                    }
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

                // Side Complications
                if (showComplications) {
                    val sideIconSize = (height * 0.075f).toInt()
                    sideValuePaint.textSize = height * 0.055f
                    val accentColor = getClockColor()

                    circleOutlinePaint.strokeWidth = height * 0.01f
                    val circleRadius = height * 0.11f
                    val sideIconY = centerY - (height * 0.045f)
                    val sideTextY = centerY + (height * 0.065f)

                    // Helper to draw a complication
                    fun drawComplication(type: String, compCenterX: Float) {
                        if (type == "NONE") return
                        if (complicationOutline) {
                            canvas.drawCircle(compCenterX, centerY, circleRadius, circleOutlinePaint)
                        }
                        when (type) {
                            "DYNAMIC" -> {
                                val flashlightOn = prefs?.getBoolean("phone_flashlight_on", false) ?: false
                                val travelActive = prefs?.getBoolean("phone_travel_active", false) ?: false
                                val syncLocationReachedEnabled = prefs?.getBoolean("phone_watch_sync_location_reached_enabled", true) ?: true
                                val ringerMode = prefs?.getInt("phone_ringer_mode", 2) ?: 2
                                val travelTime = prefs?.getString("phone_travel_remaining_time", "") ?: ""
                                val travelDist = prefs?.getString("phone_travel_remaining_distance", "") ?: ""

                                if (flashlightOn) {
                                    drawTintedDrawable(canvas, flashlightIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                    canvas.drawText("On", compCenterX, sideTextY, sideValuePaint)
                                } else if (travelActive && syncLocationReachedEnabled) {
                                    drawTintedDrawable(canvas, travelIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                    val travelText = when {
                                        travelTime.isNotBlank() -> travelTime
                                        travelDist.isNotBlank() -> travelDist
                                        else -> "ETA"
                                    }
                                    canvas.drawText(travelText, compCenterX, sideTextY, sideValuePaint)
                                } else if (ringerMode != 2) {
                                    val icon = if (ringerMode == 1) vibrateIconDrawable else muteIconDrawable
                                    drawTintedDrawable(canvas, icon ?: soundIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                    val modeText = if (ringerMode == 1) "Vib" else "Mute"
                                    canvas.drawText(modeText, compCenterX, sideTextY, sideValuePaint)
                                } else {
                                    // Default state: Only mobile icon centered, no text
                                    drawTintedDrawable(canvas, mobileIconDrawable, compCenterX, centerY, (sideIconSize * 1.15f).toInt(), accentColor)
                                }
                            }
                            "HEART_RATE" -> {
                                drawTintedDrawable(canvas, heartIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                val hrText = if (currentHeartRate > 0) currentHeartRate.toString() else "--"
                                canvas.drawText(hrText, compCenterX, sideTextY, sideValuePaint)
                            }
                            "STEPS" -> {
                                drawTintedDrawable(canvas, stepsIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                val stepsText = currentSteps.toString()
                                canvas.drawText(stepsText, compCenterX, sideTextY, sideValuePaint)
                            }
                            "DISTANCE" -> {
                                drawTintedDrawable(canvas, distanceIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                val km = (currentSteps * 0.000762f)
                                val distText = if (km >= 10f) String.format(Locale.getDefault(), "%.0fkm", km) else String.format(Locale.getDefault(), "%.1fkm", km)
                                canvas.drawText(distText, compCenterX, sideTextY, sideValuePaint)
                            }
                            "CALORIES" -> {
                                drawTintedDrawable(canvas, fireIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                val kcal = (currentSteps * 0.04f).toInt()
                                val calText = if (kcal > 0) "${kcal}k" else "--"
                                canvas.drawText(calText, compCenterX, sideTextY, sideValuePaint)
                            }
                            "NOTIFICATIONS" -> {
                                drawTintedDrawable(canvas, notifIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                val count = try {
                                    val notifJson = prefs?.getString("watch_notifications_json", "[]") ?: "[]"
                                    org.json.JSONArray(notifJson).length()
                                } catch (_: Exception) { 0 }
                                val notifText = if (count > 0) count.toString() else "0"
                                canvas.drawText(notifText, compCenterX, sideTextY, sideValuePaint)
                            }
                            "NOW_PLAYING" -> {
                                drawTintedDrawable(canvas, musicIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                canvas.drawText("▶", compCenterX, sideTextY, sideValuePaint)
                            }
                            "TRAVEL" -> {
                                drawTintedDrawable(canvas, travelIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                val travelActive = prefs?.getBoolean("phone_travel_active", false) ?: false
                                val travelTime = prefs?.getString("phone_travel_remaining_time", "") ?: ""
                                val travelDist = prefs?.getString("phone_travel_remaining_distance", "") ?: ""
                                val travelText = when {
                                    !travelActive -> "--"
                                    travelTime.isNotBlank() -> travelTime
                                    travelDist.isNotBlank() -> travelDist
                                    else -> "ETA"
                                }
                                canvas.drawText(travelText, compCenterX, sideTextY, sideValuePaint)
                            }
                            "SOUND_MODE" -> {
                                drawTintedDrawable(canvas, soundIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                val ringerMode = prefs?.getInt("phone_ringer_mode", 2) ?: 2
                                val modeText = when (ringerMode) {
                                    0 -> "Mute"
                                    1 -> "Vib"
                                    else -> "Ring"
                                }
                                canvas.drawText(modeText, compCenterX, sideTextY, sideValuePaint)
                            }
                            "WATCH_BATTERY" -> {
                                drawTintedDrawable(canvas, watchIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                val watchBat = getWatchBatteryLevel()
                                val batText = if (watchBat >= 0) "${watchBat}%" else "--"
                                canvas.drawText(batText, compCenterX, sideTextY, sideValuePaint)
                            }
                            "PHONE_BATTERY" -> {
                                drawTintedDrawable(canvas, mobileIconDrawable, compCenterX, sideIconY, sideIconSize, accentColor)
                                val phoneBat = getPhoneBatteryLevel()
                                val batText = if (phoneBat >= 0) "${phoneBat}%" else "--"
                                canvas.drawText(batText, compCenterX, sideTextY, sideValuePaint)
                            }
                        }
                    }

                    val leftSideCenterX = width * 0.12f
                    drawComplication(leftComplicationType, leftSideCenterX)

                    val rightSideCenterX = width * 0.88f
                    drawComplication(rightComplicationType, rightSideCenterX)
                }

                // Draw Top Curved Text: Next upcoming meeting for today or next alarm
                if (topInfo != null) {
                    val accentColor = getClockColor()
                    topEventPaint.textSize = height * 0.065f
                    val topRadius = height * 0.43f
                    val topPathRect = RectF(
                        centerX - topRadius,
                        centerY - topRadius,
                        centerX + topRadius,
                        centerY + topRadius
                    )
                    val topPath = Path().apply {
                        addArc(topPathRect, 210f, 120f)
                    }
                    canvas.drawTextOnPath(topInfo.text, topPath, 0f, 0f, topEventPaint)

                    val topIcon = if (topInfo.isMeeting) calendarIconDrawable else alarmIconDrawable
                    val topIconSize = (height * 0.065f).toInt()
                    val topIconY = centerY - (height * 0.35f)
                    drawTintedDrawable(canvas, topIcon, centerX, topIconY, topIconSize, accentColor)
                }
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

        private fun getUpcomingMeetingOrAlarm(): TopScheduleInfo? {
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 999)
            val endOfToday = cal.timeInMillis

            val prefs = getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("synced_calendar_events", null)

            if (!json.isNullOrBlank()) {
                try {
                    val array = org.json.JSONArray(json)
                    var earliestEventTitle: String? = null
                    var earliestBegin = Long.MAX_VALUE

                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val begin = obj.optLong("begin", 0L)
                        val end = obj.optLong("end", 0L)
                        val title = obj.optString("title", "")
                        val allDay = obj.optBoolean("allDay", false)

                        if (!allDay && begin > now && begin <= endOfToday && begin < earliestBegin) {
                            earliestBegin = begin
                            earliestEventTitle = title
                        } else if (!allDay && now in begin..end && begin < earliestBegin) {
                            earliestBegin = begin
                            earliestEventTitle = title
                        }
                    }

                    if (earliestEventTitle != null) {
                        val diffMs = earliestBegin - now
                        val diffMinutes = if (diffMs <= 0) 0L else (diffMs / 60000L).coerceAtLeast(1L)
                        val timeStr = if (diffMs <= 0) {
                            "Now"
                        } else {
                            if (diffMinutes < 60) {
                                "in ${diffMinutes}m"
                            } else {
                                val hours = diffMinutes / 60
                                val remainingMin = diffMinutes % 60
                                if (remainingMin > 0) "in ${hours}h ${remainingMin}m" else "in ${hours}h"
                            }
                        }
                        val cleanTitle = if (earliestEventTitle.length > 20) earliestEventTitle.take(19) + "…" else earliestEventTitle
                        return TopScheduleInfo("$cleanTitle $timeStr", true, diffMinutes)
                    }
                } catch (_: Exception) { }
            }

            // Fallback: Next Alarm
            try {
                val am = getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                val nextAlarm = am?.nextAlarmClock
                if (nextAlarm != null && nextAlarm.triggerTime > now) {
                    val alarmCal = Calendar.getInstance().apply { timeInMillis = nextAlarm.triggerTime }
                    val diffMs = nextAlarm.triggerTime - now
                    val diffMinutes = (diffMs / 60000L).coerceAtLeast(1L)
                    val is24 = android.text.format.DateFormat.is24HourFormat(this@EssentialsWatchFaceService)
                    val timeStr = if (is24) {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(alarmCal.time)
                    } else {
                        val h = alarmCal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
                        val m = alarmCal.get(Calendar.MINUTE)
                        val amPm = if (alarmCal.get(Calendar.AM_PM) == Calendar.AM) "AM" else "PM"
                        String.format(Locale.getDefault(), "%d:%02d %s", h, m, amPm)
                    }
                    return TopScheduleInfo(timeStr, false, diffMinutes)
                }
            } catch (_: Exception) { }

            return null
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
