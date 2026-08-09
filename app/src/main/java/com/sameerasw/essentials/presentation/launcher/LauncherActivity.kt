package com.sameerasw.essentials.presentation.launcher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sameerasw.essentials.presentation.theme.EssentialsTheme
import kotlinx.coroutines.flow.MutableSharedFlow

enum class CrownAction { GO_TO_CLOCK, TOGGLE_LAUNCHER }

class LauncherActivity : ComponentActivity() {
    private val crownEvents = MutableSharedFlow<CrownAction>(extraBufferCapacity = 16)
    private var lastEventTime = 0L
    private var wasInBackground = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            EssentialsTheme {
                LauncherScreen(crownEvents)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        wasInBackground = true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_1 || keyCode == KeyEvent.KEYCODE_HOME) {
            handleCrownEvent()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCrownEvent()
    }

    private fun handleCrownEvent() {
        val now = System.currentTimeMillis()
        if (now - lastEventTime < 300) return
        lastEventTime = now

        if (wasInBackground) {
            // We just came from another app/background, so go to clock.
            crownEvents.tryEmit(CrownAction.GO_TO_CLOCK)
            wasInBackground = false
        } else {
            // We were already at the Home activity, so toggle/open launcher.
            crownEvents.tryEmit(CrownAction.TOGGLE_LAUNCHER)
        }
    }
}
