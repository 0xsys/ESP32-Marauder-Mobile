package com.marauder.mobile.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.marauder.mobile.data.LiveClassifier
import com.marauder.mobile.ui.components.EmptyState
import com.marauder.mobile.ui.components.StatusPanel
import com.marauder.mobile.ui.theme.Danger
import com.marauder.mobile.ui.theme.MarauderCyan
import com.marauder.mobile.ui.theme.MarauderTextDim
import com.marauder.mobile.usb.UsbSerialManager
import com.marauder.mobile.vm.ConsoleEntry
import com.marauder.mobile.vm.LineKind
import com.marauder.mobile.vm.MarauderViewModel
import androidx.compose.material.icons.filled.Terminal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveActivityScreen(
    title: String,
    command: String,
    vm: MarauderViewModel,
    onBack: () -> Unit,
) {
    val spec = remember(command) { LiveClassifier.of(command) }
    val mark = remember(command) { vm.consoleMark() }

    DisposableEffect(command) {
        vm.startLiveActivity(command, spec.list)
        onDispose { vm.stopLiveActivity(spec.continuous) }
    }

    val connStatus by vm.status.collectAsState()
    val connName by vm.connectedName.collectAsState()
    val devStatus by vm.deviceStatus.collectAsState()
    val console by vm.console.collectAsState()
    val output = console.filter { it.id >= mark }

    var tab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    if (spec.continuous) {
                        IconButton(onClick = { vm.stopScan() }) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = Danger)
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
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {

            StatusPanel(
                connected = connStatus == UsbSerialManager.Status.CONNECTED,
                name = connName,
                status = devStatus,
                onStop = { vm.stopScan() },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            Text(
                text = "$ $command",
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MarauderCyan,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )

            if (spec.isList) {
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Live") })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Output") })
                }
                Box(Modifier.fillMaxSize()) {
                    if (tab == 0) {
                        ListContent(type = spec.list!!, vm = vm)
                    } else {
                        OutputPane(output)
                    }
                }
            } else {
                OutputPane(output, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun OutputPane(entries: List<ConsoleEntry>, modifier: Modifier = Modifier) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
            EmptyState(
                icon = Icons.Filled.Terminal,
                title = "Running…",
                subtitle = "Waiting for the device to respond.",
            )
        }
        return
    }
    val listState = rememberLazyListState()
    LaunchedEffect(entries.size) {
        if (entries.isNotEmpty()) listState.animateScrollToItem(entries.lastIndex)
    }
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        items(entries, key = { it.id }) { entry ->
            val color: Color = when (entry.kind) {
                LineKind.INPUT -> MarauderCyan
                LineKind.OUTPUT -> MaterialTheme.colorScheme.onSurface
                LineKind.SYSTEM -> MarauderTextDim
                LineKind.ERROR -> Danger
            }
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = color,
            )
        }
    }
}
