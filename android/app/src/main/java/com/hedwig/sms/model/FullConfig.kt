package com.hedwig.sms.model

import android.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 整套配置（用于二维码备份/迁移）/ full app config for QR backup & transfer.
 *
 * 把当前模式、设备名、中转配置、以及全部直连渠道一次性编码进二维码；
 * 另一台手机扫一扫即可整套还原。
 * Encodes mode + device + relay config + all direct channels into one QR; scanning on
 * another phone restores everything at once.
 */
@Serializable
data class FullConfig(
    val v: Int = 1,
    val mode: String = "direct",
    val device: String = "",
    val workerUrl: String = "",
    val token: String = "",
    val channels: ChannelConfig = ChannelConfig(),
) {
    /** 编码为 hedwig://full?d=<base64> / encode as a hedwig://full URI. */
    fun encode(): String {
        val json = JSON.encodeToString(this)
        val b64 = Base64.encodeToString(
            json.toByteArray(Charsets.UTF_8),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
        return "hedwig://full?d=$b64"
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        fun parse(raw: String?): FullConfig? {
            val s = raw?.trim().orEmpty()
            val marker = "hedwig://full?d="
            if (!s.startsWith(marker, ignoreCase = true)) return null
            return runCatching {
                val b64 = s.substring(marker.length)
                val json = String(Base64.decode(b64, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
                JSON.decodeFromString<FullConfig>(json)
            }.getOrNull()
        }
    }
}
