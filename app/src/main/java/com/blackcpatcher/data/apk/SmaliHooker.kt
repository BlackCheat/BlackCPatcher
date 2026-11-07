package com.blackcpatcher.data.apk

import java.io.File

/**
 * Activity Lifecycle Method Hooking Engine.
 * Identifies the target application's primary launchable activity, locates its lifecycle methods,
 * and programmatically inserts the telemetry/overlay initialization hook line.
 */
object SmaliHooker {

    fun injectHook(
        extractedTargetDir: File,
        detectedLauncherActivity: String?,
        manualSmaliPathOverride: String,
        hookLine: String,
        log: (String) -> Unit
    ): Boolean {
        val cleanHookLine = hookLine.trim()
        if (cleanHookLine.isEmpty()) {
            log("No initialization hook line provided. Skipping smali lifecycle hooking.")
            return true
        }

        // Determine target smali file path
        val targetSmaliRelPath = if (manualSmaliPathOverride.isNotBlank()) {
            manualSmaliPathOverride.trim().removePrefix("/")
        } else if (!detectedLauncherActivity.isNullOrBlank()) {
            detectedLauncherActivity.replace('.', '/') + ".smali"
        } else {
            log("WARNING: Could not auto-detect main activity launcher smali path.")
            return false
        }

        log("Targeting launcher lifecycle hook file: $targetSmaliRelPath")

        // Search in extracted files or smali subdirectories (like smali/, smali_classes2/, etc.)
        var targetSmaliFile: File? = null
        extractedTargetDir.walkTopDown().forEach { file ->
            if (file.isFile && file.path.replace('\\', '/').endsWith(targetSmaliRelPath)) {
                targetSmaliFile = file
            }
        }

        if (targetSmaliFile == null || !targetSmaliFile!!.exists()) {
            log("ERROR: Target smali file '$targetSmaliRelPath' not found in unpacked directory.")
            log("Please ensure the APK is decompiled (e.g., via Baksmali) so .smali files exist.")
            return false
        }

        val lines = targetSmaliFile!!.readLines().toMutableList()
        var injected = false
        var inOnCreate = false

        for (i in lines.indices) {
            val line = lines[i].trim()

            // Handle variations in modifiers: public, protected, constructor, etc.
            if (line.startsWith(".method") && line.contains("onCreate(Landroid/os/Bundle;)V")) {
                inOnCreate = true
                log("Located onCreate method at line ${i + 1}")
                continue
            }

            if (inOnCreate) {
                // Safely adjust the .locals count by +1 to accommodate register usage
                if (line.startsWith(".locals ")) {
                    try {
                        val currentLocals = line.substringAfter(".locals ").trim().toInt()
                        lines[i] = "    .locals ${currentLocals + 1}"
                        log("Safely adjusted .locals count from $currentLocals to ${currentLocals + 1}")
                    } catch (_: Exception) {
                        log("WARNING: Failed to parse .locals count.")
                    }
                }

                // Locate the super call instruction
                if ((line.startsWith("invoke-super") || line.contains("super.onCreate") || line.contains("->onCreate(")) && line.contains("Landroid/os/Bundle;)V")) {
                    log("Located super.onCreate call at line ${i + 1}")
                    // Insert the exact hook line immediately on the next line
                    lines.add(i + 1, "    $cleanHookLine")
                    injected = true
                    log("SUCCESS: Injected initialization hook exactly after super call.")
                    break
                }

                // Fallback to inserting before .end method if no standard super call is found
                if (line.startsWith(".end method")) {
                    log("WARNING: super.onCreate call not found. Injecting immediately before .end method.")
                    lines.add(i, "    $cleanHookLine")
                    injected = true
                    break
                }
            }
        }

        if (injected) {
            targetSmaliFile!!.writeText(lines.joinToString("\n"), Charsets.UTF_8)
            log("Smali modification saved successfully to ${targetSmaliFile!!.absolutePath}")
            return true
        }

        log("ERROR: Could not find onCreate method inside target smali.")
        return false
    }
}
