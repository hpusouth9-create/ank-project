package com.p2pvoice.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.p2pvoice.data.CallLogEntity
import com.p2pvoice.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    myUserId: String,
    targetUserId: String,
    onTargetIdChanged: (String) -> Unit,
    onStartCall: () -> Unit,
    onRegenerateId: () -> Unit,
    errorMessage: String?,
    onClearError: () -> Unit,
    callLogs: List<CallLogEntity> = emptyList(),
    onClearLogs: () -> Unit = {},
    onCallLogClick: (String) -> Unit = {}
) {
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    // Pulse animation for the signal rings
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseScale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseOutQuad),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(DeepNavy, SpaceBlack),
                    radius = 1200f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // ── App title ────────────────────────────────────────────────
            Text(
                text = "ECHO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 8.sp,
                color = ElectricTeal,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "P2P Voice",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = TextPrimary,
                letterSpacing = 2.sp
            )

            Spacer(Modifier.height(40.dp))

            // ── Signal icon with pulse rings ──────────────────────────────
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(120.dp)
            ) {
                // Outer pulse ring
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = ElectricTeal.copy(alpha = pulseAlpha),
                        radius = size.minDimension / 2 * pulseScale,
                        style = Stroke(width = 2f)
                    )
                    drawCircle(
                        color = ElectricTeal.copy(alpha = pulseAlpha * 0.5f),
                        radius = size.minDimension / 2 * pulseScale * 1.3f,
                        style = Stroke(width = 1f)
                    )
                }

                // Center circle
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    ElectricTeal.copy(alpha = 0.2f),
                                    ElectricTeal.copy(alpha = 0.05f)
                                )
                            )
                        )
                        .border(1.dp, ElectricTeal.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = ElectricTeal,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── My Peer ID Card ───────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardNavy,
                border = BorderStroke(1.dp, BorderNavy)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "YOUR PEER ID",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(Modifier.height(12.dp))

                    // Big ID display
                    Text(
                        text = myUserId.ifEmpty { "Loading..." },
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = ElectricTeal,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 4.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "Share this ID with someone to receive calls",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Copy button
                        OutlinedButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(myUserId))
                                copied = true
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, ElectricTeal.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (copied) PulseGreen else ElectricTeal
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (copied) "Copied!" else "Copy",
                                fontSize = 13.sp
                            )
                        }

                        // Regenerate button
                        OutlinedButton(
                            onClick = {
                                copied = false
                                onRegenerateId()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, TextDim),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = TextSecondary
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate ID",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Dial pad section ──────────────────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = CardNavy,
                border = BorderStroke(1.dp, BorderNavy)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "CALL SOMEONE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(Modifier.height(16.dp))

                    // Peer ID input
                    OutlinedTextField(
                        value = targetUserId,
                        onValueChange = { onTargetIdChanged(it) },
                        placeholder = {
                            Text(
                                "XXXX-XXXX",
                                color = TextDim,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 20.sp,
                                letterSpacing = 3.sp
                            )
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            letterSpacing = 3.sp,
                            textAlign = TextAlign.Center
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Text,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { onStartCall() }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricTeal,
                            unfocusedBorderColor = BorderNavy,
                            focusedContainerColor = DeepNavy,
                            unfocusedContainerColor = DeepNavy,
                            cursorColor = ElectricTeal,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))

                    // Call button
                    Button(
                        onClick = onStartCall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricTeal,
                            contentColor = SpaceBlack
                        ),
                        enabled = targetUserId.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Start Call",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Call Log Section ──────────────────────────────────────────
            if (callLogs.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "RECENT CALLS",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp,
                        color = TextSecondary,
                        fontFamily = FontFamily.Monospace
                    )
                    TextButton(onClick = onClearLogs) {
                        Text("Clear All", color = TextDim, fontSize = 10.sp)
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    callLogs.take(10).forEach { log ->
                        CallLogItem(log, onCallLogClick)
                    }
                }

                Spacer(Modifier.height(28.dp))
            }

            // ── Info footer ───────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = TextDim,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "End-to-end encrypted • Audio never touches any server",
                    fontSize = 11.sp,
                    color = TextDim,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }

            Spacer(Modifier.height(40.dp))
        }

        // ── Error snackbar ────────────────────────────────────────────────
        AnimatedVisibility(
            visible = errorMessage != null,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = AlertRed.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Error, null, tint = AlertRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClearError, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun CallLogItem(log: CallLogEntity, onClick: (String) -> Unit) {
    val sdf = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val timeString = remember(log.timestamp) { sdf.format(Date(log.timestamp)) }
    
    val durationText = if (log.duration > 0) {
        val mins = log.duration / 60
        val secs = log.duration % 60
        if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
    } else {
        "Missed"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick(log.peerId) },
        shape = RoundedCornerShape(12.dp),
        color = CardNavy.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, BorderNavy.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(if (log.isIncoming) PulseGreen.copy(alpha = 0.1f) else ElectricTeal.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (log.isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
                    contentDescription = null,
                    tint = if (log.isIncoming) PulseGreen else ElectricTeal,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = log.peerId,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = timeString,
                    fontSize = 11.sp,
                    color = TextDim
                )
            }
            
            Text(
                text = durationText,
                fontSize = 12.sp,
                color = if (durationText == "Missed") AlertRed else TextSecondary,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
