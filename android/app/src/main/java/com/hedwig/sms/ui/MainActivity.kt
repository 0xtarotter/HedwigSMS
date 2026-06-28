package com.hedwig.sms.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hedwig.sms.R
import com.hedwig.sms.core.Constants
import com.hedwig.sms.core.HedwigState
import com.hedwig.sms.data.ForwardLog
import com.hedwig.sms.data.HedwigDatabase
import com.hedwig.sms.data.SecurePrefs
import com.hedwig.sms.service.HedwigForwardService
import com.hedwig.sms.ui.theme.HedwigTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.hedwig.sms.core.LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeAutoStart(intent)
        setContent {
            HedwigTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        maybeAutoStart(intent)
    }

    /** 来自磁贴/小插件的「打开并自动启动守护」/ auto-start guard when opened from tile/widget. */
    private fun maybeAutoStart(intent: Intent?) {
        if (intent?.getBooleanExtra(Constants.EXTRA_AUTOSTART, false) != true) return
        val prefs = SecurePrefs(this)
        if (!prefs.isConfigured()) return
        val svc = Intent(this, HedwigForwardService::class.java).apply { action = Constants.ACTION_START }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(svc) else startService(svc)
    }
}

private enum class Screen { ONBOARDING, MAIN, HELP, CHANNELS, SETTINGS, LOGS }

@Composable
private fun AppRoot() {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var screen by remember {
        mutableStateOf(if (prefs.onboardingDone) Screen.MAIN else Screen.ONBOARDING)
    }
    var update by remember { mutableStateOf<com.hedwig.sms.net.UpdateChecker.UpdateInfo?>(null) }

    // 启动时静默检查更新（可在设置里关闭）/ silent update check on launch (toggleable in Settings)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (prefs.autoCheckUpdate) {
            (com.hedwig.sms.net.UpdateChecker.check(com.hedwig.sms.BuildConfig.VERSION_NAME)
                as? com.hedwig.sms.net.UpdateChecker.Result.Available)?.let { update = it.info }
        }
    }
    fun manualCheck() {
        toast(context, R.string.update_checking)
        scope.launch {
            when (val r = com.hedwig.sms.net.UpdateChecker.check(com.hedwig.sms.BuildConfig.VERSION_NAME)) {
                is com.hedwig.sms.net.UpdateChecker.Result.Available -> update = r.info
                is com.hedwig.sms.net.UpdateChecker.Result.UpToDate -> toast(context, R.string.update_latest)
                is com.hedwig.sms.net.UpdateChecker.Result.NotConfigured -> toast(context, R.string.update_not_configured)
                is com.hedwig.sms.net.UpdateChecker.Result.Failed ->
                    android.widget.Toast.makeText(context, context.getString(R.string.update_failed, r.message), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    when (screen) {
        Screen.ONBOARDING -> OnboardingScreen(onFinish = {
            prefs.onboardingDone = true
            screen = Screen.MAIN
        })
        Screen.MAIN -> HedwigScreen(
            onOpenHelp = { screen = Screen.HELP },
            onOpenChannels = { screen = Screen.CHANNELS },
            onOpenLogs = { screen = Screen.LOGS },
        )
        Screen.HELP -> HelpScreen(
            onBack = { screen = Screen.MAIN },
            onReplayOnboarding = { screen = Screen.ONBOARDING },
            onCheckUpdate = { manualCheck() },
            onOpenSettings = { screen = Screen.SETTINGS },
        )
        Screen.SETTINGS -> SettingsScreen(
            onBack = { screen = Screen.HELP },
            onOpenLogs = { screen = Screen.LOGS },
            onLanguageChanged = { (context as? android.app.Activity)?.recreate() },
        )
        Screen.LOGS -> LogsScreen(onBack = { screen = Screen.MAIN })
        Screen.CHANNELS -> ChannelsScreen(onBack = { screen = Screen.MAIN })
    }

    update?.let { info ->
        UpdateDialog(
            info = info,
            onDismiss = { update = null },
            onDownload = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(info.downloadUrl))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
                update = null
            },
        )
    }
}

@Composable
private fun UpdateDialog(
    info: com.hedwig.sms.net.UpdateChecker.UpdateInfo,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDownload) {
                Text(stringResource(R.string.update_download))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_later))
            }
        },
        title = { Text(stringResource(R.string.update_title, info.version)) },
        text = {
            Text(
                info.notes.ifBlank { info.version },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HedwigScreen(onOpenHelp: () -> Unit, onOpenChannels: () -> Unit, onOpenLogs: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { SecurePrefs(context) }
    val dao = remember { HedwigDatabase.get(context).forwardLogDao() }

    var mode by remember { mutableStateOf(prefs.mode) }
    var workerUrl by remember { mutableStateOf(prefs.workerUrl) }
    var token by remember { mutableStateOf(prefs.token) }
    var deviceName by remember { mutableStateOf(prefs.deviceName) }
    // 直连模式下已配置的渠道数（返回本页时重新读取）/ configured channel count in direct mode.
    val directChannelCount = remember(mode) {
        com.hedwig.sms.model.ChannelConfig.fromJson(prefs.channelConfigJson).configuredIds().size
    }
    val isConfigured = if (mode == SecurePrefs.MODE_DIRECT) directChannelCount > 0
        else workerUrl.isNotBlank() && token.isNotBlank()

    val running by HedwigState.running.collectAsState()
    val phase by HedwigState.phase.collectAsState()
    val battery by HedwigState.batteryLevel.collectAsState()
    val successCount by HedwigState.successCount.collectAsState()

    // 主屏只显示最近 10 条；完整日志在设置里 / main shows latest 10, full logs in Settings.
    val logs by dao.observeRecent(10)
        .collectAsState(initial = emptyList())

    // 权限请求 / permission launchers
    val smsPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* result handled by re-check */ }
    val notifPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val simPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { }

    // 统一扫码：自动识别「中转快速配置」或「整套配置」/ unified scan, auto-detect type.
    var qrToShow by remember { mutableStateOf<String?>(null) }
    val scanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract(),
    ) { result ->
        when (val scanned = com.hedwig.sms.core.HedwigConfig.parseAny(result.contents)) {
            null -> toast(context, if (result.contents == null) R.string.scan_cancelled else R.string.scan_failed)
            is com.hedwig.sms.core.HedwigConfig.Scanned.Relay -> {
                val c = scanned.config
                workerUrl = c.url; token = c.token
                prefs.workerUrl = c.url; prefs.token = c.token
                if (c.device.isNotBlank()) { deviceName = c.device; prefs.deviceName = c.device }
                prefs.mode = SecurePrefs.MODE_RELAY; mode = SecurePrefs.MODE_RELAY
                toast(context, R.string.scan_success)
            }
            is com.hedwig.sms.core.HedwigConfig.Scanned.Full -> {
                val f = scanned.config
                prefs.channelConfigJson = f.channels.toJson()
                if (f.device.isNotBlank()) { deviceName = f.device; prefs.deviceName = f.device }
                if (f.workerUrl.isNotBlank()) { workerUrl = f.workerUrl; prefs.workerUrl = f.workerUrl }
                if (f.token.isNotBlank()) { token = f.token; prefs.token = f.token }
                if (f.mode.isNotBlank()) { prefs.mode = f.mode; mode = f.mode }
                toast(context, R.string.scan_success_full)
            }
        }
    }
    fun launchScan() {
        val options = com.journeyapps.barcodescanner.ScanOptions()
            .setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE)
            .setBeepEnabled(false)
            .setOrientationLocked(true)
            .setCaptureActivity(ScannerActivity::class.java)
        scanLauncher.launch(options)
    }

    qrToShow?.let { content ->
        QrDialog(content = content, onDismiss = { qrToShow = null })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.hedwig_logo),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = { launchScan() }) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = stringResource(R.string.menu_scan),
                        )
                    }
                    IconButton(onClick = onOpenHelp) {
                        Icon(
                            Icons.AutoMirrored.Filled.HelpOutline,
                            contentDescription = stringResource(R.string.menu_help),
                        )
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatusCard(running, phase, battery, successCount)

            ModeCard(mode = mode, onMode = { prefs.mode = it; mode = it })

            // 设备名（两种模式都需要）/ device name (used by both modes)
            DeviceNameCard(deviceName, onChange = { deviceName = it; prefs.deviceName = it })

            if (mode == SecurePrefs.MODE_DIRECT) {
                DirectModeCard(channelCount = directChannelCount, onConfigure = onOpenChannels)
            } else {
                ConfigCard(
                    workerUrl = workerUrl,
                    token = token,
                    onWorkerUrl = { workerUrl = it },
                    onToken = { token = it },
                    onSave = {
                        prefs.workerUrl = workerUrl
                        prefs.token = token
                    },
                    onScanImport = { launchScan() },
                    onShowQr = {
                        if (workerUrl.isBlank() || token.isBlank()) {
                            toast(context, R.string.qr_need_config)
                        } else {
                            qrToShow = com.hedwig.sms.core.HedwigConfig.encode(workerUrl, token, deviceName)
                        }
                    },
                )
            }

            ActionsCard(
                running = running,
                isConfigured = isConfigured,
                onSaveBeforeAction = {
                    prefs.workerUrl = workerUrl
                    prefs.token = token
                    prefs.deviceName = deviceName
                },
            )

            PermissionCard(
                onRequestSms = { smsPermLauncher.launch(Manifest.permission.RECEIVE_SMS) },
                onRequestNotif = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                },
                onRequestBattery = { requestIgnoreBatteryOptimizations(context) },
                onRequestSim = {
                    val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS)
                    } else {
                        arrayOf(Manifest.permission.READ_PHONE_STATE)
                    }
                    simPermLauncher.launch(perms)
                },
            )

            LogsCard(logs, onViewAll = onOpenLogs)

            Text(
                text = stringResource(R.string.privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun StatusCard(running: Boolean, phase: HedwigState.Phase, battery: Int, count: Int) {
    val (emoji, statusText) = when {
        !running -> "⚪" to stringResource(R.string.status_stopped)
        phase == HedwigState.Phase.FORWARDING -> "🔄" to stringResource(R.string.status_forwarding)
        phase == HedwigState.Phase.FAILED -> "⚠️" to stringResource(R.string.status_failed)
        else -> "🟢" to stringResource(R.string.status_guarding)
    }
    val gradient = when {
        !running -> listOf(Color(0xFF3A4252), Color(0xFF20262F))
        phase == HedwigState.Phase.FORWARDING -> listOf(Color(0xFFC2740B), Color(0xFF7C3D0A))
        phase == HedwigState.Phase.FAILED -> listOf(Color(0xFFC62F2F), Color(0xFF7F1D1D))
        else -> listOf(Color(0xFF0FB47A), Color(0xFF0B5138))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(gradient))
            .padding(22.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.hedwig_logo),
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(stringResource(R.string.app_name),
                        color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
                    Text("$emoji  $statusText",
                        color = Color.White, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatPill("🔋", if (battery in 0..100) "$battery%" else "—",
                    stringResource(R.string.stat_battery), Modifier.weight(1f))
                StatPill("📈", "$count",
                    stringResource(R.string.stat_forwarded), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatPill(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column {
            Text("$emoji  $value", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(label, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ModeCard(mode: String, onMode: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(stringResource(R.string.section_mode))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                val relay = mode != SecurePrefs.MODE_DIRECT
                ModePill(stringResource(R.string.mode_relay), relay, Modifier.weight(1f)) {
                    onMode(SecurePrefs.MODE_RELAY)
                }
                ModePill(stringResource(R.string.mode_direct), !relay, Modifier.weight(1f)) {
                    onMode(SecurePrefs.MODE_DIRECT)
                }
            }
            Text(
                stringResource(
                    if (mode == SecurePrefs.MODE_DIRECT) R.string.mode_direct_hint
                    else R.string.mode_relay_hint,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val txt: @Composable () -> Unit = { Text(label, maxLines = 1, softWrap = false) }
    if (selected) {
        Button(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp)) { txt() }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp)) { txt() }
    }
}

@Composable
private fun DeviceNameCard(deviceName: String, onChange: (String) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = deviceName, onValueChange = onChange,
                label = { Text(stringResource(R.string.field_device_name)) },
                placeholder = { Text("国内备用机 / Backup Phone") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DirectModeCard(channelCount: Int, onConfigure: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.section_channels))
            Text(
                stringResource(R.string.direct_channels_count, channelCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_configure_channels))
            }
        }
    }
}

@Composable
private fun ConfigCard(
    workerUrl: String,
    token: String,
    onWorkerUrl: (String) -> Unit,
    onToken: (String) -> Unit,
    onSave: () -> Unit,
    onScanImport: () -> Unit,
    onShowQr: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.section_config))
            // 一键扫码导入 / one-tap scan-to-import
            Button(onClick = onScanImport, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_scan_import))
            }
            OutlinedTextField(
                value = workerUrl, onValueChange = onWorkerUrl,
                label = { Text(stringResource(R.string.field_worker_url)) },
                placeholder = { Text("https://hedwigsms.xxx.workers.dev") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token, onValueChange = onToken,
                label = { Text(stringResource(R.string.field_token)) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onSave, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.btn_save), maxLines = 1, softWrap = false)
                }
                OutlinedButton(onClick = onShowQr, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.btn_show_qr), maxLines = 1, softWrap = false)
                }
            }
        }
    }
}

/** 配置二维码弹窗 / config QR dialog. */
@Composable
private fun QrDialog(content: String, onDismiss: () -> Unit) {
    val bitmap = remember(content) {
        com.journeyapps.barcodescanner.BarcodeEncoder()
            .encodeBitmap(content, com.google.zxing.BarcodeFormat.QR_CODE, 640, 640)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.qr_close))
            }
        },
        title = { Text(stringResource(R.string.qr_dialog_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.size(240.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.qr_dialog_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

private fun toast(context: Context, resId: Int) {
    android.widget.Toast.makeText(context, context.getString(resId), android.widget.Toast.LENGTH_SHORT).show()
}

@Composable
private fun ActionsCard(running: Boolean, isConfigured: Boolean, onSaveBeforeAction: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(stringResource(R.string.section_control))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = {
                        onSaveBeforeAction()
                        sendServiceCommand(context, Constants.ACTION_START)
                    },
                    enabled = !running && isConfigured,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.btn_start)) }
                OutlinedButton(
                    onClick = { sendServiceCommand(context, Constants.ACTION_STOP) },
                    enabled = running,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.btn_stop)) }
            }
            Button(
                onClick = {
                    onSaveBeforeAction()
                    sendServiceCommand(context, Constants.ACTION_TEST)
                },
                enabled = isConfigured,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.btn_test)) }
            // 模拟一条「收到的短信」走完整转发链路 / inject a fake incoming SMS through the full chain.
            OutlinedButton(
                onClick = {
                    onSaveBeforeAction()
                    sendSimulatedSms(context)
                    toast(context, R.string.sim_sms_toast)
                },
                enabled = isConfigured,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.btn_simulate_sms), maxLines = 1, softWrap = false) }
        }
    }
}

/** 模拟收到一条短信：注入与真实广播完全相同的事件，走完整转发逻辑。 */
private fun sendSimulatedSms(context: Context) {
    val intent = Intent(context, HedwigForwardService::class.java).apply {
        action = Constants.ACTION_EVENT
        putExtra(Constants.EXTRA_EVENT_TYPE, com.hedwig.sms.model.HedwigPayload.TYPE_SMS)
        putExtra(Constants.EXTRA_SENDER, context.getString(R.string.sim_sms_sender))
        putExtra(Constants.EXTRA_BODY, context.getString(R.string.sim_sms_body))
        putExtra(Constants.EXTRA_SIM, context.getString(R.string.sim_sms_sim))
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

@Composable
private fun PermissionCard(
    onRequestSms: () -> Unit,
    onRequestNotif: () -> Unit,
    onRequestBattery: () -> Unit,
    onRequestSim: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle(stringResource(R.string.section_permissions))
            Text(stringResource(R.string.perm_hint), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = onRequestSms, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_perm_sms), maxLines = 1, softWrap = false)
            }
            OutlinedButton(onClick = onRequestNotif, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_perm_notif), maxLines = 1, softWrap = false)
            }
            OutlinedButton(onClick = onRequestSim, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_perm_sim), maxLines = 1, softWrap = false)
            }
            OutlinedButton(onClick = onRequestBattery, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.btn_perm_battery), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun LogsCard(logs: List<ForwardLog>, onViewAll: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SectionTitle(stringResource(R.string.section_logs))
            Spacer(Modifier.height(8.dp))
            if (logs.isEmpty()) {
                Text(stringResource(R.string.logs_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            } else {
                // 主屏只列最近 10 条，避免嵌套滚动用 Column / list latest 10 with a plain Column.
                Column(Modifier.fillMaxWidth()) {
                    logs.forEach { log ->
                        LogRow(log, fmt.format(Date(log.timestamp)))
                        HorizontalDivider()
                    }
                }
            }
            TextButton(onClick = onViewAll, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.logs_view_all))
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
        Column(Modifier.weight(1f)) {
            Text("${typeLabel(log.type)} · $time",
                fontWeight = FontWeight.Medium, fontSize = 14.sp)
            val detail = if (log.success) log.detail
            else "${log.detail} (code=${log.statusCode})"
            Text(detail, fontSize = 12.sp,
                color = if (log.success) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFFC62828))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun typeLabel(type: String): String = when (type) {
    "sms" -> "📩 " + stringResource(R.string.type_sms)
    "battery_low" -> "🪫 " + stringResource(R.string.type_battery_low)
    "power_connected" -> "🔌 " + stringResource(R.string.type_power_connected)
    "power_disconnected" -> "⚡️ " + stringResource(R.string.type_power_disconnected)
    "heartbeat" -> "💚 " + stringResource(R.string.type_heartbeat)
    else -> type
}

// ---------------- helpers ----------------

private fun sendServiceCommand(context: Context, action: String) {
    val intent = Intent(context, HedwigForwardService::class.java).apply { this.action = action }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        context.startForegroundService(intent)
    } else {
        context.startService(intent)
    }
}

private fun requestIgnoreBatteryOptimizations(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
        // 已豁免，打开常规电池设置页即可 / already exempt; open battery settings.
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
        return
    }
    runCatching {
        @Suppress("BatteryLife")
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        context.startActivity(intent)
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }
}

// stringResource 便捷封装（支持格式化参数）/ tiny wrapper supporting format args.
@Composable
private fun stringResource(id: Int, vararg formatArgs: Any): String =
    if (formatArgs.isEmpty()) androidx.compose.ui.res.stringResource(id)
    else androidx.compose.ui.res.stringResource(id, *formatArgs)
