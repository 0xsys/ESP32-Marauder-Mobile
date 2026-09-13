package com.marauder.mobile.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marauder.mobile.protocol.DeviceMessage
import com.marauder.mobile.ui.components.EmptyState
import com.marauder.mobile.ui.theme.Danger
import com.marauder.mobile.ui.theme.MarauderTextDim
import com.marauder.mobile.ui.theme.Success
import com.marauder.mobile.ui.theme.Warning
import com.marauder.mobile.vm.EvilPortalUiState
import com.marauder.mobile.vm.MarauderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EvilPortalScreen(vm: MarauderViewModel, onBack: () -> Unit) {
    val state by vm.evilPortal.collectAsState()
    val aps by vm.aps.collectAsState()
    val context = LocalContext.current
    var confirmStart by remember { mutableStateOf(false) }

    // Fresh flow each time the screen opens; pull the current device AP list so a
    // target can be picked without leaving.
    LaunchedEffect(Unit) {
        vm.resetEvilPortal()
        vm.refreshList(com.marauder.mobile.data.ListType.ACCESS_POINTS)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
        val name = displayName(context, uri) ?: uri.lastPathSegment?.substringAfterLast('/') ?: "portal.html"
        if (bytes != null) vm.uploadPortalHtml(bytes, name)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Evil Portal") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.phase == EvilPortalUiState.Phase.Running) {
                        IconButton(onClick = { vm.stopEvilPortal() }) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IntroCard()

            when (state.phase) {
                EvilPortalUiState.Phase.Uploading -> UploadingCard(state)
                EvilPortalUiState.Phase.Running -> RunningCard(state, onStop = { vm.stopEvilPortal() })
                else -> {
                    HtmlCard(
                        state = state,
                        onPick = { picker.launch(arrayOf("text/html", "text/plain", "*/*")) },
                    )
                    if (state.htmlReady) {
                        TargetApCard(aps = aps, selected = state.targetApIndex, onSelect = vm::setPortalTargetAp)
                        Button(
                            onClick = { confirmStart = true },
                            enabled = state.targetApIndex != null,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Start portal") }
                        if (state.targetApIndex == null) {
                            Text(
                                "Pick a target access point above to enable Start.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MarauderTextDim,
                            )
                        }
                    }
                }
            }

            if (state.creds.isNotEmpty()) CredsCard(state.creds)
        }
    }

    if (confirmStart) {
        AlertDialog(
            onDismissRequest = { confirmStart = false },
            icon = { Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = Warning) },
            title = { Text("Start Evil Portal?") },
            text = {
                Text(
                    "This broadcasts a rogue access point and serves your page to anyone who " +
                        "connects, capturing what they submit. Only use it on networks and devices " +
                        "you are authorised to test.",
                )
            },
            confirmButton = {
                Button(onClick = { confirmStart = false; vm.startEvilPortal() }) { Text("Start") }
            },
            dismissButton = {
                TextButton(onClick = { confirmStart = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun IntroCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text("Host-supplied portal", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                Text(
                    "Pick a captive-portal HTML page from this phone — it streams to the device over USB, so no SD card is needed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HtmlCard(state: EvilPortalUiState, onPick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("1 · Portal page", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)

            when (state.phase) {
                EvilPortalUiState.Phase.Ready -> StatusRow(
                    icon = Icons.Filled.CheckCircle, tint = Success,
                    title = state.fileName ?: "Page uploaded",
                    subtitle = state.message ?: "Page set on device",
                )
                EvilPortalUiState.Phase.Error -> StatusRow(
                    icon = Icons.Filled.ErrorOutline, tint = Danger,
                    title = "Upload failed",
                    subtitle = state.message ?: "Try another file",
                )
                else -> Text(
                    "No page selected yet. Choose an .html file to send to the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (state.htmlReady) "Choose a different page" else "Choose HTML file")
            }
            Text(
                "Kept in device RAM, so keep it small (≈11 KB, or ≈30 KB on PSRAM boards).",
                style = MaterialTheme.typography.labelSmall,
                color = MarauderTextDim,
            )
        }
    }
}

@Composable
private fun UploadingCard(state: EvilPortalUiState) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Uploading page…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(state.message ?: "Streaming ${state.htmlBytes} B", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Keep the cable connected…", style = MaterialTheme.typography.labelMedium, color = MarauderTextDim)
            }
        }
    }
}

@Composable
private fun TargetApCard(
    aps: List<DeviceMessage.Ap>,
    selected: Int?,
    onSelect: (Int?) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("2 · Target access point", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            if (aps.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Wifi,
                    title = "No access points yet",
                    subtitle = "Run an AP scan first, then reopen this screen to pick a target.",
                )
            } else {
                Column(
                    Modifier.heightIn(max = 240.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    aps.forEach { ap ->
                        val isSel = selected == ap.index
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(if (isSel) null else ap.index) },
                        ) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Router, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(
                                        ap.essid.ifBlank { "(hidden)" },
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text("#${ap.index} · ch ${ap.channel} · ${ap.rssi} dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isSel) Icon(Icons.Filled.CheckCircle, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RunningCard(state: EvilPortalUiState, onStop: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Success.copy(alpha = 0.10f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Portal running", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            }
            Text(
                "Serving ${state.fileName ?: "the uploaded page"} · ${state.creds.size} credential(s) captured.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onStop, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Stop portal")
            }
        }
    }
}

@Composable
private fun CredsCard(creds: List<DeviceMessage.Cred>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Key, contentDescription = null, tint = Warning, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Captured credentials (${creds.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            Column(
                Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                creds.asReversed().forEach { c ->
                    Text(
                        "${c.user}  :  ${c.pass}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Resolve a content Uri's display name via the OpenableColumns provider. */
private fun displayName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }.getOrNull()
