package com.blackcpatcher.data.apk

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile

object ManifestInfoDetector {
    data class Info(val format: String, val size: Int, val detail: String, val headerHex: String)

    fun detect(apkFile: File): Info? = try {
        ZipFile(apkFile).use { zip ->
            val entry = zip.getEntry("AndroidManifest.xml")
                ?: return Info("MISSING", 0, "no manifest entry in zip", "")
            val bytes = zip.getInputStream(entry).readBytes()
            val hex = bytes.take(8).joinToString(" ") { "%02X".format(it) }
            if (bytes.size < 8) return Info("TRUNCATED", bytes.size, "", hex)
            val magic = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).int
            when {
                magic == 0x00080003 -> {
                    val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
                    var off = 8; var detail = "no pool?"
                    while (off + 8 <= bytes.size) {
                        val type = b.getShort(off).toInt() and 0xFFFF
                        val sz = b.getInt(off + 4)
                        if (sz < 8 || off + sz > bytes.size) break
                        if (type == 0x0001) {
                            val count = b.getInt(off + 8); val styles = b.getInt(off + 12)
                            val flags = b.getInt(off + 16)
                            detail = "strings=$count styles=$styles utf8=${(flags and 0x100) != 0} poolBytes=$sz"
                            break
                        }
                        off += sz
                    }
                    Info("BINARY AXML", bytes.size, detail, hex)
                }
                bytes[0] == '<'.toByte() -> Info("TEXT XML", bytes.size, "not binary — APK will NOT install", hex)
                else -> Info("UNKNOWN / PACKED / ENCRYPTED", bytes.size,
                    "magic=0x${Integer.toHexString(magic)}", hex)
            }
        }
    } catch (e: Exception) { Info("READ ERROR", 0, e.message ?: "", "") }
}