package com.aurora.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aurora.app.util.AppLog
import com.aurora.app.viewmodel.AuroraViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LogScreen(vm: AuroraViewModel, modifier: Modifier = Modifier) {
    val entries by AppLog.entries.collectAsState()
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    Column(modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Event log · ${entries.size}/200",
                style = MaterialTheme.typography.titleMedium
            )
            OutlinedButton(onClick = { vm.clearLog() }, enabled = entries.isNotEmpty()) {
                Text("Clear")
            }
        }
        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Nothing yet.\nCommands and responses appear here live.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(entries, key = { it.id }) { e ->
                    val dirColor = when (e.dir) {
                        AppLog.Dir.SENT -> MaterialTheme.colorScheme.primary
                        AppLog.Dir.RECV -> MaterialTheme.colorScheme.secondary
                        AppLog.Dir.SYSTEM -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            timeFormat.format(Date(e.timeMillis)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            when (e.dir) {
                                AppLog.Dir.SENT -> "→"
                                AppLog.Dir.RECV -> "←"
                                AppLog.Dir.SYSTEM -> "·"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = dirColor
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text(
                            e.text,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace,
                            color = dirColor,
                            maxLines = 3
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
