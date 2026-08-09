package com.sameerasw.essentials.presentation.launcher

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import com.sameerasw.essentials.R
import com.sameerasw.essentials.presentation.components.EssentialsTimeText
import com.sameerasw.essentials.presentation.theme.GoogleSansFlexRoundedWide
import com.sameerasw.essentials.utils.HapticUtil
import com.sameerasw.essentials.utils.ThemeUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

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

    // Physical heights for QS shade drawer
    val panelHeightPx = 320f
    val triggerThresholdPx = 12f
    val drawerOffset = remember { Animatable(0f) }

    var crownAccumulator by remember { mutableStateOf(0f) }

    fun snapDrawer(targetPx: Float) {
        scope.launch {
            drawerOffset.animateTo(
                targetValue = targetPx,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Touch drag with continuous relative movement
    val draggableState = rememberDraggableState { delta ->
        val newOffset = (drawerOffset.value + delta).coerceIn(0f, panelHeightPx)
        scope.launch {
            drawerOffset.snapTo(newOffset)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        timeText = { EssentialsTimeText() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onRotaryScrollEvent { event ->
                    // Invert crown direction (-verticalScrollPixels)
                    val delta = -event.verticalScrollPixels
                    
                    // Clamp accumulator strictly to trigger bounds (0 to triggerThresholdPx)
                    val currentAcc = if (drawerOffset.value == 0f) {
                        (crownAccumulator + delta).coerceIn(0f, triggerThresholdPx)
                    } else if (drawerOffset.value == panelHeightPx) {
                        (crownAccumulator + delta).coerceIn(-triggerThresholdPx, 0f)
                    } else {
                        crownAccumulator + delta
                    }
                    crownAccumulator = currentAcc

                    if (drawerOffset.value < panelHeightPx / 2) {
                        if (crownAccumulator >= triggerThresholdPx) {
                            HapticUtil.performUIHaptic(view)
                            snapDrawer(panelHeightPx)
                            crownAccumulator = 0f
                        }
                    } else {
                        if (crownAccumulator <= -triggerThresholdPx) {
                            HapticUtil.performUIHaptic(view)
                            snapDrawer(0f)
                            crownAccumulator = 0f
                        }
                    }
                    true
                }
                .focusRequester(focusRequester)
                .focusable()
                .draggable(
                    state = draggableState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity ->
                        HapticUtil.performUIHaptic(view)
                        crownAccumulator = 0f
                        if (velocity > 150f || (velocity >= 0f && drawerOffset.value > panelHeightPx * 0.35f)) {
                            snapDrawer(panelHeightPx)
                        } else if (velocity < -150f || (velocity <= 0f && drawerOffset.value <= panelHeightPx * 0.65f)) {
                            snapDrawer(0f)
                        } else {
                            if (drawerOffset.value > panelHeightPx / 2) snapDrawer(panelHeightPx) else snapDrawer(0f)
                        }
                    }
                )
        ) {
            // Main Clock: Scales accurately based on screen width to fit perfectly without overflow
            BoxWithConstraints(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
            ) {
                // "hh:mm" has 5 characters (e.g. 12:45). 0.65f ratio ensures full width fit without overflow
                val computedFontSize = (maxWidth.value * 0.65f / 3.2f).coerceIn(40f, 68f).sp
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

            // Quick Settings Shade Overlay (pull-down panel)
            val currentOffset = drawerOffset.value
            if (currentOffset > 0f) {
                val progress = (currentOffset / panelHeightPx).coerceIn(0f, 1f)
                val topY = -panelHeightPx + currentOffset

                Box(
                    modifier = Modifier
                        .offset { IntOffset(0, topY.roundToInt()) }
                        .fillMaxWidth()
                        .height(panelHeightPx.dp)
                        .background(Color.Black.copy(alpha = 0.95f * progress))
                        .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                        .clickable(
                            indication = null,
                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        ) {
                            HapticUtil.performUIHaptic(view)
                            snapDrawer(0f)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 20.dp)
                            .alpha(progress)
                    ) {
                        Text(
                            text = stringResource(R.string.feature_settings),
                            style = TextStyle(
                                fontFamily = GoogleSansFlexRoundedWide,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = lightAccentColor
                            )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1. Android Settings Button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Button(
                                    onClick = {
                                        HapticUtil.performUIHaptic(view)
                                        try {
                                            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            // Fallback
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .border(
                                            BorderStroke(1.dp, lightAccentColor.copy(alpha = 0.5f)),
                                            CircleShape
                                        ),
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = tonedThemeColor,
                                        contentColor = Color.White
                                    ),
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.rounded_settings_heart_24),
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.launcher_open_settings),
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                )
                            }

                            // 2. Watch Sound Mode Toggle Button
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
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

                                val (soundIcon, soundLabelRes) = when (watchRingerMode) {
                                    AudioManager.RINGER_MODE_VIBRATE -> Pair(R.drawable.rounded_mobile_vibrate_24, R.string.launcher_sound_vibrate)
                                    AudioManager.RINGER_MODE_SILENT -> Pair(R.drawable.rounded_volume_off_24, R.string.launcher_sound_silent)
                                    else -> Pair(R.drawable.rounded_volume_up_24, R.string.launcher_sound_normal)
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
                                            watchRingerMode = audioManager.ringerMode
                                        } catch (e: Exception) {
                                            // Fallback
                                        }
                                    },
                                    modifier = Modifier
                                        .size(56.dp)
                                        .then(
                                            if (isNormal) Modifier.border(
                                                BorderStroke(1.dp, lightAccentColor.copy(alpha = 0.5f)),
                                                CircleShape
                                            ) else Modifier
                                        ),
                                    colors = soundModeColors,
                                    shape = CircleShape
                                ) {
                                    Icon(
                                        painter = painterResource(id = soundIcon),
                                        contentDescription = null,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(soundLabelRes),
                                    style = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color.White
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Pill handle indicator
                        Box(
                            modifier = Modifier
                                .size(width = 36.dp, height = 4.dp)
                                .background(
                                    color = Color.White.copy(alpha = 0.4f),
                                    shape = CircleShape
                                )
                                .clickable {
                                    HapticUtil.performUIHaptic(view)
                                    snapDrawer(0f)
                                }
                        )
                    }
                }
            }
        }
    }
}
