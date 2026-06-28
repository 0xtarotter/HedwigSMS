package com.hedwig.sms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedwig.sms.R
import com.hedwig.sms.core.Constants
import com.hedwig.sms.data.ForwardLog
import com.hedwig.sms.data.HedwigDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { HedwigDatabase.get(context).forwardLogDao() }
    val logs by dao.observeRecent(Constants.MAX_LOGS).collectAsState(initial = emptyList())
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }

    var query by remember { mutableStateOf("") }
    var simFilter by remember { mutableStateOf<String?>(null) }

    val sims = remember(logs) { logs.mapNotNull { it.sim }.distinct() }
    val filtered = logs.filter { log ->
        (simFilter == null || log.sim == simFilter) &&
            (query.isBlank() ||
                log.detail.contains(query, true) ||
                log.type.contains(query, true) ||
                (log.sim?.contains(query, true) == true))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { inner ->
        Column(Modifier.padding(inner).fillMaxSize().padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text(stringResource(R.string.logs_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
            // 按接收卡/号码过滤 / filter by receiving SIM
            if (sims.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                ) {
                    FilterChip(
                        selected = simFilter == null,
                        onClick = { simFilter = null },
                        label = { Text(stringResource(R.string.logs_filter_all)) },
                    )
                    sims.forEach { s ->
                        FilterChip(
                            selected = simFilter == s,
                            onClick = { simFilter = if (simFilter == s) null else s },
                            label = { Text(s, maxLines = 1) },
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                Text(
                    stringResource(R.string.logs_empty_filtered),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
                    items(filtered) { log ->
                        LogRow(log, fmt.format(Date(log.timestamp)))
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun LogRow(log: ForwardLog, time: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(if (log.success) "✅" else "❌")
        Column(Modifier.fillMaxWidth()) {
            Text("${typeLabel(log.type)} · $time", fontWeight = FontWeight.Medium, fontSize = 14.sp)
            log.sim?.let {
                Text("📲 $it", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
            val detail = if (log.success) log.detail else "${log.detail} (code=${log.statusCode})"
            Text(
                detail, fontSize = 12.sp,
                color = if (log.success) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFC62828),
            )
        }
    }
}

@Composable
private fun typeLabel(type: String): String = when (type) {
    "sms" -> "📩 " + stringResource(R.string.type_sms)
    "battery_low" -> "🪫 " + stringResource(R.string.type_battery_low)
    "power_connected" -> "🔌 " + stringResource(R.string.type_power_connected)
    "power_disconnected" -> "⚡️ " + stringResource(R.string.type_power_disconnected)
    "heartbeat" -> "💚 " + stringResource(R.string.type_heartbeat)
    "service_on" -> "✅ " + stringResource(R.string.status_guarding)
    "service_off" -> "🛑 " + stringResource(R.string.status_stopped)
    else -> type
}

@Composable
private fun stringResource(id: Int): String = androidx.compose.ui.res.stringResource(id)
