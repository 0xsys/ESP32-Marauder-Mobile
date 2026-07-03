package com.marauder.mobile.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marauder.mobile.data.Accent
import com.marauder.mobile.ui.theme.CatAttack
import com.marauder.mobile.ui.theme.CatBluetooth
import com.marauder.mobile.ui.theme.CatDevice
import com.marauder.mobile.ui.theme.CatGeneral
import com.marauder.mobile.ui.theme.CatGps
import com.marauder.mobile.ui.theme.CatNeutral
import com.marauder.mobile.ui.theme.CatScanner
import com.marauder.mobile.ui.theme.CatSniffer
import com.marauder.mobile.ui.theme.CatWifi
import com.marauder.mobile.ui.theme.MarauderCyan

fun accentColor(accent: Accent): Color = when (accent) {
    Accent.WIFI -> CatWifi
    Accent.BLUETOOTH -> CatBluetooth
    Accent.GPS -> CatGps
    Accent.DEVICE -> CatDevice
    Accent.NEUTRAL -> CatNeutral
    Accent.SNIFFER -> CatSniffer
    Accent.SCANNER -> CatScanner
    Accent.ATTACK -> CatAttack
    Accent.GENERAL -> CatGeneral
    Accent.PRIMARY -> MarauderCyan
}

/** A small pulsing status dot. */
@Composable
fun ConnectionDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier, size: Int = 10) {
    val alpha = if (pulsing) {
        val transition = rememberInfiniteTransition(label = "dot")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "dotAlpha",
        ).value
    } else 1f
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha)),
    )
}

/** A compact labelled value chip, e.g. "AP · 12". */
@Composable
fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(32.dp),
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(44.dp),
        )
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
