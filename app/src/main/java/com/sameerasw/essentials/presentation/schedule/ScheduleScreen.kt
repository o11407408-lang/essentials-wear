package com.sameerasw.essentials.presentation.schedule

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.sameerasw.essentials.R
import com.sameerasw.essentials.presentation.components.EssentialsChip
import com.sameerasw.essentials.presentation.components.EssentialsScreen
import com.sameerasw.essentials.presentation.components.EssentialsTitle
import com.sameerasw.essentials.tile.MainTileService
import com.sameerasw.essentials.utils.ThemeUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    var allEvents by remember {
        mutableStateOf(MainTileService.getSyncedEvents(context))
    }
    val themeColor = remember {
        ThemeUtil.getThemeColor(context)
    }

    androidx.compose.runtime.DisposableEffect(context) {
        val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "synced_calendar_events") {
                allEvents = MainTileService.getSyncedEvents(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val groupedEvents = remember(allEvents) {
        val dateFormatter = SimpleDateFormat("EEE, d MMM", Locale.getDefault())
        allEvents.groupBy { dateFormatter.format(Date(it.begin)) }
    }

    val lightAccentColor = themeColor?.let {
        Color(ThemeUtil.getLightAccentColor(it))
    } ?: Color(0xFFB39DDB.toInt())

    val tonedThemeColor = themeColor?.let {
        Color(ThemeUtil.getTonedColor(it))
    } ?: Color.DarkGray

    EssentialsScreen {
        if (groupedEvents.isEmpty()) {
            item {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    textAlign = TextAlign.Center,
                    color = lightAccentColor,
                    text = stringResource(R.string.no_events)
                )
            }
        } else {
            item {
                EssentialsTitle(
                    text = stringResource(R.string.upcoming_agenda),
                    color = lightAccentColor
                )
            }

            groupedEvents.forEach { (date, eventsInDate) ->
                item {
                    Text(
                        text = date,
                        style = MaterialTheme.typography.caption1,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colors.onSurfaceVariant
                    )
                }

                items(eventsInDate) { event ->
                    EssentialsChip(
                        label = event.title ?: "No Title",
                        onClick = { },
                        secondaryLabel = if (event.allDay) "All day" else ThemeUtil.getTimeCountdown(
                            event.begin
                        ),
                        colors = if (event.allDay) {
                            ChipDefaults.secondaryChipColors(
                                backgroundColor = Color.Transparent,
                                contentColor = Color.White,
                                secondaryContentColor = Color.White.copy(alpha = 0.7f)
                            )
                        } else {
                            ChipDefaults.secondaryChipColors(
                                backgroundColor = tonedThemeColor,
                                contentColor = Color.White,
                                secondaryContentColor = Color.White.copy(alpha = 0.7f)
                            )
                        },

                        border = ChipDefaults.chipBorder(
                            borderStroke = if (event.allDay) BorderStroke(
                                1.dp,
                                tonedThemeColor
                            ) else null
                        )
                    )
                }
            }
        }
    }
}
