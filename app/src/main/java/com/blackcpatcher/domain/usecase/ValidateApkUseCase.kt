package com.blackcpatcher.domain.usecase

import android.content.Context
import com.blackcpatcher.data.apk.ValidationEngine
import com.blackcpatcher.domain.model.ApkReport
import com.blackcpatcher.domain.model.SigningConfig
import com.blackcpatcher.domain.model.ValidationReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Suppress("unused")
class ValidateApkUseCase(private val context: Context) {

    suspend operator fun invoke(
        sourceReport: ApkReport?,
        targetReport: ApkReport?,
        signingConfig: SigningConfig,
        abiFilter: String
    ): ValidationReport = withContext(Dispatchers.Default) {
        ValidationEngine.validate(context, sourceReport, targetReport, signingConfig, abiFilter)
    }
}
