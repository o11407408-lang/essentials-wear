package com.sameerasw.essentials.complication

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sameerasw.essentials.R
import com.sameerasw.essentials.presentation.MainActivity
import com.sameerasw.essentials.tile.MainTileService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val previewPrimary = "Meeting"
        val previewSecondary = "2h"
        val icon = Icon.createWithResource(this, R.drawable.rounded_calendar_today_24)
        val monochromaticImage = MonochromaticImage.Builder(icon).build()
        val contentDesc = PlainComplicationText.Builder("Meeting in 2h").build()

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(previewPrimary).build(),
                    contentDescription = contentDesc
                )
                .setTitle(PlainComplicationText.Builder(previewSecondary).build())
                .setMonochromaticImage(monochromaticImage)
                .build()
            }
            ComplicationType.SMALL_IMAGE -> {
                SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build(),
                    contentDescription = contentDesc
                ).build()
            }
            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = monochromaticImage,
                    contentDescription = contentDesc
                ).build()
            }
            else -> null
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val now = System.currentTimeMillis()
        val calendarEvents = MainTileService.getSyncedEvents(this)
        
        // Find upcoming non all-day event for the current day
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfDay = cal.timeInMillis

        val upcomingEvent = calendarEvents
            .filter { !it.allDay && it.end > now && it.begin <= endOfDay }
            .minByOrNull { it.begin }

        if (upcomingEvent != null) {
            val title = upcomingEvent.title ?: getString(R.string.feature_schedule)
            val remainingFormatted = formatRemainingTime(upcomingEvent.begin - now)
            val icon = Icon.createWithResource(this, R.drawable.rounded_calendar_today_24)
            val monochromaticImage = MonochromaticImage.Builder(icon).build()
            val contentDesc = PlainComplicationText.Builder("$title in $remainingFormatted").build()
            val tapAction = getCalendarTapAction()

            return buildComplicationData(
                type = request.complicationType,
                primaryText = title,
                secondaryText = remainingFormatted,
                icon = icon,
                monochromaticImage = monochromaticImage,
                contentDescription = contentDesc,
                tapAction = tapAction
            )
        }

        // Fallback: Next Alarm
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val nextAlarm = alarmManager?.nextAlarmClock

        if (nextAlarm != null && nextAlarm.triggerTime > now) {
            val alarmTimeStr = formatAlarmTime(nextAlarm.triggerTime)
            val remainingFormatted = formatRemainingTime(nextAlarm.triggerTime - now)
            val icon = Icon.createWithResource(this, R.drawable.rounded_alarm_24)
            val monochromaticImage = MonochromaticImage.Builder(icon).build()
            val contentDesc = PlainComplicationText.Builder("Alarm at $alarmTimeStr, in $remainingFormatted").build()
            val tapAction = nextAlarm.showIntent ?: getAlarmTapAction()

            return buildComplicationData(
                type = request.complicationType,
                primaryText = alarmTimeStr,
                secondaryText = remainingFormatted,
                icon = icon,
                monochromaticImage = monochromaticImage,
                contentDescription = contentDesc,
                tapAction = tapAction
            )
        }

        // No events and no alarms
        val noEventText = getString(R.string.no_events)
        val icon = Icon.createWithResource(this, R.drawable.rounded_calendar_today_24)
        val monochromaticImage = MonochromaticImage.Builder(icon).build()
        val contentDesc = PlainComplicationText.Builder(noEventText).build()
        val tapAction = getCalendarTapAction()

        return buildComplicationData(
            type = request.complicationType,
            primaryText = noEventText,
            secondaryText = "--",
            icon = icon,
            monochromaticImage = monochromaticImage,
            contentDescription = contentDesc,
            tapAction = tapAction
        )
    }

    private fun buildComplicationData(
        type: ComplicationType,
        primaryText: String,
        secondaryText: String,
        icon: Icon,
        monochromaticImage: MonochromaticImage,
        contentDescription: PlainComplicationText,
        tapAction: PendingIntent
    ): ComplicationData {
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(primaryText).build(),
                    contentDescription = contentDescription
                )
                .setTitle(PlainComplicationText.Builder(secondaryText).build())
                .setMonochromaticImage(monochromaticImage)
                .setTapAction(tapAction)
                .build()
            }
            ComplicationType.SMALL_IMAGE -> {
                SmallImageComplicationData.Builder(
                    smallImage = SmallImage.Builder(icon, SmallImageType.ICON).build(),
                    contentDescription = contentDescription
                )
                .setTapAction(tapAction)
                .build()
            }
            ComplicationType.MONOCHROMATIC_IMAGE -> {
                MonochromaticImageComplicationData.Builder(
                    monochromaticImage = monochromaticImage,
                    contentDescription = contentDescription
                )
                .setTapAction(tapAction)
                .build()
            }
            else -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(primaryText).build(),
                    contentDescription = contentDescription
                )
                .setTitle(PlainComplicationText.Builder(secondaryText).build())
                .setMonochromaticImage(monochromaticImage)
                .setTapAction(tapAction)
                .build()
            }
        }
    }

    private fun formatRemainingTime(diffMillis: Long): String {
        if (diffMillis <= 0) return "Now"
        val totalMinutes = diffMillis / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return when {
            hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
            hours > 0 -> "${hours}h"
            minutes > 0 -> "${minutes}m"
            else -> "<1m"
        }
    }

    private fun formatAlarmTime(triggerMillis: Long): String {
        val is24Hour = DateFormat.is24HourFormat(this)
        val date = Date(triggerMillis)
        return if (is24Hour) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
        } else {
            val cal = Calendar.getInstance().apply { time = date }
            val hour = cal.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            val minute = cal.get(Calendar.MINUTE)
            String.format(Locale.getDefault(), "%d:%02d", hour, minute)
        }
    }

    private fun getCalendarTapAction(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_NAVIGATE_TO, MainActivity.NAV_SCHEDULE)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            this,
            1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getAlarmTapAction(): PendingIntent {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            this,
            4,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}