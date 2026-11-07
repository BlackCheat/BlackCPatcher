package com.blackcpatcher.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.blackcpatcher.data.apk.ApkInspectorEngine
import com.blackcpatcher.data.apk.ApkInjectorEngine
import com.blackcpatcher.domain.model.ActivityLogEntry
import com.blackcpatcher.domain.model.ApkReport
import com.blackcpatcher.domain.model.ArchitectureMode
import com.blackcpatcher.domain.model.InjectionConfig
import com.blackcpatcher.domain.model.InjectionResult
import com.blackcpatcher.domain.model.LogLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val sourceUri: Uri? = null,
    val targetUri: Uri? = null,
    val sourceName: String = "Select Overlay Source Package (BlackCPatcher Panel)",
    val targetName: String = "Select Target Destination Package (Game/App)",
    val sourceReport: ApkReport? = null,
    val targetReport: ApkReport? = null,
    val injectionConfig: InjectionConfig = InjectionConfig(),
    val isInjecting: Boolean = false,
    val progressPercentage: Int = 0,
    val progressMessage: String = "Ready for Cyberpunk overlay injection.",
    val injectionResult: InjectionResult? = null,
    val activityLogs: List<ActivityLogEntry> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        addLog(LogLevel.INFO, "BlackCPatcher Studio initialized.")
        addLog(LogLevel.INFO, "System online. Package SAF pickers & smali hooking pipeline ready.")
    }

    fun addLog(level: LogLevel, message: String) {
        val entry = ActivityLogEntry(level = level, message = message)
        _uiState.update { currentState ->
            val logs = (listOf(entry) + currentState.activityLogs).take(300)
            currentState.copy(activityLogs = logs)
        }
    }

    fun setSourceUri(uri: Uri, fileName: String) {
        _uiState.update { it.copy(sourceUri = uri, sourceName = fileName, sourceReport = null) }
        addLog(LogLevel.INFO, "Selected Overlay Source Package: $fileName")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ApkInspectorEngine.inspect(getApplication(), uri) }
            }.onSuccess { report ->
                _uiState.update { it.copy(sourceReport = report) }
                addLog(LogLevel.SUCCESS, "Source Overlay Panel analyzed (${report.dexReport.totalClassesCount} classes, ${report.nativeLibReport.totalLibraries} .so libs).")
            }
        }
    }

    fun setTargetUri(uri: Uri, fileName: String) {
        _uiState.update { it.copy(targetUri = uri, targetName = fileName, targetReport = null) }
        addLog(LogLevel.INFO, "Selected Target Destination Package: $fileName")
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                runCatching { ApkInspectorEngine.inspect(getApplication(), uri) }
            }.onSuccess { report ->
                _uiState.update { it.copy(targetReport = report) }
                addLog(LogLevel.SUCCESS, "Target App analyzed: Package '${report.packageName}' | Launcher: ${report.manifestReport.launcherActivity ?: "Auto-detected"}")
            }
        }
    }

    fun setArchitectureMode(arch: ArchitectureMode) {
        _uiState.update { it.copy(injectionConfig = it.injectionConfig.copy(selectedArchitecture = arch)) }
        addLog(LogLevel.INFO, "Architecture Mode: ${if (arch == ArchitectureMode.ARM64_V8A) "64-Bit (arm64-v8a)" else "32-Bit (armeabi-v7a)"}")
    }

    fun setPermissionTag(tag: String) {
        _uiState.update { it.copy(injectionConfig = it.injectionConfig.copy(permissionTag = tag)) }
    }

    fun setServiceTag(tag: String) {
        _uiState.update { it.copy(injectionConfig = it.injectionConfig.copy(serviceTag = tag)) }
    }

    fun setHookLine(line: String) {
        _uiState.update { it.copy(injectionConfig = it.injectionConfig.copy(hookLine = line)) }
    }

    fun setSmaliOverride(path: String) {
        _uiState.update { it.copy(injectionConfig = it.injectionConfig.copy(manualSmaliPathOverride = path)) }
    }

    fun fetchFormatInput1() {
        val defaultTag = "<uses-permission android:name=\"android.permission.SYSTEM_ALERT_WINDOW\" />"
        setPermissionTag(defaultTag)
        addLog(LogLevel.INFO, "Formatted Input Box 1: Window Overlay Permission Tag reset to standard.")
    }

    fun fetchFormatInput2() {
        val defaultService = "<service android:name=\"com.android.support.Launcher\" android:enabled=\"true\" android:exported=\"false\" android:stopWithTask=\"true\" />"
        setServiceTag(defaultService)
        addLog(LogLevel.INFO, "Formatted Input Box 2: Telemetry Service Component Tag reset to standard.")
    }

    fun fetchFormatInput3() {
        val defaultHook = "invoke-static {p0}, Lcom/android/support/Main;->Start(Landroid/content/Context;)V"
        setHookLine(defaultHook)
        addLog(LogLevel.INFO, "Formatted Input Box 3: Initialization Hook Line reset to standard.")
    }

    fun executeInjectionPipeline() {
        val srcUri = _uiState.value.sourceUri
        val tgtUri = _uiState.value.targetUri

        if (srcUri == null || tgtUri == null) {
            addLog(LogLevel.ERROR, "Selection Error: Please select both Source Overlay Package and Target Destination Package first.")
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isInjecting = true,
                    progressPercentage = 0,
                    progressMessage = "Initializing Pipeline…",
                    injectionResult = null
                )
            }

            val result = withContext(Dispatchers.IO) {
                ApkInjectorEngine.executeInjection(
                    context = getApplication(),
                    sourceUri = srcUri,
                    targetUri = tgtUri,
                    config = _uiState.value.injectionConfig,
                    onProgress = { pct, msg ->
                        _uiState.update { it.copy(progressPercentage = pct, progressMessage = msg) }
                    }
                )
            }

            result.logs.forEach { logMsg ->
                val level = when {
                    logMsg.startsWith("ERROR") -> LogLevel.ERROR
                    logMsg.startsWith("CLEANUP") || logMsg.startsWith("SUCCESS") -> LogLevel.SUCCESS
                    logMsg.startsWith("WARNING") -> LogLevel.WARNING
                    else -> LogLevel.INFO
                }
                addLog(level, logMsg)
            }

            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isInjecting = false,
                        progressPercentage = 100,
                        progressMessage = "Injection & Signing Completed!",
                        injectionResult = result
                    )
                }
                addLog(LogLevel.SUCCESS, "Pipeline Completed! Output signed: ${result.outputPath}")
            } else {
                _uiState.update {
                    it.copy(
                        isInjecting = false,
                        progressPercentage = 0,
                        progressMessage = "Injection Pipeline Failed.",
                        injectionResult = result
                    )
                }
                addLog(LogLevel.ERROR, "Injection Pipeline Failed: ${result.errorMessage}")
            }
        }
    }

    fun clearLogs() {
        _uiState.update { it.copy(activityLogs = emptyList()) }
    }
}
