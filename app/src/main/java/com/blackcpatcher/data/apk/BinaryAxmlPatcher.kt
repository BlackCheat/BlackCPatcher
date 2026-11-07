package com.blackcpatcher.data.apk

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real binary AXML patcher. Inserts <uses-permission> and <service> nodes
 * directly at binary level. Output stays valid binary AndroidManifest.xml.
 */
object BinaryAxmlPatcher {

    private const val CHUNK_STRING_POOL = 0x00010001
    private const val CHUNK_START_TAG   = 0x00100102
    private const val CHUNK_END_TAG     = 0x00100103
    private const val TYPE_STRING       = 0x03
    private const val TYPE_INT_BOOLEAN  = 0x12
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    /**
     * @param permissionName permission to add, or null to skip
     * @param serviceName    service class to add, or null to skip
     */
    fun patch(
        manifestBytes: ByteArray,
        permissionName: String?,
        serviceName: String?,
        serviceEnabled: Boolean = true,
        serviceExported: Boolean = false,
        serviceStopWithTask: Boolean = true
    ): ByteArray {
        val buf = ByteBuffer.wrap(manifestBytes).order(ByteOrder.LITTLE_ENDIAN)

        // ---- 1. Parse string pool ----
        var offset = 8
        var poolStart = -1; var poolEnd = -1
        var poolFlags = 0; var poolStyleCount = 0
        val strings = mutableListOf<String>()
        var stylesBytes = byteArrayOf()

        while (offset + 8 <= manifestBytes.size) {
            val type = buf.getInt(offset); val size = buf.getInt(offset + 4)
            if (size <= 0 || offset + size > manifestBytes.size) break
            if (type == CHUNK_STRING_POOL) {
                poolStart = offset; poolEnd = offset + size
                strings.addAll(readPool(buf, offset))
                poolStyleCount = buf.getInt(offset + 12)
                poolFlags = buf.getInt(offset + 16)
                val stylesStart = buf.getInt(offset + 24)
                stylesBytes = if (poolStyleCount > 0 && stylesStart > 0)
                    manifestBytes.copyOfRange(offset + stylesStart, offset + size) else byteArrayOf()
                break
            }
            offset += size
        }
        require(poolStart >= 0) { "No string pool found - not a valid AXML manifest" }
        val isUtf8 = (poolFlags and 0x100) != 0

        // ---- 2. Scan body for insertion points ----
        var manifestTagEnd = -1
        var applicationEndStart = -1
        var lineNo = 0
        offset = poolEnd
        while (offset + 8 <= manifestBytes.size) {
            val type = buf.getInt(offset); val size = buf.getInt(offset + 4)
            if (size <= 0 || offset + size > manifestBytes.size) break
            if (type == CHUNK_START_TAG) {
                if (strings.getOrNull(buf.getInt(offset + 20)) == "manifest") {
                    manifestTagEnd = offset + size
                    lineNo = buf.getInt(offset + 8)
                }
            } else if (type == CHUNK_END_TAG) {
                if (strings.getOrNull(buf.getInt(offset + 20)) == "application")
                    applicationEndStart = offset
            }
            offset += size
        }
        require(manifestTagEnd > 0) { "<manifest> start tag not found" }
        require(applicationEndStart > 0) { "</application> end tag not found" }

        // ---- 3. Register needed strings in pool ----
        fun poolIndex(s: String): Int = strings.indexOf(s).let {
            if (it >= 0) it else strings.size.also { strings.add(s) }
        }
        val androidNsIdx  = poolIndex(ANDROID_NS)
        val permTagIdx    = permissionName?.let { poolIndex("uses-permission") } ?: 0
        val permValIdx    = permissionName?.let { poolIndex(it) } ?: 0
        val svcTagIdx     = serviceName?.let { poolIndex("service") } ?: 0
        val svcValIdx     = serviceName?.let { poolIndex(it) } ?: 0
        val attrNameIdx   = poolIndex("name")
        val attrEnabled   = poolIndex("enabled")
        val attrExported  = poolIndex("exported")
        val attrStopTask  = poolIndex("stopWithTask")

        // ---- 4. Build chunks ----
        fun attr(ns: Int, name: Int, raw: Int, type: Int, dataVal: Int): ByteArray {
            val a = ByteArray(20)
            ByteBuffer.wrap(a).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(ns); putInt(name); putInt(raw)
                putShort(8); put(0); put(type.toByte()); putInt(dataVal)
            }
            return a
        }
        fun stringAttr(nameIdx: Int, valueIdx: Int) = attr(androidNsIdx, nameIdx, valueIdx, TYPE_STRING, valueIdx)
        fun boolAttr(nameIdx: Int, value: Boolean) = attr(androidNsIdx, nameIdx, -1, TYPE_INT_BOOLEAN, if (value) -1 else 0)

        fun startTag(nameIdx: Int, attrs: List<ByteArray>): ByteArray {
            val header = ByteArray(36)
            ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(CHUNK_START_TAG); putInt(36); putInt(36 + attrs.sumOf { it.size })
                putInt(lineNo); putInt(-1); putInt(-1); putInt(nameIdx)
                putShort(20); putShort(20); putShort(attrs.size.toShort())
                putShort(0); putShort(0); putShort(0)
            }
            return header + attrs.fold(ByteArray(0)) { acc, b -> acc + b }
        }
        fun endTag(nameIdx: Int): ByteArray {
            val b = ByteArray(24)
            ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).apply {
                putInt(CHUNK_END_TAG); putInt(24); putInt(24)
                putInt(lineNo); putInt(-1); putInt(-1); putInt(nameIdx)
            }
            return b
        }

        val permChunk = if (permissionName != null)
            startTag(permTagIdx, listOf(stringAttr(attrNameIdx, permValIdx))) + endTag(permTagIdx)
        else byteArrayOf()

        val svcChunk = if (serviceName != null)
            startTag(svcTagIdx, listOf(
                stringAttr(attrNameIdx, svcValIdx),
                boolAttr(attrEnabled, serviceEnabled),
                boolAttr(attrExported, serviceExported),
                boolAttr(attrStopTask, serviceStopWithTask)
            )) + endTag(svcTagIdx)
        else byteArrayOf()

        // ---- 5. Rebuild string pool (existing + appended strings) ----
        val newPool = buildPool(strings, stylesBytes, poolStyleCount, poolFlags, isUtf8)

        // ---- 6. Splice everything back ----
        val out = ByteArrayOutputStream()
        out.write(manifestBytes, 0, poolStart)
        out.write(newPool)
        out.write(manifestBytes, poolEnd, manifestTagEnd - poolEnd)
        out.write(permChunk)
        out.write(manifestBytes, manifestTagEnd, applicationEndStart - manifestTagEnd)
        out.write(svcChunk)
        out.write(manifestBytes, applicationEndStart, manifestBytes.size - applicationEndStart)
        val result = out.toByteArray()
        ByteBuffer.wrap(result).order(ByteOrder.LITTLE_ENDIAN).putInt(4, result.size)
        return result
    }

    private fun buildPool(strings: List<String>, styles: ByteArray, styleCount: Int, flags: Int, utf8: Boolean): ByteArray {
        val data = ByteArrayOutputStream()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { i, s ->
            offsets[i] = data.size()
            if (utf8) {
                val b = s.toByteArray(Charsets.UTF_8)
                writeLen(data, s.length); writeLen(data, b.size); data.write(b)
            } else {
                writeShort(data, s.length)
                s.forEach { c -> writeShort(data, c.code) }
            }
        }
        val stringsStart = 28 + strings.size * 4 + styleCount * 4
        val stylesStart = if (styleCount > 0) stringsStart + data.size() else 0
        val total = stringsStart + data.size() + styles.size

        val out = ByteArrayOutputStream()
        val header = ByteArray(28)
        ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).apply {
            putInt(CHUNK_STRING_POOL); putInt(28); putInt(total)
            putInt(strings.size); putInt(styleCount); putInt(flags)
            putInt(stringsStart); putInt(stylesStart)
        }
        out.write(header)
        offsets.forEach { writeInt(out, it) }
        out.write(data.toByteArray())
        out.write(styles)
        return out.toByteArray()
    }

    private fun readPool(buf: ByteBuffer, offset: Int): List<String> {
        val count = buf.getInt(offset + 8)
        val flags = buf.getInt(offset + 16)
        val stringsStart = offset + buf.getInt(offset + 20)
        val utf8 = (flags and 0x100) != 0
        val result = mutableListOf<String>()
        for (i in 0 until count) {
            val off = stringsStart + buf.getInt(offset + 28 + i * 4)
            result.add(if (utf8) readUtf8(buf, off) else readUtf16(buf, off))
        }
        return result
    }

    private fun readUtf8(buf: ByteBuffer, offset: Int): String {
        var p = offset
        var charLen = buf.get(p).toInt() and 0xFF
        if ((charLen and 0x80) != 0) { p++; charLen = ((charLen and 0x7F) shl 8) or (buf.get(p).toInt() and 0xFF) }
        p++
        var byteLen = buf.get(p).toInt() and 0xFF
        if ((byteLen and 0x80) != 0) { p++; byteLen = ((byteLen and 0x7F) shl 8) or (buf.get(p).toInt() and 0xFF) }
        p++
        if (p + byteLen > buf.capacity()) return ""
        val b = ByteArray(byteLen); buf.position(p); buf.get(b)
        return String(b, Charsets.UTF_8)
    }

    private fun readUtf16(buf: ByteBuffer, offset: Int): String {
        val len = buf.getShort(offset).toInt() and 0xFFFF
        val p = offset + 2
        if (p + len * 2 > buf.capacity()) return ""
        val chars = CharArray(len)
        for (i in 0 until len) chars[i] = buf.getShort(p + i * 2).toInt().toChar()
        return String(chars)
    }

    private fun writeLen(out: ByteArrayOutputStream, len: Int) {
        if (len >= 0x80) { out.write(0x80 or (len shr 8)); out.write(len and 0xFF) } else out.write(len)
    }
    private fun writeShort(out: ByteArrayOutputStream, v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
    private fun writeInt(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF); out.write((v shr 8) and 0xFF); out.write((v shr 16) and 0xFF); out.write((v shr 24) and 0xFF)
    }
}
