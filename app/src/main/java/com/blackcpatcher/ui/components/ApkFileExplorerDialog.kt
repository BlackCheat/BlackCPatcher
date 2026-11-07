package com.blackcpatcher.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.blackcpatcher.domain.model.DexClassInfo

sealed class FileNode(val name: String) {
    class Directory(name: String, val children: MutableMap<String, FileNode> = mutableMapOf()) : FileNode(name)
    class File(name: String, val path: String) : FileNode(name)
}

@Composable
fun ApkFileExplorerDialog(
    classes: List<DexClassInfo>,
    onDismiss: () -> Unit,
    onFileSelected: (String) -> Unit
) {
    // Build Virtual File System
    val root = remember(classes) {
        val rootNode = FileNode.Directory("root")
        
        // Add AndroidManifest.xml
        rootNode.children["AndroidManifest.xml"] = FileNode.File("AndroidManifest.xml", "AndroidManifest.xml")
        
        // Build DEX Classes Tree
        val classesDex = FileNode.Directory("classes.dex")
        rootNode.children["classes.dex"] = classesDex

        classes.forEach { cls ->
            val parts = cls.className.split(".")
            var currentDir = classesDex
            for (i in 0 until parts.size - 1) {
                val part = parts[i]
                if (!currentDir.children.containsKey(part)) {
                    currentDir.children[part] = FileNode.Directory(part)
                }
                currentDir = currentDir.children[part] as FileNode.Directory
            }
            val fileName = parts.last() + ".smali"
            currentDir.children[fileName] = FileNode.File(fileName, cls.className.replace(".", "/") + ".smali")
        }
        rootNode
    }

    var currentPath by remember { mutableStateOf(listOf<FileNode.Directory>(root)) }
    val currentDir = currentPath.last()

    val items = currentDir.children.values.sortedWith(compareBy({ it is FileNode.File }, { it.name }))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (currentPath.size > 1) {
                            IconButton(onClick = { currentPath = currentPath.dropLast(1) }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                        Text(
                            text = if (currentPath.size == 1) "APK Root" else currentPath.drop(1).joinToString("/") { it.name },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismiss) {
                            Text("Cancel")
                        }
                    }
                }

                // File List
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items) { node ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    when (node) {
                                        is FileNode.Directory -> {
                                            currentPath = currentPath + node
                                        }
                                        is FileNode.File -> {
                                            onFileSelected(node.path)
                                        }
                                    }
                                }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (node is FileNode.Directory) Icons.Default.Folder else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (node is FileNode.Directory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = node.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (node is FileNode.Directory) {
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    }
                }
            }
        }
    }
}
