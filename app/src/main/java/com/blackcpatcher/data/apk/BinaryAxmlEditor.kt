package com.blackcpatcher.data.apk

import com.blackcpatcher.domain.model.ManifestReport
import com.blackcpatcher.domain.model.PermissionDetail

/**
 * Controlled AndroidManifest editor for authorized modifications.
 * Allows adding/removing permissions and previewing manifest changes without corrupting XML syntax.
 */
object BinaryAxmlEditor {

    fun previewPermissionChange(
        report: ManifestReport,
        addPermissions: List<String>,
        removePermissions: List<String>
    ): ManifestReport {
        val currentNames = report.permissions.map { it.name }.toMutableSet()
        currentNames.addAll(addPermissions)
        currentNames.removeAll(removePermissions.toSet())

        val updatedPermissions = currentNames.sorted().map { name ->
            PermissionDetail(name = name, isDangerous = name.contains("PERMISSION"))
        }

        val updatedXml = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"${report.packageName}\" android:versionCode=\"${report.versionCode}\" android:versionName=\"${report.versionName}\">")
            if (report.minSdk != null || report.targetSdk != null) {
                appendLine("    <uses-sdk android:minSdkVersion=\"${report.minSdk ?: 1}\" android:targetSdkVersion=\"${report.targetSdk ?: 35}\" />")
            }
            updatedPermissions.forEach { p ->
                appendLine("    <uses-permission android:name=\"${p.name}\" />")
            }
            appendLine("    <application android:label=\"${report.appLabel}\">")
            report.activities.forEach { a ->
                appendLine("        <activity android:name=\"${a.name}\" android:exported=\"${a.isExported}\" />")
            }
            report.services.forEach { s ->
                appendLine("        <service android:name=\"${s.name}\" android:exported=\"${s.isExported}\" />")
            }
            report.receivers.forEach { r ->
                appendLine("        <receiver android:name=\"${r.name}\" android:exported=\"${r.isExported}\" />")
            }
            report.providers.forEach { pr ->
                appendLine("        <provider android:name=\"${pr.name}\" android:exported=\"${pr.isExported}\" />")
            }
            appendLine("    </application>")
            appendLine("</manifest>")
        }

        return report.copy(
            permissions = updatedPermissions,
            rawXml = updatedXml
        )
    }
}
