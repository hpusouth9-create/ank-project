package com.p2pvoice.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.p2pvoice.ui.theme.*
import kotlinx.coroutines.delay

// ─── Outgoing / Connecting Screen ─────────────────────────────────────────

@Composable
fun OutgoingCallScreen(
    targetId: String,
    onCancel: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "connecting")
    val dotAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "d1"
    )
    val dotAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, 200), RepeatMode.Reverse),
        label = "d2"
    )
    val dotAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, 400), RepeatMode.Reverse),
        label = "d3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SpaceBlack),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            Text(
                "CALLING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = targetId,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(48.dp))

            // Animated avatar
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(CardNavy)
                    .border(2.dp, BorderNavy, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = targetId.take(2),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(32.dp))

            // Animated dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Connecting", fontSize = 15.sp, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    repeat(3) { idx ->
                        val alpha = when (idx) { 0 -> dotAlpha1; 1 -> dotAlpha2; else -> dotAlpha3 }
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(ElectricTeal.copy(alpha = alpha))
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Cancel button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(AlertRed),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onCancel, modifier = Modifier.size(72.dp)) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = "Cancel",
                        tint = TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Cancel", fontSize = 13.sp, color = TextSecondary)
            Spacer(Modifier.height(64.dp))
        }
    }
}

// ─── Active Call Screen ────────────────────────────────────────────────────

@Composable
fun ActiveCallScreen(
    peerId: String,
    isMuted: Boolean,
    isSpeakerOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onEndCall: () -> Unit
) {
    // Call duration timer
    var seconds by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            seconds++
        }
    }

    val duration = remember(seconds) {
        val m = seconds / 60
        val s = seconds % 60
        "%02d:%02d".format(m, s)
    }

    // Subtle audio waveform animation
    val infiniteTransition = rememberInfiniteTransition(label = "wave")
    val waveHeights = (0..7).map { i ->
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(400 + i * 80, easing = EaseInOutSine),
                RepeatMode.Reverse
            ),
            label = "wh$i"
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        PulseGreen.copy(alpha = 0.06f),
                        SpaceBlack
                    ),
                    radius = 900f
                )
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.weight(1f))

            // Connected badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PulseGreen.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, PulseGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(PulseGreen)
                    )
                    Text(
                        "CONNECTED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = PulseGreen,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Peer ID
            Text(
                text = peerId,
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(8.dp))

            // Duration
            Text(
                text = duration,
                fontSize = 20.sp,
                color = TextSecondary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(48.dp))

            // ── Audio waveform visualizer ─────────────────────────────────
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(60.dp)
            ) {
                waveHeights.forEachIndexed { idx, heightAnim ->
                    val barHeight = 60.dp * heightAnim.value
                    Box(
                        modifier = Modifier
                            .width(5.dp)
                            .height(barHeight)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                if (isMuted) TextDim
                                else PulseGreen.copy(alpha = 0.4f + heightAnim.value * 0.6f)
                            )
                    )
                }
            }

            if (isMuted) {
                Spacer(Modifier.height(8.dp))
                Text("Microphone muted", fontSize = 12.sp, color = AlertRed)
            }

            Spacer(Modifier.weight(1f))

            // ── Call control buttons ──────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mute
                CallControlButton(
                    icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    label = if (isMuted) "Unmute" else "Mute",
                    isActive = isMuted,
                    activeColor = AlertRed,
                    onClick = onToggleMute
                )

                // End call — larger
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(AlertRed),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(onClick = onEndCall, modifier = Modifier.size(80.dp)) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "End call",
                            tint = TextPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                // Speaker
                CallControlButton(
                    icon = if (isSpeakerOn) Icons.Default.VolumeUp else Icons.Default.VolumeDown,
                    label = if (isSpeakerOn) "Speaker" else "Earpiece",
                    isActive = isSpeakerOn,
                    activeColor = ElectricTeal,
                    onClick = onToggleSpeaker
                )
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}

@Composable
private fun CallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(
                    if (isActive) activeColor.copy(alpha = 0.2f)
                    else CardNavy
                )
                .border(
                    1.dp,
                    if (isActive) activeColor.copy(alpha = 0.6f) else BorderNavy,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onClick, modifier = Modifier.size(60.dp)) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) activeColor else TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}
