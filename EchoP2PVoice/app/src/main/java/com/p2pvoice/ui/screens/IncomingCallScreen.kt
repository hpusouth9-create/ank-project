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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.composed
import com.p2pvoice.ui.theme.*

@Composable
fun IncomingCallScreen(
    callerId: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "incoming")

    // Multiple expanding ring waves
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseOutQuad), RepeatMode.Restart),
        label = "w1"
    )
    val wave1Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, easing = EaseOutQuad), RepeatMode.Restart),
        label = "a1"
    )
    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2000, 500, EaseOutQuad), RepeatMode.Restart),
        label = "w2"
    )
    val wave2Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, 500, EaseOutQuad), RepeatMode.Restart),
        label = "a2"
    )
    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1.5f,
        animationSpec = infiniteRepeatable(tween(2000, 1000, EaseOutQuad), RepeatMode.Restart),
        label = "w3"
    )
    val wave3Alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(2000, 1000, EaseOutQuad), RepeatMode.Restart),
        label = "a3"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        ElectricTeal.copy(alpha = 0.08f),
                        SpaceBlack
                    ),
                    radius = 1000f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Spacer(Modifier.weight(1f))

            // ── Incoming call label ───────────────────────────────────────
            Text(
                text = "INCOMING CALL",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp,
                color = ElectricTeal,
                fontFamily = FontFamily.Monospace
            )

            Spacer(Modifier.height(8.dp))

            // ── Caller ID ─────────────────────────────────────────────────
            Text(
                text = callerId,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = "wants to connect",
                fontSize = 16.sp,
                color = TextSecondary
            )

            Spacer(Modifier.height(64.dp))

            // ── Animated avatar with pulse waves ──────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(200.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = this.center
                    val baseRadius = size.minDimension / 2 * 0.4f

                    drawCircle(
                        color = ElectricTeal.copy(alpha = wave1Alpha),
                        radius = baseRadius * wave1,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = ElectricTeal.copy(alpha = wave2Alpha),
                        radius = baseRadius * wave2,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = ElectricTeal.copy(alpha = wave3Alpha),
                        radius = baseRadius * wave3,
                        center = center,
                        style = Stroke(width = 2f)
                    )
                }

                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(90.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    ElectricTeal.copy(alpha = 0.25f),
                                    ElectricTeal.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(2.dp, ElectricTeal.copy(alpha = 0.7f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerId.take(2),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricTeal,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Accept / Reject buttons ───────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 60.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(AlertRed)
                            .clickable { onReject() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = "Reject",
                            tint = TextPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Decline", fontSize = 13.sp, color = TextSecondary)
                }

                // Accept
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(PulseGreen)
                            .clickable { onAccept() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Accept",
                            tint = SpaceBlack,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Accept", fontSize = 13.sp, color = TextSecondary)
                }
            }

            Spacer(Modifier.height(64.dp))
        }
    }
}

// Helper to avoid ripple on custom click areas
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    this.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
}
