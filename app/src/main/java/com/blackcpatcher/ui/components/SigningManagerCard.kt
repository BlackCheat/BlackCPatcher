package com.blackcpatcher.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.blackcpatcher.domain.model.SigningConfig

@Composable
fun SigningManagerCard(
    config: SigningConfig,
    onConfigChange: (SigningConfig) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "APK Signing Manager",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            // Auto Debug Key vs Custom KeyStore Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Auto Debug Key (RSA 2048-bit)", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Automatically generate and sign with self-signed RSA debug cert",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = config.useAutoDebugKey,
                    onCheckedChange = { onConfigChange(config.copy(useAutoDebugKey = it)) }
                )
            }

            if (!config.useAutoDebugKey) {
                OutlinedTextField(
                    value = config.keystorePath,
                    onValueChange = { onConfigChange(config.copy(keystorePath = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom Keystore Path (.jks / .p12)") }
                )
                OutlinedTextField(
                    value = config.keyAlias,
                    onValueChange = { onConfigChange(config.copy(keyAlias = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Key Alias") }
                )
            }

            HorizontalDivider()

            // Signature Scheme Checkboxes
            Text("Signature Schemes Enabled:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.enableV1,
                        onCheckedChange = { onConfigChange(config.copy(enableV1 = it)) }
                    )
                    Text("V1 (JAR)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.enableV2,
                        onCheckedChange = { onConfigChange(config.copy(enableV2 = it)) }
                    )
                    Text("V2 (APK Scheme)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = config.enableV3,
                        onCheckedChange = { onConfigChange(config.copy(enableV3 = it)) }
                    )
                    Text("V3 (Rotation)")
                }
            }
        }
    }
}
