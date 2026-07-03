package com.marauder.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.marauder.mobile.R

/** The Marauder skull + wordmark lockup. */
@Composable
fun LogoHeader(
    subtitle: String,
    modifier: Modifier = Modifier,
    large: Boolean = true,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (large) 10.dp else 6.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.marauder_skull),
            contentDescription = "Marauder",
            modifier = Modifier.size(if (large) 104.dp else 56.dp),
        )
        Text(
            text = "MARAUDER",
            fontSize = if (large) 28.sp else 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = if (large) 8.sp else 5.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
