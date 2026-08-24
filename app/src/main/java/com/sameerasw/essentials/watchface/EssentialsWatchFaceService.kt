package com.sameerasw.essentials.watchface

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import java.lang.ref.WeakReference
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class EssentialsWatchFaceService : WallpaperService() {

    override fun onCreateEngine(): Engine {
        return EssentialsEngine()
    }

    inner class EssentialsEngine : WallpaperService.Engine() {

        private val updateTimeHandler = EngineHandler(this)
        private var timeZoneReceiverRegistered = false

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
        private var customTypeface: Typeface? = null

        override fun onCreate(holder: SurfaceHolder) {
            super.onCreate(holder)

            calendar = Calendar.getInstance()

            try {
                customTypeface = ResourcesCompat.getFont(this@EssentialsWatchFaceService, R.font.google_sans_flex)
            } catch (_: Exception) {
                customTypeface = Typeface.DEFAULT_BOLD
            }

            textPaint = Paint().apply {
                color = Color.WHITE
                typeface = customTypeface ?: Typeface.DEFAULT_BOLD
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
        }

        override fun onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME)
            unregisterReceiver()
            super.onDestroy()
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState = visible

            if (visible) {
                registerReceiver()
                calendar.timeZone = TimeZone.getDefault()
                draw()
            } else {
                unregisterReceiver()
            }

            updateTimer()
        }

        private fun registerReceiver() {
            if (timeZoneReceiverRegistered) return
            timeZoneReceiverRegistered = true
            val filter = IntentFilter(Intent.ACTION_TIMEZONE_CHANGED).apply {
                addAction(Intent.ACTION_TIME_TICK)
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

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            val textSize = height * 0.38f
            textPaint.textSize = textSize
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
                textPaint.textSize = height * 0.38f
            }

            val textBounds = Rect()
            textPaint.getTextBounds("88", 0, 2, textBounds)
            val textHeight = textBounds.height().toFloat()

            val lineSpacing = 12f
            val hourY = centerY - lineSpacing
            val minuteY = centerY + textHeight + lineSpacing

            canvas.drawText(hourText, centerX, hourY, textPaint)
            canvas.drawText(minuteText, centerX, minuteY, textPaint)
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
