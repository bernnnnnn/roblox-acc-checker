package com.robloxvault.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The dark Noctra header bar shown at the top of each tab. */
@Composable
fun GradientHeader(
    title: String,
    subtitle: String,
    icon: ImageVector,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(NoctraSurfaceHi, NoctraFloating)),
                RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
            )
            .padding(start = 20.dp, end = 12.dp, top = 26.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = NoctraAccent.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp)) {
            Icon(
                icon,
                contentDescription = null,
                tint = NoctraAccent,
                modifier = Modifier.padding(8.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = NoctraTextHi, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = NoctraMuted, fontSize = 13.sp)
        }
        if (trailing != null) trailing()
    }
}

/** A compact labelled stat pill used on account-info cards. */
@Composable
fun StatPill(label: String, value: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Text(label.uppercase(), color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A single "label: value  [copy]" row with its own copy button. */
@Composable
fun CopyableRow(
    label: String,
    value: String,
    onCopy: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.width(96.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
        IconButton(onClick = onCopy) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = "Copy $label",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
