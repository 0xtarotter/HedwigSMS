package com.hedwig.sms.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hedwig.sms.R
import com.hedwig.sms.alarm.HeartbeatScheduler
import com.hedwig.sms.core.BatteryUtil
import com.hedwig.sms.core.Constants
import com.hedwig.sms.core.HedwigState
import com.hedwig.sms.data.ForwardLog
import com.hedwig.sms.data.HedwigDatabase
import com.hedwig.sms.data.SecurePrefs
import com.hedwig.sms.model.HedwigData
import com.hedwig.sms.model.HedwigPayload
import com.hedwig.sms.net.HedwigApiClient
import com.hedwig.sms.receiver.SMSAndStatusReceiver
import com.hedwig.sms.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * 前台保活服务 / Foreground keep-alive service.
 *
 * 职责 / Responsibilities:
 *  - 常驻前台通知，动态三态反馈（守护中 / 转发中 / 失败）。
 *  - 运行时注册电量&电源广播（这些在 Android 8+ 无法静态注册）。
 *  - 收到事件后构建统一负载，经 OkHttp 异步 POST 到用户的 Cloudflare Worker。
 *  - 写入本地 Room 日志（保留最近 50 条）并维护成功计数。
 */
class HedwigForwardService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: SecurePrefs
    private val dao by lazy { HedwigDatabase.get(this).forwardLogDao() }

    /** 运行时注册的电量/电源接收器 / runtime-registered battery & power receiver. */
    private var runtimeReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        prefs = SecurePrefs(this)
        HedwigState.setBattery(BatteryUtil.currentLevel(this))
        observeSuccessCount()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 任何分支都要先 startForeground，避免 Android 限制 / always promote to foreground first.
        startForeground(Constants.NOTIF_ID, buildNotification(HedwigState.Phase.GUARDING))

        when (intent?.action) {
            Constants.ACTION_STOP -> {
                // 先告知「守护已停止」（独立作用域，不受停止影响）再停 / notify off, then stop.
                ServiceStatusNotifier.send(this, on = false)
                stopEverything()
                return START_NOT_STICKY
            }
            Constants.ACTION_TEST -> handleTest()
            Constants.ACTION_EVENT -> handleEvent(intent)
            Constants.ACTION_START -> {
                val wasRunning = HedwigState.running.value
                startGuarding()
                if (!wasRunning) ServiceStatusNotifier.send(this, on = true) // 仅在真正开启时告知
            }
            else -> startGuarding() // 服务被系统重启，不重复告知 / system restart, no notify
        }
        return START_STICKY
    }

    /** 进入守护态：注册运行时广播 + 排定每日心跳 / enter guarding state. */
    private fun startGuarding() {
        prefs.serviceEnabled = true
        registerRuntimeReceiver()
        HeartbeatScheduler.schedule(this)
        HedwigState.setRunning(true)
        refreshGuardingNotification()
        syncWidgets()
    }

    /** 把状态写入快照并刷新桌面小插件/磁贴 / persist snapshot, refresh widgets & tile. */
    private fun syncWidgets() {
        com.hedwig.sms.core.WidgetState.save(
            this,
            HedwigState.running.value,
            HedwigState.phase.value.name,
            HedwigState.batteryLevel.value,
            HedwigState.successCount.value,
        )
        com.hedwig.sms.widget.HedwigWidgetProvider.updateAll(this)
        runCatching {
            android.service.quicksettings.TileService.requestListeningState(
                this,
                android.content.ComponentName(this, com.hedwig.sms.tile.HedwigTileService::class.java),
            )
        }
    }

    private fun stopEverything() {
        prefs.serviceEnabled = false
        unregisterRuntimeReceiver()
        HeartbeatScheduler.cancel(this)
        HedwigState.setRunning(false)
        syncWidgets()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** 处理「发送测试消息」/ handle the in-app "send test message" action. */
    private fun handleTest() {
        startGuarding()
        val payload = HedwigPayload(
            type = HedwigPayload.TYPE_HEARTBEAT,
            deviceName = resolveDeviceName(),
            timestamp = System.currentTimeMillis(),
            data = HedwigData(batteryLevel = BatteryUtil.currentLevel(this)),
        )
        forward(payload, label = getString(R.string.test_label))
    }

    /** 处理来自 Receiver 的事件 / handle an event delivered by the receiver. */
    private fun handleEvent(intent: Intent) {
        // 确保守护态资源就绪（如服务因系统重启而无注册）/ ensure guarding resources are up.
        if (runtimeReceiver == null) startGuarding()

        val type = intent.getStringExtra(Constants.EXTRA_EVENT_TYPE) ?: return
        val battery = intent.getIntExtra(Constants.EXTRA_BATTERY_LEVEL, BatteryUtil.currentLevel(this))
        HedwigState.setBattery(BatteryUtil.currentLevel(this))

        val payload: HedwigPayload
        val label: String
        when (type) {
            HedwigPayload.TYPE_SMS -> {
                val sender = intent.getStringExtra(Constants.EXTRA_SENDER) ?: "Unknown"
                val body = intent.getStringExtra(Constants.EXTRA_BODY) ?: ""
                val sim = intent.getStringExtra(Constants.EXTRA_SIM)
                payload = HedwigPayload(
                    type = type,
                    deviceName = resolveDeviceName(),
                    timestamp = System.currentTimeMillis(),
                    data = HedwigData(sender = sender, body = body, sim = sim),
                )
                label = sender
            }
            HedwigPayload.TYPE_BATTERY_LOW,
            HedwigPayload.TYPE_POWER_CONNECTED,
            HedwigPayload.TYPE_POWER_DISCONNECTED,
            HedwigPayload.TYPE_HEARTBEAT -> {
                payload = HedwigPayload(
                    type = type,
                    deviceName = resolveDeviceName(),
                    timestamp = System.currentTimeMillis(),
                    data = HedwigData(batteryLevel = battery),
                )
                label = getString(R.string.status_label)
            }
            else -> return
        }
        forward(payload, label)
    }

    /** 核心转发逻辑：更新通知 → 异步发送 → 记录结果 / forward with dynamic notification. */
    private fun forward(payload: HedwigPayload, label: String) {
        if (!prefs.isConfigured()) {
            // 未配置直接记失败 / not configured -> record a failure.
            logResult(payload.type, success = false, code = 0, detail = getString(R.string.err_not_configured))
            updatePhase(HedwigState.Phase.FAILED)
            return
        }

        // 1) 通知切到「转发中」/ switch notification to "forwarding".
        HedwigState.setForwardingLabel(label)
        updatePhase(HedwigState.Phase.FORWARDING)

        // 2) 持有短时 WakeLock 保证发送完成 / hold a short wake lock to complete the send.
        val wl = acquireWakeLock()

        if (prefs.mode == SecurePrefs.MODE_DIRECT) {
            // 手机直连：本地排版并直接发往各 IM / direct on-phone dispatch.
            val config = com.hedwig.sms.model.ChannelConfig.fromJson(prefs.channelConfigJson)
            scope.launch {
                val results = runCatching {
                    com.hedwig.sms.net.DirectDispatcher.dispatch(config, payload)
                }.getOrDefault(emptyList())
                val ok = results.isNotEmpty() && results.any { it.ok }
                val okCount = results.count { it.ok }
                val detail = directSummary(payload, okCount, results.size,
                    results.firstOrNull { !it.ok }?.error)
                val code = results.firstOrNull { !it.ok }?.code ?: 0
                logResult(payload.type, ok, code, detail, payload.data.sim)
                updatePhase(if (ok) HedwigState.Phase.GUARDING else HedwigState.Phase.FAILED)
                releaseWakeLock(wl)
            }
        } else {
            // 中转模式：POST 到用户的 Worker/relay / relay mode.
            HedwigApiClient.send(prefs.buildRequestUrl(), payload) { result ->
                logResult(payload.type, result.success, result.statusCode, summaryFor(payload, result.message), payload.data.sim)
                updatePhase(if (result.success) HedwigState.Phase.GUARDING else HedwigState.Phase.FAILED)
                releaseWakeLock(wl)
            }
        }
    }

    private fun directSummary(payload: HedwigPayload, okCount: Int, total: Int, firstErr: String?): String {
        val head = if (payload.type == HedwigPayload.TYPE_SMS) {
            "${payload.data.sender}: ${payload.data.body?.take(30) ?: ""}"
        } else {
            payload.type
        }
        val tail = if (total == 0) "无已配置渠道 / no channel"
        else if (okCount == total) "$okCount/$total 渠道成功"
        else "$okCount/$total 成功${firstErr?.let { " · $it" } ?: ""}"
        return "$head ($tail)"
    }

    private fun summaryFor(payload: HedwigPayload, message: String): String {
        return when (payload.type) {
            HedwigPayload.TYPE_SMS -> "${payload.data.sender}: ${payload.data.body?.take(40) ?: ""} ($message)"
            else -> message
        }
    }

    /** 写日志、裁剪到 50 条、刷新成功计数 / persist log, trim to 50, refresh count. */
    private fun logResult(type: String, success: Boolean, code: Int, detail: String, sim: String? = null) {
        scope.launch {
            dao.insert(
                ForwardLog(
                    timestamp = System.currentTimeMillis(),
                    type = type,
                    success = success,
                    statusCode = code,
                    detail = detail,
                    sim = sim,
                ),
            )
            dao.trim(Constants.MAX_LOGS)
        }
    }

    private fun observeSuccessCount() {
        scope.launch {
            dao.observeSuccessCount().collect {
                HedwigState.setSuccessCount(it)
                syncWidgets()
            }
        }
    }

    // ---------------- 通知 / Notification ----------------

    private fun updatePhase(phase: HedwigState.Phase) {
        HedwigState.setPhase(phase)
        HedwigState.setBattery(BatteryUtil.currentLevel(this))
        syncWidgets()
        NotificationManagerCompat.from(this).let { nm ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                nm.areNotificationsEnabled()
            ) {
                nm.notify(Constants.NOTIF_ID, buildNotification(phase))
            }
        }
    }

    private fun refreshGuardingNotification() = updatePhase(HedwigState.Phase.GUARDING)

    private fun buildNotification(phase: HedwigState.Phase): Notification {
        val battery = HedwigState.batteryLevel.value
        val count = HedwigState.successCount.value
        // 关闭「常驻状态通知」详情时只显示精简文案（前台服务仍需一条通知）。
        // When the dynamic status toggle is off, show minimal text (FGS still needs a notification).
        val text = if (!prefs.notifDynamic) {
            getString(R.string.notif_minimal)
        } else when (phase) {
            HedwigState.Phase.GUARDING ->
                getString(R.string.notif_guarding, battery, count)
            HedwigState.Phase.FORWARDING ->
                getString(R.string.notif_forwarding, HedwigState.forwardingLabel.value)
            HedwigState.Phase.FAILED ->
                getString(R.string.notif_failed)
        }

        val tapIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, Constants.NOTIF_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hedwig)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(tapIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    // ---------------- 运行时广播 / runtime receiver ----------------

    private fun registerRuntimeReceiver() {
        if (runtimeReceiver != null) return
        val receiver = SMSAndStatusReceiver()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        runtimeReceiver = receiver
    }

    private fun unregisterRuntimeReceiver() {
        runtimeReceiver?.let {
            runCatching { unregisterReceiver(it) }
            runtimeReceiver = null
        }
    }

    // ---------------- WakeLock ----------------

    private fun acquireWakeLock(): PowerManager.WakeLock? {
        return runCatching {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HedwigSMS::forward").apply {
                acquire(30_000L) // 最多持有 30s / hold at most 30s
            }
        }.getOrNull()
    }

    private fun releaseWakeLock(wl: PowerManager.WakeLock?) {
        runCatching { if (wl?.isHeld == true) wl.release() }
    }

    // ---------------- misc ----------------

    private fun resolveDeviceName(): String =
        prefs.deviceName.ifBlank { Build.MODEL ?: "Android" }

    override fun onDestroy() {
        unregisterRuntimeReceiver()
        HedwigState.setRunning(false)
        scope.cancel()
        super.onDestroy()
    }
}
