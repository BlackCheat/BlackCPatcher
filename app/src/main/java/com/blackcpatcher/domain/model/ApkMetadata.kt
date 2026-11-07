package com.blackcpatcher.domain.model

import android.net.Uri

data class ApkReport(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val appLabel: String,
    val sha256: String,
    val dexReport: DexReport,
    val manifestReport: ManifestReport,
    val nativeLibReport: NativeLibReport,
    val archiveEntries: Int
)

data class DexReport(
    val dexFiles: List<DexFileInfo>,
    val totalClassesCount: Int,
    val totalMethodCount: Int,
    val totalFieldCount: Int,
    val allClasses: List<DexClassInfo>,
    val duplicateClasses: List<String>,
    val packageHierarchy: List<String>
)

data class DexFileInfo(
    val fileName: String,
    val size: Long,
    val classCount: Int,
    val methodCount: Int,
    val fieldCount: Int
)

data class DexClassInfo(
    val className: String,
    val packageName: String,
    val simpleName: String,
    val dexFileName: String,
    val methodCount: Int,
    val fieldCount: Int
)

data class ManifestReport(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int?,
    val targetSdk: Int?,
    val appLabel: String,
    val permissions: List<PermissionDetail>,
    val activities: List<ComponentDetail>,
    val services: List<ComponentDetail>,
    val receivers: List<ComponentDetail>,
    val providers: List<ComponentDetail>,
    val launcherActivity: String?,
    val duplicateComponents: List<String>,
    val rawXml: String
)

data class PermissionDetail(
    val name: String,
    val isDangerous: Boolean = false
)

data class ComponentDetail(
    val name: String,
    val simpleName: String,
    val isExported: Boolean,
    val intentFilters: List<IntentFilterDetail> = emptyList()
)

data class IntentFilterDetail(
    val actions: List<String>,
    val categories: List<String>
)

data class NativeLibReport(
    val librariesByAbi: Map<String, List<NativeLibInfo>>,
    val supportedAbis: List<String>,
    val totalLibraries: Int,
    val duplicatePaths: List<String>,
    val abiWarnings: List<String>
)

data class NativeLibInfo(
    val name: String,
    val abi: String,
    val size: Long,
    val path: String
)

enum class ValidationSeverity {
    PASS, WARNING, FAIL
}

data class ValidationItem(
    val category: String,
    val title: String,
    val message: String,
    val severity: ValidationSeverity
)

data class ValidationReport(
    val timestamp: Long,
    val overallStatus: ValidationSeverity,
    val items: List<ValidationItem>,
    val passCount: Int,
    val warningCount: Int,
    val failCount: Int
)

data class SigningConfig(
    val useAutoDebugKey: Boolean = true,
    val keystorePath: String = "",
    val keystorePassword: CharArray = CharArray(0),
    val keyAlias: String = "",
    val keyPassword: CharArray = CharArray(0),
    val enableV1: Boolean = true,
    val enableV2: Boolean = true,
    val enableV3: Boolean = true
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SigningConfig
        if (useAutoDebugKey != other.useAutoDebugKey) return false
        if (keystorePath != other.keystorePath) return false
        if (!keystorePassword.contentEquals(other.keystorePassword)) return false
        if (keyAlias != other.keyAlias) return false
        if (!keyPassword.contentEquals(other.keyPassword)) return false
        if (enableV1 != other.enableV1) return false
        if (enableV2 != other.enableV2) return false
        if (enableV3 != other.enableV3) return false
        return true
    }

    override fun hashCode(): Int {
        var result = useAutoDebugKey.hashCode()
        result = 31 * result + keystorePath.hashCode()
        result = 31 * result + keystorePassword.contentHashCode()
        result = 31 * result + keyAlias.hashCode()
        result = 31 * result + keyPassword.contentHashCode()
        result = 31 * result + enableV1.hashCode()
        result = 31 * result + enableV2.hashCode()
        result = 31 * result + enableV3.hashCode()
        return result
    }
}

data class BuildResult(
    val isSuccess: Boolean,
    val outputPath: String?,
    val outputSize: Long?,
    val buildDurationMs: Long,
    val v1Verified: Boolean,
    val v2Verified: Boolean,
    val v3Verified: Boolean,
    val errorMessage: String? = null,
    val logOutput: List<String> = emptyList()
)

data class ActivityLogEntry(
    val timestamp: Long = System.currentTimeMillis(),
    val level: LogLevel = LogLevel.INFO,
    val message: String
)

enum class LogLevel {
    INFO, SUCCESS, WARNING, ERROR
}

enum class ArchitectureMode {
    ARM64_V8A, ARMEABI_V7A
}

data class InjectionConfig(
    val selectedArchitecture: ArchitectureMode = ArchitectureMode.ARM64_V8A,
    val permissionTag: String = "<uses-permission android:name=\"android.permission.SYSTEM_ALERT_WINDOW\" />",
    val serviceTag: String = "<service android:name=\"com.android.support.Launcher\" android:enabled=\"true\" android:exported=\"false\" android:stopWithTask=\"true\" />",
    val hookLine: String = "invoke-static {p0}, Lcom/android/support/Main;->Start(Landroid/content/Context;)V",
    val manualSmaliPathOverride: String = ""
)

data class InjectionResult(
    val isSuccess: Boolean,
    val outputPath: String?,
    val outputSize: Long?,
    val buildDurationMs: Long,
    val outputUri: Uri? = null,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList()
)
