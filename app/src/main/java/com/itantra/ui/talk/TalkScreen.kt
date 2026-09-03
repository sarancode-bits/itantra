package com.itantra.ui.talk

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.core.speech.SpeakingState
import com.itantra.core.speech.SttState
import com.itantra.core.transport.ConnectionState
import com.itantra.data.repository.DeliveryStatus
import com.itantra.data.repository.TranscriptEntry
import com.itantra.ui.theme.AlertRed
import com.itantra.ui.theme.ConnectedGreen
import com.itantra.ui.theme.DarkBackground
import com.itantra.ui.theme.OutlineBorder
import com.itantra.ui.theme.SafetyOrange
import com.itantra.ui.theme.SurfaceCard
import com.itantra.ui.theme.SurfaceVariant
import com.itantra.ui.theme.TextPrimary
import com.itantra.ui.theme.TextSecondary
import com.itantra.ui.theme.WarningYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TalkScreen(
    viewModel: TalkViewModel,
    onNavigateToHome: () -> Unit,
    onNavigateToSosConfirm: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val transcript by viewModel.transcript.collectAsState()
    val sttState by viewModel.sttState.collectAsState()
    val rmsLevel by viewModel.rmsLevel.collectAsState()
    val speakingState by viewModel.speakingState.collectAsState()
    val peerBatteryPct by viewModel.peerBatteryPct.collectAsState()

    val listState = rememberLazyListState()

    // Auto scroll to latest message
    LaunchedEffect(transcript.size) {
        if (transcript.isNotEmpty()) {
            listState.animateScrollToItem(transcript.size - 1)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Pinned Connection Status Bar + Header
            ConnectionStatusBar(
                connectionState = connectionState,
                peerBatteryPct = peerBatteryPct,
                onBack = onNavigateToHome,
                onSosClick = onNavigateToSosConfirm,
                onDisconnect = {
                    viewModel.disconnect()
                    onNavigateToHome()
                }
            )

            // TTS Speaking Indicator Strip
            if (speakingState is SpeakingState.Speaking) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SafetyOrange.copy(alpha = 0.2f))
                        .padding(vertical = 4.dp, horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔊 Playing audio out loud...",
                        style = MaterialTheme.typography.labelSmall,
                        color = SafetyOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Transcript Scroll Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                if (transcript.isEmpty()) {
                    EmptyTranscriptPlaceholder()
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                        items(transcript, key = { it.id }) { entry ->
                            TranscriptBubble(
                                entry = entry,
                                onRetry = { viewModel.retrySendMessage(entry) }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(12.dp)) }
                    }
                }
            }

            // Mic & PTT Area (Dominant control)
            PushToTalkArea(
                sttState = sttState,
                rmsLevel = rmsLevel,
                onPressStart = { viewModel.startRecording() },
                onPressEnd = { viewModel.stopRecording() }
            )
        }
    }
}

@Composable
fun ConnectionStatusBar(
    connectionState: ConnectionState,
    peerBatteryPct: Int?,
    onBack: () -> Unit,
    onSosClick: () -> Unit,
    onDisconnect: () -> Unit
) {
    val (statusText, statusColor) = when (connectionState) {
        is ConnectionState.Connected -> Pair("Connected to ${connectionState.peerName}", ConnectedGreen)
        is ConnectionState.Connecting -> Pair("Connecting to ${connectionState.peerName}...", WarningYellow)
        is ConnectionState.Advertising -> Pair("Hosting (Waiting for peer)", WarningYellow)
        is ConnectionState.Discovering -> Pair("Searching for peers...", WarningYellow)
        is ConnectionState.Disconnected -> Pair("Disconnected", AlertRed)
        else -> Pair("Offline", TextSecondary)
    }

    Surface(
        color = SurfaceCard,
        tonalElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
                }

                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Column {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    if (peerBatteryPct != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.BatteryChargingFull,
                                contentDescription = null,
                                tint = ConnectedGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Peer Battery: $peerBatteryPct%",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // SOS Button (Distinct Red Emergency trigger)
                Button(
                    onClick = onSosClick,
                    colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "SOS",
                        tint = TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("SOS", fontWeight = FontWeight.Black, color = TextPrimary)
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(onClick = onDisconnect) {
                    Text("Disconnect", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun TranscriptBubble(entry: TranscriptEntry, onRetry: () -> Unit) {
    val alignment = if (entry.isOwn) Alignment.End else Alignment.Start
    val bubbleColor = if (entry.isOwn) SurfaceVariant else SurfaceCard
    val borderColor = if (entry.isOwn) SafetyOrange.copy(alpha = 0.4f) else OutlineBorder
    val formattedTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(entry.timestampMs))

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (entry.isOwn) 16.dp else 4.dp,
                bottomEnd = if (entry.isOwn) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(containerColor = bubbleColor),
            border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = entry.senderName,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (entry.isOwn) SafetyOrange else ConnectedGreen,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )

                if (entry.isOwn) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when (entry.deliveryStatus) {
                            DeliveryStatus.Sending -> {
                                Icon(imageVector = Icons.Default.Schedule, contentDescription = "Sending", tint = WarningYellow, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sending...", style = MaterialTheme.typography.labelSmall, color = WarningYellow)
                            }
                            DeliveryStatus.Sent -> {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Sent", tint = TextSecondary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Sent", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                            }
                            DeliveryStatus.Delivered -> {
                                Icon(imageVector = Icons.Default.DoneAll, contentDescription = "Delivered", tint = ConnectedGreen, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Delivered", style = MaterialTheme.typography.labelSmall, color = ConnectedGreen)
                            }
                            DeliveryStatus.Failed -> {
                                Row(
                                    modifier = Modifier.clickable { onRetry() },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.ErrorOutline, contentDescription = "Failed", tint = AlertRed, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Failed (Tap to Retry)", style = MaterialTheme.typography.labelSmall, color = AlertRed, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PushToTalkArea(
    sttState: SttState,
    rmsLevel: Float,
    onPressStart: () -> Unit,
    onPressEnd: () -> Unit
) {
    val isRecording = sttState is SttState.Listening

    val transition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Surface(
        color = SurfaceCard,
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when (sttState) {
                    is SttState.Listening -> "RECORDING SPEECH... RELEASE TO SEND"
                    is SttState.Processing -> "TRANSCRIBING SPEECH ON-DEVICE..."
                    else -> "HOLD MIC TO TALK"
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (isRecording) SafetyOrange else TextSecondary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Massive Mic Press-and-Hold Button
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .scale(if (isRecording) pulseScale + (rmsLevel / 50f) else 1.0f)
                    .clip(CircleShape)
                    .background(if (isRecording) AlertRed else SafetyOrange)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onPressStart()
                                tryAwaitRelease()
                                onPressEnd()
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Hold to talk",
                    tint = TextPrimary,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyTranscriptPlaceholder() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Walkie-Talkie Ready",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Hold the mic button below to record and send an instant transcribed voice message.",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
