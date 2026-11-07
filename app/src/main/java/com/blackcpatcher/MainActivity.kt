package com.blackcpatcher

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.blackcpatcher.ui.MainViewModel
import com.blackcpatcher.ui.components.MainDashboard
import com.blackcpatcher.ui.theme.AppTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkStoragePermissions()
        
        setContent {
            AppTheme {
                BlackCPatcherApp(viewModel)
            }
        }
    }

    private fun checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    intent.addCategory("android.intent.category.DEFAULT")
                    intent.data = Uri.parse(String.format("package:%s", applicationContext.packageName))
                    startActivity(intent)
                } catch (e: Exception) {
                    val intent = Intent()
                    intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                    startActivity(intent)
                }
            }
        } else {
            val write = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            if (write != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
            }
        }
    }
}

@Composable
private fun BlackCPatcherApp(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val mimeTypes = arrayOf("application/vnd.android.package-archive", "application/octet-stream")

    val sourcePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = queryFileName(context, it)
            viewModel.setSourceUri(it, name)
        }
    }

    val targetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val name = queryFileName(context, it)
            viewModel.setTargetUri(it, name)
        }
    }

    MainDashboard(
        sourceName = uiState.sourceName,
        targetName = uiState.targetName,
        sourceReport = uiState.sourceReport,
        targetReport = uiState.targetReport,
        config = uiState.injectionConfig,
        isInjecting = uiState.isInjecting,
        progressPercentage = uiState.progressPercentage,
        progressMessage = uiState.progressMessage,
        injectionResult = uiState.injectionResult,
        logs = uiState.activityLogs,
        onSelectSource = { sourcePicker.launch(mimeTypes) },
        onSelectTarget = { targetPicker.launch(mimeTypes) },
        onArchChange = { viewModel.setArchitectureMode(it) },
        onPermissionChange = { viewModel.setPermissionTag(it) },
        onServiceChange = { viewModel.setServiceTag(it) },
        onHookChange = { viewModel.setHookLine(it) },
        onSmaliOverrideChange = { viewModel.setSmaliOverride(it) },
        onFetchFormat1 = { viewModel.fetchFormatInput1() },
        onFetchFormat2 = { viewModel.fetchFormatInput2() },
        onFetchFormat3 = { viewModel.fetchFormatInput3() },
        onExecutePipeline = { viewModel.executeInjectionPipeline() },
        onInstallApk = {
            val outputPath = uiState.injectionResult?.outputPath
            if (!outputPath.isNullOrBlank()) {
                val apkFile = File(outputPath)
                if (apkFile.exists()) {
                    installApk(context, apkFile)
                } else {
                    Toast.makeText(context, "Apk file not found: $outputPath", Toast.LENGTH_SHORT).show()
                }
            }
        },
        onClearLogs = { viewModel.clearLogs() }
    )
}

private fun installApk(context: android.content.Context, file: File) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(fallbackIntent)
        } catch (ex: Exception) {
            Toast.makeText(context, "Install trigger launched for: ${file.name}", Toast.LENGTH_LONG).show()
        }
    }
}

private fun queryFileName(context: android.content.Context, uri: Uri): String {
    var result = uri.lastPathSegment ?: "selected.apk"
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) result = it.getString(0) ?: result
    }
    return result
}
