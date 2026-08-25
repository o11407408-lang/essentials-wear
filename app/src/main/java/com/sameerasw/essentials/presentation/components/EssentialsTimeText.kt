package com.sameerasw.essentials.presentation.components

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.*
import com.sameerasw.essentials.R
import com.sameerasw.essentials.utils.ThemeUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun EssentialsTimeText(
    modifier: Modifier = Modifier,
    scrollState: ScalingLazyListState? = null,
    showWatchBattery: Boolean = false,
    showTime: Boolean = true,
    showDate: Boolean = false
) {
    val context = LocalContext.current

    var watchBatteryLevel by remember { mutableStateOf(-1) }
    var isWatchCharging by remember { mutableStateOf(false) }

    if (showWatchBattery) {
        DisposableEffect(context) {
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    intent?.let {
                        val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        if (level != -1 && scale != -1) {
                            watchBatteryLevel = (level * 100 / scale.toFloat()).toInt()
                        }
                        val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                        isWatchCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                                status == BatteryManager.BATTERY_STATUS_FULL
                    }
                }
            }
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val stickyIntent = context.registerReceiver(receiver, filter)
            stickyIntent?.let {
                val level = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (level != -1 && scale != -1) {
                    watchBatteryLevel = (level * 100 / scale.toFloat()).toInt()
                }
                val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
                isWatchCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL
            }
            onDispose {
                context.unregisterReceiver(receiver)
            }
        }
    }

    // Theme color is now reactive: if the user changes it in Settings (system <-> one of the
    // 10 fixed colors), this updates immediately without needing to reopen the app.
    var themeColorInt by remember { mutableStateOf(ThemeUtil.getThemeColor(context)) }
    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in ThemeUtil.WATCHED_PREF_KEYS) {
                themeColorInt = ThemeUtil.getThemeColor(context)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }
    val lightAccentColor = themeColorInt?.let {
        Color(ThemeUtil.getLightAccentColor(it))
    } ?: Color(0xFFB39DDB.toInt())

    val typography = MaterialTheme.typography

    if (showWatchBattery) {
        val batteryIcon = if (isWatchCharging) {
            R.drawable.rounded_battery_android_frame_bolt_24
        } else {
            when {
                watchBatteryLevel >= 75 -> R.drawable.rounded_battery_android_frame_full_24
                watchBatteryLevel >= 50 -> R.drawable.rounded_battery_android_frame_5_24
                watchBatteryLevel > 20 -> R.drawable.rounded_battery_android_frame_2_24
                else -> R.drawable.rounded_battery_android_alert_24
            }
        }

        val emptyTimeSource = remember {
            object : TimeSource {
                override val currentTime: String
                    @Composable
                    get() = ""
            }
        }

        val dateFormatter = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }
        val formattedDate = remember { LocalDate.now().format(dateFormatter) }

        TimeText(
            modifier = modifier,
            timeSource = if (showTime) TimeTextDefaults.timeSource(TimeTextDefaults.timeFormat()) else emptyTimeSource,
            timeTextStyle = typography.caption1.copy(color = lightAccentColor),
            textLinearSeparator = if (showTime) { { TimeTextDefaults.TextSeparator(textStyle = typography.caption1.copy(color = Color.LightGray)) } } else { {} },
            textCurvedSeparator = if (showTime) { { with(TimeTextDefaults) { CurvedTextSeparator(curvedTextStyle = CurvedTextStyle(typography.caption1.copy(color = Color.LightGray))) } } } else { {} },
            startLinearContent = if (watchBatteryLevel != -1) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(id = batteryIcon),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.LightGray
                        )
                        Text(
                            text = "$watchBatteryLevel%",
                            style = typography.caption1,
                            modifier = Modifier.padding(start = 2.dp),
                            color = Color.LightGray
                        )
                    }
                }
            } else null,
            startCurvedContent = if (watchBatteryLevel != -1) {
                {
                    curvedComposable {
                        Icon(
                            painter = painterResource(id = batteryIcon),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.LightGray
                        )
                    }
                    curvedText(
                        text = " $watchBatteryLevel%",
                        style = CurvedTextStyle(typography.caption1.copy(color = Color.LightGray))
                    )
                }
            } else null,
            endLinearContent = if (showDate) {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = " $formattedDate",
                            style = typography.caption1,
                            color = Color.LightGray
                        )
                    }
                }
            } else null,
            endCurvedContent = if (showDate) {
                {
                    curvedText(
                        text = " $formattedDate",
                        style = CurvedTextStyle(typography.caption1.copy(color = Color.LightGray))
                    )
                }
            } else null
        )
    } else {
        val prefs = remember { context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE) }
        var phoneBatteryLevel by remember { mutableStateOf(prefs.getInt("phone_battery_level", -1)) }
        var isPhoneCharging by remember { mutableStateOf(prefs.getBoolean("phone_is_charging", false)) }
        var deviceName by remember { mutableStateOf(prefs.getString("phone_device_name", "")) }

        val showDetails by remember(scrollState) {
            derivedStateOf {
                scrollState == null || (scrollState.centerItemIndex == 0 && scrollState.centerItemScrollOffset <= 100)
            }
        }

        DisposableEffect(Unit) {
            val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { p, key ->
                when (key) {
                    "phone_battery_level" -> phoneBatteryLevel = p.getInt(key, -1)
                    "phone_is_charging" -> isPhoneCharging = p.getBoolean(key, false)
                    "phone_device_name" -> deviceName = p.getString(key, "")
                }
            }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
            }
        }

        val phoneBatteryIcon = if (isPhoneCharging) {
            R.drawable.rounded_battery_android_frame_bolt_24
        } else {
            when {
                phoneBatteryLevel >= 75 -> R.drawable.rounded_battery_android_frame_full_24
                phoneBatteryLevel >= 50 -> R.drawable.rounded_battery_android_frame_5_24
                phoneBatteryLevel > 20 -> R.drawable.rounded_battery_android_frame_2_24
                else -> R.drawable.rounded_battery_android_alert_24
            }
        }

        val isAnyDetailVisible = showDetails && (!deviceName.isNullOrBlank() || phoneBatteryLevel != -1)

        if (!isAnyDetailVisible) {
            TimeText(modifier = modifier)
        } else {
            TimeText(
                modifier = modifier,
                textLinearSeparator = {},
                textCurvedSeparator = {},
                startLinearContent = if (!deviceName.isNullOrBlank()) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = deviceName!!,
                                maxLines = 1,
                                style = typography.caption1,
                                color = lightAccentColor
                            )
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_mobile_24),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp).padding(start = 2.dp),
                                tint = lightAccentColor
                            )
                            TimeTextDefaults.TextSeparator(
                                textStyle = typography.caption1.copy(color = lightAccentColor)
                            )
                        }
                    }
                } else null,
                startCurvedContent = if (!deviceName.isNullOrBlank()) {
                    {
                        curvedText(
                            text = deviceName!!,
                            style = CurvedTextStyle(typography.caption1.copy(color = lightAccentColor))
                        )
                        curvedComposable {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_mobile_24),
                                contentDescription = null,
                                modifier = Modifier.size(12.dp).padding(start = 2.dp),
                                tint = lightAccentColor
                            )
                        }
                        with(TimeTextDefaults) {
                            CurvedTextSeparator(
                                curvedTextStyle = CurvedTextStyle(typography.caption1.copy(color = lightAccentColor))
                            )
                        }
                    }
                } else null,
                endLinearContent = if (phoneBatteryLevel != -1) {
                    {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TimeTextDefaults.TextSeparator(
                                textStyle = typography.caption1.copy(color = lightAccentColor)
                            )
                            Icon(
                                painter = painterResource(id = phoneBatteryIcon),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = lightAccentColor
                            )
                            Text(
                                text = "$phoneBatteryLevel%",
                                style = typography.caption1,
                                modifier = Modifier.padding(start = 2.dp),
                                color = lightAccentColor
                            )
                        }
                    }
                } else null,
                endCurvedContent = if (phoneBatteryLevel != -1) {
                    {
                        with(TimeTextDefaults) {
                            CurvedTextSeparator(
                                curvedTextStyle = CurvedTextStyle(typography.caption1.copy(color = lightAccentColor))
                            )
                        }
                        curvedComposable {
                            Icon(
                                painter = painterResource(id = phoneBatteryIcon),
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = lightAccentColor
                            )
                        }
                        curvedText(
                            text = " $phoneBatteryLevel%",
                            style = CurvedTextStyle(typography.caption1.copy(color = lightAccentColor))
                        )
                    }
                } else null
            )
        }
    }
}
