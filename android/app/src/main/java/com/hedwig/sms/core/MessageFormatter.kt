package com.hedwig.sms.core

import com.hedwig.sms.model.HedwigPayload
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * 直连模式的消息排版 / Message formatting for DIRECT mode.
 *
 * 与 Cloudflare Worker 的 format.js 保持一致，使两种模式（中转/直连）输出完全相同。
 * Mirrors the Worker's format.js so relay mode and direct mode produce identical text.
 */
object MessageFormatter {

    data class Formatted(val title: String, val text: String)

    private const val SEP = "———————————————"

    private fun time(ts: Long): String {
        val ms = if (ts > 0) ts else System.currentTimeMillis()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("GMT+8") // 北京时间 / Beijing time
        return fmt.format(Date(ms))
    }

    fun format(payload: HedwigPayload): Formatted {
        val device = payload.deviceName.ifBlank { "未知设备 / Unknown device" }
        val t = time(payload.timestamp)
        val d = payload.data
        return when (payload.type) {
            HedwigPayload.TYPE_SMS -> {
                val sender = d.sender ?: "未知号码 / Unknown"
                val body = d.body ?: "(空内容 / empty)"
                val simLine = d.sim?.takeIf { it.isNotBlank() }?.let { "📲 接收卡 SIM：$it\n" } ?: ""
                Formatted(
                    "📩 新短信 New SMS",
                    "📩 新短信 | New SMS\n$SEP\n" +
                        "📱 设备 Device：$device\n${simLine}👤 发件人 From：$sender\n🕒 时间 Time：$t\n$SEP\n$body",
                )
            }
            HedwigPayload.TYPE_BATTERY_LOW -> {
                val lv = d.batteryLevel ?: -1
                Formatted(
                    "🪫 低电量预警 Low Battery",
                    "🪫 低电量预警 | Low Battery Warning\n$SEP\n" +
                        "📱 设备 Device：$device\n🔋 当前电量 Level：${lvStr(lv)}\n🕒 时间 Time：$t\n$SEP\n" +
                        "⚠️ 请尽快为备用机充电 / Please charge the backup phone soon.",
                )
            }
            HedwigPayload.TYPE_POWER_DISCONNECTED -> {
                val lv = d.batteryLevel ?: -1
                Formatted(
                    "⚡️ 电源已断开 Power Lost",
                    "⚡️ 电源已断开（疑似停电！）| Power Disconnected (possible outage!)\n$SEP\n" +
                        "📱 设备 Device：$device\n🔋 当前电量 Level：${lvStr(lv)}\n🕒 时间 Time：$t\n$SEP\n" +
                        "🏠 请留意家中电力情况 / Please check the power at home.",
                )
            }
            HedwigPayload.TYPE_POWER_CONNECTED -> {
                val lv = d.batteryLevel ?: -1
                Formatted(
                    "🔌 电源已接通 Power Restored",
                    "🔌 电源已接通 | Power Connected\n$SEP\n" +
                        "📱 设备 Device：$device\n🔋 当前电量 Level：${lvStr(lv)}\n🕒 时间 Time：$t\n$SEP\n" +
                        "✅ 充电中，一切正常 / Charging, all good.",
                )
            }
            HedwigPayload.TYPE_HEARTBEAT -> {
                val lv = d.batteryLevel ?: -1
                Formatted(
                    "💚 设备平安报告 Daily Report",
                    "💚 设备平安报告 | Device Health Report\n$SEP\n" +
                        "📱 设备 Device：$device\n🔋 当前电量 Level：${lvStr(lv)}\n🌐 网络状态 Network：正常 OK\n🕒 时间 Time：$t\n$SEP\n" +
                        "🛡️ Hedwig 正在守护中 / Hedwig is on guard.",
                )
            }
            else -> Formatted(
                "📨 Hedwig 通知 Notification",
                "📨 Hedwig 通知 | Notification\n📱 设备 Device：$device\n🏷️ 类型 Type：${payload.type}\n🕒 时间 Time：$t",
            )
        }
    }

    private fun lvStr(lv: Int) = if (lv in 0..100) "$lv%" else "?%"
}
