package com.hedwig.sms.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.hedwig.sms.core.Constants
import com.hedwig.sms.data.SecurePrefs
import com.hedwig.sms.receiver.SMSAndStatusReceiver
import java.util.Calendar

/**
 * 每日心跳调度 / Daily heartbeat scheduler.
 *
 * 利用 AlarmManager 每天固定时间触发一次 HEARTBEAT 广播，作为「设备平安报告」。
 * Uses AlarmManager to fire a HEARTBEAT broadcast once per day as a "device health report".
 *
 * 优先使用精确闹钟（setExactAndAllowWhileIdle，可在 Doze 下唤醒）；
 * 若系统不允许精确闹钟，则降级为非精确重复闹钟兜底。
 * Prefers exact alarms (wakes through Doze); falls back to inexact repeating alarms
 * when exact alarms are not permitted.
 */
object HeartbeatScheduler {

    private const val REQUEST_CODE = 7001

    fun schedule(context: Context) {
        val prefs = SecurePrefs(context)
        // 设置里关闭「每日平安报告」则不排定 / skip if disabled in Settings.
        if (!prefs.heartbeatEnabled) {
            cancel(context)
            return
        }
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = buildPendingIntent(context)

        val triggerAt = nextTrigger(prefs.heartbeatHour, prefs.heartbeatMinute)

        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else {
            true
        }

        if (canExact) {
            // 精确且可在 Doze 下触发；触发后由 Receiver 重新排下一天 / re-armed daily by the receiver.
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            // 兜底：每天重复一次（不精确，但不会漏）/ inexact daily repeat fallback.
            am.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                AlarmManager.INTERVAL_DAY,
                pi,
            )
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(buildPendingIntent(context))
    }

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, SMSAndStatusReceiver::class.java).apply {
            action = Constants.ACTION_HEARTBEAT
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags = flags or PendingIntent.FLAG_IMMUTABLE
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    /** 计算下一次触发的绝对时间戳 / next absolute trigger time. */
    private fun nextTrigger(hour: Int, minute: Int): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) {
            next.add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }
}
