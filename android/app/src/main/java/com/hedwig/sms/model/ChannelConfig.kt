package com.hedwig.sms.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 手机直连模式的渠道配置 / Channel config for DIRECT (on-phone) mode.
 *
 * 直连模式下，手机不经服务器，直接把消息发到各 IM 平台（国内可直达：飞书/邮件/Bark/
 * Server酱/企业微信/通用 Webhook；Telegram/Discord/Slack 需手机能连上其服务器）。
 * In direct mode the phone dispatches straight to each IM platform without a server.
 */
@Serializable
data class ChannelConfig(
    val telegramBotToken: String = "",
    val telegramChatId: String = "",
    val feishuWebhookUrl: String = "",
    val feishuSignSecret: String = "",
    val discordWebhookUrl: String = "",
    val slackWebhookUrl: String = "",
    val customWebhookUrl: String = "",
    val resendApiKey: String = "",
    val emailFrom: String = "",
    val emailTo: String = "",
) {
    fun telegramOk() = telegramBotToken.isNotBlank() && telegramChatId.isNotBlank()
    fun feishuOk() = feishuWebhookUrl.isNotBlank()
    fun discordOk() = discordWebhookUrl.isNotBlank()
    fun slackOk() = slackWebhookUrl.isNotBlank()
    fun customOk() = customWebhookUrl.isNotBlank()
    fun emailOk() = resendApiKey.isNotBlank() && emailFrom.isNotBlank() && emailTo.isNotBlank()

    /** 已配置的渠道 id 列表 / ids of configured channels. */
    fun configuredIds(): List<String> = buildList {
        if (telegramOk()) add(ID_TELEGRAM)
        if (feishuOk()) add(ID_FEISHU)
        if (discordOk()) add(ID_DISCORD)
        if (slackOk()) add(ID_SLACK)
        if (customOk()) add(ID_CUSTOM)
        if (emailOk()) add(ID_EMAIL)
    }

    fun anyConfigured(): Boolean = configuredIds().isNotEmpty()

    fun toJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        const val ID_TELEGRAM = "telegram"
        const val ID_FEISHU = "feishu"
        const val ID_DISCORD = "discord"
        const val ID_SLACK = "slack"
        const val ID_CUSTOM = "webhook"
        const val ID_EMAIL = "email"

        fun fromJson(s: String?): ChannelConfig =
            if (s.isNullOrBlank()) ChannelConfig()
            else runCatching { JSON.decodeFromString<ChannelConfig>(s) }.getOrDefault(ChannelConfig())
    }
}
