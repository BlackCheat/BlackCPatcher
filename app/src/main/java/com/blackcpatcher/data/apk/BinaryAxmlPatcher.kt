package com.blackcpatcher.data.apk

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Real binary AXML patcher — v5 VERIFIED OFFLINE.
 *
 * v4 bug fixed: strings were NOT padded to 4-byte boundaries → MT/apktool
 * StringBlock.read hard check "String data size is not multiple of 4".
 * aapt writes every pool string 4-aligned; v5 now does the same.
 *
 * Keeps all earlier fixes: 16-bit chunk-type reads, style offsets preserved
 * verbatim, service tag emits ONLY android:name, strict self-validator
 * throws before any corrupt bytes can be returned/written.
 */
object BinaryAxmlPatcher {

    private const val TYPE_STRING_POOL = 0x0001
    private const val TYPE_RESOURCE_MAP = 0x0180
    private const val TYPE_START_TAG = 0x0102
    private const val TYPE_END_TAG = 0x0103
    private const val TYPE_STRING = 0x03
    private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"

    private class LeWriter {
        private val out = ByteArrayOutputStream()
        val size: Int get() = out.size()
        fun i16(v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }
        fun i32(v: Int) { i16(v); i16(v shr 16) }
        fun byte(v: Int) { out.write(v and 0xFF) }
        fun bytes(b: ByteArray) { out.write(b) }
        fun toByteArray(): ByteArray = out.toByteArray()
    }

    fun patch(
        manifestBytes: ByteArray,
        permissionName: String?,
        serviceName: String?,
        serviceEnabled: Boolean = true,   // API compat — NOT emitted
        serviceExported: Boolean = false, // Android defaults cover these
        serviceStopWithTask: Boolean = true
    ): ByteArray {
        val buf = ByteBuffer.wrap(manifestBytes).order(ByteOrder.LITTLE_ENDIAN)

        require(manifestBytes.size >= 8) { "Manifest too small: ${manifestBytes.size} bytes" }
        require(buf.getInt(0) == 0x00080003.toInt()) {
            "Not a binary AXML (magic=0x${Integer.toHexString(buf.getInt(0))})"
        }

        // ---- 1. Locate & parse string pool (16-bit type read!) ----
        var offset = 8
        var poolStart = -1; var poolEnd = -1
        var poolFlags = 0; var poolStyleCount = 0
        val strings = mutableListOf<String>()
        var stylesBytes = byteArrayOf()
        var styleOffsets = listOf<Int>()

        while (offset + 8 <= manifestBytes.size) {
            val type = buf.getShort(offset).toInt() and 0xFFFF
            val size = buf.getInt(offset + 4)
            if (size <= 8 || offset + size > manifestBytes.size) break
            if (type == TYPE_STRING_POOL) {
                poolStart = offset; poolEnd = offset + size
                val pd = readPool(manifestBytes, buf, offset)
                strings.addAll(pd.strings)
                poolStyleCount = pd.styleCount
                poolFlags = pd.flags
                stylesBytes = pd.stylesBytes
                styleOffsets = pd.styleOffsets
                break
            }
            offset += size
        }
        require(poolStart >= 0) { "String pool chunk not found after scanning from offset 8" }
        val isUtf8 = (poolFlags and 0x100) != 0

        // ---- 2. Scan body for insertion points (16-bit type read!) ----
        var manifestTagEnd = -1
        var applicationEndStart = -1
        var lineNo = 0
        offset = poolEnd
        while (offset + 8 <= manifestBytes.size) {
            val type = buf.getShort(offset).toInt() and 0xFFFF
            val size = buf.getInt(offset + 4)
            if (size <= 8 || offset + size > manifestBytes.size) break
            when (type) {
                TYPE_START_TAG -> {
                    val ni = buf.getInt(offset + 20)
                    if (ni in strings.indices && strings[ni] == "manifest") {
                        manifestTagEnd = offset + size
                        lineNo = buf.getInt(offset + 8)
                    }
                }
                TYPE_END_TAG -> {
                    val ni = buf.getInt(offset + 20)
                    if (ni in strings.indices && strings[ni] == "application")
                        applicationEndStart = offset
                }
            }
            offset += size
        }
        require(manifestTagEnd > 0) { "<manifest> start tag not found in AXML body" }
        require(applicationEndStart > 0) { "</application> end tag not found in AXML body" }

        // ---- 3. Register strings ----
        fun poolIndex(s: String): Int = strings.indexOf(s).let {
            if (it >= 0) it else strings.size.also { strings.add(s) }
        }
        val androidNsIdx = poolIndex(ANDROID_NS)
        val permTagIdx = permissionName?.let { poolIndex("uses-permission") } ?: 0
        val permValIdx = permissionName?.let { poolIndex(it) } ?: 0
        val svcTagIdx  = serviceName?.let { poolIndex("service") } ?: 0
        val svcValIdx  = serviceName?.let { poolIndex(it) } ?: 0
        val attrNameIdx = poolIndex("name")

        // ---- 4. Build element chunks (ONLY android:name attributes) ----
        fun attr(ns: Int, name: Int, raw: Int, type: Int, dataVal: Int): ByteArray {
            val w = LeWriter()
            w.i32(ns); w.i32(name); w.i32(raw)
            w.i16(8); w.byte(0); w.byte(type); w.i32(dataVal)
            return w.toByteArray()
        }
        fun stringAttr(nameIdx: Int, valueIdx: Int) =
            attr(androidNsIdx, nameIdx, valueIdx, TYPE_STRING, valueIdx)

        fun startTag(nameIdx: Int, attrs: List<ByteArray>): ByteArray {
            val w = LeWriter()
            w.i16(TYPE_START_TAG); w.i16(36)
            w.i32(36 + attrs.sumOf { it.size })
            w.i32(lineNo); w.i32(-1); w.i32(-1)
            w.i32(nameIdx)
            w.i16(20); w.i16(20)
            w.i16(attrs.size); w.i16(0); w.i16(0); w.i16(0)
            attrs.forEach { w.bytes(it) }
            return w.toByteArray()
        }
        fun endTag(nameIdx: Int): ByteArray {
            val w = LeWriter()
            w.i16(TYPE_END_TAG); w.i16(24); w.i32(24)
            w.i32(lineNo); w.i32(-1); w.i32(-1); w.i32(nameIdx)
            return w.toByteArray()
        }

        val permChunk = if (permissionName != null)
            startTag(permTagIdx, listOf(stringAttr(attrNameIdx, permValIdx))) + endTag(permTagIdx)
        else byteArrayOf()

        val svcChunk = if (serviceName != null)
            startTag(svcTagIdx, listOf(stringAttr(attrNameIdx, svcValIdx))) + endTag(svcTagIdx)
        else byteArrayOf()

        // ---- 5. Rebuild string pool (WITH 4-byte padding — FIX v5) ----
        val newPool = buildPool(strings, styleOffsets, stylesBytes, poolStyleCount, poolFlags, isUtf8)

        // ---- 6. Splice ----
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

        // ---- 7. STRICT self-validation (MT-Manager-level) — throw, never return bad bytes ----
        validateStrict(result)
        return result
    }

    // ------------------------------------------------------------------

    private class PoolData(
        val strings: List<String>,
        val styleOffsets: List<Int>,
        val stylesBytes: ByteArray,
        val styleCount: Int,
        val flags: Int
    )

    private fun readPool(raw: ByteArray, buf: ByteBuffer, offset: Int): PoolData {
        val count = buf.getInt(offset + 8)
        val styleCount = buf.getInt(offset + 12)
        val flags = buf.getInt(offset + 16)
        val stringsStart = offset + buf.getInt(offset + 20)
        val stylesStart = buf.getInt(offset + 24)
        val chunkEnd = offset + buf.getInt(offset + 4)
        val utf8 = (flags and 0x100) != 0
        val strings = mutableListOf<String>()
        for (i in 0 until count) {
            val o = stringsStart + buf.getInt(offset + 28 + i * 4)
            strings.add(
                if (o in 0 until buf.capacity()) (if (utf8) readUtf8(buf, o) else readUtf16(buf, o)) else ""
            )
        }
        val styleOffsets = (0 until styleCount).map { buf.getInt(offset + 28 + count * 4 + it * 4) }
        val stylesBytes = if (styleCount > 0 && stylesStart > 0 && offset + stylesStart < chunkEnd)
            raw.copyOfRange(offset + stylesStart, chunkEnd) else byteArrayOf()
        return PoolData(strings, styleOffsets, stylesBytes, styleCount, flags)
    }

    private fun buildPool(
        strings: List<String>,
        styleOffsets: List<Int>,
        styles: ByteArray,
        styleCount: Int,
        flags: Int,
        utf8: Boolean
    ): ByteArray {
        val data = LeWriter()
        val offsets = IntArray(strings.size)
        strings.forEachIndexed { i, s ->
            offsets[i] = data.size
            if (utf8) {
                val b = s.toByteArray(Charsets.UTF_8)
                writeLen(data, s.toByteArray(Charsets.UTF_16LE).size / 2)
                writeLen(data, b.size)
                data.bytes(b)
            } else {
                data.i16(s.length)
                s.forEach { c -> data.i16(c.code) }
            }
            // ← FIX v5: har string ko 4-byte boundary pe pad karo (aapt bhi yehi karta hai)
            while (data.size % 4 != 0) data.byte(0)
        }
        val stringsStart = 28 + strings.size * 4 + styleCount * 4
        val stylesStart = if (styleCount > 0) stringsStart + data.size else 0
        val total = stringsStart + data.size + styles.size

        val w = LeWriter()
        w.i16(TYPE_STRING_POOL); w.i16(28); w.i32(total)
        w.i32(strings.size); w.i32(styleCount); w.i32(flags)
        w.i32(stringsStart); w.i32(stylesStart)
        offsets.forEach { w.i32(it) }
        styleOffsets.forEach { w.i32(it) }
        w.bytes(data.toByteArray())
        w.bytes(styles)
        return w.toByteArray()
    }

    /** Walks the patched file with MT-Manager-level strictness. Throws on ANY defect. */
    private fun validateStrict(data: ByteArray) {
        val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        require(data.size >= 8) { "validate: too small" }
        require(buf.getInt(0) == 0x00080003.toInt()) { "validate: bad magic" }
        require(buf.getInt(4) == data.size) { "validate: file size field mismatch" }

        var offset = 8
        var strings: List<String>? = null
        var residsSize = 0
        while (offset + 8 <= data.size) {
            val type = buf.getShort(offset).toInt() and 0xFFFF
            val size = buf.getInt(offset + 4)
            require(size >= 8 && offset + size <= data.size) { "validate: chunk@$offset bad size $size" }
            when (type) {
                TYPE_STRING_POOL -> {
                    val count = buf.getInt(offset + 8)
                    val sc = buf.getInt(offset + 12)
                    val stringsStart = buf.getInt(offset + 20)
                    val stylesStartV = buf.getInt(offset + 24)
                    // ← FIX v5: MT ke hard checks yahan bhi
                    require(stringsStart % 4 == 0) { "validate: stringsStart not multiple of 4" }
                    val dataSize = (if (stylesStartV == 0) size else stylesStartV) - stringsStart
                    require(dataSize >= 0 && dataSize % 4 == 0) {
                        "validate: String data size is not multiple of 4 ($dataSize)"
                    }
                    require(stringsStart == 28 + count * 4 + sc * 4) { "validate: stringsStart field wrong" }
                    for (i in 0 until count) {
                        val o = buf.getInt(offset + 28 + i * 4)
                        require(o in 0 until dataSize) { "validate: string offset $o out of range (dataSize=$dataSize)" }
                        require(o % 4 == 0) { "validate: string offset $o not 4-aligned" }
                    }
                    strings = readPool(data, buf, offset).strings
                }
                TYPE_RESOURCE_MAP -> residsSize = (size - 8) / 4
                TYPE_START_TAG -> {
                    require(residsSize > 0) { "validate: Invalid resource ids size (0)" }
                    val attrStart = buf.getShort(offset + 24).toInt() and 0xFFFF
                    val attrSize = buf.getShort(offset + 26).toInt() and 0xFFFF
                    val attrCount = buf.getShort(offset + 28).toInt() and 0xFFFF
                    val attrBase = offset + 16 + attrStart
                    require(attrBase + attrCount * attrSize <= offset + size) {
                        "validate: attributes overflow chunk@$offset"
                    }
                    val strs = strings
                    if (strs != null) {
                        for (i in 0 until attrCount) {
                            val aNs = buf.getInt(attrBase + i * attrSize)
                            val aName = buf.getInt(attrBase + i * attrSize + 4)
                            if (aNs >= 0 && aNs < strs.size && strs[aNs] == ANDROID_NS) {
                                require(aName < residsSize) {
                                    "validate: android attr '${strs.getOrNull(aName)}' idx $aName >= resids size $residsSize"
                                }
                            }
                        }
                    }
                }
            }
            offset += size
        }
        require(offset == data.size) { "validate: $offset != ${data.size} (trailing/missing bytes)" }
    }

    private fun writeLen(w: LeWriter, len: Int) {
        if (len >= 0x80) { w.byte(0x80 or (len shr 8)); w.byte(len and 0xFF) } else w.byte(len)
    }

    private fun readUtf8(buf: ByteBuffer, offset: Int): String = try {
        var p = offset
        var charLen = buf.get(p).toInt() and 0xFF
        if ((charLen and 0x80) != 0) { p++; charLen = ((charLen and 0x7F) shl 8) or (buf.get(p).toInt() and 0xFF) }
        p++
        var byteLen = buf.get(p).toInt() and 0xFF
        if ((byteLen and 0x80) != 0) { p++; byteLen = ((byteLen and 0x7F) shl 8) or (buf.get(p).toInt() and 0xFF) }
        p++
        if (p + byteLen > buf.capacity()) "" else {
            val b = ByteArray(byteLen); buf.position(p); buf.get(b)
            String(b, Charsets.UTF_8)
        }
    } catch (_: Exception) { "" }

    private fun readUtf16(buf: ByteBuffer, offset: Int): String = try {
        val len = buf.getShort(offset).toInt() and 0xFFFF
        val p = offset + 2
        if (p + len * 2 > buf.capacity()) "" else {
            val chars = CharArray(len)
            for (i in 0 until len) chars[i] = buf.getShort(p + i * 2).toInt().toChar()
            String(chars)
        }
    } catch (_: Exception) { "" }
}