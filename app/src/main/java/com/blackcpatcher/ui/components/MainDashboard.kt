package com.blackcpatcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.blackcpatcher.domain.model.ActivityLogEntry
import com.blackcpatcher.domain.model.ApkReport
import com.blackcpatcher.domain.model.ArchitectureMode
import com.blackcpatcher.domain.model.InjectionConfig
import com.blackcpatcher.domain.model.InjectionResult
import com.blackcpatcher.domain.model.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainDashboard(
    sourceName: String,
    targetName: String,
    sourceReport: ApkReport?,
    targetReport: ApkReport?,
    config: InjectionConfig,
    isInjecting: Boolean,
    progressPercentage: Int,
    progressMessage: String,
    injectionResult: InjectionResult?,
    logs: List<ActivityLogEntry>,
    onSelectSource: () -> Unit,
    onSelectTarget: () -> Unit,
    onArchChange: (ArchitectureMode) -> Unit,
    onPermissionChange: (String) -> Unit,
    onServiceChange: (String) -> Unit,
    onHookChange: (String) -> Unit,
    onSmaliOverrideChange: (String) -> Unit,
    onFetchFormat1: () -> Unit,
    onFetchFormat2: () -> Unit,
    onFetchFormat3: () -> Unit,
    onExecutePipeline: () -> Unit,
    onInstallApk: () -> Unit,
    onClearLogs: () -> Unit
) {
    var showFileExplorer by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    Column {
                        Text("BlackCPatcher", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("Telemetry & Overlay Studio", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Dual Pickers
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Packages")

                    PickerCard(
                        label = "Source Overlay Package",
                        fileName = sourceReport?.let { "${it.fileName} (${it.fileSize / 1024} KB)" } ?: sourceName,
                        icon = Icons.Default.Android,
                        onClick = onSelectSource
                    )

                    PickerCard(
                        label = "Target Destination Package",
                        fileName = targetReport?.let { "${it.fileName} (${it.fileSize / 1024} KB)" } ?: targetName,
                        icon = Icons.Default.FolderOpen,
                        onClick = onSelectTarget
                    )
                }
            }

            // Architecture Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Architecture")
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = config.selectedArchitecture == ArchitectureMode.ARM64_V8A,
                                    onClick = { onArchChange(ArchitectureMode.ARM64_V8A) },
                                )
                                Text("arm64-v8a", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = config.selectedArchitecture == ArchitectureMode.ARMEABI_V7A,
                                    onClick = { onArchChange(ArchitectureMode.ARMEABI_V7A) },
                                )
                                Text("armeabi-v7a", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Configuration Inputs
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Configuration")

                    InputWithFormatCard(
                        title = "Permission Tag",
                        value = config.permissionTag,
                        onValueChange = onPermissionChange,
                        onFormatClick = onFetchFormat1
                    )

                    InputWithFormatCard(
                        title = "Service Tag",
                        value = config.serviceTag,
                        onValueChange = onServiceChange,
                        onFormatClick = onFetchFormat2
                    )

                    InputWithFormatCard(
                        title = "Initialization Hook",
                        value = config.hookLine,
                        onValueChange = onHookChange,
                        onFormatClick = onFetchFormat3
                    )
                }
            }

            // Entry Point
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Entry Point Override")
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Detected Launcher: ${targetReport?.manifestReport?.launcherActivity ?: "Unknown"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { showFileExplorer = true },
                                    enabled = targetReport != null,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Browse", fontSize = 12.sp)
                                }
                            }
                            OutlinedTextField(
                                value = config.manualSmaliPathOverride,
                                onValueChange = onSmaliOverrideChange,
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Manual .smali Path") },
                                singleLine = true,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                }
            }

            // Action Button
            item {
                Button(
                    onClick = onExecutePipeline,
                    enabled = !isInjecting,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("BUILD & SIGN APK", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            // Post-Process View
            if (injectionResult?.isSuccess == true) {
                item {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                                Spacer(Modifier.width(8.dp))
                                Text("Build Successful", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                            Text(injectionResult.outputPath ?: "", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(
                                onClick = onInstallApk,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("INSTALL APK", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Console
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SectionHeader("Build Output")
                        IconButton(onClick = onClearLogs, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                    ) {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().height(180.dp).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (logs.isEmpty()) {
                                item { Text("Waiting for task...", fontSize = 12.sp, color = Color.Gray) }
                            } else {
                                items(logs) { log ->
                                    val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp))
                                    val color = when (log.level) {
                                        LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                                        LogLevel.SUCCESS -> MaterialTheme.colorScheme.secondary
                                        LogLevel.WARNING -> Color(0xFFF59E0B)
                                        LogLevel.ERROR -> MaterialTheme.colorScheme.error
                                    }
                                    Text("[$timeStr] ${log.message}", color = color, fontSize = 12.sp, lineHeight = 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Loading Dialog
        if (isInjecting) {
            Dialog(onDismissRequest = {}) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text("Processing", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        LinearProgressIndicator(progress = { progressPercentage / 100f }, modifier = Modifier.fillMaxWidth())
                        Text("$progressPercentage% - $progressMessage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // File Explorer Dialog
        if (showFileExplorer && targetReport != null) {
            ApkFileExplorerDialog(
                classes = targetReport.dexReport.allClasses,
                onDismiss = { showFileExplorer = false },
                onFileSelected = { path ->
                    onSmaliOverrideChange(path)
                    showFileExplorer = false
                }
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun PickerCard(label: String, fileName: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Text(fileName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
        }
    }
}

@Composable
private fun InputWithFormatCard(title: String, value: String, onValueChange: (String) -> Unit, onFormatClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                TextButton(onClick = onFormatClick, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp), modifier = Modifier.height(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Format", fontSize = 12.sp)
                }
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                shape = RoundedCornerShape(8.dp)
            )
        }
    }
}
