package com.blackcpatcher.data.apk

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.blackcpatcher.domain.model.ApkReport
import com.blackcpatcher.domain.model.NativeLibInfo
import com.blackcpatcher.domain.model.NativeLibReport
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Orchestrates complete APK inspection (metadata, binary manifest, DEX inventory, native libraries, SHA-256).
 */
object ApkInspectorEngine {

    fun inspect(context: Context, uri: Uri): ApkReport {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: (uri.lastPathSegment ?: "selected.apk")

        val tempFile = File.createTempFile("bcp_inspect_", ".apk", context.cacheDir)
        try {
            resolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Failed to open input stream for Uri: $uri")

            require(tempFile.length() > 0) { "Selected APK file is empty." }

            val dexEntries = mutableListOf<Pair<String, ByteArray>>()
            var manifestBytes: ByteArray? = null
            val nativeLibsByAbi = mutableMapOf<String, MutableList<NativeLibInfo>>()
            val allNativeLibPaths = mutableListOf<String>()
            var archiveEntriesCount = 0

            ZipFile(tempFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    archiveEntriesCount++
                    val entryName = entry.name
                    if (entry.isDirectory) continue

                    if (entryName == "AndroidManifest.xml") {
                        zip.getInputStream(entry).use { manifestBytes = it.readBytes() }
                    } else if (entryName.matches(Regex("classes(\\d*)\\.dex"))) {
                        zip.getInputStream(entry).use { dexBytes ->
                            dexEntries.add(entryName to dexBytes.readBytes())
                        }
                    } else if (entryName.startsWith("lib/") && entryName.endsWith(".so")) {
                        val parts = entryName.split('/')
                        if (parts.size >= 3) {
                            val abi = parts[1]
                            val libName = parts.last()
                            val libInfo = NativeLibInfo(
                                name = libName,
                                abi = abi,
                                size = entry.size,
                                path = entryName
                            )
                            nativeLibsByAbi.getOrPut(abi) { mutableListOf() }.add(libInfo)
                            allNativeLibPaths.add(entryName)
                        }
                    }
                }
            }

            val manifestReport = if (manifestBytes != null) {
                BinaryAxmlParser.parse(manifestBytes.inputStream())
            } else {
                error("AndroidManifest.xml not found in APK archive.")
            }

            val dexReport = DexInspector.inspectDexEntries(
                dexEntries.sortedWith(compareBy({ if (it.first == "classes.dex") 0 else it.first.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 999 }))
            )

            val duplicateLibPaths = allNativeLibPaths.groupBy { it }.filter { it.value.size > 1 }.keys.toList()

            val supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64").filter { nativeLibsByAbi.containsKey(it) }

            val abiWarnings = mutableListOf<String>()
            if (nativeLibsByAbi.isNotEmpty()) {
                if ("arm64-v8a" !in supportedAbis && "armeabi-v7a" in supportedAbis) {
                    abiWarnings.add("32-bit armeabi-v7a libraries detected without 64-bit arm64-v8a support.")
                }
                if (supportedAbis.size > 1) {
                    abiWarnings.add("Multi-ABI APK detected (${supportedAbis.joinToString(", ")}).")
                }
            }

            val nativeLibReport = NativeLibReport(
                librariesByAbi = nativeLibsByAbi.mapValues { it.value.toList() },
                supportedAbis = supportedAbis,
                totalLibraries = nativeLibsByAbi.values.sumOf { it.size },
                duplicatePaths = duplicateLibPaths,
                abiWarnings = abiWarnings
            )

            val sha256 = calculateSha256(tempFile)

            return ApkReport(
                uri = uri,
                fileName = name,
                fileSize = tempFile.length(),
                packageName = manifestReport.packageName,
                versionName = manifestReport.versionName,
                versionCode = manifestReport.versionCode,
                minSdk = manifestReport.minSdk,
                targetSdk = manifestReport.targetSdk,
                appLabel = manifestReport.appLabel.ifBlank { name.substringBeforeLast('.') },
                sha256 = sha256,
                dexReport = dexReport,
                manifestReport = manifestReport,
                nativeLibReport = nativeLibReport,
                archiveEntries = archiveEntriesCount
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
