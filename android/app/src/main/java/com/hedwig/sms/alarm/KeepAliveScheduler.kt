package com.hedwig.sms.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hedwig.sms.core.Constants
import com.hedwig.sms.data.SecurePrefs
import com.hedwig.sms.receiver.SMSAndStatusReceiver

/**
 * 后台保活看门狗调度 / Background keep-alive watchdog scheduler.
 *
 * 每隔 [Constants.KEEPALIVE_INTERVAL_MS] 触发一次 KEEPALIVE 广播，由接收器检查守护服务
 * 是否仍在运行；若被系统/厂商 ROM 杀掉则自动重启。配合 START_STICKY 与开机自启，
 * 最大化 7×24 存活率。
 * Fires a KEEPALIVE broadcast every [Constants.KEEPALIVE_INTERVAL_MS]; the receiver checks
 * whether the guard service is still running and restarts it if it was killed. Combined with
 * START_STICKY and boot auto-start, this maximizes 24/7 uptime.
 *
 * 使用一次性精确闹钟（触发后在接收器里重新排定）/ one-shot exact alarm, re-armed by the receiver.
 */
object KeepAliveScheduler {

    private const val REQUEST_CODE = 7002

    fun schedule(context: Context) {
        val prefs = SecurePrefs(context)
        // 未开启保活，或服务本就不该运行时不排定 / skip if disabled or service shouldn't run.
        if (!prefs.keepAliveEnabled || !prefs.serviceEnabled) {
            cancel(context)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context)
        val triggerAt = System.currentTimeMillis() + Constants.KEEPALIVE_INTERVAL_MS

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // 兜底：非精确但可在 Doze 下触发 / inexact fallback that still fires under Doze.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SMSAndStatusReceiver::class.java).apply {
            action = Constants.ACTION_KEEPALIVE
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
