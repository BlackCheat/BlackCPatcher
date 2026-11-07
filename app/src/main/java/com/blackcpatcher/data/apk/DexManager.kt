package com.blackcpatcher.data.apk

import java.io.File

/**
 * Sequential Multi-Dex Indexing & Renumbering Engine.
 * Scans Target APK for existing `classes.dex`, `classes2.dex`, ... `classesN.dex`.
 * Renumbers Source Overlay Panel's DEX bytecode files to `classes(N+1).dex`, `classes(N+2).dex`,
 * and merges them seamlessly into Target root without overwriting existing bytecode.
 */
object DexManager {

    fun renumberAndMergeDex(
        extractedSourceDir: File,
        extractedTargetDir: File,
        log: (String) -> Unit
    ): List<File> {
        val targetDexFiles = extractedTargetDir.listFiles { _, name ->
            name.matches(Regex("classes(\\d*)\\.dex"))
        }?.toList().orEmpty()

        val maxIndex = targetDexFiles.mapNotNull { file ->
            val name = file.name
            if (name == "classes.dex") 1 else name.removePrefix("classes").removeSuffix(".dex").toIntOrNull()
        }.maxOrNull() ?: 1

        val sourceDexFiles = extractedSourceDir.listFiles { _, name ->
            name.matches(Regex("classes(\\d*)\\.dex"))
        }?.sortedWith(compareBy({ if (it.name == "classes.dex") 0 else it.name.removePrefix("classes").removeSuffix(".dex").toIntOrNull() ?: 999 }))
        .orEmpty()

        if (sourceDexFiles.isEmpty()) {
            log("No DEX files found in Source Panel APK.")
            return emptyList()
        }

        val addedDexFiles = mutableListOf<File>()
        var nextIdx = maxIndex + 1

        for (srcDex in sourceDexFiles) {
            val destDexName = "classes$nextIdx.dex"
            val destDexFile = File(extractedTargetDir, destDexName)
            srcDex.copyTo(destDexFile, overwrite = true)
            addedDexFiles.add(destDexFile)
            log("Sequential Multi-Dex: Renumbered ${srcDex.name} -> $destDexName (${srcDex.length() / 1024} KB)")
            nextIdx++
        }

        return addedDexFiles
    }
}
