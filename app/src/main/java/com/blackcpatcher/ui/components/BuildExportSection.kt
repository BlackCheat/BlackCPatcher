package com.blackcpatcher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackcpatcher.domain.model.BuildResult

@Composable
fun BuildExportSection(
    isBuilding: Boolean,
    progressPercentage: Int,
    progressMessage: String,
    buildResult: BuildResult?,
    onRunValidation: () -> Unit,
    onRunBuild: () -> Unit,
    onExportApk: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "2. Build & Export Engine",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onRunValidation,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Run Pre-Check")
            }

            Button(
                onClick = onRunBuild,
                enabled = !isBuilding,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (isBuilding) "Building…" else "Build & Sign")
            }
        }

        // Progress Bar
        if (isBuilding || progressPercentage > 0) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = progressMessage, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Text(text = "$progressPercentage%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                    LinearProgressIndicator(
                        progress = { progressPercentage / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Export Button & Summary
        if (buildResult?.isSuccess == true && !buildResult.outputPath.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Build Pipeline Completed Successfully!",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Duration: ${buildResult.buildDurationMs} ms • Size: ${(buildResult.outputSize ?: 0) / (1024 * 1024)} MB\nSignature Schemes: V1 (JAR)=${buildResult.v1Verified}, V2=${buildResult.v2Verified}, V3=${buildResult.v3Verified}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Button(
                        onClick = onExportApk,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export Signed Output APK (SAF)")
                    }
                }
            }
        }
    }
}
