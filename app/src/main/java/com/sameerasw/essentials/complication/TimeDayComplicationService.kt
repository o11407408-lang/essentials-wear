package com.sameerasw.essentials.complication

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.format.DateFormat
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.sameerasw.essentials.R
import com.sameerasw.essentials.presentation.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TimeDayComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val calendar = Calendar.getInstance()
        return createComplicationData(calendar, type)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        val calendar = Calendar.getInstance()
        return createComplicationData(calendar, request.complicationType)
    }

    private fun createComplicationData(calendar: Calendar, type: ComplicationType): ComplicationData {
        val is24Hour = DateFormat.is24HourFormat(this)
        
        val timeText = if (is24Hour) {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(calendar.time)
        } else {
            val hour = calendar.get(Calendar.HOUR).let { if (it == 0) 12 else it }
            val minute = calendar.get(Calendar.MINUTE)
            String.format(Locale.getDefault(), "%d:%02d", hour, minute)
        }

        val dateDayText = SimpleDateFormat("EEE d", Locale.getDefault()).format(calendar.time)
        val contentDescription = PlainComplicationText.Builder("$timeText, $dateDayText").build()

        val iconRes = getTimeOfDayIconRes(calendar.get(Calendar.HOUR_OF_DAY))
        val icon = Icon.createWithResource(this, iconRes)
        val monochromaticImage = MonochromaticImage.Builder(icon).build()
        val tapAction = getTapAction()

        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(timeText).build(),
                    contentDescription = contentDescription
                )
                .setTitle(PlainComplicationText.Builder(dateDayText).build())
                .setMonochromaticImage(monochromaticImage)
                .setTapAction(tapAction)
                .build()
            }
            ComplicationType.RANGED_VALUE -> {
                val minuteOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 60 + calendar.get(Calendar.MINUTE)
                androidx.wear.watchface.complications.data.RangedValueComplicationData.Builder(
                    value = minuteOfDay.toFloat(),
                    min = 0f,
                    max = 1440f,
                    contentDescription = contentDescription
                )
                .setText(PlainComplicationText.Builder(timeText).build())
                .setTitle(PlainComplicationText.Builder(dateDayText).build())
                .setMonochromaticImage(monochromaticImage)
                .setTapAction(tapAction)
                .build()
            }
            ComplicationType.SMALL_IMAGE -> {
                androidx.wear.watchface.complications.data.SmallImageComplicationData.Builder(
                    smallImage = androidx.wear.watchface.complications.data.SmallImage.Builder(
                        image = icon,
                        type = androidx.wear.watchface.complications.data.SmallImageType.ICON
                    ).build(),
                    contentDescription = contentDescription
                )
                .setTapAction(tapAction)
                .build()
            }
            ComplicationType.MONOCHROMATIC_IMAGE -> {
                androidx.wear.watchface.complications.data.MonochromaticImageComplicationData.Builder(
                    monochromaticImage = monochromaticImage,
                    contentDescription = contentDescription
                )
                .setTapAction(tapAction)
                .build()
            }
            else -> {
                ShortTextComplicationData.Builder(
                    text = PlainComplicationText.Builder(timeText).build(),
                    contentDescription = contentDescription
                )
                .setTitle(PlainComplicationText.Builder(dateDayText).build())
                .setMonochromaticImage(monochromaticImage)
                .setTapAction(tapAction)
                .build()
            }
        }
    }

    private fun getTimeOfDayIconRes(hourOfDay: Int): Int {
        return when (hourOfDay) {
            in 5..11 -> R.drawable.rounded_wb_twilight_24
            in 12..16 -> R.drawable.rounded_wb_sunny_24
            in 17..20 -> R.drawable.rounded_wb_twilight_24
            else -> R.drawable.rounded_nights_stay_24
        }
    }

    private fun getTapAction(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        return PendingIntent.getActivity(
            this,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
