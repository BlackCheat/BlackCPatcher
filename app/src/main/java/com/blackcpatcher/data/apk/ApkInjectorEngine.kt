package com.blackcpatcher.data.apk

import android.content.Context
import android.net.Uri
import com.blackcpatcher.domain.model.ArchitectureMode
import com.blackcpatcher.domain.model.InjectionConfig
import com.blackcpatcher.domain.model.InjectionResult
import java.io.File
import java.util.zip.ZipFile

/**
 * Orchestrates the strict background automation pipeline:
 * 1. Native Library Architecture Filter & Cleanup (extract target ABI .so libs, delete opposing ABI dir).
 * 2. Sequential Multi-Dex Indexing & Renumbering (classes(N+1).dex).
 * 3. Configuration Manifest Auto-Parsing & Injection (Permission & Telemetry Service tags).
 * 4. Activity Lifecycle Method Hooking Engine (onCreate hook line insertion).
 * 5. Re-packaging & Cryptographic Signing Engine -> /storage/emulated/0/BlackCPatcher/TargetApp_Patched_Signed.apk
 */
object ApkInjectorEngine {

    fun executeInjection(
        context: Context,
        sourceUri: Uri,
        targetUri: Uri,
        config: InjectionConfig,
        onProgress: (Int, String) -> Unit
    ): InjectionResult {
        val startTime = System.currentTimeMillis()
        val logs = mutableListOf<String>()

        fun log(msg: String) {
            logs.add(msg)
        }

        val workDir = File(context.cacheDir, "inject_work_${startTime}").apply { mkdirs() }

        try {
            onProgress(5, "Copying source and target APK archives…")
            log("Initializing Injection Pipeline…")

            val sourceCopy = File(workDir, "source_panel.apk")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                sourceCopy.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Failed to read Source Panel APK.")

            val targetCopy = File(workDir, "target_app.apk")
            context.contentResolver.openInputStream(targetUri)?.use { input ->
                targetCopy.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Failed to read Target App APK.")

            // Unpack both APKs
            onProgress(15, "Unpacking Target APK structure…")
            val extractedTargetDir = File(workDir, "extracted_target").apply { mkdirs() }
            unzip(targetCopy, extractedTargetDir)

            onProgress(25, "Unpacking Source Overlay Panel structure…")
            val extractedSourceDir = File(workDir, "extracted_source").apply { mkdirs() }
            unzip(sourceCopy, extractedSourceDir)

            // Step 1: Native Library Architecture Filter & Cleanup
            val selectedArchName = if (config.selectedArchitecture == ArchitectureMode.ARM64_V8A) "arm64-v8a" else "armeabi-v7a"
            val opposingArchName = if (config.selectedArchitecture == ArchitectureMode.ARM64_V8A) "armeabi-v7a" else "arm64-v8a"

            onProgress(35, "Filtering native libraries for $selectedArchName architecture…")
            log("Architecture Selected: $selectedArchName (Opposing ABI: $opposingArchName)")

            val sourceLibDir = File(extractedSourceDir, "lib")
            val targetLibDir = File(extractedTargetDir, "lib").apply { mkdirs() }

            // Copy selected ABI .so libraries from Source into Target
            val sourceAbiDir = File(sourceLibDir, selectedArchName)
            if (sourceAbiDir.exists() && sourceAbiDir.isDirectory) {
                val targetAbiDir = File(targetLibDir, selectedArchName).apply { mkdirs() }
                sourceAbiDir.walkTopDown().forEach { file ->
                    if (file.isFile && file.extension == "so") {
                        val destSo = File(targetAbiDir, file.name)
                        file.copyTo(destSo, overwrite = true)
                        log("Copied telemetry .so lib: lib/$selectedArchName/${file.name}")
                    }
                }
            }

            // AUTOMATICALLY DELETE the opposing architecture directory from Destination APK
            val opposingTargetAbiDir = File(targetLibDir, opposingArchName)
            if (opposingTargetAbiDir.exists()) {
                opposingTargetAbiDir.deleteRecursively()
                log("CLEANUP: Automatically deleted opposing architecture directory 'lib/$opposingArchName/' to prevent runtime mismatches.")
            }

            // Step 2: Sequential Multi-Dex Indexing & Renumbering
            onProgress(50, "Renumbering and merging sequential multi-dex streams…")
            DexManager.renumberAndMergeDex(extractedSourceDir, extractedTargetDir) { log(it) }

            // Step 3: Original manifest parser to detect launcher
            val targetManifestFile = File(extractedTargetDir, "AndroidManifest.xml")
            val originalManifestBytes = targetManifestFile.readBytes()
            val manifestReport = try {
                BinaryAxmlParser.parse(originalManifestBytes.inputStream())
            } catch (_: Exception) { null }
            val launcherActivity = manifestReport?.launcherActivity
            log("Detected Launcher: ${launcherActivity ?: "Unknown"}")

            // Step 4: Manifest (ab binary-safe)
            onProgress(65, "Patching AndroidManifest.xml (binary)…")
            val manifestPatched = ManifestPatcher.patchManifest(
                extractedTargetDir = extractedTargetDir,
                permissionTag = config.permissionTag,
                serviceTag = config.serviceTag,
                log = { log(it) }
            )
            if (!manifestPatched) {
                error("Manifest patching failed. Packaging stopped to prevent an invalid APK.")
            }

            // Step 5: smali hook via real decompile/edit/assemble
            onProgress(80, "Hooking launcher onCreate…")
            val targetClass = config.manualSmaliPathOverride.ifBlank { launcherActivity ?: "" }
            if (targetClass.isNotBlank()) {
                val dexFile = DexSmaliEngine.findDexContainingClass(extractedTargetDir, targetClass)
                if (dexFile != null) {
                    val hookApplied = DexSmaliEngine.hookLauncherDex(dexFile, targetClass, config.hookLine, workDir, ::log)
                    if (!hookApplied) {
                        error("Lifecycle hook stage failed. Packaging stopped.")
                    }
                } else {
                    error("ERROR: $targetClass kisi bhi dex me nahi mila")
                }
            } else {
                log("WARNING: Launcher unknown, manual .smali path required")
                error("Cannot hook unknown launcher.")
            }

            // Step 6: Re-packaging & Cryptographic Signing Engine
            onProgress(90, "Packaging and signing final APK output archive…")
            val finalSignedApk = ApkSignerUtil.archiveAndSign(
                context = context,
                extractedDir = extractedTargetDir,
                log = { log(it) }
            )

            val duration = System.currentTimeMillis() - startTime
            onProgress(100, "Injection & Signing Pipeline completed successfully!")
            log("Pipeline Duration: ${duration} ms | Output File: ${finalSignedApk.name} (${finalSignedApk.length() / (1024 * 1024)} MB)")

            return InjectionResult(
                isSuccess = true,
                outputPath = finalSignedApk.absolutePath,
                outputSize = finalSignedApk.length(),
                buildDurationMs = duration,
                outputUri = Uri.fromFile(finalSignedApk),
                errorMessage = null,
                logs = logs
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            log("ERROR: Injection Pipeline failed: ${e.message}")
            return InjectionResult(
                isSuccess = false,
                outputPath = null,
                outputSize = null,
                buildDurationMs = duration,
                errorMessage = e.message ?: "Unknown injection failure",
                logs = logs
            )
        } finally {
            workDir.deleteRecursively()
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
}
