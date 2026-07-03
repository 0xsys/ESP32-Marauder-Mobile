package com.marauder.mobile.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marauder.mobile.data.AnalyzerKind
import com.marauder.mobile.ui.components.EmptyState
import com.marauder.mobile.ui.theme.MarauderCyan
import com.marauder.mobile.vm.MarauderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzerScreen(
    command: String,
    kind: AnalyzerKind,
    vm: MarauderViewModel,
    onBack: () -> Unit,
) {
    val state by vm.analyzer.collectAsState()

    DisposableEffect(command) {
        vm.startAnalyzer(command, kind)
        onDispose { vm.stopScan() }
    }

    val title = when (kind) {
        AnalyzerKind.WIFI -> "Channel Analyzer"
        AnalyzerKind.BT -> "Bluetooth Analyzer"
        AnalyzerKind.CHANNEL -> "Channel Summary"
    }
    val isBars = kind == AnalyzerKind.CHANNEL

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { vm.stopScan() }) {
                        Icon(Icons.Filled.Stop, contentDescription = "Stop", tint = MaterialTheme.colorScheme.error)
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
            modifier = Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val hasData = if (isBars) state.values.isNotEmpty() else state.samples.isNotEmpty()

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(260.dp).padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        !hasData -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            CircularProgressIndicator(color = MarauderCyan)
                            Text("Waiting for samples…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        isBars -> ChannelBars(state.channels, state.values)
                        else -> RollingLine(state.samples)
                    }
                }
            }

            if (isBars && state.channels.isNotEmpty()) {
                Text(
                    "Page ${state.page} · ${state.channels.size} channels",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (!isBars && state.samples.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    Legend("Now", state.samples.last().toString())
                    Legend("Min", (state.samples.minOrNull() ?: 0).toString())
                    Legend("Max", (state.samples.maxOrNull() ?: 0).toString())
                }
            }

            EmptyState(
                icon = Icons.Filled.Insights,
                title = title,
                subtitle = "Streaming live from the firmware. Leave this screen to stop.",
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun Legend(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = MarauderCyan, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RollingLine(samples: List<Int>) {
    val accent = MarauderCyan
    val grid = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawGrid(grid)
        if (samples.size < 2) return@Canvas
        val maxV = samples.max()
        val minV = samples.min()
        val range = (maxV - minV).coerceAtLeast(1).toFloat()
        val stepX = size.width / (samples.size - 1).toFloat()

        fun yFor(v: Int) = size.height - ((v - minV) / range) * size.height

        val line = Path()
        val fill = Path()
        samples.forEachIndexed { i, v ->
            val x = i * stepX
            val y = yFor(v)
            if (i == 0) { line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y) }
            else { line.lineTo(x, y); fill.lineTo(x, y) }
        }
        fill.lineTo((samples.size - 1) * stepX, size.height)
        fill.close()

        drawPath(fill, color = accent.copy(alpha = 0.15f))
        drawPath(line, color = accent, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
    }
}

@Composable
private fun ChannelBars(channels: List<Int>, values: List<Int>) {
    val accent = MarauderCyan
    val grid = MaterialTheme.colorScheme.outline
    Canvas(modifier = Modifier.fillMaxSize()) {
        drawGrid(grid)
        if (values.isEmpty()) return@Canvas
        val maxV = (values.maxOrNull() ?: 1).coerceAtLeast(1).toFloat()
        val n = values.size
        val slot = size.width / n
        val barW = slot * 0.6f
        values.forEachIndexed { i, v ->
            val h = (v / maxV) * size.height
            val left = i * slot + (slot - barW) / 2f
            drawRect(
                color = accent.copy(alpha = 0.85f),
                topLeft = Offset(left, size.height - h),
                size = Size(barW, h),
            )
        }
    }
}

private fun DrawScope.drawGrid(color: androidx.compose.ui.graphics.Color) {
    val rows = 4
    for (r in 0..rows) {
        val y = size.height * r / rows
        drawLine(
            color = color.copy(alpha = 0.4f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}
