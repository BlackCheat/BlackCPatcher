package com.blackcpatcher.data.apk

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Binary AXML → readable text XML (same rendering as MT Manager's decompile view).
 *  Sirf DISPLAY/tracking ke liye. EDIT hamesha BinaryAxmlPatcher (v5) se hi hota hai. */
object AxmlToText {
    fun convert(bytes: ByteArray): String {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 8 || buf.getInt(0) != 0x00080003.toInt()) return "(not a binary AXML)"
        var off = 8
        var strings = listOf<String>()
        var resids = intArrayOf()
        val sb = StringBuilder()
        var depth = 0
        fun pad() { repeat(depth) { sb.append("    ") } }
        while (off + 8 <= bytes.size) {
            val type = buf.getShort(off).toInt() and 0xFFFF
            val size = buf.getInt(off + 4)
            if (size < 8 || off + size > bytes.size) break
            when (type) {
                0x0001 -> { // string pool
                    val count = buf.getInt(off + 8); val flags = buf.getInt(off + 16)
                    val ss = off + buf.getInt(off + 20); val utf8 = (flags and 0x100) != 0
                    val list = mutableListOf<String>()
                    for (i in 0 until count) {
                        val o = ss + buf.getInt(off + 28 + i * 4)
                        list.add(if (utf8) readUtf8(buf, o) else readUtf16(buf, o))
                    }
                    strings = list
                }
                0x0180 -> { // resource map
                    resids = IntArray((size - 8) / 4) { buf.getInt(off + 8 + it * 4) }
                }
                0x0102 -> { // start tag
                    val name = strings.getOrElse(buf.getInt(off + 20)) { "?" }
                    val aCount = buf.getShort(off + 28).toInt() and 0xFFFF
                    val aStart = buf.getShort(off + 24).toInt() and 0xFFFF
                    val aSize = buf.getShort(off + 26).toInt() and 0xFFFF
                    val base = off + 16 + aStart
                    pad(); sb.append('<').append(name)
                    for (i in 0 until aCount) {
                        val aNs = buf.getInt(base + i * aSize)
                        val aName = buf.getInt(base + i * aSize + 4)
                        val aRaw = buf.getInt(base + i * aSize + 8)
                        val aType = buf.get(base + i * aSize + 15).toInt() and 0xFF
                        val aVal = buf.getInt(base + i * aSize + 16)
                        val prefix = if (aNs >= 0 && aNs < strings.size &&
                            strings[aNs] == "http://schemas.android.com/apk/res/android") "android:" else ""
                        val value = when (aType) {
                            0x03 -> strings.getOrElse(if (aRaw >= 0) aRaw else aVal) { "?" }
                            0x12 -> if (aVal != 0) "true" else "false"
                            0x10 -> aVal.toString()
                            0x01 -> "@0x${Integer.toHexString(aVal)}"
                            0x02 -> "?0x${Integer.toHexString(aVal)}"
                            else -> "0x${Integer.toHexString(aVal)} (type $aType)"
                        }
                        sb.append('\n'); pad(); sb.append("    $prefix${strings.getOrElse(aName) { "?" }}=\"$value\"")
                    }
                    sb.append(">\n"); depth++
                }
                0x0103 -> { // end tag
                    depth = (depth - 1).coerceAtLeast(0)
                    val name = strings.getOrElse(buf.getInt(off + 20)) { "?" }
                    pad(); sb.append("</").append(name).append(">\n")
                }
            }
            off += size
        }
        return sb.toString()
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