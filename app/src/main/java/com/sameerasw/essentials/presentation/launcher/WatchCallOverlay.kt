/*
 * Copyright (c) 2026 sameerasw.com
 * License: MIT License
 *
 * Feature Module: WearOS Launcher
 * File: WatchCallOverlay.kt
 * Description: Composable UI component for WearOS incoming and active call overlay with remote call actions.
 */

package com.sameerasw.essentials.presentation.launcher

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import com.sameerasw.essentials.R
import com.sameerasw.essentials.presentation.theme.GoogleSansFlexRounded
import com.sameerasw.essentials.presentation.theme.GoogleSansFlexRoundedWide
import com.sameerasw.essentials.utils.HapticUtil
import kotlinx.coroutines.delay

data class CallStateData(
    val state: String,
    val number: String,
    val contactName: String,
    val contactPhotoBase64: String,
    val isIncoming: Boolean,
    val timestamp: Long
)

fun sendCallActionToPhone(context: Context, action: String) {
    try {
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            for (node in nodes) {
                Wearable.getMessageClient(context).sendMessage(node.id, "/watch_call_action", action.toByteArray())
            }
        }
    } catch (e: Exception) {
        Log.e("WatchCallOverlay", "Error sending call action to phone", e)
    }
}

@Composable
fun WatchCallOverlay(
    callData: CallStateData,
    onAction: (String) -> Unit
) {
    val view = LocalView.current
    val displayName = callData.contactName.ifBlank { callData.number.ifBlank { "Unknown Caller" } }

    val photoBitmap = remember(callData.contactPhotoBase64) {
        if (callData.contactPhotoBase64.isNotBlank()) {
            try {
                val bytes = Base64.decode(callData.contactPhotoBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else null
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(callData.state) {
        if (callData.state == "RINGING") {
            val vibrator = HapticUtil.startRingingVibration(context)
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                HapticUtil.stopRingingVibration(vibrator)
            }
        }
    }

    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    LaunchedEffect(callData.state, callData.timestamp) {
        if (callData.state == "OFFHOOK") {
            val startTime = if (callData.timestamp > 0) callData.timestamp else System.currentTimeMillis()
            while (true) {
                elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).coerceAtLeast(0L)
                delay(1000)
            }
        }
    }

    val timerText = if (callData.state == "OFFHOOK") {
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        String.format("%02d:%02d", mins, secs)
    } else ""

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Contact Photo or Avatar
            if (photoBitmap != null) {
                Image(
                    bitmap = photoBitmap,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF333333))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF333333)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_mobile_24),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Contact Name / Number
            Text(
                text = displayName,
                fontFamily = GoogleSansFlexRoundedWide,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            // Status Subtitle
            Text(
                text = when (callData.state) {
                    "RINGING" -> stringResource(R.string.call_incoming)
                    "OFFHOOK" -> if (timerText.isNotBlank()) timerText else stringResource(R.string.call_active)
                    else -> stringResource(R.string.call_ended)
                },
                fontFamily = GoogleSansFlexRounded,
                fontSize = 13.sp,
                color = if (callData.state == "RINGING") Color(0xFF81C784) else Color.LightGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (callData.state == "RINGING") {
                    // Decline Call Button (Red)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .clickable {
                                HapticUtil.performStrongDoubleTap(view)
                                onAction("REJECT")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_mobile_off_24),
                            contentDescription = stringResource(R.string.call_action_reject),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Answer Call Button (Green)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF43A047))
                            .clickable {
                                HapticUtil.performUIHaptic(view)
                                onAction("ANSWER")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_mobile_vibrate_24),
                            contentDescription = stringResource(R.string.call_action_answer),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else if (callData.state == "OFFHOOK") {
                    // Mute Button
                    var isMuted by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(if (isMuted) Color(0xFFFFB74D) else Color(0xFF424242))
                            .clickable {
                                HapticUtil.performUIHaptic(view)
                                isMuted = !isMuted
                                onAction("MUTE")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_volume_off_24),
                            contentDescription = stringResource(R.string.call_action_mute),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // End Call Button (Red)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE53935))
                            .clickable {
                                HapticUtil.performStrongDoubleTap(view)
                                onAction("END")
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.rounded_mobile_off_24),
                            contentDescription = stringResource(R.string.call_action_end),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
