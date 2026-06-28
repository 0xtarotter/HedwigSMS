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

        const val MODE_RELAY = "relay"
        const val MODE_DIRECT = "direct"
    }
}
