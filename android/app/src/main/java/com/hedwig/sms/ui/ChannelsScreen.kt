package com.hedwig.sms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedwig.sms.R
import com.hedwig.sms.data.SecurePrefs
import com.hedwig.sms.model.ChannelConfig
import com.hedwig.sms.model.HedwigData
import com.hedwig.sms.model.HedwigPayload
import com.hedwig.sms.net.DirectDispatcher
import com.hedwig.sms.net.TelegramHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ChannelsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs(context) }
    val scope = rememberCoroutineScope()
    val initial = remember { ChannelConfig.fromJson(prefs.channelConfigJson) }

    var tgToken by remember { mutableStateOf(initial.telegramBotToken) }
    var tgChat by remember { mutableStateOf(initial.telegramChatId) }
    var feishuUrl by remember { mutableStateOf(initial.feishuWebhookUrl) }
    var feishuSecret by remember { mutableStateOf(initial.feishuSignSecret) }
    var discordUrl by remember { mutableStateOf(initial.discordWebhookUrl) }
    var slackUrl by remember { mutableStateOf(initial.slackWebhookUrl) }
    var customUrl by remember { mutableStateOf(initial.customWebhookUrl) }
    var resendKey by remember { mutableStateOf(initial.resendApiKey) }
    var emailFrom by remember { mutableStateOf(initial.emailFrom) }
    var emailTo by remember { mutableStateOf(initial.emailTo) }

    var tgChats by remember { mutableStateOf<List<TelegramHelper.Chat>>(emptyList()) }
    // 每渠道状态：是否成功 + 文本 / per-channel status (ok, text)
    val status = remember { mutableStateMapOf<String, Pair<Boolean, String>>() }

    var qrToShow by remember { mutableStateOf<String?>(null) }

    // 扫码导入整套配置 / scan to import the full config
    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract(),
    ) { result ->
        when (val scanned = com.hedwig.sms.core.HedwigConfig.parseAny(result.contents)) {
            null -> toast(context, if (result.contents == null) R.string.ch_scan_cancelled else R.string.ch_scan_fail)
            is com.hedwig.sms.core.HedwigConfig.Scanned.Full -> {
                val c = scanned.config.channels
                tgToken = c.telegramBotToken; tgChat = c.telegramChatId
                feishuUrl = c.feishuWebhookUrl; feishuSecret = c.feishuSignSecret
                discordUrl = c.discordWebhookUrl; slackUrl = c.slackWebhookUrl
                customUrl = c.customWebhookUrl
                resendKey = c.resendApiKey; emailFrom = c.emailFrom; emailTo = c.emailTo
                prefs.channelConfigJson = c.toJson()
                if (scanned.config.device.isNotBlank()) prefs.deviceName = scanned.config.device
                if (scanned.config.mode.isNotBlank()) prefs.mode = scanned.config.mode
                if (scanned.config.workerUrl.isNotBlank()) prefs.workerUrl = scanned.config.workerUrl
                if (scanned.config.token.isNotBlank()) prefs.token = scanned.config.token
                toast(context, R.string.ch_scan_ok)
            }
            is com.hedwig.sms.core.HedwigConfig.Scanned.Relay -> {
                // 扫到的是中转配置：保存并切到中转模式 / a relay config was scanned.
                prefs.workerUrl = scanned.config.url
                prefs.token = scanned.config.token
                if (scanned.config.device.isNotBlank()) prefs.deviceName = scanned.config.device
                prefs.mode = SecurePrefs.MODE_RELAY
                toast(context, R.string.ch_scan_ok)
            }
        }
    }

    fun cfg() = ChannelConfig(
        telegramBotToken = tgToken, telegramChatId = tgChat,
        feishuWebhookUrl = feishuUrl, feishuSignSecret = feishuSecret,
        discordWebhookUrl = discordUrl, slackWebhookUrl = slackUrl,
        customWebhookUrl = customUrl,
        resendApiKey = resendKey, emailFrom = emailFrom, emailTo = emailTo,
    )

    fun save() {
        prefs.channelConfigJson = cfg().toJson()
        toast(context, R.string.ch_saved)
    }

    fun test(id: String) {
        status[id] = true to context.getString(R.string.ch_sending)
        scope.launch {
            val r = DirectDispatcher.send(
                id, cfg(),
                HedwigPayload(
                    type = HedwigPayload.TYPE_HEARTBEAT,
                    deviceName = prefs.deviceName.ifBlank { "测试 / Test" },
                    timestamp = System.currentTimeMillis(),
                    data = HedwigData(batteryLevel = 100),
                ),
            )
            status[id] = r.ok to if (r.ok) context.getString(R.string.ch_test_ok)
            else context.getString(R.string.ch_test_fail, r.error ?: "error")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ch_title)) },
                navigationIcon = {
                    IconButton(onClick = { save(); onBack() }) {
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
            Text(stringResource(R.string.ch_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 备份/迁移：导出二维码 + 扫码导入 / backup & transfer
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        val full = com.hedwig.sms.model.FullConfig(
                            mode = prefs.mode, device = prefs.deviceName,
                            workerUrl = prefs.workerUrl, token = prefs.token, channels = cfg(),
                        )
                        qrToShow = full.encode()
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) { Text(stringResource(R.string.ch_export_qr), maxLines = 1, softWrap = false) }
                OutlinedButton(
                    onClick = {
                        val options = com.journeyapps.barcodescanner.ScanOptions()
                            .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
                            .setBeepEnabled(false).setOrientationLocked(true)
                            .setCaptureActivity(ScannerActivity::class.java)
                        scanLauncher.launch(options)
                    },
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
                ) { Text(stringResource(R.string.ch_scan_import), maxLines = 1, softWrap = false) }
            }

            // ---- Telegram ----
            ChannelCard("✈️ Telegram", status[ChannelConfig.ID_TELEGRAM]) {
                Field("Bot Token", tgToken) { tgToken = it }
                OutlinedButton(
                    onClick = {
                        if (tgToken.isBlank()) { status[ChannelConfig.ID_TELEGRAM] = false to context.getString(R.string.ch_need_token); return@OutlinedButton }
                        status[ChannelConfig.ID_TELEGRAM] = true to context.getString(R.string.ch_querying)
                        scope.launch {
                            TelegramHelper.discover(tgToken).onSuccess { d ->
                                tgChats = d.chats
                                status[ChannelConfig.ID_TELEGRAM] = (d.chats.isNotEmpty()) to
                                    if (d.chats.isEmpty()) context.getString(R.string.ch_tg_none, d.botUsername)
                                    else context.getString(R.string.ch_tg_found, d.botUsername, d.chats.size)
                            }.onFailure {
                                status[ChannelConfig.ID_TELEGRAM] = false to (it.message ?: "error")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.ch_tg_discover)) }
                if (tgChats.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        tgChats.forEach { c ->
                            AssistChip(onClick = { tgChat = c.id }, label = { Text("${c.title} (${c.id})") })
                        }
                    }
                }
                Field("Chat ID", tgChat) { tgChat = it }
                TestButton { test(ChannelConfig.ID_TELEGRAM) }
            }

            // ---- 飞书 / Feishu ----
            ChannelCard("🐦 ${stringResource(R.string.type_feishu_name)}", status[ChannelConfig.ID_FEISHU]) {
                Field("Webhook URL", feishuUrl) { feishuUrl = it }
                Field(stringResource(R.string.ch_feishu_secret), feishuSecret) { feishuSecret = it }
                TestButton { test(ChannelConfig.ID_FEISHU) }
            }

            // ---- Discord ----
            ChannelCard("🎮 Discord", status[ChannelConfig.ID_DISCORD]) {
                Field("Webhook URL", discordUrl) { discordUrl = it }
                TestButton { test(ChannelConfig.ID_DISCORD) }
            }

            // ---- Slack ----
            ChannelCard("💬 Slack", status[ChannelConfig.ID_SLACK]) {
                Field("Incoming Webhook URL", slackUrl) { slackUrl = it }
                TestButton { test(ChannelConfig.ID_SLACK) }
            }

            // ---- 通用 Webhook ----
            ChannelCard("🔗 ${stringResource(R.string.ch_custom_name)}", status[ChannelConfig.ID_CUSTOM]) {
                Field("Webhook URL", customUrl) { customUrl = it }
                Text(stringResource(R.string.ch_custom_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TestButton { test(ChannelConfig.ID_CUSTOM) }
            }

            // ---- Email ----
            ChannelCard("✉️ Email (Resend)", status[ChannelConfig.ID_EMAIL]) {
                Field("Resend API Key", resendKey) { resendKey = it }
                Field(stringResource(R.string.ch_email_from), emailFrom) { emailFrom = it }
                Field(stringResource(R.string.ch_email_to), emailTo) { emailTo = it }
                TestButton { test(ChannelConfig.ID_EMAIL) }
            }

            Button(onClick = { save() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ch_save))
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    qrToShow?.let { content -> ConfigQrDialog(content) { qrToShow = null } }
}

@Composable
private fun ConfigQrDialog(content: String, onDismiss: () -> Unit) {
    val bitmap = remember(content) {
        com.journeyapps.barcodescanner.BarcodeEncoder()
            .encodeBitmap(content, com.google.zxing.BarcodeFormat.QR_CODE, 720, 720)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ch_qr_close))
            }
        },
        title = { Text(stringResource(R.string.ch_qr_title)) },
        text = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(260.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.ch_qr_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
    )
}

@Composable
private fun ChannelCard(
    title: String,
    status: Pair<Boolean, String>?,
    content: @Composable () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
            content()
            status?.let {
                Text(it.second, fontSize = 13.sp,
                    color = if (it.first) MaterialTheme.colorScheme.primary else Color(0xFFC62828))
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onValue: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onValue,
        label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TestButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ch_test))
    }
}

private fun toast(context: android.content.Context, resId: Int) {
    android.widget.Toast.makeText(context, context.getString(resId), android.widget.Toast.LENGTH_SHORT).show()
}
