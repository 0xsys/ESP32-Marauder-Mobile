package com.marauder.mobile.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marauder.mobile.data.ScanModes
import com.marauder.mobile.protocol.DeviceMessage
import com.marauder.mobile.ui.theme.CatWifi
import com.marauder.mobile.ui.theme.Danger
import com.marauder.mobile.ui.theme.MarauderCyan

@Composable
fun StatusPanel(
    connected: Boolean,
    name: String?,
    status: DeviceMessage.Status?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(verticalAlignment = Alignment.CenterVertically) {
                ConnectionDot(
                    color = if (connected) CatWifi else MaterialTheme.colorScheme.onSurfaceVariant,
                    pulsing = connected,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = name ?: if (connected) "Connected" else "Disconnected",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.weight(1f))
                status?.let {
                    Text(
                        "RAM ${it.free / 1024} KB",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            when {
                status != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val running = status.running
                        val modeColor = if (running) Danger else MarauderCyan
                        StatChip(
                            label = if (running) "LIVE" else "MODE",
                            value = ScanModes.name(status.mode),
                            color = modeColor,
                        )
                        Spacer(Modifier.weight(1f))
                        if (running) {
                            Surface(
                                onClick = onStop,
                                shape = RoundedCornerShape(10.dp),
                                color = Danger.copy(alpha = 0.16f),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = null, tint = Danger, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Stop", style = MaterialTheme.typography.labelLarge, color = Danger, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatChip("AP", status.aps.toString(), CatWifi)
                        StatChip("STA", status.stations.toString(), MarauderCyan)
                        StatChip("SSID", status.ssids.toString(), MaterialTheme.colorScheme.tertiary)
                        StatChip("PROBE", status.probes.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
                        StatChip("IP", status.ips.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
                        StatChip("AIRTAG", status.airtags.toString(), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                connected -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MarauderCyan,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "Waiting for device…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
