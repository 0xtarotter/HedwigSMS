package com.hedwig.sms.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedwig.sms.R
import com.hedwig.sms.alarm.HeartbeatScheduler
import com.hedwig.sms.data.SecurePrefs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onLanguageChanged: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs(context) }

    var autoUpdate by remember { mutableStateOf(prefs.autoCheckUpdate) }
    var bootStart by remember { mutableStateOf(prefs.bootAutoStart) }
    var keepAlive by remember { mutableStateOf(prefs.keepAliveEnabled) }
    var powerNotify by remember { mutableStateOf(prefs.powerNotifyEnabled) }
    var heartbeat by remember { mutableStateOf(prefs.heartbeatEnabled) }
    var notif by remember { mutableStateOf(prefs.notifDynamic) }
    var hbHour by remember { mutableStateOf(prefs.heartbeatHour) }
    var hbMin by remember { mutableStateOf(prefs.heartbeatMinute) }
    var lang by remember { mutableStateOf(prefs.language) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { inner ->
        Column(
            Modifier.padding(inner).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 通用 / general
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    SectionLabel(stringResource(R.string.settings_section_general))
                    SwitchRow(stringResource(R.string.set_auto_update), autoUpdate) {
                        autoUpdate = it; prefs.autoCheckUpdate = it
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.set_boot_autostart), bootStart) {
                        bootStart = it; prefs.bootAutoStart = it
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.set_keep_alive), keepAlive) {
                        keepAlive = it; prefs.keepAliveEnabled = it
                        if (it) com.hedwig.sms.alarm.KeepAliveScheduler.schedule(context)
                        else com.hedwig.sms.alarm.KeepAliveScheduler.cancel(context)
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.set_power_notify), powerNotify) {
                        powerNotify = it; prefs.powerNotifyEnabled = it
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.set_heartbeat), heartbeat) {
                        heartbeat = it; prefs.heartbeatEnabled = it
                        if (it) HeartbeatScheduler.schedule(context) else HeartbeatScheduler.cancel(context)
                    }
                    HorizontalDivider()
                    SwitchRow(stringResource(R.string.set_notification), notif) {
                        notif = it; prefs.notifDynamic = it
                        // 即时刷新通知栏 / refresh the ongoing notification now.
                        if (com.hedwig.sms.core.HedwigState.running.value) {
                            context.startService(
                                android.content.Intent(context, com.hedwig.sms.service.HedwigForwardService::class.java)
                                    .setAction(com.hedwig.sms.core.Constants.ACTION_EVENT)
                                    .putExtra(com.hedwig.sms.core.Constants.EXTRA_EVENT_TYPE, "noop"),
                            )
                        }
                    }
                    HorizontalDivider()
                    // 每日报告时间 / daily report time
                    RowItem(
                        title = stringResource(R.string.set_daily_time),
                        value = "%02d:%02d".format(hbHour, hbMin),
                        enabled = heartbeat,
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m ->
                                    hbHour = h; hbMin = m
                                    prefs.heartbeatHour = h; prefs.heartbeatMinute = m
                                    HeartbeatScheduler.schedule(context)
                                },
                                hbHour, hbMin, true,
                            ).show()
                        },
                    )
                    HorizontalDivider()
                    // 语言 / language
                    LanguageRow(lang) { code ->
                        lang = code
                        prefs.language = code
                        onLanguageChanged()
                    }
                }
            }

            // 数据 / data
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel(stringResource(R.string.settings_section_data))
                    OutlinedButton(onClick = onOpenLogs, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.settings_view_logs))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text, fontSize = 13.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f), fontSize = 15.sp)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun RowItem(title: String, value: String, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title, Modifier.weight(1f), fontSize = 15.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
        )
        Text(value, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun LanguageRow(current: String, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (current) {
        "zh" -> stringResource(R.string.lang_zh)
        "en" -> stringResource(R.string.lang_en)
        else -> stringResource(R.string.lang_system)
    }
    Box {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.set_language), Modifier.weight(1f), fontSize = 15.sp)
            Text(label, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.lang_system)) },
                onClick = { expanded = false; onPick("") })
            DropdownMenuItem(text = { Text(stringResource(R.string.lang_zh)) },
                onClick = { expanded = false; onPick("zh") })
            DropdownMenuItem(text = { Text(stringResource(R.string.lang_en)) },
                onClick = { expanded = false; onPick("en") })
        }
    }
}
