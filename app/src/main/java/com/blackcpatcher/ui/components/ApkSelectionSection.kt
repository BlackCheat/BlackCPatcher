package com.blackcpatcher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackcpatcher.domain.model.ApkReport

@Composable
fun ApkSelectionSection(
    sourceReport: ApkReport?,
    targetReport: ApkReport?,
    sourceName: String,
    targetName: String,
    onSelectSource: () -> Unit,
    onSelectTarget: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "1. APK Selection",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        ApkPickerCard(
            title = "Source / Menu APK (Optional)",
            filename = sourceReport?.let { "${it.fileName} (${it.fileSize / 1024} KB) • ${it.packageName}" } ?: sourceName,
            icon = Icons.Default.Android,
            onClick = onSelectSource
        )

        ApkPickerCard(
            title = "Target APK (Required)",
            filename = targetReport?.let { "${it.fileName} (${it.fileSize / 1024} KB) • ${it.packageName}" } ?: targetName,
            icon = Icons.Default.FolderOpen,
            onClick = onSelectTarget
        )
    }
}

@Composable
private fun ApkPickerCard(
    title: String,
    filename: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, fontWeight = FontWeight.SemiBold)
                Text(
                    text = filename,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.FolderOpen,
                contentDescription = "Browse",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}
