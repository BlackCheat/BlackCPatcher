package com.blackcpatcher.data.apk

import com.blackcpatcher.domain.model.DexClassInfo
import com.blackcpatcher.domain.model.DexFileInfo
import com.blackcpatcher.domain.model.DexReport
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Binary DEX reader for analyzing multidex layout, class descriptors, duplicate class definitions,
 * method counts, and field counts across DEX files.
 */
object DexInspector {

    private val DEX_MAGIC = byteArrayOf(0x64, 0x65, 0x78, 0x0a) // "dex\n"

    fun inspectDexEntries(entries: List<Pair<String, ByteArray>>): DexReport {
        val dexFileInfos = mutableListOf<DexFileInfo>()
        val allClasses = mutableListOf<DexClassInfo>()
        val classOriginMap = mutableMapOf<String, MutableList<String>>()

        var grandTotalClasses = 0
        var grandTotalMethods = 0
        var grandTotalFields = 0

        for ((fileName, bytes) in entries) {
            val parsed = parseSingleDex(fileName, bytes)
            dexFileInfos.add(parsed.fileInfo)
            grandTotalClasses += parsed.fileInfo.classCount
            grandTotalMethods += parsed.fileInfo.methodCount
            grandTotalFields += parsed.fileInfo.fieldCount

            for (cls in parsed.classes) {
                allClasses.add(cls)
                classOriginMap.getOrPut(cls.className) { mutableListOf() }.add(fileName)
            }
        }

        val duplicates = classOriginMap.filter { it.value.size > 1 }.map { entry ->
            "${entry.key} (in ${entry.value.joinToString(", ")})"
        }.sorted()

        val packages = allClasses.map { it.packageName }.filter { it.isNotEmpty() }.distinct().sorted()

        return DexReport(
            dexFiles = dexFileInfos,
            totalClassesCount = grandTotalClasses,
            totalMethodCount = grandTotalMethods,
            totalFieldCount = grandTotalFields,
            allClasses = allClasses.distinctBy { it.className },
            duplicateClasses = duplicates,
            packageHierarchy = packages
        )
    }

    private data class SingleDexResult(
        val fileInfo: DexFileInfo,
        val classes: List<DexClassInfo>
    )

    private fun parseSingleDex(fileName: String, bytes: ByteArray): SingleDexResult {
        if (bytes.size < 112) {
            return SingleDexResult(
                DexFileInfo(fileName, bytes.size.toLong(), 0, 0, 0),
                emptyList()
            )
        }

        // Verify Magic
        if (bytes[0] != DEX_MAGIC[0] || bytes[1] != DEX_MAGIC[1] || bytes[2] != DEX_MAGIC[2] || bytes[3] != DEX_MAGIC[3]) {
            return SingleDexResult(
                DexFileInfo(fileName, bytes.size.toLong(), 0, 0, 0),
                emptyList()
            )
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        val stringIdsSize = buffer.getInt(56)
        val stringIdsOff = buffer.getInt(60)
        val typeIdsSize = buffer.getInt(64)
        val typeIdsOff = buffer.getInt(68)
        val fieldIdsSize = buffer.getInt(80)
        val methodIdsSize = buffer.getInt(88)
        val classDefsSize = buffer.getInt(96)
        val classDefsOff = buffer.getInt(100)

        val strings = parseStringIds(buffer, stringIdsSize, stringIdsOff)
        val types = parseTypeIds(buffer, typeIdsSize, typeIdsOff, strings)

        val classes = mutableListOf<DexClassInfo>()
        var offset = classDefsOff
        for (i in 0 until classDefsSize) {
            if (offset + 32 > bytes.size) break
            val classIdx = buffer.getInt(offset)
            val classDescriptor = types.getOrNull(classIdx) ?: ""
            if (classDescriptor.startsWith("L") && classDescriptor.endsWith(";")) {
                val fullName = classDescriptor.substring(1, classDescriptor.length - 1).replace('/', '.')
                val pkgName = if (fullName.contains('.')) fullName.substringBeforeLast('.') else ""
                val simpleName = fullName.substringAfterLast('.')
                classes.add(
                    DexClassInfo(
                        className = fullName,
                        packageName = pkgName,
                        simpleName = simpleName,
                        dexFileName = fileName,
                        methodCount = 0,
                        fieldCount = 0
                    )
                )
            }
            offset += 32
        }

        return SingleDexResult(
            fileInfo = DexFileInfo(
                fileName = fileName,
                size = bytes.size.toLong(),
                classCount = classes.size,
                methodCount = methodIdsSize,
                fieldCount = fieldIdsSize
            ),
            classes = classes
        )
    }

    private fun parseStringIds(buffer: ByteBuffer, size: Int, offset: Int): List<String> {
        val strings = mutableListOf<String>()
        var p = offset
        for (i in 0 until size) {
            if (p + 4 > buffer.capacity()) break
            val strDataOff = buffer.getInt(p)
            strings.add(readMutedUtf8String(buffer, strDataOff))
            p += 4
        }
        return strings
    }

    private fun readMutedUtf8String(buffer: ByteBuffer, offset: Int): String {
        try {
            var p = offset
            if (p >= buffer.capacity()) return ""
            // Read ULEB128 utf16_size
            var utf16Size = 0
            var shift = 0
            while (p < buffer.capacity()) {
                val b = buffer.get(p).toInt() and 0xFF
                p++
                utf16Size = utf16Size or ((b and 0x7F) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            val start = p
            while (p < buffer.capacity() && buffer.get(p) != 0.toByte()) {
                p++
            }
            val len = p - start
            if (len <= 0) return ""
            val bytes = ByteArray(len)
            buffer.position(start)
            buffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return ""
        }
    }

    private fun parseTypeIds(buffer: ByteBuffer, size: Int, offset: Int, strings: List<String>): List<String> {
        val types = mutableListOf<String>()
        var p = offset
        for (i in 0 until size) {
            if (p + 4 > buffer.capacity()) break
            val descriptorIdx = buffer.getInt(p)
            types.add(strings.getOrNull(descriptorIdx) ?: "")
            p += 4
        }
        return types
    }
}
