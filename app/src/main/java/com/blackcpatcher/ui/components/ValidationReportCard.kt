package com.blackcpatcher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackcpatcher.domain.model.ValidationReport
import com.blackcpatcher.domain.model.ValidationSeverity

@Composable
fun ValidationReportCard(
    report: ValidationReport,
    onExportReport: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Pre-Build Validation Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = onExportReport) {
                    Text("Export Report")
                }
            }

            // Summary Badges Row
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BadgeChip("PASS: ${report.passCount}", Color(0xFF4CAF50))
                BadgeChip("WARN: ${report.warningCount}", Color(0xFFFF9800))
                BadgeChip("FAIL: ${report.failCount}", Color(0xFFF44336))
            }

            HorizontalDivider()

            // Item List
            report.items.forEach { item ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    val (icon, tint) = when (item.severity) {
                        ValidationSeverity.PASS -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
                        ValidationSeverity.WARNING -> Icons.Default.Warning to Color(0xFFFF9800)
                        ValidationSeverity.FAIL -> Icons.Default.Error to Color(0xFFF44336)
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "[${item.category}] ${item.title}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = item.message,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.15f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
