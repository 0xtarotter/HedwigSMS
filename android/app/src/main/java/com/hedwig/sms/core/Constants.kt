package com.hedwig.sms.core

/**
 * 全局常量 / Global constants.
 */
object Constants {
    // GitHub 仓库（用于检查更新）/ GitHub repo for update checks.
    // ⚠️ 仓库设为 public 后，把下面改成你的「用户名/仓库名」。
    // After making the repo public, change this to "your-username/HedwigSMS".
    const val GITHUB_REPO = "REPLACE_ME/HedwigSMS"

    // 自定义心跳广播 action / custom heartbeat broadcast action
    const val ACTION_HEARTBEAT = "com.hedwig.sms.action.HEARTBEAT"

    // 传给 Service 的事件 extra / extras passed to the Service
    const val EXTRA_EVENT_TYPE = "extra_event_type"
    const val EXTRA_SENDER = "extra_sender"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_BATTERY_LEVEL = "extra_battery_level"
    const val EXTRA_SIM = "extra_sim"

    // Service 指令 / Service commands
    const val ACTION_START = "com.hedwig.sms.START"
    const val ACTION_STOP = "com.hedwig.sms.STOP"
    const val ACTION_EVENT = "com.hedwig.sms.EVENT"
    const val ACTION_TEST = "com.hedwig.sms.TEST" // 发送测试消息 / send a test message

    // 从磁贴/小插件「打开 App 并自动启动守护」/ open app & auto-start guard (from tile/widget)
    const val EXTRA_AUTOSTART = "extra_autostart"
    // 小插件「停止」广播 / widget stop broadcast
    const val ACTION_WIDGET_STOP = "com.hedwig.sms.WIDGET_STOP"

    // 通知 / notification
    const val NOTIF_CHANNEL_ID = "hedwig_guard"
    const val NOTIF_ID = 1001

    // 低电量阈值 / low-battery threshold
    const val LOW_BATTERY_THRESHOLD = 15

    // 本地日志保留条数 / number of local logs to retain
    const val MAX_LOGS = 50

    // 每日心跳默认时间（小时，24h）/ default daily heartbeat hour
    const val DEFAULT_HEARTBEAT_HOUR = 9
    const val DEFAULT_HEARTBEAT_MINUTE = 0
}
