package com.blackcpatcher.data.apk

import android.content.Context
import com.blackcpatcher.domain.model.ApkReport
import com.blackcpatcher.domain.model.SigningConfig
import com.blackcpatcher.domain.model.ValidationItem
import com.blackcpatcher.domain.model.ValidationReport
import com.blackcpatcher.domain.model.ValidationSeverity

/**
 * Pre-build validation engine checking archive integrity, manifest validity, duplicate classes,
 * ABI compatibility, signing setup, and storage space.
 */
object ValidationEngine {

    fun validate(
        context: Context,
        sourceReport: ApkReport?,
        targetReport: ApkReport?,
        signingConfig: SigningConfig,
        selectedAbiFilter: String
    ): ValidationReport {
        val items = mutableListOf<ValidationItem>()

        // 1. Target APK Integrity Check
        if (targetReport == null) {
            items.add(
                ValidationItem(
                    category = "Archive Integrity",
                    title = "Target APK missing",
                    message = "No Target APK selected. Please select a target APK to proceed.",
                    severity = ValidationSeverity.FAIL
                )
            )
        } else {
            if (targetReport.fileSize <= 0) {
                items.add(
                    ValidationItem(
                        category = "Archive Integrity",
                        title = "Target APK Empty",
                        message = "Target APK file size is 0 bytes.",
                        severity = ValidationSeverity.FAIL
                    )
                )
            } else {
                items.add(
                    ValidationItem(
                        category = "Archive Integrity",
                        title = "Target APK Valid",
                        message = "Target APK '${targetReport.fileName}' parsed successfully (${targetReport.fileSize / 1024} KB, ${targetReport.archiveEntries} entries).",
                        severity = ValidationSeverity.PASS
                    )
                )
            }
        }

        // 2. Source APK Check (Optional in automatic/manual inspect)
        if (sourceReport != null) {
            items.add(
                ValidationItem(
                    category = "Source Integration",
                    title = "Source APK Provided",
                    message = "Source/Menu APK '${sourceReport.fileName}' loaded (${sourceReport.fileSize / 1024} KB).",
                    severity = ValidationSeverity.PASS
                )
            )

            // Package & SDK Incompatibility Check
            if (targetReport != null) {
                if (sourceReport.targetSdk != null && targetReport.minSdk != null && sourceReport.targetSdk < targetReport.minSdk) {
                    items.add(
                        ValidationItem(
                            category = "Compatibility",
                            title = "SDK Target Mismatch",
                            message = "Source target SDK (${sourceReport.targetSdk}) is lower than Target min SDK (${targetReport.minSdk}).",
                            severity = ValidationSeverity.WARNING
                        )
                    )
                }

                // Check Class Collisions
                val sourceClasses = sourceReport.dexReport.allClasses.map { it.className }.toSet()
                val targetClasses = targetReport.dexReport.allClasses.map { it.className }.toSet()
                val collidingClasses = sourceClasses.intersect(targetClasses)
                if (collidingClasses.isNotEmpty()) {
                    items.add(
                        ValidationItem(
                            category = "DEX Inventory",
                            title = "Duplicate Classes Detected",
                            message = "${collidingClasses.size} duplicate class definitions found between Source and Target APKs (e.g., ${collidingClasses.take(3).joinToString()}). Re-indexing will separate multidex streams.",
                            severity = ValidationSeverity.WARNING
                        )
                    )
                } else {
                    items.add(
                        ValidationItem(
                            category = "DEX Inventory",
                            title = "No Class Collisions",
                            message = "Source and Target class namespaces are disjoint.",
                            severity = ValidationSeverity.PASS
                        )
                    )
                }
            }
        }

        // 3. Manifest Validity Check
        if (targetReport != null) {
            val manifest = targetReport.manifestReport
            if (manifest.packageName.isBlank() || manifest.packageName == "unknown.package") {
                items.add(
                    ValidationItem(
                        category = "Manifest Validity",
                        title = "Invalid Package Name",
                        message = "Target AndroidManifest.xml has an invalid or missing package name.",
                        severity = ValidationSeverity.FAIL
                    )
                )
            } else {
                items.add(
                    ValidationItem(
                        category = "Manifest Validity",
                        title = "Manifest Structure Valid",
                        message = "Package '${manifest.packageName}', Version '${manifest.versionName}' (${manifest.versionCode}), Min SDK ${manifest.minSdk ?: "N/A"}, Target SDK ${manifest.targetSdk ?: "N/A"}.",
                        severity = ValidationSeverity.PASS
                    )
                )
            }

            if (manifest.duplicateComponents.isNotEmpty()) {
                items.add(
                    ValidationItem(
                        category = "Manifest Validity",
                        title = "Duplicate Component Declarations",
                        message = "Duplicate component names found: ${manifest.duplicateComponents.joinToString()}.",
                        severity = ValidationSeverity.WARNING
                    )
                )
            }
        }

        // 4. Native ABI Compatibility Check
        if (targetReport != null) {
            val targetAbis = targetReport.nativeLibReport.supportedAbis
            if (targetAbis.isNotEmpty()) {
                if (selectedAbiFilter != "Auto detect" && selectedAbiFilter != "Keep all ABIs") {
                    val desiredAbi = selectedAbiFilter.substringBefore(" only")
                    if (desiredAbi !in targetAbis) {
                        items.add(
                            ValidationItem(
                                category = "Native ABI",
                                title = "Selected ABI Missing",
                                message = "Selected ABI '$desiredAbi' is not available in Target APK native libraries (${targetAbis.joinToString()}).",
                                severity = ValidationSeverity.WARNING
                            )
                        )
                    } else {
                        items.add(
                            ValidationItem(
                                category = "Native ABI",
                                title = "ABI Compatible",
                                message = "Selected ABI '$desiredAbi' is present in Target APK.",
                                severity = ValidationSeverity.PASS
                            )
                        )
                    }
                } else {
                    items.add(
                        ValidationItem(
                            category = "Native ABI",
                            title = "Native Libraries Present",
                            message = "Target APK supports ABIs: ${targetAbis.joinToString()}.",
                            severity = ValidationSeverity.PASS
                        )
                    )
                }
            }
        }

        // 5. Signing Configuration Check
        if (signingConfig.useAutoDebugKey) {
            items.add(
                ValidationItem(
                    category = "Signing Config",
                    title = "Auto Debug Keystore",
                    message = "Automatic RSA-2048 debug key configuration enabled (V1, V2, V3 signatures enabled).",
                    severity = ValidationSeverity.PASS
                )
            )
        } else {
            if (signingConfig.keystorePath.isBlank()) {
                items.add(
                    ValidationItem(
                        category = "Signing Config",
                        title = "Missing Custom Keystore Path",
                        message = "Manual signing mode active but custom keystore file path is empty.",
                        severity = ValidationSeverity.FAIL
                    )
                )
            } else if (signingConfig.keyAlias.isBlank()) {
                items.add(
                    ValidationItem(
                        category = "Signing Config",
                        title = "Missing Key Alias",
                        message = "Custom keystore key alias field is empty.",
                        severity = ValidationSeverity.FAIL
                    )
                )
            } else {
                items.add(
                    ValidationItem(
                        category = "Signing Config",
                        title = "Custom Keystore Configured",
                        message = "Custom keystore '${signingConfig.keystorePath}', alias '${signingConfig.keyAlias}'.",
                        severity = ValidationSeverity.PASS
                    )
                )
            }
        }

        // 6. Output Storage Space Check
        val freeBytes = context.cacheDir.freeSpace
        if (freeBytes < 50 * 1024 * 1024) { // Less than 50MB
            items.add(
                ValidationItem(
                    category = "Storage Space",
                    title = "Low Disk Space",
                    message = "Available app storage space is low (${freeBytes / (1024 * 1024)} MB). Build output may fail.",
                    severity = ValidationSeverity.WARNING
                )
            )
        } else {
            items.add(
                ValidationItem(
                    category = "Storage Space",
                    title = "Sufficient Disk Space",
                    message = "Available storage: ${freeBytes / (1024 * 1024)} MB.",
                    severity = ValidationSeverity.PASS
                )
            )
        }

        val passes = items.count { it.severity == ValidationSeverity.PASS }
        val warnings = items.count { it.severity == ValidationSeverity.WARNING }
        val fails = items.count { it.severity == ValidationSeverity.FAIL }

        val overall = when {
            fails > 0 -> ValidationSeverity.FAIL
            warnings > 0 -> ValidationSeverity.WARNING
            else -> ValidationSeverity.PASS
        }

        return ValidationReport(
            timestamp = System.currentTimeMillis(),
            overallStatus = overall,
            items = items,
            passCount = passes,
            warningCount = warnings,
            failCount = fails
        )
    }
}
