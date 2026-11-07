package com.blackcpatcher

import com.blackcpatcher.data.apk.DexInspector
import com.blackcpatcher.data.storage.ExportManager
import com.blackcpatcher.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class ApkParsingTest {

    @Test
    fun testDexInspectorClassAndDuplicateDetection() {
        // Create synthetic DEX binary header
        val header = ByteArray(112)
        // Magic "dex\n"
        header[0] = 0x64; header[1] = 0x65; header[2] = 0x78; header[3] = 0x0a
        header[4] = 0x30; header[5] = 0x33; header[6] = 0x35; header[7] = 0x00

        val dexEntries = listOf(
            "classes.dex" to header,
            "classes2.dex" to header
        )

        val report = DexInspector.inspectDexEntries(dexEntries)

        assertNotNull(report)
        assertEquals(2, report.dexFiles.size)
        assertEquals(0, report.totalClassesCount)
    }

    @Test
    fun testValidationReportGeneration() {
        val items = listOf(
            ValidationItem("Integrity", "Target Valid", "OK", ValidationSeverity.PASS),
            ValidationItem("Manifest", "Package Valid", "OK", ValidationSeverity.PASS),
            ValidationItem("Signing", "Debug Key", "OK", ValidationSeverity.PASS)
        )

        val report = ValidationReport(
            timestamp = System.currentTimeMillis(),
            overallStatus = ValidationSeverity.PASS,
            items = items,
            passCount = 3,
            warningCount = 0,
            failCount = 0
        )

        val formattedText = ExportManager.generateValidationReportText(report)

        assertTrue(formattedText.contains("BLACKCPATCHER PRE-BUILD VALIDATION REPORT"))
        assertTrue(formattedText.contains("Overall Status: PASS"))
        assertTrue(formattedText.contains("Summary: 3 PASS, 0 WARNING, 0 FAIL"))
    }

    @Test
    fun testSigningConfigEquality() {
        val config1 = SigningConfig(useAutoDebugKey = true, enableV1 = true, enableV2 = true, enableV3 = true)
        val config2 = SigningConfig(useAutoDebugKey = true, enableV1 = true, enableV2 = true, enableV3 = true)

        assertEquals(config1, config2)
        assertEquals(config1.hashCode(), config2.hashCode())
    }
}
