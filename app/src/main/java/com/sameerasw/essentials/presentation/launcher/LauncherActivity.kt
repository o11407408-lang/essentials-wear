package com.sameerasw.essentials.presentation.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.sameerasw.essentials.presentation.theme.EssentialsTheme

class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        setContent {
            EssentialsTheme {
                LauncherScreen()
            }
        }
    }
}
