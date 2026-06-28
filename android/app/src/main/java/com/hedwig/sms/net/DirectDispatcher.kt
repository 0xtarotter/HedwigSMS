package com.hedwig.sms.net

import android.util.Base64
import com.hedwig.sms.core.MessageFormatter
import com.hedwig.sms.model.ChannelConfig
import com.hedwig.sms.model.HedwigPayload
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 手机直连分发 / On-phone direct dispatch.
 *
 * 与 Cloudflare Worker 的 channels 模块行为一致：把同一条消息并行发到所有已配置渠道。
 * Mirrors the Worker's channels modules: fan-out the same message to all configured channels.
 * Payload shapes adapted from OpenClaw (MIT). Copyright (c) 2026 OpenClaw Foundation.
 */
object DirectDispatcher {

    data class Result(val channel: String, val ok: Boolean, val code: Int, val error: String?)

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    /** 并行分发到所有已配置渠道 / fan-out to all configured channels. */
    suspend fun dispatch(config: ChannelConfig, payload: HedwigPayload): List<Result> = coroutineScope {
        config.configuredIds().map { id ->
            async(Dispatchers.IO) { send(id, config, payload) }
        }.awaitAll()
    }

    /** 发送到单个渠道（供测试与分发复用）/ send to a single channel (used by test & dispatch). */
    suspend fun send(channelId: String, config: ChannelConfig, payload: HedwigPayload): Result =
        withContext(Dispatchers.IO) {
            val msg = MessageFormatter.format(payload)
            try {
                when (channelId) {
                    ChannelConfig.ID_TELEGRAM -> sendTelegram(config, msg.text)
                    ChannelConfig.ID_FEISHU -> sendFeishu(config, msg.text)
                    ChannelConfig.ID_DISCORD -> sendDiscord(config, msg.text)
                    ChannelConfig.ID_SLACK -> sendSlack(config, msg.text)
                    ChannelConfig.ID_CUSTOM -> sendCustom(config, msg, payload)
                    ChannelConfig.ID_EMAIL -> sendEmail(config, msg)
                    else -> throw IllegalArgumentException("unknown channel")
                }
            } catch (e: Exception) {
                Result(channelId, false, 0, e.message ?: "error")
            }
        }

    private fun post(channel: String, url: String, body: String, headers: Map<String, String> = emptyMap()): Result {
        val builder = Request.Builder().url(url).post(body.toRequestBody(JSON))
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        client.newCall(builder.build()).execute().use { res ->
            if (res.isSuccessful) return Result(channel, true, res.code, null)
            // 失败时带上响应体，便于定位（如 Resend 422 的具体原因）/ include body on failure.
            val detail = res.body?.string()?.trim()?.take(300).orEmpty()
            return Result(channel, false, res.code, "HTTP ${res.code}${if (detail.isNotEmpty()) ": $detail" else ""}")
        }
    }

    private fun escapeHtml(s: String) =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun sendTelegram(c: ChannelConfig, text: String): Result {
        val url = "https://api.telegram.org/bot${c.telegramBotToken}/sendMessage"
        val body = JSONObject()
            .put("chat_id", c.telegramChatId)
            .put("text", escapeHtml(text))
            .put("parse_mode", "HTML")
            .put("disable_web_page_preview", true)
            .toString()
        return post(ChannelConfig.ID_TELEGRAM, url, body)
    }

    private fun sendFeishu(c: ChannelConfig, text: String): Result {
        val payload = JSONObject().put("msg_type", "text")
            .put("content", JSONObject().put("text", text))
        if (c.feishuSignSecret.isNotBlank()) {
            val ts = System.currentTimeMillis() / 1000
            payload.put("timestamp", ts.toString())
            payload.put("sign", feishuSign(c.feishuSignSecret, ts))
        }
        // 飞书 HTTP 200 但 code!=0 也算失败 / 200 with code!=0 is still a failure.
        val req = Request.Builder().url(c.feishuWebhookUrl)
            .post(payload.toString().toRequestBody(JSON)).build()
        client.newCall(req).execute().use { res ->
            val bodyStr = res.body?.string().orEmpty()
            val code = runCatching { JSONObject(bodyStr).optInt("code", 0) }.getOrDefault(0)
            return if (res.isSuccessful && code == 0) Result(ChannelConfig.ID_FEISHU, true, res.code, null)
            else Result(ChannelConfig.ID_FEISHU, false, res.code, "Feishu: $bodyStr")
        }
    }

    private fun feishuSign(secret: String, timestamp: Long): String {
        val stringToSign = "$timestamp\n$secret"
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(stringToSign.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return Base64.encodeToString(mac.doFinal(ByteArray(0)), Base64.NO_WRAP)
    }

    private fun sendDiscord(c: ChannelConfig, text: String): Result {
        val body = JSONObject()
            .put("username", "HedwigSMS")
            .put("content", text.take(1990))
            .toString()
        return post(ChannelConfig.ID_DISCORD, c.discordWebhookUrl, body)
    }

    private fun sendSlack(c: ChannelConfig, text: String): Result {
        val body = JSONObject().put("text", text).toString()
        return post(ChannelConfig.ID_SLACK, c.slackWebhookUrl, body)
    }

    private fun sendCustom(c: ChannelConfig, msg: MessageFormatter.Formatted, payload: HedwigPayload): Result {
        val data = JSONObject()
        payload.data.sender?.let { data.put("sender", it) }
        payload.data.body?.let { data.put("body", it) }
        payload.data.batteryLevel?.let { data.put("battery_level", it) }
        payload.data.sim?.let { data.put("sim", it) }
        val body = JSONObject()
            .put("title", msg.title)
            .put("text", msg.text)
            .put("type", payload.type)
            .put("device_name", payload.deviceName)
            .put("timestamp", payload.timestamp)
            .put("data", data)
            .toString()
        return post(ChannelConfig.ID_CUSTOM, c.customWebhookUrl, body)
    }

    private fun sendEmail(c: ChannelConfig, msg: MessageFormatter.Formatted): Result {
        val to = JSONArray()
        c.emailTo.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { to.put(it) }
        val html = emailHtml(msg.title, msg.text)
        val body = JSONObject()
            .put("from", c.emailFrom)
            .put("to", to)
            .put("subject", "[HedwigSMS] ${msg.title}")
            .put("html", html)
            .put("text", msg.text)
            .toString()
        return post(ChannelConfig.ID_EMAIL, "https://api.resend.com/emails", body,
            mapOf("Authorization" to "Bearer ${c.resendApiKey}"))
    }

    /** 品牌化邮件模板 / branded email template. */
    private fun emailHtml(title: String, text: String): String {
        val safe = escapeHtml(text)
        return """
            <div style="margin:0;padding:24px 12px;background:#0A0D12;font-family:-apple-system,'PingFang SC','Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
              <div style="max-width:480px;margin:0 auto;background:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 10px 36px rgba(0,0,0,.35);">
                <div style="background:linear-gradient(135deg,#0e2a20 0%,#0a0d12 100%);padding:20px 24px;">
                  <div style="color:#ffffff;font-size:18px;font-weight:700;letter-spacing:.3px;">🦉 HedwigSMS</div>
                  <div style="color:#34D399;font-size:13px;margin-top:3px;">$title</div>
                </div>
                <div style="padding:22px 24px;color:#1b1f24;font-size:15px;line-height:1.75;white-space:pre-wrap;word-break:break-word;">$safe</div>
                <div style="padding:14px 24px;border-top:1px solid #eef0f2;color:#9aa6b2;font-size:12px;">本邮件由 HedwigSMS 自动转发 · 全本地运行 · 零数据收集</div>
              </div>
            </div>
        """.trimIndent()
    }
}
