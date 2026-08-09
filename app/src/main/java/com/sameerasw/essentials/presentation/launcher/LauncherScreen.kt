package com.sameerasw.essentials.presentation.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class WatchNotificationItem(
    val key: String,
    val packageName: String,
    val appName: String,
    val iconBase64: String = "",
    val title: String,
    val text: String,
    val postTime: Long,
    val isMedia: Boolean = false,
    val canReply: Boolean = false
)

@Composable
fun LauncherScreen(crownEvents: SharedFlow<CrownAction>) {
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
    
    val notifListState = rememberScalingLazyListState()

    // Reset crown accumulator and ensure focus when page changes
    LaunchedEffect(pagerState.currentPage) {
        focusRequester.requestFocus()
        
        // Auto-reset notification list to top when swiping to it
        if (pagerState.currentPage == 2) {
            notifListState.scrollToItem(0)
        }
    }

    LaunchedEffect(crownEvents) {
        crownEvents.collect { action ->
            when (action) {
                CrownAction.GO_TO_CLOCK -> {
                    if (pagerState.currentPage != 1) {
                        HapticUtil.performUIHaptic(view)
                        pagerState.animateScrollToPage(1)
                    }
                }
                CrownAction.TOGGLE_LAUNCHER -> {
                    if (pagerState.currentPage != 1) {
                        HapticUtil.performUIHaptic(view)
                        pagerState.animateScrollToPage(1)
                    } else {
                        HapticUtil.performUIHaptic(view)
                        delay(50)
                        val intent = Intent(context, AppLauncherActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            }
        }
    }

    // Notifications state
    val notifications = remember { mutableStateListOf<WatchNotificationItem>() }
    var activeNewNotification by remember { mutableStateOf<WatchNotificationItem?>(null) }
    var replyTargetNotification by remember { mutableStateOf<WatchNotificationItem?>(null) }
    var detailTargetNotification by remember { mutableStateOf<WatchNotificationItem?>(null) }
    var overlayTimeoutKey by remember { mutableStateOf(0) }

    fun playNotificationSound(context: Context) {
        try {
            val mediaPlayer = MediaPlayer.create(context, R.raw.carmen_nexus)
            mediaPlayer?.start()
            mediaPlayer?.setOnCompletionListener { it.release() }
        } catch (e: Exception) {}
    }

    fun loadNotifications() {
        try {
            val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("watch_notifications_json", "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            val iconsJsonStr = prefs.getString("watch_app_icons_json", "{}") ?: "{}"
            val iconsObj = org.json.JSONObject(iconsJsonStr)

            val now = System.currentTimeMillis()
            val maxAgeMs = 48 * 60 * 60 * 1000L
            val validArray = JSONArray()

            notifications.clear()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val postTime = obj.optLong("postTime", 0L)
                
                // Auto-purge notifications older than 48 hours only if postTime is positive
                if (postTime > 0 && now - postTime > maxAgeMs) continue

                validArray.put(obj)
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
                        postTime = if (postTime > 0) postTime else now,
                        isMedia = obj.optBoolean("isMedia", false),
                        canReply = obj.optBoolean("canReply", false)
                    )
                )
            }

            if (validArray.length() != jsonArray.length()) {
                prefs.edit().putString("watch_notifications_json", validArray.toString()).apply()
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

    fun sendReplyFromWatchToPhone(key: String, replyText: String) {
        val nodeClient = Wearable.getNodeClient(context)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) return@addOnSuccessListener
            val messageClient = Wearable.getMessageClient(context)
            val jsonObj = org.json.JSONObject().apply {
                put("key", key)
                put("replyText", replyText)
            }
            val bytes = jsonObj.toString().toByteArray()
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/reply_phone_notification", bytes)
            }
        }
    }

    fun clearAllNotificationsOnWatchAndPhone() {
        try {
            val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            val jsonStr = prefs.getString("watch_notifications_json", "[]") ?: "[]"
            val jsonArray = JSONArray(jsonStr)
            
            val nodeClient = Wearable.getNodeClient(context)
            nodeClient.connectedNodes.addOnSuccessListener { nodes ->
                if (nodes.isNotEmpty()) {
                    val messageClient = Wearable.getMessageClient(context)
                    for (i in 0 until jsonArray.length()) {
                        val key = jsonArray.getJSONObject(i).optString("key")
                        if (key.isNotBlank()) {
                            for (node in nodes) {
                                messageClient.sendMessage(node.id, "/dismiss_phone_notification", key.toByteArray())
                            }
                        }
                    }
                }
            }
            prefs.edit().putString("watch_notifications_json", "[]").apply()
            loadNotifications()
        } catch (e: Exception) {}
    }

    DisposableEffect(context) {
        loadNotifications()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                android.util.Log.d("LauncherScreen", "Notification broadcast received")
                val newNotifJson = intent?.getStringExtra("new_notification_json")
                if (newNotifJson != null && pagerState.currentPage <= 1) {
                    try {
                        val obj = org.json.JSONObject(newNotifJson)
                        val pkg = obj.optString("packageName", "")
                        val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
                        val iconsJsonStr = prefs.getString("watch_app_icons_json", "{}") ?: "{}"
                        val iconsObj = org.json.JSONObject(iconsJsonStr)
                        val iconBase64 = iconsObj.optString(pkg, "")

                        activeNewNotification = WatchNotificationItem(
                            key = obj.getString("key"),
                            packageName = pkg,
                            appName = obj.optString("appName", ""),
                            iconBase64 = iconBase64,
                            title = obj.optString("title", ""),
                            text = obj.optString("text", ""),
                            postTime = obj.optLong("postTime", System.currentTimeMillis()),
                            isMedia = obj.optBoolean("isMedia", false),
                            canReply = obj.optBoolean("canReply", false)
                        )
                        overlayTimeoutKey++
                        playNotificationSound(context)
                        android.util.Log.d("LauncherScreen", "Showing new notification overlay")
                    } catch (e: Exception) {
                        android.util.Log.e("LauncherScreen", "Error parsing notification JSON", e)
                    }
                } else if (newNotifJson != null) {
                    playNotificationSound(context)
                }
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

    LaunchedEffect(activeNewNotification, overlayTimeoutKey) {
        if (activeNewNotification != null) {
            delay(7000)
            activeNewNotification = null
        }
    }

    Scaffold(
        timeText = {
            EssentialsTimeText(
                showWatchBattery = true,
                showTime = pagerState.currentPage != 1 || activeNewNotification != null
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    val rawDelta = event.verticalScrollPixels
                    if (pagerState.currentPage == 2) {
                        notifListState.dispatchRawDelta(rawDelta)
                        true
                    } else false
                }
                .focusRequester(focusRequester)
                .focusable()
        ) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = activeNewNotification == null
            ) { page ->
                when (page) {
                    0 -> QuickSettingsPage(tonedThemeColor, lightAccentColor, audioManager, watchRingerMode, focusRequester) {
                        watchRingerMode = it
                    }
                    1 -> ClockFacePage(formattedTime, lightAccentColor)
                    2 -> NotificationsPage(
                        notifications = notifications,
                        listState = notifListState,
                        lightAccentColor = lightAccentColor,
                        onDismiss = { key -> dismissNotificationOnPhoneAndWatch(key) },
                        onClearAll = { clearAllNotificationsOnWatchAndPhone() },
                        onSelectDetail = { notif -> detailTargetNotification = notif }
                    )
                }
            }

            activeNewNotification?.let { notif ->
                NewNotificationOverlay(
                    notification = notif,
                    lightAccentColor = lightAccentColor,
                    onDismissOverlay = { activeNewNotification = null },
                    onDismissNotification = {
                        dismissNotificationOnPhoneAndWatch(notif.key)
                        activeNewNotification = null
                    },
                    onReply = {
                        replyTargetNotification = notif
                        activeNewNotification = null
                    },
                    onInteraction = { overlayTimeoutKey++ }
                )
            }

            detailTargetNotification?.let { notif ->
                NotificationDetailSheet(
                    notification = notif,
                    lightAccentColor = lightAccentColor,
                    onDismissRequest = { detailTargetNotification = null },
                    onDismissNotification = { key ->
                        dismissNotificationOnPhoneAndWatch(key)
                        detailTargetNotification = null
                    },
                    onReply = { targetNotif ->
                        detailTargetNotification = null
                        replyTargetNotification = targetNotif
                    }
                )
            }

            replyTargetNotification?.let { notif ->
                ReplySheet(
                    notification = notif,
                    lightAccentColor = lightAccentColor,
                    onDismissRequest = { replyTargetNotification = null },
                    onSendReply = { replyText ->
                        sendReplyFromWatchToPhone(notif.key, replyText)
                        dismissNotificationOnPhoneAndWatch(notif.key)
                        replyTargetNotification = null
                        android.widget.Toast.makeText(context, "Replied ✓", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
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
    focusRequester: FocusRequester,
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
                Button(
                    onClick = {
                        HapticUtil.performUIHaptic(view)
                        try {
                            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {}
                        focusRequester.requestFocus()
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
                        focusRequester.requestFocus()
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
    listState: ScalingLazyListState,
    lightAccentColor: Color,
    onDismiss: (String) -> Unit,
    onClearAll: () -> Unit,
    onSelectDetail: (WatchNotificationItem) -> Unit
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
                    contentPadding = PaddingValues(vertical = 32.dp)
                ) {
                    items(notifications, key = { it.key }) { notif ->
                        WatchNotificationCardItem(
                            notif = notif,
                            lightAccentColor = lightAccentColor,
                            onDismiss = onDismiss,
                            onSelectDetail = onSelectDetail
                        )
                    }

                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 16.dp)
                                .background(
                                    color = Color(0xFF2A2A2A),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .clickable {
                                    HapticUtil.performUIHaptic(view)
                                    onClearAll()
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_clear_all_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.LightGray
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Clear All",
                                    style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color.White)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WatchNotificationCardItem(
    notif: WatchNotificationItem,
    lightAccentColor: Color,
    onDismiss: (String) -> Unit,
    onSelectDetail: (WatchNotificationItem) -> Unit
) {
    val view = LocalView.current
    var offsetX by remember { mutableStateOf(0f) }
    val swipeState = rememberDraggableState { delta ->
        offsetX += delta
    }

    val cardBg = if (notif.isMedia) Color(0xFF231B2B) else Color(0xFF1E1E1E)
    val borderColor = if (notif.isMedia) lightAccentColor.copy(alpha = 0.35f) else Color.Transparent

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer { translationX = offsetX }
            .draggable(
                state = swipeState,
                orientation = Orientation.Horizontal,
                onDragStopped = { velocity ->
                    if (kotlin.math.abs(offsetX) > 80f || kotlin.math.abs(velocity) > 200f) {
                        HapticUtil.performUIHaptic(view)
                        onDismiss(notif.key)
                    } else {
                        offsetX = 0f
                    }
                }
            )
            .background(color = cardBg, shape = RoundedCornerShape(24.dp))
            .border(
                width = if (notif.isMedia) 1.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(24.dp)
            )
            .clickable {
                HapticUtil.performUIHaptic(view)
                onSelectDetail(notif)
            }
            .padding(12.dp)
    ) {
         Column {
             Row(verticalAlignment = Alignment.CenterVertically) {
                 if (notif.iconBase64.isNotBlank()) {
                     val bitmap = remember(notif.iconBase64) {
                         try {
                             val bytes = android.util.Base64.decode(notif.iconBase64, android.util.Base64.NO_WRAP)
                             android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                         } catch (e: Exception) { null }
                     }
                     bitmap?.let { bmp ->
                         androidx.compose.foundation.Image(
                             bitmap = bmp.asImageBitmap(),
                             contentDescription = null,
                             modifier = Modifier.size(16.dp).clip(CircleShape)
                         )
                         Spacer(modifier = Modifier.width(6.dp))
                     }
                 }
                 if (notif.appName.isNotBlank()) {
                     Text(
                         text = notif.appName,
                         style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = lightAccentColor),
                         maxLines = 1,
                         overflow = TextOverflow.Ellipsis
                     )
                 }
                 if (notif.isMedia) {
                     Spacer(modifier = Modifier.weight(1f))
                     Icon(
                         painter = painterResource(id = R.drawable.rounded_music_note_24),
                         contentDescription = null,
                         modifier = Modifier.size(14.dp),
                         tint = lightAccentColor
                     )
                 } else if (notif.canReply) {
                     Spacer(modifier = Modifier.weight(1f))
                     Icon(
                         painter = painterResource(id = R.drawable.rounded_mobile_text_2_24),
                         contentDescription = null,
                         modifier = Modifier.size(14.dp),
                         tint = lightAccentColor
                     )
                 }
             }
             Spacer(modifier = Modifier.height(4.dp))
             if (notif.title.isNotBlank()) {
                Text(
                    text = notif.title,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (notif.text.isNotBlank()) {
                Text(
                    text = notif.text,
                    style = TextStyle(fontSize = 12.sp, color = Color.LightGray),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
         }
    }
}

@Composable
fun NewNotificationOverlay(
    notification: WatchNotificationItem,
    lightAccentColor: Color,
    onDismissOverlay: () -> Unit,
    onDismissNotification: () -> Unit,
    onReply: (WatchNotificationItem) -> Unit,
    onInteraction: () -> Unit
) {
    val view = LocalView.current
    val scrollState = rememberScalingLazyListState()

    val swipeDownState = rememberDraggableState { delta ->
        if (delta > 20f) { onDismissOverlay() }
    }

    val swipeHorizontalState = rememberDraggableState { delta ->
        if (kotlin.math.abs(delta) > 30f) {
            HapticUtil.performUIHaptic(view)
            onDismissNotification()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = false) {}
            .draggable(state = swipeDownState, orientation = Orientation.Vertical, onDragStopped = { onInteraction() })
            .draggable(state = swipeHorizontalState, orientation = Orientation.Horizontal, onDragStopped = { onInteraction() }),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            EssentialsTimeText(showWatchBattery = true, showTime = true)

            ScalingLazyColumn(
                state = scrollState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)
            ) {
                item {
                    if (notification.iconBase64.isNotBlank()) {
                        val bitmap = remember(notification.iconBase64) {
                            try {
                                val bytes = android.util.Base64.decode(notification.iconBase64, android.util.Base64.NO_WRAP)
                                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            } catch (e: Exception) { null }
                        }
                        bitmap?.let { bmp ->
                            androidx.compose.foundation.Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF1E1E1E)).padding(8.dp)
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = notification.appName,
                        style = TextStyle(fontSize = 12.sp, color = lightAccentColor, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                item {
                    Text(
                        text = notification.title,
                        style = TextStyle(fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
                item {
                    Text(
                        text = notification.text,
                        style = TextStyle(fontSize = 14.sp, color = Color.LightGray, textAlign = TextAlign.Center),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                if (notification.canReply) {
                    item {
                        Box(
                            modifier = Modifier
                                .padding(top = 4.dp, bottom = 16.dp)
                                .background(lightAccentColor, shape = RoundedCornerShape(20.dp))
                                .clickable {
                                    HapticUtil.performUIHaptic(view)
                                    onReply(notification)
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_mobile_text_2_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.Black
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Reply",
                                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Black)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) { onInteraction() }
    }
}

@Composable
fun ReplySheet(
    notification: WatchNotificationItem,
    lightAccentColor: Color,
    onDismissRequest: () -> Unit,
    onSendReply: (String) -> Unit
) {
    val view = LocalView.current
    var customText by remember { mutableStateOf("") }
    val quickReplies = remember {
        listOf(
            "OK 👍",
            "Yes",
            "No",
            "Thanks!",
            "On my way 🚗",
            "Call you later 📞"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable { onDismissRequest() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Reply to ${notification.appName.ifBlank { "Message" }}",
                style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = lightAccentColor),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                quickReplies.forEach { replyOption ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .background(Color(0xFF2A2A2A), shape = RoundedCornerShape(16.dp))
                            .clickable {
                                HapticUtil.performUIHaptic(view)
                                onSendReply(replyOption)
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = replyOption,
                            style = TextStyle(fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2A2A2A), shape = RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .background(if (customText.isNotBlank()) lightAccentColor else Color.DarkGray, shape = CircleShape)
                        .clickable(enabled = customText.isNotBlank()) {
                            HapticUtil.performUIHaptic(view)
                            onSendReply(customText)
                        }
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_check_24),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (customText.isNotBlank()) Color.Black else Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationDetailSheet(
    notification: WatchNotificationItem,
    lightAccentColor: Color,
    onDismissRequest: () -> Unit,
    onDismissNotification: (String) -> Unit,
    onReply: ((WatchNotificationItem) -> Unit)? = null
) {
    val view = LocalView.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable { onDismissRequest() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (notification.iconBase64.isNotBlank()) {
                    val bitmap = remember(notification.iconBase64) {
                        try {
                            val bytes = android.util.Base64.decode(notification.iconBase64, android.util.Base64.NO_WRAP)
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (e: Exception) { null }
                    }
                    bitmap?.let { bmp ->
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp).clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
                Text(
                    text = notification.appName.ifBlank { "Notification" },
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp, color = lightAccentColor),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (notification.title.isNotBlank()) {
                Text(
                    text = notification.title,
                    style = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            if (notification.text.isNotBlank()) {
                Text(
                    text = notification.text,
                    style = TextStyle(fontSize = 12.sp, color = Color.LightGray, textAlign = TextAlign.Center),
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                if (notification.canReply && onReply != null) {
                    Box(
                        modifier = Modifier
                            .background(lightAccentColor, shape = RoundedCornerShape(16.dp))
                            .clickable {
                                HapticUtil.performUIHaptic(view)
                                onDismissRequest()
                                onReply(notification)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(text = "Reply", style = TextStyle(color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp))
                    }
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF3A2323), shape = RoundedCornerShape(16.dp))
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            onDismissNotification(notification.key)
                            onDismissRequest()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(text = "Dismiss", style = TextStyle(color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold, fontSize = 12.sp))
                }
            }
        }
    }
}
