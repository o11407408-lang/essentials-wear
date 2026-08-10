package com.sameerasw.essentials.presentation.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaPlayer
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.curvedComposable
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
import com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded
import com.sameerasw.essentials.presentation.theme.GoogleSansFlexRoundedWide
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.utils.ThemeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.distinctUntilChanged
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
fun LauncherScreen(crownEvents: SharedFlow<CrownAction>, isAmbient: Boolean = false) {
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
    val qsListState = rememberScalingLazyListState()

    // Reset crown accumulator and ensure focus when page changes
    LaunchedEffect(pagerState.currentPage, isAmbient) {
        if (!isAmbient) {
            focusRequester.requestFocus()
        } else {
            // Auto-return to clock in ambient mode
            if (pagerState.currentPage != 1) {
                pagerState.scrollToPage(1)
            }
        }
        HapticUtil.performPageSwitchHaptic(view)
        
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
                        HapticUtil.performPageSwitchHaptic(view)
                        pagerState.animateScrollToPage(1)
                    }
                }
                CrownAction.TOGGLE_LAUNCHER -> {
                    if (pagerState.currentPage != 1) {
                        HapticUtil.performPageSwitchHaptic(view)
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

    // Immutable list reference replaced on every update so child composables always recompose
    var notifications by remember { mutableStateOf(listOf<WatchNotificationItem>()) }
    var activeNewNotification by remember { mutableStateOf<WatchNotificationItem?>(null) }
    var replyTargetNotification by remember { mutableStateOf<WatchNotificationItem?>(null) }
    var detailTargetNotification by remember { mutableStateOf<WatchNotificationItem?>(null) }
    var overlayTimeoutKey by remember { mutableStateOf(0) }
    var crownAccumulator by remember { mutableStateOf(0f) }
    val crownThreshold = 80f

    fun playNotificationSound(context: Context) {
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        try {
            val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            val soundName = prefs.getString("selected_notification_sound", "carmen_nexus") ?: "carmen_nexus"
            val soundResId = when (soundName) {
                "google" -> R.raw.google
                "notification" -> R.raw.notification
                "dock" -> R.raw.dock
                else -> R.raw.carmen_nexus
            }
            val mediaPlayer = MediaPlayer.create(context, soundResId)
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
            val newList = mutableListOf<WatchNotificationItem>()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val postTime = obj.optLong("postTime", 0L)
                if (postTime > 0 && now - postTime > maxAgeMs) continue
                validArray.put(obj)
                val pkg = obj.optString("packageName", "")
                val iconBase64 = iconsObj.optString(pkg, "")
                newList.add(
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
            notifications = newList
        } catch (e: Exception) {}
    }

    fun dismissNotificationOnPhoneAndWatch(key: String) {
        notifications = notifications.filter { it.key != key }
        try {
            val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
            val existingJson = prefs.getString("watch_notifications_json", "[]") ?: "[]"
            val jsonArray = JSONArray(existingJson)
            val updatedArray = JSONArray()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                if (obj.optString("key") != key) updatedArray.put(obj)
            }
            prefs.edit().putString("watch_notifications_json", updatedArray.toString()).apply()
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
            notifications = emptyList()
        } catch (e: Exception) {}
    }

    DisposableEffect(context) {
        loadNotifications()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                android.util.Log.d("LauncherScreen", "Notification broadcast received")
                val newNotifJson = intent?.getStringExtra("new_notification_json")
                if (newNotifJson != null) {
                    try {
                        val obj = org.json.JSONObject(newNotifJson)
                        val pkg = obj.optString("packageName", "")
                        val prefs = context.getSharedPreferences("schedule_prefs", Context.MODE_PRIVATE)
                        val iconsJsonStr = prefs.getString("watch_app_icons_json", "{}") ?: "{}"
                        val iconsObj = org.json.JSONObject(iconsJsonStr)
                        val iconBase64 = iconsObj.optString(pkg, "")

                        val item = WatchNotificationItem(
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

                        if (!item.isMedia) {
                            if (pagerState.currentPage <= 1) {
                                activeNewNotification = item
                                overlayTimeoutKey++
                                playNotificationSound(context)
                                HapticUtil.performStrongDoubleTap(view)
                                android.util.Log.d("LauncherScreen", "Showing new notification overlay")
                            } else {
                                // Drawer is open, play sound only for regular notifications
                                playNotificationSound(context)
                                HapticUtil.performStrongDoubleTap(view)
                            }
                        } else {
                            android.util.Log.d("LauncherScreen", "Media notification received - skipping overlay/sound")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("LauncherScreen", "Error parsing notification JSON", e)
                    }
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
            delay(10000)
            activeNewNotification = null
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        crownAccumulator = 0f
    }

    Scaffold(
        timeText = {
            AnimatedVisibility(
                visible = !isAmbient,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                EssentialsTimeText(
                    showWatchBattery = true,
                    showTime = pagerState.currentPage != 1 || activeNewNotification != null,
                    showDate = pagerState.currentPage == 0 // Show date in Quick Settings
                )
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    if (isAmbient) return@onRotaryScrollEvent false
                    val rawDelta = event.verticalScrollPixels
                    val pageAnim = tween<Float>(durationMillis = 600)
                    when (pagerState.currentPage) {
                        0 -> {
                            // Unified scrolling for QS: scroll down at bottom goes to clock
                            val atBottom = qsListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index == qsListState.layoutInfo.totalItemsCount - 1
                            if (rawDelta > 0 && atBottom) {
                                crownAccumulator += rawDelta
                                if (crownAccumulator > crownThreshold) {
                                    crownAccumulator = 0f
                                    scope.launch { pagerState.animateScrollToPage(1, animationSpec = pageAnim) }
                                }
                            } else {
                                crownAccumulator = 0f
                                qsListState.dispatchRawDelta(rawDelta)
                            }
                            true
                        }
                        1 -> {
                            crownAccumulator += rawDelta
                            if (crownAccumulator > crownThreshold) {
                                crownAccumulator = 0f
                                scope.launch { pagerState.animateScrollToPage(2, animationSpec = pageAnim) }
                            } else if (crownAccumulator < -crownThreshold) {
                                crownAccumulator = 0f
                                scope.launch { pagerState.animateScrollToPage(0, animationSpec = pageAnim) }
                            }
                            true
                        }
                        2 -> {
                            // From notifications: crown up at top returns to clock, otherwise scroll list
                            val atTop = notifListState.centerItemIndex <= 0
                            if (rawDelta < 0 && atTop) {
                                crownAccumulator += rawDelta
                                if (crownAccumulator < -crownThreshold) {
                                    crownAccumulator = 0f
                                    scope.launch { pagerState.animateScrollToPage(1, animationSpec = pageAnim) }
                                }
                            } else {
                                crownAccumulator = 0f
                                notifListState.dispatchRawDelta(rawDelta)
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
                userScrollEnabled = activeNewNotification == null && !isAmbient
            ) { page ->
                when (page) {
                    0 -> QuickSettingsPage(tonedThemeColor, lightAccentColor, audioManager, watchRingerMode, focusRequester, qsListState) {
                        watchRingerMode = it
                    }
                    1 -> ClockFacePage(formattedTime, lightAccentColor, isAmbient)
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

            AnimatedVisibility(
                visible = pagerState.currentPage == 1 && activeNewNotification == null && !isAmbient,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                BottomNotificationIndicator(
                    notifications = notifications,
                    lightAccentColor = lightAccentColor,
                    modifier = Modifier.fillMaxSize()
                )
            }

            AnimatedVisibility(
                visible = activeNewNotification != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
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
    listState: ScalingLazyListState,
    onRingerModeChanged: (Int) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    val bubbleColors = ButtonDefaults.buttonColors(
        backgroundColor = lightAccentColor,
        contentColor = Color.Black
    )

    val inactiveBubbleColors = ButtonDefaults.buttonColors(
        backgroundColor = tonedThemeColor,
        contentColor = Color.White
    )

    fun sendMessage(path: String, data: ByteArray = byteArrayOf()) {
        val nodeClient = Wearable.getNodeClient(context)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            val messageClient = Wearable.getMessageClient(context)
            for (node in nodes) {
                messageClient.sendMessage(node.id, path, data)
            }
        }
    }

    var aodEnabled by remember {
        mutableStateOf(Settings.Secure.getInt(context.contentResolver, "doze_enabled", 0) != 0)
    }
    var powerSavingEnabled by remember {
        mutableStateOf(Settings.Global.getInt(context.contentResolver, "low_power", 0) != 0)
    }

    // Update states periodically while visible
    LaunchedEffect(Unit) {
        while (true) {
            aodEnabled = Settings.Secure.getInt(context.contentResolver, "doze_enabled", 0) != 0
            powerSavingEnabled = Settings.Global.getInt(context.contentResolver, "low_power", 0) != 0
            delay(2000)
        }
    }

    // List of keys, reversed as requested
    val activeKeys = remember {
        listOf("POWER_SAVING", "AOD", "LOCK", "FLASHLIGHT", "PHONE", "SOUND", "SETTINGS")
    }
    
    val rows = remember(activeKeys) {
        val result = mutableListOf<List<String>>()
        var index = 0
        var size = 3
        while (index < activeKeys.size) {
            result.add(activeKeys.subList(index, minOf(index + size, activeKeys.size)))
            index += size
            size = if (size == 3) 2 else 3
        }
        result
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        items(rows.size) { rowIndex ->
            val rowItems = rows[rowIndex]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (key in rowItems) {
                    when (key) {
                        "SETTINGS" -> {
                            Button(
                                onClick = {
                                    HapticUtil.performPageSwitchHaptic(view)
                                    try {
                                        val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {}
                                    focusRequester.requestFocus()
                                },
                                modifier = Modifier.size(52.dp),
                                colors = inactiveBubbleColors
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_settings_heart_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        "SOUND" -> {
                            val isNormal = watchRingerMode == AudioManager.RINGER_MODE_NORMAL
                            val soundModeColors = if (!isNormal) bubbleColors else inactiveBubbleColors
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
                                modifier = Modifier.size(52.dp),
                                colors = soundModeColors
                            ) {
                                Icon(
                                    painter = painterResource(id = soundIcon),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        "PHONE" -> {
                            Button(
                                onClick = {
                                    HapticUtil.performUIHaptic(view)
                                    try {
                                        val intent = Intent(context, com.sameerasw.essentials.presentation.MainActivity::class.java).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            putExtra(com.sameerasw.essentials.presentation.MainActivity.EXTRA_NAVIGATE_TO, com.sameerasw.essentials.presentation.MainActivity.NAV_YOUR_ANDROID)
                                        }
                                        context.startActivity(intent)
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier.size(52.dp),
                                colors = inactiveBubbleColors
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_mobile_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        "FLASHLIGHT" -> {
                            Button(
                                onClick = {
                                    HapticUtil.performUIHaptic(view)
                                    try {
                                        // Attempt multiple package names if needed
                                        val flashlightPackages = listOf(
                                            "com.google.android.apps.wearable.flashlight",
                                            "com.samsung.android.watch.flashlight",
                                            "com.mobvoi.ticwear.flashlight",
                                            "com.fossil.wearables.flashlight"
                                        )
                                        var launched = false
                                        for (pkg in flashlightPackages) {
                                            val intent = context.packageManager.getLaunchIntentForPackage(pkg)
                                            if (intent != null) {
                                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                                launched = true
                                                break
                                            }
                                        }
                                        if (!launched) {
                                            // Final fallback: try query intent for any activity with "flashlight" in name/category
                                            val searchIntent = Intent(Intent.ACTION_MAIN).apply {
                                                addCategory(Intent.CATEGORY_LAUNCHER)
                                            }
                                            val apps = context.packageManager.queryIntentActivities(searchIntent, 0)
                                            val flashlightApp = apps.find { 
                                                it.activityInfo.packageName.contains("flashlight", ignoreCase = true) ||
                                                it.loadLabel(context.packageManager).toString().contains("flashlight", ignoreCase = true)
                                            }
                                            flashlightApp?.let {
                                                val intent = context.packageManager.getLaunchIntentForPackage(it.activityInfo.packageName)
                                                intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                context.startActivity(intent)
                                            }
                                        }
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier.size(52.dp),
                                colors = inactiveBubbleColors
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_flashlight_on_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        "LOCK" -> {
                            Button(
                                onClick = {
                                    HapticUtil.performUIHaptic(view)
                                    sendMessage("/lock_device")
                                },
                                modifier = Modifier.size(52.dp),
                                colors = inactiveBubbleColors
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_lock_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        "AOD" -> {
                            val aodColors = if (aodEnabled) bubbleColors else inactiveBubbleColors
                            Button(
                                onClick = {
                                    HapticUtil.performUIHaptic(view)
                                    try {
                                        val newValue = if (aodEnabled) 0 else 1
                                        Settings.Secure.putInt(context.contentResolver, "doze_enabled", newValue)
                                        aodEnabled = newValue != 0
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier.size(52.dp),
                                colors = aodColors
                            ) {
                                Icon(
                                    painter = painterResource(id = if (aodEnabled) R.drawable.rounded_mobile_text_2_24 else R.drawable.rounded_mobile_off_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        "POWER_SAVING" -> {
                            val psColors = if (powerSavingEnabled) bubbleColors else inactiveBubbleColors
                            Button(
                                onClick = {
                                    HapticUtil.performUIHaptic(view)
                                    try {
                                        val newValue = if (powerSavingEnabled) 0 else 1
                                        Settings.Global.putInt(context.contentResolver, "low_power", newValue)
                                        powerSavingEnabled = newValue != 0
                                    } catch (e: Exception) {}
                                },
                                modifier = Modifier.size(52.dp),
                                colors = psColors
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.rounded_battery_android_alert_24),
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
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
fun ClockFacePage(formattedTime: String, lightAccentColor: Color, isAmbient: Boolean = false) {
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 4.dp)
    ) {
        val computedFontSize = (maxWidth.value * 0.65f / 3.2f).coerceIn(40f, 68f).sp
        val fontWeight = if (isAmbient) FontWeight.Light else FontWeight.Bold
        
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formattedTime,
                style = TextStyle(
                    fontFamily = GoogleSansFlexRoundedWide,
                    fontWeight = fontWeight,
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
    
    // Read directly from SnapshotStateList so recomposition triggers on every item change
    val mediaNotifications = notifications.filter { it.isMedia }.sortedByDescending { it.postTime }
    val regularNotifications = notifications.filter { !it.isMedia }

    // Scroll haptics
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.centerItemIndex }
            .distinctUntilChanged()
            .collect {
                HapticUtil.performSubtleTick(view)
            }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            if (notifications.isEmpty()) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
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
                    contentPadding = PaddingValues(vertical = 32.dp, horizontal = 8.dp)
                ) {
                    if (mediaNotifications.isNotEmpty()) {
                        item {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = if (mediaNotifications.size == 1) Arrangement.Center else Arrangement.spacedBy(8.dp)
                            ) {
                                lazyRowItems(mediaNotifications, key = { it.key }) { notif ->
                                    WatchNotificationCardItem(
                                        notif = notif,
                                        lightAccentColor = lightAccentColor,
                                        onDismiss = onDismiss,
                                        onSelectDetail = onSelectDetail,
                                        isHorizontal = true
                                    )
                                }
                            }
                        }
                    }

                    items(regularNotifications, key = { it.key }) { notif ->
                        WatchNotificationCardItem(
                            notif = notif,
                            lightAccentColor = lightAccentColor,
                            onDismiss = onDismiss,
                            onSelectDetail = onSelectDetail,
                            isHorizontal = false
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
                                    style = TextStyle(
                                        fontFamily = GoogleSansFlexRounded,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                        color = Color.White
                                    )
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
    onSelectDetail: (WatchNotificationItem) -> Unit,
    isHorizontal: Boolean = false
) {
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var dismissed by remember { mutableStateOf(false) }
    val offsetX = remember { Animatable(0f) }
    val swipeState = rememberDraggableState { delta ->
        scope.launch { offsetX.snapTo(offsetX.value + delta) }
    }

    val cardBg = if (notif.isMedia) {
        lightAccentColor.copy(alpha = 0.15f)
    } else {
        lightAccentColor.copy(alpha = 0.12f)
    }

    val baseModifier = if (isHorizontal) Modifier.width(170.dp) else Modifier.fillMaxWidth()

    LaunchedEffect(dismissed) {
        if (dismissed) onDismiss(notif.key)
    }

    AnimatedVisibility(
        visible = !dismissed,
        exit = slideOutHorizontally(targetOffsetX = { if (offsetX.value >= 0f) it else -it }) + shrinkVertically()
    ) {
        val interactiveModifier = if (!notif.isMedia) {
            baseModifier
                .graphicsLayer { translationX = offsetX.value }
                .draggable(
                    state = swipeState,
                    orientation = Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        if (kotlin.math.abs(offsetX.value) > 80f || kotlin.math.abs(velocity) > 300f) {
                            HapticUtil.performUIHaptic(view)
                            dismissed = true
                        } else {
                            scope.launch {
                                offsetX.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f)
                                )
                            }
                        }
                    }
                )
        } else {
            baseModifier
        }

        Box(
            modifier = interactiveModifier
                .padding(vertical = 1.dp)
                .background(color = Color(0xFF141414), shape = RoundedCornerShape(24.dp))
                .background(color = cardBg, shape = RoundedCornerShape(24.dp))
                .clickable {
                    HapticUtil.performUIHaptic(view)
                    onSelectDetail(notif)
                }
                .padding(vertical = 10.dp, horizontal = 12.dp)
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
                     if (notif.appName.isNotBlank() && !notif.isMedia) {
                         Text(
                             text = notif.appName,
                             style = TextStyle(
                                 fontFamily = GoogleSansFlexRounded,
                                 fontWeight = FontWeight.SemiBold,
                                 fontSize = 11.sp,
                                 color = lightAccentColor
                             ),
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
                             painter = painterResource(id = R.drawable.rounded_reply_24),
                             contentDescription = null,
                             modifier = Modifier.size(14.dp),
                             tint = lightAccentColor
                         )
                     }
                 }
                 Spacer(modifier = Modifier.height(2.dp))
                 if (notif.title.isNotBlank()) {
                     Text(
                         text = notif.title,
                         style = TextStyle(
                             fontFamily = GoogleSansFlexRounded,
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
                             fontFamily = GoogleSansFlexRounded,
                             fontSize = 12.sp,
                             color = Color.LightGray
                         ),
                         maxLines = 1,
                         overflow = TextOverflow.Ellipsis,
                         modifier = Modifier.padding(top = 1.dp)
                     )
                 }
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

    var totalDragX by remember { mutableStateOf(0f) }
    var totalDragY by remember { mutableStateOf(0f) }

    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) {
            onInteraction()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(enabled = false) {}
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        totalDragX = 0f
                        totalDragY = 0f
                    },
                    onDragEnd = {
                        if (totalDragY > 100f) {
                            onDismissOverlay()
                        } else if (kotlin.math.abs(totalDragX) > 100f) {
                            HapticUtil.performUIHaptic(view)
                            onDismissNotification()
                        }
                        onInteraction()
                    },
                    onDragCancel = {
                        onInteraction()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                    }
                )
            },
        contentAlignment = Alignment.TopCenter
    ) {
        ScalingLazyColumn(
            state = scrollState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 28.dp, bottom = 48.dp)
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
                            modifier = Modifier
                                .size(60.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1A1A1A))
                                .padding(10.dp)
                        )
                    }
                }
            }
            item {
                Text(
                    text = notification.title.ifBlank { "Notification" },
                    style = TextStyle(
                        fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRoundedWide,
                        fontSize = 18.sp,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(top = 6.dp, bottom = 8.dp).padding(horizontal = 16.dp)
                )
            }
            item {
                Text(
                    text = notification.text,
                    style = TextStyle(
                        fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                        fontSize = 15.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    ),
                    modifier = Modifier.padding(bottom = 20.dp).padding(horizontal = 16.dp)
                )
            }
            if (notification.canReply) {
                item {
                    Box(
                        modifier = Modifier
                            .padding(top = 4.dp, bottom = 16.dp)
                            .background(lightAccentColor, shape = RoundedCornerShape(24.dp))
                            .clickable {
                                HapticUtil.performUIHaptic(view)
                                onReply(notification)
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_reply_24),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Reply",
                                style = TextStyle(
                                    fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            )
                        }
                    }
                }
            }
        }
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
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(24.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Reply to ${notification.appName.ifBlank { "Message" }}",
                style = TextStyle(
                    fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = lightAccentColor
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                quickReplies.forEach { replyOption ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(Color(0xFF2A2A2A), shape = RoundedCornerShape(20.dp))
                            .clickable {
                                HapticUtil.performUIHaptic(view)
                                onSendReply(replyOption)
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = replyOption,
                            style = TextStyle(
                                fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                                fontSize = 13.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = customText,
                    onValueChange = { customText = it },
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF2A2A2A), shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    textStyle = TextStyle(
                        fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                        color = Color.White,
                        fontSize = 13.sp
                    ),
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
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(24.dp))
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
                    style = TextStyle(
                        fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = lightAccentColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (notification.title.isNotBlank()) {
                Text(
                    text = notification.title,
                    style = TextStyle(
                        fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            if (notification.text.isNotBlank()) {
                Text(
                    text = notification.text,
                    style = TextStyle(
                        fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                        fontSize = 13.sp,
                        color = Color.LightGray,
                        textAlign = TextAlign.Center
                    ),
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (notification.canReply && onReply != null) {
                    Box(
                        modifier = Modifier
                            .background(lightAccentColor, shape = RoundedCornerShape(20.dp))
                            .clickable {
                                HapticUtil.performUIHaptic(view)
                                onDismissRequest()
                                onReply(notification)
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(id = R.drawable.rounded_reply_24),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = Color.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Reply",
                                style = TextStyle(
                                    fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                Box(
                    modifier = Modifier
                        .background(Color(0xFF3A2323), shape = RoundedCornerShape(20.dp))
                        .clickable {
                            HapticUtil.performUIHaptic(view)
                            onDismissNotification(notification.key)
                            onDismissRequest()
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Dismiss",
                        style = TextStyle(
                            fontFamily = com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded,
                            color = Color(0xFFFF6B6B),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun BottomNotificationIndicator(
    notifications: List<WatchNotificationItem>,
    lightAccentColor: Color,
    modifier: Modifier = Modifier
) {
    // Read directly from SnapshotStateList — remember(notifications) uses object reference
    // as key which never changes, so derived values would never recompute.
    val regularNotifs = notifications.filter { !it.isMedia }.sortedByDescending { it.postTime }
    val hasMedia = notifications.any { it.isMedia }

    if (regularNotifs.isEmpty() && !hasMedia) return

    val visibleNotifs = regularNotifs.take(3)
    val hasMore = regularNotifs.size > 3

    // anchor=85f keeps content off the very edge. CurvedLayout at the bottom renders
    // items right-to-left visually, so we declare them in reverse display order:
    // hasMore dot → notifications (newest-last) → separator dot → music icon
    // which renders on screen as: music icon · notif1 notif2 notif3 · dot
    CurvedLayout(
        anchor = 85f,
        modifier = modifier
    ) {
        if (hasMore) {
            curvedComposable {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(4.dp)
                        .background(Color.Gray, CircleShape)
                        .graphicsLayer { rotationZ = 180f }
                )
            }
        }

        visibleNotifs.reversed().forEach { notif ->
            curvedComposable {
                val bitmap = remember(notif.iconBase64) {
                    if (notif.iconBase64.isNotBlank()) {
                        try {
                            val bytes = android.util.Base64.decode(notif.iconBase64, android.util.Base64.NO_WRAP)
                            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (e: Exception) { null }
                    } else null
                }
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .graphicsLayer { rotationZ = 180f }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .size(8.dp)
                            .background(lightAccentColor, CircleShape)
                            .graphicsLayer { rotationZ = 180f }
                    )
                }
            }
        }

        if (hasMedia) {
            if (visibleNotifs.isNotEmpty()) {
                curvedComposable {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(4.dp)
                            .background(Color.Gray, CircleShape)
                            .graphicsLayer { rotationZ = 180f }
                    )
                }
            }
            curvedComposable {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_music_note_24),
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer { rotationZ = 180f },
                    tint = lightAccentColor
                )
            }
        }
    }
}
