package com.blackcpatcher.data.apk

import org.jf.baksmali.Baksmali
import org.jf.baksmali.BaksmaliOptions
import org.jf.dexlib2.DexFileFactory
import org.jf.dexlib2.Opcodes
import org.jf.dexlib2.writer.pool.DexPool
import org.jf.smali.Smali
import org.jf.smali.SmaliOptions
import org.jf.dexlib2.writer.io.FileDataStore
import org.jf.dexlib2.writer.builder.DexBuilder
import java.io.File

object DexSmaliEngine {

    /** Kaunsi classesN.dex me launcher class hai — string pool me descriptor dhundh ke */
    fun findDexContainingClass(extractedDir: File, className: String): File? {
        val descriptor = ("L" + className.replace('.', '/') + ";").toByteArray(Charsets.UTF_8)
        return extractedDir.listFiles { f -> f.name.matches(Regex("classes\\d*\\.dex")) }
            ?.firstOrNull { it.readBytes().let { b ->
                outer@ for (i in 0..(b.size - descriptor.size).coerceAtLeast(0)) {
                    for (j in descriptor.indices) if (b[i + j] != descriptor[j]) continue@outer
                    return@let true
                }
                false
            } }
    }

    /** dex → smali → hook → wapas dex */
    fun hookLauncherDex(
        dexFile: File,
        launcherClass: String,
        hookLine: String,
        workDir: File,
        log: (String) -> Unit
    ): Boolean {
        val smaliOutDir = File(workDir, "baksmali_out_${dexFile.nameWithoutExtension}").apply { mkdirs() }
        return try {
            log("Disassembling ${dexFile.name} with baksmali…")
            val dex = DexFileFactory.loadDexFile(dexFile, Opcodes.getDefault())
            
            // In smali 2.5.2, BaksmaliOptions has outputDirectory field but might be set differently
            // or the disassemble signature differs. Let's use the standard api.
            val bOptions = BaksmaliOptions()
            
            val successBaksmali = Baksmali.disassembleDexFile(dex, smaliOutDir, Runtime.getRuntime().availableProcessors(), bOptions)
            if (!successBaksmali) { log("ERROR: baksmali failed"); return false }
            
            val hooked = SmaliHooker.injectHook(
                extractedTargetDir = smaliOutDir,
                detectedLauncherActivity = launcherClass,
                manualSmaliPathOverride = "",
                hookLine = hookLine,
                log = log
            )
            if (!hooked) return false

            log("Reassembling smali back into ${dexFile.name}…")
            val sOptions = SmaliOptions()
            sOptions.outputDexFile = dexFile.absolutePath
            val successSmali = org.jf.smali.Smali.assemble(sOptions, smaliOutDir.absolutePath)
            if (successSmali) {
                log("SUCCESS: ${dexFile.name} reassembled with hook")
                return true
            } else {
                log("ERROR: smali assemble failed")
                return false
            }
        } catch (e: Exception) {
            log("ERROR: dex smali pipeline failed: ${e.message}")
            return false
        }
    }
}