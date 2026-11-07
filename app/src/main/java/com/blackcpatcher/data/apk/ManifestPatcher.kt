package com.blackcpatcher.data.apk

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Configuration Manifest Auto-Parsing & Injection Engine.
 * Programmatically inserts Window Overlay Permission string and Telemetry Service Component Tag
 * into the Target application's AndroidManifest.xml before re-packaging.
 */
object ManifestPatcher {

    fun patchManifest(
        extractedTargetDir: File,
        permissionTag: String,
        serviceTag: String,
        log: (String) -> Unit
    ): Boolean {
        val manifestFile = File(extractedTargetDir, "AndroidManifest.xml")
        if (!manifestFile.exists()) {
            log("ERROR: AndroidManifest.xml not found in target extracted archive.")
            return false
        }

        val rawBytes = manifestFile.readBytes()
        val textContent = String(rawBytes, Charsets.UTF_8)

        val xmlToPatch = if (textContent.trimStart().startsWith("<")) {
            log("Text XML Manifest detected.")
            rawBytes
        } else {
            log("Binary AXML Manifest detected. Triggering robust text conversion fallback...")
            try {
                val report = BinaryAxmlParser.parse(rawBytes.inputStream())
                log("Successfully decompiled Binary AXML to Text XML structure.")
                report.rawXml.toByteArray(Charsets.UTF_8)
            } catch (e: Exception) {
                log("ERROR: Failed to decompile Binary AXML: ${e.message}")
                return false
            }
        }

        return patchTextXml(manifestFile, xmlToPatch, permissionTag, serviceTag, log)
    }

    private fun patchTextXml(manifestFile: File, xmlBytes: ByteArray, permTag: String, svcTag: String, log: (String) -> Unit): Boolean {
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc: Document = dBuilder.parse(ByteArrayInputStream(xmlBytes))
            doc.documentElement.normalize()

            val manifestElement = doc.documentElement

            // Insert Permission
            val permName = extractAttribute(permTag, "android:name") ?: "android.permission.SYSTEM_ALERT_WINDOW"
            val existingPerms = doc.getElementsByTagName("uses-permission")
            var permExists = false
            for (i in 0 until existingPerms.length) {
                val node = existingPerms.item(i) as Element
                if (node.getAttribute("android:name") == permName) {
                    permExists = true
                    break
                }
            }

            if (!permExists) {
                val newPerm = doc.createElement("uses-permission")
                newPerm.setAttribute("android:name", permName)
                val appNodeList = manifestElement.getElementsByTagName("application")
                if (appNodeList.length > 0) {
                    manifestElement.insertBefore(newPerm, appNodeList.item(0))
                    log("SUCCESS: Injected uses-permission: $permName right under <manifest>")
                }
            } else {
                log("Permission $permName already exists.")
            }

            // Insert Service
            val svcName = extractAttribute(svcTag, "android:name") ?: "com.android.support.Launcher"
            val applicationNodes = doc.getElementsByTagName("application")
            if (applicationNodes.length > 0) {
                val appElement = applicationNodes.item(0) as Element
                val existingServices = appElement.getElementsByTagName("service")
                var svcExists = false
                for (i in 0 until existingServices.length) {
                    val node = existingServices.item(i) as Element
                    if (node.getAttribute("android:name") == svcName) {
                        svcExists = true
                        break
                    }
                }

                if (!svcExists) {
                    val newSvc = doc.createElement("service")
                    newSvc.setAttribute("android:name", svcName)
                    newSvc.setAttribute("android:enabled", "true")
                    newSvc.setAttribute("android:exported", "false")
                    newSvc.setAttribute("android:stopWithTask", "true")
                    appElement.appendChild(newSvc)
                    log("SUCCESS: Injected service tag right before closing </application> tag")
                } else {
                    log("Service $svcName already exists.")
                }
            }

            // Save Modified DOM
            val transformer = TransformerFactory.newInstance().newTransformer()
            transformer.setOutputProperty(OutputKeys.INDENT, "yes")
            val source = DOMSource(doc)
            val out = ByteArrayOutputStream()
            val result = StreamResult(out)
            transformer.transform(source, result)
            
            manifestFile.writeBytes(out.toByteArray())
            log("DOM manipulation completed and patched AndroidManifest.xml saved.")
            return true
        } catch (e: Exception) {
            log("ERROR: DOM manipulation failed: ${e.message}")
            return false
        }
    }

    @Suppress("SameParameterValue")
    private fun extractAttribute(tag: String, attrName: String): String? {
        val regex = Regex("$attrName=\"([^\"]+)\"")
        return regex.find(tag)?.groupValues?.get(1)
    }
}
