package com.blackcpatcher.domain.usecase

import android.content.Context
import android.net.Uri
import com.blackcpatcher.data.apk.ApkBuilderEngine
import com.blackcpatcher.data.apk.ApkSignerManager
import com.blackcpatcher.domain.model.BuildResult
import com.blackcpatcher.domain.model.SigningConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("unused")
class BuildAndSignApkUseCase(private val context: Context) {

    suspend operator fun invoke(
        sourceUri: Uri?,
        targetUri: Uri,
        abiFilter: String,
        signingConfig: SigningConfig,
        onProgress: (Int, String) -> Unit
    ): BuildResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val logs = mutableListOf<String>()

        try {
            logs.add("Starting authorized APK build pipeline…")
            val builtApk = ApkBuilderEngine.build(
                context = context,
                sourceUri = sourceUri,
                targetUri = targetUri,
                abiFilter = abiFilter,
                onProgress = { pct, msg ->
                    logs.add("[$pct%] $msg")
                    onProgress(pct / 2, msg)
                }
            )

            logs.add("Build output generated: ${builtApk.name} (${builtApk.length() / 1024} KB)")

            onProgress(50, "Starting signing and verification engine…")
            val signResult = ApkSignerManager.signAndVerify(
                inputApk = builtApk,
                config = signingConfig,
                onProgress = { pct, msg ->
                    logs.add("[${50 + pct / 2}%] $msg")
                    onProgress(50 + pct / 2, msg)
                }
            )

            logs.addAll(signResult.logOutput)
            val duration = System.currentTimeMillis() - startTime

            BuildResult(
                isSuccess = true,
                outputPath = signResult.signedFile.absolutePath,
                outputSize = signResult.signedFile.length(),
                buildDurationMs = duration,
                v1Verified = signResult.v1Verified,
                v2Verified = signResult.v2Verified,
                v3Verified = signResult.v3Verified,
                errorMessage = null,
                logOutput = logs
            )
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logs.add("ERROR: Build failed: ${e.message}")
            BuildResult(
                isSuccess = false,
                outputPath = null,
                outputSize = null,
                buildDurationMs = duration,
                v1Verified = false,
                v2Verified = false,
                v3Verified = false,
                errorMessage = e.message ?: "Unknown build failure",
                logOutput = logs
            )
        }
    }
}
