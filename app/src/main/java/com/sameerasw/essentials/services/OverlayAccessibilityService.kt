package com.sameerasw.essentials.services

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.google.android.gms.wearable.Wearable
import com.sameerasw.essentials.presentation.launcher.CallStateData
import com.sameerasw.essentials.presentation.launcher.NewNotificationOverlay
import com.sameerasw.essentials.presentation.launcher.WatchNotificationItem
import com.sameerasw.essentials.presentation.theme.EssentialsTheme
import com.sameerasw.essentials.utils.ThemeUtil
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

class OverlayAccessibilityService : AccessibilityService(), LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val TAG = "OverlayAccessService"
    private var windowManager: WindowManager? = null
    private var overlayView: ComposeView? = null

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val _viewModelStore = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.sameerasw.essentials.SHOW_OVERLAY") {
                val jsonStr = intent.getStringExtra("notification_json")
                if (jsonStr != null) {
                    showOverlay(jsonStr)
                }
            } else if (intent?.action == "com.sameerasw.essentials.HIDE_OVERLAY") {
                hideOverlay()
            } else if (intent?.action == "com.sameerasw.essentials.SHOW_CALL_OVERLAY") {
                val state = intent.getStringExtra("state") ?: ""
                val number = intent.getStringExtra("number") ?: ""
                val name = intent.getStringExtra("contactName") ?: ""
                val photo = intent.getStringExtra("contactPhoto") ?: ""
                val isIncoming = intent.getBooleanExtra("isIncoming", false)
                val timestamp = intent.getLongExtra("timestamp", System.currentTimeMillis())
                showCallOverlay(CallStateData(state, number, name, photo, isIncoming, timestamp))
            } else if (intent?.action == "com.sameerasw.essentials.HIDE_CALL_OVERLAY") {
                hideOverlay()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        
        val filter = IntentFilter().apply {
            addAction("com.sameerasw.essentials.SHOW_OVERLAY")
            addAction("com.sameerasw.essentials.HIDE_OVERLAY")
            addAction("com.sameerasw.essentials.SHOW_CALL_OVERLAY")
            addAction("com.sameerasw.essentials.HIDE_CALL_OVERLAY")
        }
        registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun showOverlay(jsonStr: String) {
        if (overlayView != null) hideOverlay()

        val notification = parseNotification(jsonStr) ?: return
        
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayAccessibilityService)
            setViewTreeViewModelStoreOwner(this@OverlayAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@OverlayAccessibilityService)
            
            setContent {
                EssentialsTheme {
                    val themeColor = remember { ThemeUtil.getThemeColor(context) }
                    val lightAccentColor = themeColor?.let {
                        Color(ThemeUtil.getLightAccentColor(it))
                    } ?: Color(0xFFB39DDB.toInt())

                    NewNotificationOverlay(
                        notification = notification,
                        lightAccentColor = lightAccentColor,
                        onDismissOverlay = { hideOverlay() },
                        onDismissNotification = {
                            dismissNotificationOnPhoneAndWatch(notification.key)
                            hideOverlay()
                        },
                        onReply = {
                            hideOverlay()
                        },
                        onInteraction = { /* Resets timeout if needed */ }
                    )

                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(10.seconds)
                        hideOverlay()
                    }
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager?.addView(overlayView, params)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
                lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay view", e)
            }
        }
        overlayView = null
    }

    private fun showCallOverlay(callData: CallStateData) {
        if (overlayView != null) hideOverlay()

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayAccessibilityService)
            setViewTreeViewModelStoreOwner(this@OverlayAccessibilityService)
            setViewTreeSavedStateRegistryOwner(this@OverlayAccessibilityService)

            setContent {
                EssentialsTheme {
                    com.sameerasw.essentials.presentation.launcher.WatchCallOverlay(
                        callData = callData,
                        onAction = { action ->
                            com.sameerasw.essentials.presentation.launcher.sendCallActionToPhone(this@OverlayAccessibilityService, action)
                            if (action == "REJECT" || action == "END") {
                                hideOverlay()
                            }
                        }
                    )
                }
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        try {
            windowManager?.addView(overlayView, params)
            Log.d(TAG, "Call overlay view added to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add call overlay view", e)
        }
    }

    private fun parseNotification(jsonStr: String): WatchNotificationItem? {
        return try {
            val obj = JSONObject(jsonStr)
            val pkg = obj.optString("packageName", "")
            val prefs = getSharedPreferences("schedule_prefs", MODE_PRIVATE)
            val iconsJsonStr = prefs.getString("watch_app_icons_json", "{}") ?: "{}"
            val iconsObj = JSONObject(iconsJsonStr)
            val iconBase64 = iconsObj.optString(pkg, "")

            WatchNotificationItem(
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
        } catch (e: Exception) {
            null
        }
    }

    private fun dismissNotificationOnPhoneAndWatch(key: String) {
        val nodeClient = Wearable.getNodeClient(this)
        nodeClient.connectedNodes.addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) return@addOnSuccessListener
            val messageClient = Wearable.getMessageClient(this)
            for (node in nodes) {
                messageClient.sendMessage(node.id, "/dismiss_phone_notification", key.toByteArray())
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        unregisterReceiver(receiver)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    }
}
