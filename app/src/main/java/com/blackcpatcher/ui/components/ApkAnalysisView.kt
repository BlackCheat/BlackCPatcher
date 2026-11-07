package com.blackcpatcher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackcpatcher.domain.model.ApkReport

@Composable
fun ApkAnalysisView(report: ApkReport, titlePrefix: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "$titlePrefix REPORT",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${report.fileName} (${report.fileSize / (1024 * 1024)} MB)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            DetailItem("Package Name", report.packageName)
            DetailItem("Version Name", "${report.versionName} (code: ${report.versionCode})")
            DetailItem("SDK Compatibility", "Min: ${report.minSdk ?: "N/A"} • Target: ${report.targetSdk ?: "N/A"}")
            DetailItem("Launcher Activity", report.manifestReport.launcherActivity ?: "None reported")
            DetailItem("Archive Entries", "${report.archiveEntries} files")
            DetailItem("SHA-256 Fingerprint", report.sha256)
        }
    }
}

@Composable
private fun DetailItem(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
