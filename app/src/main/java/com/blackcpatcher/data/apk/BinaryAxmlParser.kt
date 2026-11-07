package com.blackcpatcher.data.apk

import com.blackcpatcher.domain.model.ComponentDetail
import com.blackcpatcher.domain.model.IntentFilterDetail
import com.blackcpatcher.domain.model.ManifestReport
import com.blackcpatcher.domain.model.PermissionDetail
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * High-reliability Android Binary XML (AXML) parser for parsing AndroidManifest.xml directly from APK zip streams.
 * Extracts permissions, activities, intent-filters, launcher activity, services, receivers, and providers.
 */
object BinaryAxmlParser {

    private const val CHUNK_TYPE_XML = 0x00080003
    private const val CHUNK_STRING_POOL = 0x00010001
    private const val CHUNK_START_TAG = 0x00100102
    private const val CHUNK_END_TAG = 0x00100103

    fun parse(inputStream: InputStream): ManifestReport {
        val bytes = inputStream.readBytes()
        if (bytes.size < 8) error("Invalid AndroidManifest.xml file: empty or corrupted.")

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val fileHeader = buffer.int
        if (fileHeader != CHUNK_TYPE_XML && (fileHeader and 0xFFFF) != 0x0008) {
            // Fallback: if not binary XML, check if text XML
            val textXml = String(bytes, Charsets.UTF_8)
            if (textXml.trimStart().startsWith("<")) {
                return parseTextXmlFallback(textXml)
            }
        }

        val strings = mutableListOf<String>()
        var packageName = ""
        var versionName = "1.0"
        var versionCode = 1L
        var minSdk: Int? = null
        var targetSdk: Int? = null
        var appLabel = ""

        val permissions = mutableListOf<PermissionDetail>()
        val activities = mutableListOf<ComponentDetail>()
        val services = mutableListOf<ComponentDetail>()
        val receivers = mutableListOf<ComponentDetail>()
        val providers = mutableListOf<ComponentDetail>()

        val currentTagStack = mutableListOf<String>()
        var currentComponent: TempComponent? = null
        var currentIntentFilter: TempIntentFilter? = null
        var launcherActivity: String? = null

        var offset = 8
        while (offset + 8 <= bytes.size) {
            val chunkType = buffer.getInt(offset)
            val chunkSize = buffer.getInt(offset + 4)
            if (chunkSize <= 0 || offset + chunkSize > bytes.size) break

            when (chunkType) {
                CHUNK_STRING_POOL -> {
                    strings.clear()
                    strings.addAll(parseStringPool(buffer, offset))
                }
                CHUNK_START_TAG -> {
                    val nameIdx = buffer.getInt(offset + 20)
                    val attrCount = buffer.getShort(offset + 28).toInt() and 0xFFFF
                    val tagName = strings.getOrNull(nameIdx) ?: ""
                    currentTagStack.add(tagName)

                    val attributes = parseAttributes(buffer, offset + 36, attrCount, strings)

                    when (tagName) {
                        "manifest" -> {
                            packageName = attributes["package"] ?: ""
                            versionName = attributes["versionName"] ?: "1.0"
                            versionCode = attributes["versionCode"]?.toLongOrNull() ?: 1L
                        }
                        "uses-sdk" -> {
                            minSdk = attributes["minSdkVersion"]?.toIntOrNull()
                            targetSdk = attributes["targetSdkVersion"]?.toIntOrNull()
                        }
                        "uses-permission" -> {
                            val permName = attributes["name"] ?: ""
                            if (permName.isNotEmpty()) {
                                permissions.add(PermissionDetail(name = permName, isDangerous = permName.contains("PERMISSION")))
                            }
                        }
                        "application" -> {
                            appLabel = attributes["label"] ?: ""
                        }
                        "activity" -> {
                            val name = resolveComponentName(packageName, attributes["name"] ?: "")
                            val exported = attributes["exported"]?.toBooleanStrictOrNull() ?: false
                            currentComponent = TempComponent(name = name, type = "activity", isExported = exported)
                        }
                        "activity-alias" -> {
                            val targetActivity = attributes["targetActivity"] ?: attributes["name"] ?: ""
                            val name = resolveComponentName(packageName, targetActivity)
                            val exported = attributes["exported"]?.toBooleanStrictOrNull() ?: false
                            currentComponent = TempComponent(name = name, type = "activity", isExported = exported)
                        }
                        "service" -> {
                            val name = resolveComponentName(packageName, attributes["name"] ?: "")
                            val exported = attributes["exported"]?.toBooleanStrictOrNull() ?: false
                            currentComponent = TempComponent(name = name, type = "service", isExported = exported)
                        }
                        "receiver" -> {
                            val name = resolveComponentName(packageName, attributes["name"] ?: "")
                            val exported = attributes["exported"]?.toBooleanStrictOrNull() ?: false
                            currentComponent = TempComponent(name = name, type = "receiver", isExported = exported)
                        }
                        "provider" -> {
                            val name = resolveComponentName(packageName, attributes["name"] ?: "")
                            val exported = attributes["exported"]?.toBooleanStrictOrNull() ?: false
                            currentComponent = TempComponent(name = name, type = "provider", isExported = exported)
                        }
                        "intent-filter" -> {
                            if (currentComponent != null) {
                                currentIntentFilter = TempIntentFilter()
                            }
                        }
                        "action" -> {
                            val actionName = attributes["name"] ?: ""
                            if (actionName.isNotEmpty()) {
                                currentIntentFilter?.actions?.add(actionName)
                            }
                        }
                        "category" -> {
                            val catName = attributes["name"] ?: ""
                            if (catName.isNotEmpty()) {
                                currentIntentFilter?.categories?.add(catName)
                                if (catName == "android.intent.category.LAUNCHER" && currentComponent?.type == "activity") {
                                    launcherActivity = currentComponent?.name
                                }
                            }
                        }
                    }
                }
                CHUNK_END_TAG -> {
                    val nameIdx = buffer.getInt(offset + 20)
                    val tagName = strings.getOrNull(nameIdx) ?: ""
                    if (currentTagStack.isNotEmpty()) currentTagStack.removeAt(currentTagStack.size - 1)

                    when (tagName) {
                        "intent-filter" -> {
                            if (currentComponent != null && currentIntentFilter != null) {
                                currentComponent.intentFilters.add(currentIntentFilter.toDetail())
                                currentIntentFilter = null
                            }
                        }
                        "activity", "activity-alias", "service", "receiver", "provider" -> {
                            if (currentComponent != null) {
                                val detail = currentComponent.toDetail()
                                when (currentComponent.type) {
                                    "activity" -> activities.add(detail)
                                    "service" -> services.add(detail)
                                    "receiver" -> receivers.add(detail)
                                    "provider" -> providers.add(detail)
                                }
                                currentComponent = null
                            }
                        }
                    }
                }
            }
            offset += chunkSize
        }

        val allComponentNames = (activities + services + receivers + providers).map { it.name }
        val duplicates = allComponentNames.groupBy { it }.filter { it.value.size > 1 }.keys.toList()

        val rawXmlPretty = buildString {
            appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
            appendLine("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\" package=\"$packageName\" android:versionCode=\"$versionCode\" android:versionName=\"$versionName\">")
            if (minSdk != null || targetSdk != null) {
                appendLine("    <uses-sdk android:minSdkVersion=\"${minSdk ?: 1}\" android:targetSdkVersion=\"${targetSdk ?: 35}\" />")
            }
            permissions.distinctBy { it.name }.forEach { p ->
                appendLine("    <uses-permission android:name=\"${p.name}\" />")
            }
            appendLine("    <application android:label=\"$appLabel\">")
            activities.forEach { a ->
                appendLine("        <activity android:name=\"${a.name}\" android:exported=\"${a.isExported}\" />")
            }
            services.forEach { s ->
                appendLine("        <service android:name=\"${s.name}\" android:exported=\"${s.isExported}\" />")
            }
            receivers.forEach { r ->
                appendLine("        <receiver android:name=\"${r.name}\" android:exported=\"${r.isExported}\" />")
            }
            providers.forEach { pr ->
                appendLine("        <provider android:name=\"${pr.name}\" android:exported=\"${pr.isExported}\" />")
            }
            appendLine("    </application>")
            appendLine("</manifest>")
        }

        return ManifestReport(
            packageName = packageName.ifBlank { "unknown.package" },
            versionName = versionName,
            versionCode = versionCode,
            minSdk = minSdk,
            targetSdk = targetSdk,
            appLabel = appLabel,
            permissions = permissions.distinctBy { it.name },
            activities = activities.distinctBy { it.name },
            services = services.distinctBy { it.name },
            receivers = receivers.distinctBy { it.name },
            providers = providers.distinctBy { it.name },
            launcherActivity = launcherActivity ?: activities.firstOrNull()?.name,
            duplicateComponents = duplicates,
            rawXml = rawXmlPretty
        )
    }

    private fun parseStringPool(buffer: ByteBuffer, offset: Int): List<String> {
        val stringCount = buffer.getInt(offset + 8)
        val flags = buffer.getInt(offset + 16)
        val stringsStart = offset + buffer.getInt(offset + 20)
        val isUtf8 = (flags and (1 shl 8)) != 0

        val stringOffsets = IntArray(stringCount)
        for (i in 0 until stringCount) {
            stringOffsets[i] = buffer.getInt(offset + 28 + i * 4)
        }

        val strings = mutableListOf<String>()
        for (i in 0 until stringCount) {
            val strOffset = stringsStart + stringOffsets[i]
            if (strOffset >= buffer.capacity()) {
                strings.add("")
                continue
            }
            val str = if (isUtf8) readUtf8String(buffer, strOffset) else readUtf16String(buffer, strOffset)
            strings.add(str)
        }
        return strings
    }

    private fun readUtf8String(buffer: ByteBuffer, offset: Int): String {
        try {
            var p = offset
            // Skip character count
            var charLen = buffer.get(p).toInt() and 0xFF
            if ((charLen and 0x80) != 0) { p++; charLen = ((charLen and 0x7F) shl 8) or (buffer.get(p).toInt() and 0xFF) }
            p++
            // Skip byte length
            var byteLen = buffer.get(p).toInt() and 0xFF
            if ((byteLen and 0x80) != 0) { p++; byteLen = ((byteLen and 0x7F) shl 8) or (buffer.get(p).toInt() and 0xFF) }
            p++
            if (p + byteLen > buffer.capacity()) return ""
            val bytes = ByteArray(byteLen)
            buffer.position(p)
            buffer.get(bytes)
            return String(bytes, Charsets.UTF_8)
        } catch (_: Exception) {
            return ""
        }
    }

    private fun readUtf16String(buffer: ByteBuffer, offset: Int): String {
        try {
            val charLen = buffer.getShort(offset).toInt() and 0xFFFF
            val p = offset + 2
            if (p + charLen * 2 > buffer.capacity()) return ""
            val chars = CharArray(charLen)
            for (i in 0 until charLen) {
                chars[i] = buffer.getShort(p + i * 2).toInt().toChar()
            }
            return String(chars)
        } catch (_: Exception) {
            return ""
        }
    }

    private fun parseAttributes(buffer: ByteBuffer, offset: Int, count: Int, strings: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var p = offset
        for (i in 0 until count) {
            if (p + 20 > buffer.capacity()) break
            val attrNameIdx = buffer.getInt(p + 4)
            val rawValIdx = buffer.getInt(p + 8)
            val typedValueType = buffer.get(p + 15).toInt() and 0xFF
            val typedValueData = buffer.getInt(p + 16)

            val attrName = strings.getOrNull(attrNameIdx) ?: "attr_$i"
            val value = when {
                rawValIdx >= 0 -> strings.getOrNull(rawValIdx) ?: ""
                typedValueType == 0x03 -> strings.getOrNull(typedValueData) ?: ""
                typedValueType == 0x12 -> (typedValueData != 0).toString()
                typedValueType >= 0x10 && typedValueType <= 0x1F -> typedValueData.toString()
                else -> typedValueData.toString()
            }
            result[attrName] = value
            p += 20
        }
        return result
    }

    private fun resolveComponentName(packageName: String, name: String): String {
        return when {
            name.startsWith(".") -> packageName + name
            !name.contains(".") -> "$packageName.$name"
            else -> name
        }
    }

    private fun parseTextXmlFallback(xml: String): ManifestReport {
        val pkg = Regex("package=\"([^\"]+)\"").find(xml)?.groupValues?.get(1) ?: "fallback.package"
        val vName = Regex("versionName=\"([^\"]+)\"").find(xml)?.groupValues?.get(1) ?: "1.0"
        val vCode = Regex("versionCode=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
        val min = Regex("minSdkVersion=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)?.toIntOrNull()
        val tgt = Regex("targetSdkVersion=\"([^\"]+)\"").find(xml)?.groupValues?.get(1)?.toIntOrNull()

        val perms = Regex("<uses-permission[^>]+name=\"([^\"]+)\"").findAll(xml).map { PermissionDetail(it.groupValues[1]) }.toList()
        val acts = Regex("<activity[^>]+name=\"([^\"]+)\"").findAll(xml).map { ComponentDetail(it.groupValues[1], it.groupValues[1].substringAfterLast('.'), false) }.toList()
        val svcs = Regex("<service[^>]+name=\"([^\"]+)\"").findAll(xml).map { ComponentDetail(it.groupValues[1], it.groupValues[1].substringAfterLast('.'), false) }.toList()
        val rcvs = Regex("<receiver[^>]+name=\"([^\"]+)\"").findAll(xml).map { ComponentDetail(it.groupValues[1], it.groupValues[1].substringAfterLast('.'), false) }.toList()
        val prvs = Regex("<provider[^>]+name=\"([^\"]+)\"").findAll(xml).map { ComponentDetail(it.groupValues[1], it.groupValues[1].substringAfterLast('.'), false) }.toList()

        return ManifestReport(
            packageName = pkg, versionName = vName, versionCode = vCode, minSdk = min, targetSdk = tgt,
            appLabel = pkg.substringAfterLast('.'), permissions = perms, activities = acts, services = svcs,
            receivers = rcvs, providers = prvs, launcherActivity = acts.firstOrNull()?.name, duplicateComponents = emptyList(), rawXml = xml
        )
    }

    private class TempComponent(val name: String, val type: String, val isExported: Boolean) {
        val intentFilters = mutableListOf<IntentFilterDetail>()
        fun toDetail(): ComponentDetail = ComponentDetail(
            name = name, simpleName = name.substringAfterLast('.'), isExported = isExported, intentFilters = intentFilters.toList()
        )
    }

    private class TempIntentFilter {
        val actions = mutableListOf<String>()
        val categories = mutableListOf<String>()
        fun toDetail(): IntentFilterDetail = IntentFilterDetail(actions = actions.toList(), categories = categories.toList())
    }
}
