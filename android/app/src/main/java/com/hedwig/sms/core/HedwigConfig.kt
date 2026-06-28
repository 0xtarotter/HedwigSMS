package com.hedwig.sms.core

import android.net.Uri
import java.net.URLEncoder

/**
 * 配置二维码编解码 / Config QR codec.
 *
 * 借鉴 BlueBubbles（Apache-2.0, © BlueBubblesApp）的「扫码即配对」思路：
 * 把服务端地址 + 令牌编码进二维码，客户端扫码后自动填好，无需手动输入。
 * Inspired by BlueBubbles' "scan-to-pair": encode address + token into a QR so the
 * client auto-fills its config instead of typing it by hand.
 *
 * 编码格式 / encoded format:
 *   hedwig://config?u=<urlEncodedWorkerUrl>&t=<urlEncodedToken>&d=<urlEncodedDeviceName>
 */
object HedwigConfig {

    data class Config(val url: String, val token: String, val device: String)

    /** 统一扫码结果 / unified scan result. */
    sealed class Scanned {
        /** 中转模式快速配置 / relay quick-config (worker url + token). */
        data class Relay(val config: Config) : Scanned()
        /** 整套配置（模式 + 中转 + 全部直连渠道）/ full config backup. */
        data class Full(val config: com.hedwig.sms.model.FullConfig) : Scanned()
    }

    /**
     * 统一解析任意 HedwigSMS 二维码，自动识别类型。
     * Parse any HedwigSMS QR and auto-detect its type.
     */
    fun parseAny(raw: String?): Scanned? {
        com.hedwig.sms.model.FullConfig.parse(raw)?.let { return Scanned.Full(it) }
        parse(raw)?.let { return Scanned.Relay(it) }
        return null
    }

    fun encode(url: String, token: String, device: String): String {
        fun e(s: String) = URLEncoder.encode(s, "UTF-8")
        return "hedwig://config?u=${e(url.trim())}&t=${e(token.trim())}&d=${e(device.trim())}"
    }

    /**
     * 解析扫码结果 / parse a scanned string.
     * 兼容 hedwig:// URI 与简单 JSON（{"u":..,"t":..,"d":..} 或 url/token/device 键）。
     */
    fun parse(raw: String?): Config? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null

        // 1) hedwig:// URI
        if (s.startsWith("hedwig://", ignoreCase = true)) {
            return try {
                val uri = Uri.parse(s)
                val u = uri.getQueryParameter("u") ?: uri.getQueryParameter("url") ?: ""
                val t = uri.getQueryParameter("t") ?: uri.getQueryParameter("token") ?: ""
                val d = uri.getQueryParameter("d") ?: uri.getQueryParameter("device") ?: ""
                if (u.isBlank() || t.isBlank()) null else Config(u, t, d)
            } catch (e: Exception) {
                null
            }
        }

        // 2) 简单 JSON 兜底 / lenient JSON fallback
        if (s.startsWith("{")) {
            return try {
                val obj = org.json.JSONObject(s)
                val u = obj.optString("u", obj.optString("url"))
                val t = obj.optString("t", obj.optString("token"))
                val d = obj.optString("d", obj.optString("device"))
                if (u.isBlank() || t.isBlank()) null else Config(u, t, d)
            } catch (e: Exception) {
                null
            }
        }
        return null
    }
}
