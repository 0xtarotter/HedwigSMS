package com.hedwig.sms.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.hedwig.sms.core.BatteryUtil
import com.hedwig.sms.core.Constants
import com.hedwig.sms.data.SecurePrefs
import com.hedwig.sms.model.HedwigPayload
import com.hedwig.sms.service.HedwigForwardService

/**
 * 统一广播接收器 / Unified broadcast receiver.
 *
 * 集中处理 5 类事件并转交前台服务执行实际转发：
 * Handles all 5 event types and delegates the actual forwarding to the foreground service:
 *   1. 短信 SMS              (android.provider.Telephony.SMS_RECEIVED)  —— 静态注册 / manifest
 *   2. 低电量 battery_low     (Intent.ACTION_BATTERY_LOW)                —— 运行时注册 / runtime
 *   3. 断电 power_disconnected(Intent.ACTION_POWER_DISCONNECTED)         —— 运行时注册 / runtime
 *   4. 插电 power_connected   (Intent.ACTION_POWER_CONNECTED)            —— 运行时注册 / runtime
 *   5. 心跳 heartbeat         (Constants.ACTION_HEARTBEAT, AlarmManager) —— 静态注册 / manifest
 *
 * 另外处理 BOOT_COMPLETED 实现开机自启 / also handles BOOT_COMPLETED for auto-start.
 *
 * 说明：BATTERY_LOW 与电源连接/断开广播在 Android 8+ 无法静态注册，
 * 因此由 HedwigForwardService 在运行时用本类实例动态注册。
 */
class SMSAndStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> handleSms(context, intent)
            Intent.ACTION_BATTERY_LOW -> handleStatus(context, HedwigPayload.TYPE_BATTERY_LOW)
            Intent.ACTION_POWER_CONNECTED -> handleStatus(context, HedwigPayload.TYPE_POWER_CONNECTED)
            Intent.ACTION_POWER_DISCONNECTED -> handleStatus(context, HedwigPayload.TYPE_POWER_DISCONNECTED)
            Constants.ACTION_HEARTBEAT -> handleHeartbeat(context)
            Intent.ACTION_BOOT_COMPLETED -> handleBoot(context)
        }
    }

    /** 短信：合并多段，按发件人提取内容 / merge multipart SMS by sender. */
    private fun handleSms(context: Context, intent: Intent) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        val sender = messages[0].displayOriginatingAddress ?: messages[0].originatingAddress ?: "Unknown"
        val body = buildString {
            for (m in messages) append(m.displayMessageBody ?: m.messageBody ?: "")
        }

        // 多卡识别：解析是哪张卡收到的 / which SIM received it (multi-SIM)
        val subId = com.hedwig.sms.core.SimInfoUtil.subIdFromIntent(intent.extras)
        val sim = com.hedwig.sms.core.SimInfoUtil.label(context, subId)

        val deliver = Intent(context, HedwigForwardService::class.java).apply {
            action = Constants.ACTION_EVENT
            putExtra(Constants.EXTRA_EVENT_TYPE, HedwigPayload.TYPE_SMS)
            putExtra(Constants.EXTRA_SENDER, sender)
            putExtra(Constants.EXTRA_BODY, body)
            if (sim != null) putExtra(Constants.EXTRA_SIM, sim)
        }
        startService(context, deliver)
    }

    /** 状态变化（低电/断电/插电），附带当前电量 / status change with current battery level. */
    private fun handleStatus(context: Context, type: String) {
        val deliver = Intent(context, HedwigForwardService::class.java).apply {
            action = Constants.ACTION_EVENT
            putExtra(Constants.EXTRA_EVENT_TYPE, type)
            putExtra(Constants.EXTRA_BATTERY_LEVEL, BatteryUtil.currentLevel(context))
        }
        startService(context, deliver)
    }

    /** 每日心跳：发送平安报告并重新排下一天闹钟 / daily report + re-arm tomorrow. */
    private fun handleHeartbeat(context: Context) {
        val deliver = Intent(context, HedwigForwardService::class.java).apply {
            action = Constants.ACTION_EVENT
            putExtra(Constants.EXTRA_EVENT_TYPE, HedwigPayload.TYPE_HEARTBEAT)
            putExtra(Constants.EXTRA_BATTERY_LEVEL, BatteryUtil.currentLevel(context))
        }
        startService(context, deliver)
        // setExactAndAllowWhileIdle 是一次性的，触发后需重排 / one-shot exact alarm must be re-armed.
        com.hedwig.sms.alarm.HeartbeatScheduler.schedule(context)
    }

    /** 开机自启：若用户曾开启服务，则恢复运行 / restore service after boot if enabled. */
    private fun handleBoot(context: Context) {
        val prefs = SecurePrefs(context)
        if (prefs.serviceEnabled && prefs.isConfigured()) {
            val start = Intent(context, HedwigForwardService::class.java).apply {
                action = Constants.ACTION_START
            }
            startService(context, start)
        }
    }

    private fun startService(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }
}
