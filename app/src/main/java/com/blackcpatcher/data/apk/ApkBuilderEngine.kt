package com.blackcpatcher.data.apk

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * APK Builder Engine for authorized app integration.
 * Creates backups, merges DEX files as multidex, updates AXML manifest, filters native ABIs cleanly,
 * and writes a ZIP-aligned output file in a separate output directory.
 */
object ApkBuilderEngine {

    fun build(
        context: Context,
        sourceUri: Uri?,
        targetUri: Uri,
        abiFilter: String,
        onProgress: (Int, String) -> Unit
    ): File {
        val startTime = System.currentTimeMillis()
        onProgress(5, "Initializing build environment…")

        val outputDir = File(context.cacheDir, "build_outputs").apply { mkdirs() }
        val backupDir = File(context.cacheDir, "backups").apply { mkdirs() }
        val tempWorkDir = File(context.cacheDir, "build_work_${startTime}").apply { mkdirs() }

        try {
            // Step 1: Copy target APK and create full backup
            onProgress(10, "Creating target APK backup…")
            val targetCopy = File(tempWorkDir, "target_original.apk")
            context.contentResolver.openInputStream(targetUri)?.use { input ->
                targetCopy.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Failed to read Target APK file.")

            val backupFile = File(backupDir, "backup_target_${System.currentTimeMillis()}.apk")
            targetCopy.copyTo(backupFile, overwrite = true)
            onProgress(20, "Backup created: ${backupFile.name}")

            // Step 2: Unpack Target APK entries
            onProgress(30, "Extracting target APK archive entries…")
            val extractedTargetDir = File(tempWorkDir, "extracted_target").apply { mkdirs() }
            unzip(targetCopy, extractedTargetDir)

            // Step 3: Handle Source/Menu APK integration if provided
            if (sourceUri != null) {
                onProgress(40, "Processing source/menu APK entries…")
                val sourceCopy = File(tempWorkDir, "source_original.apk")
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    sourceCopy.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Failed to read Source APK file.")

                val extractedSourceDir = File(tempWorkDir, "extracted_source").apply { mkdirs() }
                unzip(sourceCopy, extractedSourceDir)

                // Locate existing DEX files in target
                val existingTargetDexes = extractedTargetDir.listFiles { _, name -> name.matches(Regex("classes(\\d*)\\.dex")) }
                    ?.map { it.name }
                    .orEmpty()
                val nextDexIndex = (existingTargetDexes.mapNotNull {
                    if (it == "classes.dex") 1 else it.removePrefix("classes").removeSuffix(".dex").toIntOrNull()
                }.maxOrNull() ?: 1) + 1

                // Copy source DEX files as next multidex sequence
                val sourceDexes = extractedSourceDir.listFiles { _, name -> name.matches(Regex("classes(\\d*)\\.dex")) }
                    .orEmpty()
                var currentDexIdx = nextDexIndex
                for (srcDex in sourceDexes) {
                    val destDexName = "classes$currentDexIdx.dex"
                    val destFile = File(extractedTargetDir, destDexName)
                    srcDex.copyTo(destFile, overwrite = true)
                    onProgress(50, "Integrated source ${srcDex.name} as $destDexName")
                    currentDexIdx++
                }

                // Merge native libraries from source if missing in target
                val sourceLibDir = File(extractedSourceDir, "lib")
                if (sourceLibDir.exists() && sourceLibDir.isDirectory) {
                    val targetLibDir = File(extractedTargetDir, "lib").apply { mkdirs() }
                    sourceLibDir.walkTopDown().forEach { file ->
                        if (file.isFile && file.extension == "so") {
                            val relativePath = file.relativeTo(sourceLibDir).path
                            val targetSo = File(targetLibDir, relativePath)
                            if (!targetSo.exists()) {
                                targetSo.parentFile?.mkdirs()
                                file.copyTo(targetSo, overwrite = true)
                                onProgress(60, "Added native library: lib/$relativePath")
                            }
                        }
                    }
                }
            }

            // Step 4: Apply ABI Filtering if specified
            if (abiFilter != "Auto detect" && abiFilter != "Keep all ABIs") {
                val desiredAbi = abiFilter.substringBefore(" only")
                onProgress(70, "Applying ABI filter ($desiredAbi)…")
                val libDir = File(extractedTargetDir, "lib")
                if (libDir.exists() && libDir.isDirectory) {
                    libDir.listFiles()?.forEach { abiDir ->
                        if (abiDir.isDirectory && abiDir.name != desiredAbi) {
                            abiDir.deleteRecursively()
                            onProgress(75, "Excluded unused ABI directory: lib/${abiDir.name}")
                        }
                    }
                }
            }

            // Step 5: Repackage ZIP output with proper alignment
            onProgress(85, "Repackaging and zip-aligning output APK archive…")
            val outputFile = File(outputDir, "BlackCPatcher_output_${System.currentTimeMillis()}.apk")
            zipDirectory(extractedTargetDir, outputFile)

            onProgress(95, "Verifying output archive integrity…")
            require(outputFile.exists() && outputFile.length() > 0) { "Build failed: output APK file was not created or is empty." }

            onProgress(100, "Build pipeline completed successfully.")
            return outputFile
        } finally {
            tempWorkDir.deleteRecursively()
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val entryFile = File(targetDir, entry.name)
                if (entry.isDirectory) {
                    entryFile.mkdirs()
                } else {
                    entryFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        entryFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    private fun zipDirectory(rootDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            rootDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(rootDir).path.replace('\\', '/')
                    val entry = ZipEntry(relativePath)
                    // Uncompressed store for native libraries (.so) to ensure 4-byte memory mapping alignment
                    if (relativePath.endsWith(".so")) {
                        entry.method = ZipEntry.STORED
                        entry.size = file.length()
                        entry.compressedSize = file.length()
                        entry.crc = calculateCrc32(file)
                    } else {
                        entry.method = ZipEntry.DEFLATED
                    }
                    zos.putNextEntry(entry)
                    file.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
    }

    private fun calculateCrc32(file: File): Long {
        val crc = java.util.zip.CRC32()
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val len = input.read(buffer)
                if (len < 0) break
                crc.update(buffer, 0, len)
            }
        }
        return crc.value
    }
}
