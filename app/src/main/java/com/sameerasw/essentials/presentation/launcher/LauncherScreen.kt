package com.sameerasw.essentials.presentation.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import com.sameerasw.essentials.R
import com.sameerasw.essentials.presentation.components.EssentialsTimeText
import com.sameerasw.essentials.presentation.theme.GoogleSansFlexRoundedWide
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.utils.ThemeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

data class WatchNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val iconBase64: String = "",
    val title: String,
    val text: String,
    val postTime: Long
)

@Composable
fun LauncherScreen() {
    val context = LocalContext.current
    val view = LocalView.current
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    // Theme colors
    val themeColor = remember { ThemeUtil.getThemeColor(context) }
    val lightAccentColor = themeColor?.let {
        Color(ThemeUtil.getLightAccentColor(it))
    } ?: Color(0xFFB39DDB.toInt())

    val tonedThemeColor = themeColor?.let {
        Color(ThemeUtil.getTonedColor(it))
    } ?: Color.DarkGray

    // Clock state
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000)
        }
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("hh:mm") }
    val formattedTime = currentTime.format(timeFormatter)

    // Watch Audio Manager state
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var watchRingerMode by remember { mutableStateOf(audioManager.ringerMode) }

    // Pager State - Start at page 1 (Clock Face)
    val pagerState = rememberPagerState(initialPage = 1, pageCount = { 3 })
    
    // Crown logic constants
    var crownAccumulator by remember { mutableStateOf(0f) }
    val crownTriggerThreshold = 100f // Adjust sensitivity as needed

    // Notifications state
    val notifications = remember { mutableStateListOf<WatchNotificationItem>() }

    fun loadNotifications() {
        try {
            val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("watch_notifications_json", "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            val iconsJsonStr = prefs.getString("watch_app_icons_json", "{}") ?: "{}"
            val iconsObj = org.json.JSONObject(iconsJsonStr)

            notifications.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val pkg = obj.optString("packageName", "")
                val iconBase64 = iconsObj.optString(pkg, "")

                notifications.add(
                    WatchNotificationItem(
                        key = obj.getString("key"),
                        packageName = pkg,
                        appName = obj.optString("appName", ""),
                        iconBase64 = iconBase64,
                        title = obj.optString("title", ""),
                        text = obj.optString("text", ""),
                        postTime = obj.optLong("postTime", System.currentTimeMillis())
                    )
                )
            }
        } catch (e: Exception) {
            // Fallback
        }
    }

    fun dismissNotificationOnPhoneAndWatch(key: String) {
        try {
            val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            val existingJson = prefs.getString("watch_notifications_json", "[]") ?: "[]"
            val jsonArray = JSONArray(existingJson)
            val updatedArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optString("key") != key) {
                    updatedArray.put(obj)
                }
            }
            prefs.edit().putString("watch_notifications_json", updatedArray.toString()).apply()
            loadNotifications()
        } catch (e: Exception) {}

        val nodeClient = Wearable.getNodeClient(context)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) return@addOnSuccessListener
            val messageClient = Wearable.getMessageClient(context)
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/dismiss_phone_notification", key.toByteArray())
            }
        }
    }

    DisposableEffect(context) {
        loadNotifications()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                loadNotifications()
            }
        }
        val filter = IntentFilter("com.sameerasw.essentials.NOTIFICATIONS_UPDATED")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {}
        }
    }

    val notifListState = rememberScalingLazyListState()

    Scaffold(
        timeText = {
            EssentialsTimeText(
                showWatchBattery = true,
                showTime = pagerState.currentPage != 1
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    val rawDelta = event.verticalScrollPixels
                    
                    when (pagerState.currentPage) {
                        0, 1 -> {
                            // Accumulate crown, snap to prev/next page on threshold
                            crownAccumulator += rawDelta
                            if (crownAccumulator > crownTriggerThreshold) {
                                if (pagerState.currentPage < 2) {
                                    scope.launch {
                                        HapticUtil.performUIHaptic(view)
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                                crownAccumulator = 0f
                            } else if (crownAccumulator < -crownTriggerThreshold) {
                                if (pagerState.currentPage > 0) {
                                    scope.launch {
                                        HapticUtil.performUIHaptic(view)
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                                crownAccumulator = 0f
                            }
                            true
                        }
                        2 -> {
                            // Scroll notification list directly
                            val isAtTop = notifListState.centerItemIndex == 0 && notifListState.centerItemScrollOffset <= 0
                            if (isAtTop && rawDelta < 0f) {
                                // Crown UP at top of list -> go back to home page
                                crownAccumulator += rawDelta
                                if (crownAccumulator < -crownTriggerThreshold) {
                                    scope.launch {
                                        HapticUtil.performUIHaptic(view)
                                        pagerState.animateScrollToPage(1)
                                    }
                                    crownAccumulator = 0f
                                }
                            } else {
                                crownAccumulator = 0f
                                scope.launch { notifListState.scrollBy(rawDelta) }
                            }
                            true
                        }
                        else -> false
                    }
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> QuickSettingsPage(tonedThemeColor, lightAccentColor, audioManager, watchRingerMode) {
                        watchRingerMode = it
                    }
                    1 -> ClockFacePage(formattedTime, lightAccentColor)
                    2 -> NotificationsPage(notifications, notifListState, lightAccentColor) { key ->
                        dismissNotificationOnPhoneAndWatch(key)
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
fun QuickSettingsPage(
    tonedThemeColor: Color,
    lightAccentColor: Color,
    audioManager: AudioManager,
    watchRingerMode: Int,
    onRingerModeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 1. Android Settings Button
                Button(
                    onClick = {
                        HapticUtil.performUIHaptic(view)
                        try {
                            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier.size(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = tonedThemeColor,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_settings_heart_24),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // 2. Watch Sound Mode Toggle Button
                val isNormal = watchRingerMode == AudioManager.RINGER_MODE_NORMAL
                val soundModeColors = if (!isNormal) {
                    ButtonDefaults.buttonColors(
                        backgroundColor = lightAccentColor,
                        contentColor = Color.Black
                    )
                } else {
                    ButtonDefaults.buttonColors(
                        backgroundColor = tonedThemeColor,
                        contentColor = Color.White
                    )
                }

                val soundIcon = when (watchRingerMode) {
                    AudioManager.RINGER_MODE_VIBRATE -> R.drawable.rounded_mobile_vibrate_24
                    AudioManager.RINGER_MODE_SILENT -> R.drawable.rounded_volume_off_24
                    else -> R.drawable.rounded_volume_up_24
                }

                Button(
                    onClick = {
                        HapticUtil.performUIHaptic(view)
                        val nextMode = when (watchRingerMode) {
                            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
                            else -> AudioManager.RINGER_MODE_NORMAL
                        }
                        try {
                            audioManager.ringerMode = nextMode
                            onRingerModeChanged(audioManager.ringerMode)
                        } catch (e: Exception) {}
                    },
                    modifier = Modifier.size(56.dp),
                    colors = soundModeColors
                ) {
                    Icon(
                        painter = painterResource(id = soundIcon),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ClockFacePage(formattedTime: String, lightAccentColor: Color) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        val computedFontSize = (maxWidth.value * 0.65f / 3.2f).coerceIn(40f, 68f).sp
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formattedTime,
                style = TextStyle(
                    fontFamily = GoogleSansFlexRoundedWide,
                    fontWeight = FontWeight.Bold,
                    fontSize = computedFontSize,
                    color = lightAccentColor,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier.fillMaxWidth(),
                maxLines = 1
            )
        }
    }
}

@Composable
fun NotificationsPage(
    notifications: List<WatchNotificationItem>,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    lightAccentColor: Color,
    onDismiss: (String) -> Unit
) {
    val view = LocalView.current
    
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            if (notifications.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = stringResource(R.string.launcher_notifications_empty),
                        style = TextStyle(
                            fontFamily = GoogleSansFlexRoundedWide,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    )
                }
            } else {
                ScalingLazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 32.dp) // Extra padding for top/bottom
                ) {
                    items(notifications, key = { it.key }) { notif ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(
                                    color = Color(0xFF1E1E1E),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable {
                                    HapticUtil.performUIHaptic(view)
                                    onDismiss(notif.key)
                                }
                                .padding(12.dp)
                        ) {
                             Column {
                                 Row(
                                     verticalAlignment = Alignment.CenterVertically
                                 ) {
                                     if (notif.iconBase64.isNotBlank()) {
                                         val bitmap = remember(notif.iconBase64) {
                                             try {
                                                 val bytes = android.util.Base64.decode(notif.iconBase64, android.util.Base64.NO_WRAP)
                                                 android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                             } catch (e: Exception) {
                                                 null
                                             }
                                         }
                                         bitmap?.let { bmp ->
                                             androidx.compose.foundation.Image(
                                                 bitmap = bmp.asImageBitmap(),
                                                 contentDescription = null,
                                                 modifier = Modifier
                                                     .size(16.dp)
                                                     .clip(CircleShape)
                                             )
                                              Spacer(modifier = Modifier.width(6.dp))
                                         }
                                     }

                                     if (notif.appName.isNotBlank()) {
                                         Text(
                                             text = notif.appName,
                                             style = TextStyle(
                                                 fontWeight = FontWeight.SemiBold,
                                                 fontSize = 11.sp,
                                                 color = lightAccentColor
                                             ),
                                             maxLines = 1,
                                             overflow = TextOverflow.Ellipsis
                                         )
                                     }
                                 }
                                 Spacer(modifier = Modifier.height(4.dp))
                                 if (notif.title.isNotBlank()) {
                                    Text(
                                        text = notif.title,
                                        style = TextStyle(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = Color.White
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (notif.text.isNotBlank()) {
                                    Text(
                                        text = notif.text,
                                        style = TextStyle(
                                            fontSize = 12.sp,
                                            color = Color.LightGray
                                        ),
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
