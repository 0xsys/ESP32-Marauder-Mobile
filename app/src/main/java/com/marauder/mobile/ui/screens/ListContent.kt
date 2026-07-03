package com.marauder.mobile.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.marauder.mobile.data.ListType
import com.marauder.mobile.protocol.DeviceMessage
import com.marauder.mobile.ui.components.EmptyState
import com.marauder.mobile.ui.theme.CatWifi
import com.marauder.mobile.ui.theme.Danger
import com.marauder.mobile.ui.theme.MarauderCyan
import com.marauder.mobile.ui.theme.Success
import com.marauder.mobile.ui.theme.Warning
import com.marauder.mobile.vm.MarauderViewModel

/**
 * The reusable body for any structured list (action bar + live rows). Shared by the
 * standalone [ListScreen] and the live-activity screen so a running scan shows the
 * same interactive list, refreshed live.
 */
@Composable
fun ListContent(
    type: ListType,
    vm: MarauderViewModel,
    modifier: Modifier = Modifier,
    showActions: Boolean = true,
) {
    val loading by vm.listLoading.collectAsState()
    val isLoading = loading == type

    fun sendAndRefresh(cmd: String) {
        vm.runCommand(cmd)
        vm.refreshList(type)
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showActions) {
            ActionBar(type = type, onScan = { vm.runCommand("scanall") }, onAction = ::sendAndRefresh)
        }
        Box(modifier = Modifier.fillMaxSize()) {
            when (type) {
                ListType.ACCESS_POINTS -> {
                    val data by vm.aps.collectAsState()
                    ListBody(data.isEmpty(), isLoading, "No access points", "Run a scan, then refresh.") {
                        items(data, key = { it.index }) { ApRow(it) { sendAndRefresh("select -a ${it.index}") } }
                    }
                }
                ListType.STATIONS -> {
                    val data by vm.stations.collectAsState()
                    ListBody(data.isEmpty(), isLoading, "No stations", "Run Scan AP/STA, then refresh.") {
                        items(data, key = { "${it.ap}-${it.index}" }) { StaRow(it) { sendAndRefresh("select -c ${it.index}") } }
                    }
                }
                ListType.SSIDS -> {
                    val data by vm.ssids.collectAsState()
                    ListBody(data.isEmpty(), isLoading, "No SSIDs", "Generate or load SSIDs, then refresh.") {
                        items(data, key = { it.index }) { SsidRow(it) { sendAndRefresh("select -s ${it.index}") } }
                    }
                }
                ListType.IPS -> {
                    val data by vm.ips.collectAsState()
                    ListBody(data.isEmpty(), isLoading, "No hosts", "Run a Ping/ARP scan, then refresh.") {
                        items(data, key = { it.index }) { IpRow(it) }
                    }
                }
                ListType.PROBES -> {
                    val data by vm.probes.collectAsState()
                    ListBody(data.isEmpty(), isLoading, "No probe requests", "Run Probe Request Sniff, then refresh.") {
                        items(data, key = { it.index }) { ProbeRow(it) }
                    }
                }
                ListType.AIRTAGS -> {
                    val data by vm.airtags.collectAsState()
                    ListBody(data.isEmpty(), isLoading, "No AirTags", "Run an AirTag sniff, then refresh.") {
                        items(data, key = { it.index }) { AirtagRow(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListBody(
    empty: Boolean,
    loading: Boolean,
    emptyTitle: String,
    emptyHint: String,
    content: LazyListScope.() -> Unit,
) {
    when {
        empty && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MarauderCyan)
        }
        empty -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Filled.Inbox, emptyTitle, emptyHint)
        }
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun ActionBar(type: ListType, onScan: () -> Unit, onAction: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (type) {
            ListType.ACCESS_POINTS -> {
                ActionChip("Scan", Icons.Filled.Wifi, CatWifi, onScan)
                ActionChip("Select all", Icons.Filled.DoneAll, MarauderCyan) { onAction("select -a all") }
                ActionChip("Save", Icons.Filled.Save, Success) { onAction("save -a") }
                ActionChip("Clear", Icons.Filled.DeleteSweep, Danger) { onAction("clearlist -a") }
            }
            ListType.STATIONS -> {
                ActionChip("Scan", Icons.Filled.Wifi, CatWifi, onScan)
                ActionChip("Select all", Icons.Filled.DoneAll, MarauderCyan) { onAction("select -c all") }
                ActionChip("Clear", Icons.Filled.DeleteSweep, Danger) { onAction("clearlist -c") }
            }
            ListType.SSIDS -> {
                ActionChip("Select all", Icons.Filled.DoneAll, MarauderCyan) { onAction("select -s all") }
                ActionChip("Save", Icons.Filled.Save, Success) { onAction("save -s") }
                ActionChip("Clear", Icons.Filled.DeleteSweep, Danger) { onAction("clearlist -s") }
            }
            else -> Unit
        }
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = tint.copy(alpha = 0.12f)) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = tint)
        }
    }
}

// --- Rows --------------------------------------------------------------------

@Composable
private fun RowCard(selected: Boolean, onToggle: (() -> Unit)?, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (selected) MarauderCyan else MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { content() }
            if (onToggle != null) {
                Spacer(Modifier.width(8.dp))
                SelectToggle(selected, onToggle)
            }
        }
    }
}

@Composable
private fun SelectToggle(selected: Boolean, onToggle: () -> Unit) {
    val color = if (selected) MarauderCyan else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = if (selected) 0.20f else 0.08f),
    ) {
        Text(
            if (selected) "SELECTED" else "SELECT",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ApRow(ap: DeviceMessage.Ap, onToggle: () -> Unit) {
    RowCard(selected = ap.selected, onToggle = onToggle) {
        Column {
            Text(
                ap.essid.ifBlank { "(hidden)" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                ap.bssid,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Meta("CH ${ap.channel}")
                Text("${ap.rssi} dBm", style = MaterialTheme.typography.labelMedium, color = rssiColor(ap.rssi), fontWeight = FontWeight.SemiBold)
                Meta("${ap.stations} sta")
                if (ap.packets > 0) Meta("${ap.packets} pkt")
                if (ap.sec != 0) Icon(Icons.Filled.Lock, contentDescription = "secured", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                if (ap.wps) Meta("WPS", Warning)
            }
        }
    }
}

@Composable
private fun StaRow(sta: DeviceMessage.Sta, onToggle: () -> Unit) {
    RowCard(selected = sta.selected, onToggle = onToggle) {
        Column {
            Text(sta.mac, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.size(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Meta("AP #${sta.ap}")
                if (sta.packets > 0) Meta("${sta.packets} pkt")
            }
        }
    }
}

@Composable
private fun SsidRow(ssid: DeviceMessage.SsidRow, onToggle: () -> Unit) {
    RowCard(selected = ssid.selected, onToggle = onToggle) {
        Column {
            Text(ssid.essid.ifBlank { "(empty)" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(2.dp))
            Meta("CH ${ssid.channel}")
        }
    }
}

@Composable
private fun IpRow(ip: DeviceMessage.Ip) {
    RowCard(selected = false, onToggle = null) {
        Text(ip.ip, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ProbeRow(probe: DeviceMessage.Probe) {
    RowCard(selected = probe.selected, onToggle = null) {
        Column {
            Text(probe.essid.ifBlank { "(broadcast)" }, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(2.dp))
            Meta("${probe.requests} req")
        }
    }
}

@Composable
private fun AirtagRow(tag: DeviceMessage.Airtag) {
    RowCard(selected = tag.selected, onToggle = null) {
        Column {
            Text(tag.mac, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(4.dp))
            Text("${tag.rssi} dBm", style = MaterialTheme.typography.labelMedium, color = rssiColor(tag.rssi), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Meta(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = color)
}

private fun rssiColor(rssi: Int): Color = when {
    rssi >= -60 -> Success
    rssi >= -75 -> Warning
    else -> Danger
}
