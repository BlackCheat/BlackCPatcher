package com.blackcpatcher.analysis

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Read-only APK inspection. It never changes the selected APK. */
object ApkInspector {
    data class Report(
        val fileName: String,
        val packageName: String,
        val versionName: String,
        val versionCode: Long,
        val minSdk: Int?,
        val targetSdk: Int?,
        val sha256: String,
        val dexFiles: List<String>,
        val nativeLibraries: Map<String, List<String>>,
        val permissions: List<String>,
        val activities: List<String>,
        val launcherActivities: List<String>,
        val services: List<String>,
        val receivers: List<String>,
        val providers: List<String>,
        val archiveEntries: Int
    )

    fun inspect(context: Context, uri: Uri): Report {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: "selected.apk"
        val temp = File.createTempFile("blackcpatcher_", ".apk", context.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                ?: error("Cannot open selected APK")
            require(temp.length() > 0) { "Selected file is empty" }
            val requestedFlags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS or PackageManager.GET_PERMISSIONS
            @Suppress("DEPRECATION")
            val parsed = if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageArchiveInfo(temp.absolutePath, PackageManager.PackageInfoFlags.of(requestedFlags.toLong()))
            } else {
                context.packageManager.getPackageArchiveInfo(temp.absolutePath, requestedFlags)
            }
            val info: PackageInfo = parsed ?: error("Android could not parse this APK manifest. The file may be invalid or unsupported.")
            val app = info.applicationInfo
            if (app != null) { app.sourceDir = temp.absolutePath; app.publicSourceDir = temp.absolutePath }
            val dex = mutableListOf<String>()
            val libs = linkedMapOf<String, MutableList<String>>()
            var entryCount = 0
            ZipFile(temp).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement(); entryCount++
                    if (entry.isDirectory) continue
                    val path = entry.name
                    if (path.matches(Regex("classes(\\d*)\\.dex"))) dex += path
                    if (path.startsWith("lib/") && path.endsWith(".so")) {
                        val parts = path.split('/')
                        if (parts.size >= 3) libs.getOrPut(parts[1]) { mutableListOf() }.add(parts.drop(2).joinToString("/"))
                    }
                }
            }
            val activities = info.activities.orEmpty().map { it.name }.distinct().sorted()
            // PackageManager archive metadata does not expose manifest intent-filter contents reliably.
            val launchers = emptyList<String>()
            @Suppress("DEPRECATION")
            val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
            return Report(
                fileName = name, packageName = info.packageName, versionName = info.versionName ?: "(not set)",
                versionCode = code, minSdk = app?.minSdkVersion, targetSdk = app?.targetSdkVersion,
                sha256 = sha256(temp), dexFiles = dex.sortedWith(compareBy({ if (it == "classes.dex") 1 else it.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 0 })),
                nativeLibraries = libs.mapValues { it.value.distinct().sorted() },
                permissions = info.requestedPermissions.orEmpty().toList().sorted(),
                activities = activities, launcherActivities = launchers,
                services = info.services.orEmpty().map { it.name }.distinct().sorted(),
                receivers = info.receivers.orEmpty().map { it.name }.distinct().sorted(),
                providers = info.providers.orEmpty().map { it.name }.distinct().sorted(), archiveEntries = entryCount
            )
        } finally { temp.delete() }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) { val n = input.read(buffer); if (n < 0) break; digest.update(buffer, 0, n) }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
