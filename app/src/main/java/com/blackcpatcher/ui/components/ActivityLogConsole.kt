package com.blackcpatcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.blackcpatcher.domain.model.ActivityLogEntry
import com.blackcpatcher.domain.model.LogLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityLogConsole(
    logs: List<ActivityLogEntry>,
    onClearLogs: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Activity & Build Console Log",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                IconButton(onClick = onClearLogs, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear Console",
                        tint = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .background(Color.Black, shape = RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (logs.isEmpty()) {
                    item {
                        Text(
                            text = "Console ready. Operations will be logged here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.DarkGray,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    items(logs) { log ->
                        val timeStr = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(log.timestamp))
                        val color = when (log.level) {
                            LogLevel.INFO -> Color(0xFF81D4FA)
                            LogLevel.SUCCESS -> Color(0xFFA5D6A7)
                            LogLevel.WARNING -> Color(0xFFFFE082)
                            LogLevel.ERROR -> Color(0xFFEF9A9A)
                        }
                        Text(
                            text = "[$timeStr] ${log.message}",
                            color = color,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
