package com.itantra.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.itantra.BuildConfig
import com.itantra.core.transport.ConnectionState
import com.itantra.core.transport.PeerInfo
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

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToTalk: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToPermissions: () -> Unit
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val discoveredPeers by viewModel.discoveredPeers.collectAsState()

    // Auto-navigate to Talk screen if connected (must be in LaunchedEffect to avoid
    // recomposition-triggered navigation crashes — IllegalStateException)
    LaunchedEffect(connectionState) {
        if (connectionState is ConnectionState.Connected) {
            onNavigateToTalk()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = DarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Header Row with Wordmark, Mock Badge, Settings Icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "iTantra",
                        style = MaterialTheme.typography.displayLarge,
                        color = SafetyOrange,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "OFFLINE EMERGENCY P2P",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (BuildConfig.FLAVOR == "mock") {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(WarningYellow.copy(alpha = 0.2f))
                                .border(1.dp, WarningYellow, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "MOCK MODE",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarningYellow,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Dual Primary Action Buttons: HOST and SCAN
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // HOST BUTTON
                Button(
                    onClick = { viewModel.startHosting() },
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connectionState is ConnectionState.Advertising) ConnectedGreen else SafetyOrange,
                        contentColor = TextPrimary
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiTethering,
                            contentDescription = "Host",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (connectionState is ConnectionState.Advertising) "HOSTING..." else "HOST",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Become Discoverable",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextPrimary.copy(alpha = 0.8f)
                        )
                    }
                }

                // SCAN BUTTON
                Button(
                    onClick = { viewModel.startScanning() },
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connectionState is ConnectionState.Discovering) WarningYellow else SurfaceCard,
                        contentColor = if (connectionState is ConnectionState.Discovering) DarkBackground else TextPrimary
                    ),
                    border = if (connectionState !is ConnectionState.Discovering) androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder) else null
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Radar,
                            contentDescription = "Scan",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (connectionState is ConnectionState.Discovering) "SCANNING..." else "SCAN",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Find Nearby Devices",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (connectionState is ConnectionState.Discovering) DarkBackground.copy(alpha = 0.8f) else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Radios off or permissions warning
            if (connectionState is ConnectionState.RadiosOff) {
                val missing = (connectionState as ConnectionState.RadiosOff).missingRadios.joinToString(" & ")
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = AlertRed.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AlertRed)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Radios Disabled ($missing)",
                                style = MaterialTheme.typography.titleMedium,
                                color = AlertRed,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Turn on $missing to establish offline P2P emergency link.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                        OutlinedButton(onClick = onNavigateToPermissions) {
                            Text("Setup", color = AlertRed)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Discovered Peers Section
            Text(
                text = "DISCOVERED PEERS",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (connectionState is ConnectionState.Connecting) {
                val peerName = (connectionState as ConnectionState.Connecting).peerName
                PairingScreen(peerName = peerName, onCancel = { viewModel.cancelConnection() })
            } else if (discoveredPeers.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceVariant)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CellTower,
                            contentDescription = null,
                            tint = TextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = if (connectionState is ConnectionState.Discovering) "Searching for nearby emergency hosts..." else "No peers discovered yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary
                        )
                        Text(
                            text = "Tap Host on one phone and Scan on the other.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(discoveredPeers) { peer ->
                        PeerCard(peer = peer, onConnect = { viewModel.connectTo(peer) })
                    }
                }
            }
        }
    }
}

@Composable
fun PeerCard(peer: PeerInfo, onConnect: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(ConnectedGreen)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = peer.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Signal: ${peer.rssi} dBm (Direct Radio)",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }

            Button(
                onClick = onConnect,
                colors = ButtonDefaults.buttonColors(containerColor = SafetyOrange)
            ) {
                Text("CONNECT", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PairingScreen(peerName: String, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard),
        border = androidx.compose.foundation.BorderStroke(1.dp, SafetyOrange)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = SafetyOrange,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Connecting to $peerName...",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Establishing offline P2P encryption handshake",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(onClick = onCancel) {
                Text("Cancel", color = AlertRed)
            }
        }
    }
}
