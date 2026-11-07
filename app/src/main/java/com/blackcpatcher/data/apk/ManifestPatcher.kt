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
        if (!manifestFile.exists()) { log("ERROR: AndroidManifest.xml not found"); return false }

        val rawBytes = manifestFile.readBytes()
        val text = String(rawBytes, Charsets.UTF_8)

        // Real APKs always have binary AXML. Text path kept only as safety net.
        if (text.trimStart().startsWith("<")) {
            log("Text XML manifest detected — using text path")
            return patchTextXml(manifestFile, rawBytes, permissionTag, serviceTag, log)
        }

        return try {
            val report = BinaryAxmlParser.parse(rawBytes.inputStream())
            val permName = extractAttribute(permissionTag, "android:name")
                ?: "android.permission.SYSTEM_ALERT_WINDOW"
            val svcName = extractAttribute(serviceTag, "android:name")
                ?: "com.android.support.Launcher"

            val needPerm = report.permissions.none { it.name == permName }
            val needSvc  = report.services.none { it.name == svcName }

            if (!needPerm) log("Permission $permName already exists — skipping")
            if (!needSvc) log("Service $svcName already exists — skipping")

            if (!needPerm && !needSvc) { log("Manifest already fully patched"); return true }

            val patched = BinaryAxmlPatcher.patch(
                manifestBytes = rawBytes,
                permissionName = if (needPerm) permName else null,
                serviceName = if (needSvc) svcName else null
            )
            manifestFile.writeBytes(patched)
            log("SUCCESS: Binary manifest patched (${patched.size} bytes, still binary AXML)")
            true
        } catch (e: Exception) {
            log("ERROR: Binary manifest patch failed: ${e.message}")
            false
        }
    }

    private fun patchTextXml(manifestFile: File, xmlBytes: ByteArray, permTag: String, svcTag: String, log: (String) -> Unit): Boolean {
        try {
            val dbFactory = DocumentBuilderFactory.newInstance()
            dbFactory.isNamespaceAware = true
            dbFactory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
            dbFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            dbFactory.setFeature("http://xml.org/sax/features/external-general-entities", false)
            dbFactory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            dbFactory.setXIncludeAware(false)
            dbFactory.isExpandEntityReferences = false
            val dBuilder = dbFactory.newDocumentBuilder()
            val doc: Document = dBuilder.parse(ByteArrayInputStream(xmlBytes))
            doc.documentElement.normalize()

            val manifestElement = doc.documentElement
            require(manifestElement.tagName == "manifest") { "Root XML element must be <manifest>." }
            val applicationNodes = doc.getElementsByTagName("application")
            require(applicationNodes.length > 0) { "Manifest does not contain an <application> element." }

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
                newPerm.setAttributeNS("http://schemas.android.com/apk/res/android", "android:name", permName)
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
            val applicationNodes2 = doc.getElementsByTagName("application")
            if (applicationNodes2.length > 0) {
                val appElement = applicationNodes2.item(0) as Element
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
                    newSvc.setAttributeNS("http://schemas.android.com/apk/res/android", "android:name", svcName)
                    newSvc.setAttributeNS("http://schemas.android.com/apk/res/android", "android:enabled", "true")
                    newSvc.setAttributeNS("http://schemas.android.com/apk/res/android", "android:exported", "false")
                    newSvc.setAttributeNS("http://schemas.android.com/apk/res/android", "android:stopWithTask", "true")
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
