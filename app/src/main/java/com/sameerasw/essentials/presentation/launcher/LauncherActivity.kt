package com.sameerasw.essentials.presentation.launcher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sameerasw.essentials.presentation.theme.EssentialsTheme
import kotlinx.coroutines.flow.MutableSharedFlow

class LauncherActivity : ComponentActivity() {
    private val crownEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            EssentialsTheme {
                LauncherScreen(crownEvents)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STEM_1) {
            crownEvents.tryEmit(Unit)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // System Home signal usually triggers onNewIntent for singleTask activities
        crownEvents.tryEmit(Unit)
    }
}
