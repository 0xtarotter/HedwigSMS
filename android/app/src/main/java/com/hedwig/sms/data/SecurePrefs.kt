package com.hedwig.sms.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.hedwig.sms.core.Constants

/**
 * 安全配置存储 / Secure configuration storage.
 *
 * 使用 EncryptedSharedPreferences 加密保存用户的 Worker URL、Token 等敏感信息。
 * Stores the Worker URL, auth token and other sensitive settings using
 * EncryptedSharedPreferences (AES-256, key in Android Keystore).
 */
class SecurePrefs(context: Context) {

    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "hedwig_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    var workerUrl: String
        get() = prefs.getString(KEY_URL, "") ?: ""
        set(v) = prefs.edit().putString(KEY_URL, v.trim()).apply()

    var token: String
        get() = prefs.getString(KEY_TOKEN, "") ?: ""
        set(v) = prefs.edit().putString(KEY_TOKEN, v.trim()).apply()

    var deviceName: String
        get() = prefs.getString(KEY_DEVICE, "") ?: ""
        set(v) = prefs.edit().putString(KEY_DEVICE, v.trim()).apply()

    var heartbeatHour: Int
        get() = prefs.getInt(KEY_HB_HOUR, Constants.DEFAULT_HEARTBEAT_HOUR)
        set(v) = prefs.edit().putInt(KEY_HB_HOUR, v).apply()

    var heartbeatMinute: Int
        get() = prefs.getInt(KEY_HB_MIN, Constants.DEFAULT_HEARTBEAT_MINUTE)
        set(v) = prefs.edit().putInt(KEY_HB_MIN, v).apply()

    /** 服务是否应处于运行状态（用于开机自启判断）/ whether the service should be running. */
    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    /** 是否已完成新手引导 / whether the first-launch onboarding is done. */
    var onboardingDone: Boolean
        get() = prefs.getBoolean(KEY_ONBOARDED, false)
        set(v) = prefs.edit().putBoolean(KEY_ONBOARDED, v).apply()

    /** 运行模式：relay（经服务器中转）或 direct（手机直连各 IM）/ run mode. */
    var mode: String
        get() = prefs.getString(KEY_MODE, MODE_RELAY) ?: MODE_RELAY
        set(v) = prefs.edit().putString(KEY_MODE, v).apply()

    /** 直连模式的渠道配置（JSON）/ channel config (JSON) for direct mode. */
    var channelConfigJson: String
        get() = prefs.getString(KEY_CHANNELS, "") ?: ""
        set(v) = prefs.edit().putString(KEY_CHANNELS, v).apply()

    /** 启动时自动检查更新 / auto-check updates on launch. */
    var autoCheckUpdate: Boolean
        get() = prefs.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) = prefs.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()

    /** 开机自动启动守护 / auto-start guard on boot. */
    var bootAutoStart: Boolean
        get() = prefs.getBoolean(KEY_BOOT_AUTOSTART, true)
        set(v) = prefs.edit().putBoolean(KEY_BOOT_AUTOSTART, v).apply()

    /** 每日平安报告（心跳）/ daily health report (heartbeat). */
    var heartbeatEnabled: Boolean
        get() = prefs.getBoolean(KEY_HB_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_HB_ENABLED, v).apply()

    /** 后台保活：服务被杀后由看门狗自动重启 / keep-alive: watchdog auto-restarts the service. */
    var keepAliveEnabled: Boolean
        get() = prefs.getBoolean(KEY_KEEP_ALIVE, true)
        set(v) = prefs.edit().putBoolean(KEY_KEEP_ALIVE, v).apply()

    /** 电源状态通知（插电/断电）是否转发 / whether power connected/disconnected events are forwarded. */
    var powerNotifyEnabled: Boolean
        get() = prefs.getBoolean(KEY_POWER_NOTIFY, true)
        set(v) = prefs.edit().putBoolean(KEY_POWER_NOTIFY, v).apply()

    /** 界面语言："" 跟随系统 / "zh" / "en" / app UI language. */
    var language: String
        get() = prefs.getString(KEY_LANG, "") ?: ""
        set(v) = prefs.edit().putString(KEY_LANG, v).apply()

    /** 常驻通知栏显示详细动态状态 / show detailed dynamic status in the ongoing notification. */
    var notifDynamic: Boolean
        get() = prefs.getBoolean(KEY_NOTIF_DYNAMIC, true)
        set(v) = prefs.edit().putBoolean(KEY_NOTIF_DYNAMIC, v).apply()

    /** 当前模式下是否已配置完整 / whether current mode is fully configured. */
    fun isConfigured(): Boolean =
        if (mode == MODE_DIRECT) {
            com.hedwig.sms.model.ChannelConfig.fromJson(channelConfigJson).anyConfigured()
        } else {
            workerUrl.isNotBlank() && token.isNotBlank()
        }

    /** 构建完整请求 URL（带 token 参数）/ build the full request URL with token param. */
    fun buildRequestUrl(): String {
        val base = workerUrl.trim()
        val sep = if (base.contains("?")) "&" else "?"
        return "$base${sep}token=${token.trim()}"
    }

    companion object {
        const val KEY_URL = "worker_url"
        const val KEY_TOKEN = "auth_token"
        const val KEY_DEVICE = "device_name"
        const val KEY_HB_HOUR = "hb_hour"
        const val KEY_HB_MIN = "hb_min"
        const val KEY_ENABLED = "service_enabled"
        const val KEY_ONBOARDED = "onboarding_done"
        const val KEY_MODE = "run_mode"
        const val KEY_CHANNELS = "channel_config"
        const val KEY_AUTO_UPDATE = "auto_update"
        const val KEY_BOOT_AUTOSTART = "boot_autostart"
        const val KEY_HB_ENABLED = "heartbeat_enabled"
        const val KEY_KEEP_ALIVE = "keep_alive_enabled"
        const val KEY_POWER_NOTIFY = "power_notify_enabled"
        const val KEY_LANG = "ui_language"
        const val KEY_NOTIF_DYNAMIC = "notif_dynamic"

        const val MODE_RELAY = "relay"
        const val MODE_DIRECT = "direct"
    }
}
