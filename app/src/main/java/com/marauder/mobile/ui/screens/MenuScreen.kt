package com.marauder.mobile.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.marauder.mobile.data.Catalog
import com.marauder.mobile.data.MenuAction
import com.marauder.mobile.data.MenuItem
import com.marauder.mobile.ui.components.LogoHeader
import com.marauder.mobile.ui.components.MenuCard
import com.marauder.mobile.ui.components.SectionLabel
import com.marauder.mobile.ui.components.StatusPanel
import com.marauder.mobile.ui.theme.Danger
import com.marauder.mobile.usb.UsbSerialManager
import com.marauder.mobile.vm.MarauderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    screenId: String,
    vm: MarauderViewModel,
    onItemClick: (MenuItem) -> Unit,
    onBack: () -> Unit,
    onOpenConsole: () -> Unit,
    onOpenAbout: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val screen = Catalog.screen(screenId) ?: Catalog.screen(Catalog.ROOT)!!
    val isRoot = screen.id == Catalog.ROOT

    val connStatus by vm.status.collectAsState()
    val name by vm.connectedName.collectAsState()
    val devStatus by vm.deviceStatus.collectAsState()
    val darkTheme by vm.darkTheme.collectAsState()
    val connected = connStatus == UsbSerialManager.Status.CONNECTED

    var pendingConfirm by remember { mutableStateOf<MenuItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(screen.title) },
                navigationIcon = {
                    if (!isRoot) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (devStatus?.running == true) {
                        IconButton(onClick = { vm.stopScan() }) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Danger)
                        }
                    }
                    if (isRoot) {
                        IconButton(onClick = { vm.toggleTheme() }) {
                            Icon(
                                if (darkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = if (darkTheme) "Switch to light theme" else "Switch to dark theme",
                            )
                        }
                        IconButton(onClick = onOpenAbout) {
                            Icon(Icons.Outlined.Info, contentDescription = "About")
                        }
                    }
                    IconButton(onClick = onOpenConsole) {
                        Icon(Icons.Filled.Terminal, contentDescription = "Console")
                    }
                    if (isRoot) {
                        IconButton(onClick = onDisconnect) {
                            Icon(Icons.Filled.LinkOff, contentDescription = "Disconnect")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { inner ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (isRoot) {
                item {
                    LogoHeader(
                        subtitle = "ESP32 MARAUDER CONTROLLER",
                        large = false,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
                item {
                    StatusPanel(
                        connected = connected,
                        name = name,
                        status = devStatus,
                        onStop = { vm.stopScan() },
                    )
                }
                item { SectionLabel("Main Menu", modifier = Modifier.padding(top = 6.dp)) }
            }

            items(screen.items, key = { it.title }) { item ->
                MenuCard(
                    item = item,
                    onClick = {
                        if (item.dangerous && item.action is MenuAction.Send) {
                            pendingConfirm = item
                        } else {
                            onItemClick(item)
                        }
                    },
                )
            }

            if (isRoot) {
                item { CreditFooter(onClick = onOpenAbout, modifier = Modifier.padding(top = 14.dp)) }
            }
        }
    }

    pendingConfirm?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = Danger) },
            title = { Text(item.title) },
            text = {
                Text(
                    buildString {
                        append("This runs an active attack on nearby devices. Only use it on networks you are authorised to test.\n\n")
                        (item.action as? MenuAction.Send)?.let { append(it.command) }
                    },
                    fontFamily = FontFamily.Default,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onItemClick(item)
                    pendingConfirm = null
                }) { Text("Run", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("Cancel") }
            },
        )
    }
}

/** The author credit pinned to the bottom of the main menu; taps open About. */
@Composable
private fun CreditFooter(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "by Mohammed Ali",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            "GitHub @0xsys · tap for About",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
