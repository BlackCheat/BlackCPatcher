package com.blackcpatcher.domain.usecase

import android.content.Context
import android.net.Uri
import com.blackcpatcher.data.apk.ApkInspectorEngine
import com.blackcpatcher.domain.model.ApkReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("unused")
class AnalyzeApkUseCase(private val context: Context) {

    suspend operator fun invoke(uri: Uri): Result<ApkReport> = withContext(Dispatchers.IO) {
        runCatching {
            ApkInspectorEngine.inspect(context, uri)
        }
    }
}
