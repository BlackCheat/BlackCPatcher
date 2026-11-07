package com.blackcpatcher.data.storage

import android.content.Context
import android.net.Uri
import com.blackcpatcher.domain.model.ValidationReport
import java.io.File

/**
 * Handles Storage Access Framework export, analysis/build history management,
 * validation report export, and cache cleanup.
 */
object ExportManager {

    data class HistoryRecord(
        val timestamp: Long = System.currentTimeMillis(),
        val title: String,
        val details: String,
        val type: String // "ANALYSIS", "VALIDATION", "BUILD"
    )

    private val history = mutableListOf<HistoryRecord>()

    fun addHistory(title: String, details: String, type: String) {
        history.add(0, HistoryRecord(title = title, details = details, type = type))
        if (history.size > 50) history.removeAt(history.size - 1)
    }

    fun getHistory(): List<HistoryRecord> = history.toList()

    fun exportFileToUri(context: Context, sourceFile: File, destinationUri: Uri): Boolean {
        return try {
            context.contentResolver.openOutputStream(destinationUri, "w")?.use { output ->
                sourceFile.inputStream().use { input -> input.copyTo(output) }
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun generateValidationReportText(report: ValidationReport): String {
        return buildString {
            appendLine("==================================================")
            appendLine("BLACKCPATCHER PRE-BUILD VALIDATION REPORT")
            appendLine("Timestamp: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(report.timestamp))}")
            appendLine("Overall Status: ${report.overallStatus}")
            appendLine("Summary: ${report.passCount} PASS, ${report.warningCount} WARNING, ${report.failCount} FAIL")
            appendLine("==================================================")
            appendLine()
            report.items.forEachIndexed { idx, item ->
                appendLine("[${idx + 1}] [${item.severity}] ${item.category} — ${item.title}")
                appendLine("    ${item.message}")
                appendLine()
            }
            appendLine("==================================================")
        }
    }

    fun clearTemporaryCache(context: Context): Long {
        var bytesDeleted = 0L
        context.cacheDir.walkTopDown().forEach { file ->
            if (file.isFile && (file.name.startsWith("bcp_") || file.name.startsWith("blackcpatcher_"))) {
                bytesDeleted += file.length()
                file.delete()
            }
        }
        return bytesDeleted
    }
}
