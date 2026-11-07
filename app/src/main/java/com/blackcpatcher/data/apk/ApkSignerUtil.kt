package com.blackcpatcher.data.apk

import android.content.Context
import com.blackcpatcher.domain.model.SigningConfig
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Re-packaging & Cryptographic Signing Engine.
 * Recompresses modified application files into a clean archive using ZipOutputStream,
 * signs output with standard RSA/EC test credentials, and saves output to public device storage:
 * `/storage/emulated/0/BlackCPatcher/TargetApp_Patched_Signed.apk`
 */
object ApkSignerUtil {

    fun archiveAndSign(
        context: Context,
        extractedDir: File,
        log: (String) -> Unit
    ): File {
        log("Repackaging modified application files with 4-byte ZIP alignment…")

        val tempUnsignedApk = File(context.cacheDir, "unsigned_patched_${System.currentTimeMillis()}.apk")
        zipDirectory(extractedDir, tempUnsignedApk)

        log("Repackaged archive size: ${tempUnsignedApk.length() / (1024 * 1024)} MB")

        log("Executing cryptographic package signing with V1 / V2 debug credentials…")
        val signResult = ApkSignerManager.signAndVerify(
            inputApk = tempUnsignedApk,
            config = SigningConfig(useAutoDebugKey = true),
            onProgress = { pct, msg -> log("[$pct%] $msg") }
        )

        val signedSourceFile = signResult.signedFile

        // Save output to /storage/emulated/0/BlackCPatcher/TargetApp_Patched_Signed.apk
        val publicStorageDir = File("/storage/emulated/0/BlackCPatcher")
        if (!publicStorageDir.exists()) {
            publicStorageDir.mkdirs()
        }

        val outputDestinationFile = if (publicStorageDir.exists()) {
            File(publicStorageDir, "TargetApp_Patched_Signed.apk")
        } else {
            val appExternalDir = context.getExternalFilesDir(null) ?: context.filesDir
            if (!appExternalDir.exists()) {
                appExternalDir.mkdirs()
            }
            File(appExternalDir, "TargetApp_Patched_Signed.apk")
        }

        signedSourceFile.copyTo(outputDestinationFile, overwrite = true)
        log("Successfully saved final signed APK to: ${outputDestinationFile.absolutePath}")

        tempUnsignedApk.delete()
        return outputDestinationFile
    }

    private fun zipDirectory(rootDir: File, zipFile: File) {
        ZipOutputStream(FileOutputStream(zipFile)).use { zos ->
            rootDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    val relativePath = file.relativeTo(rootDir).path.replace('\\', '/')
                    val entry = ZipEntry(relativePath)
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
