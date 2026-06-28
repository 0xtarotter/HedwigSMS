package com.hedwig.sms.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 统一外发负载 / Unified outgoing payload.
 *
 * 与 Cloudflare Worker 约定的标准 JSON 格式一致：
 * Matches the standard JSON contract agreed with the Cloudflare Worker:
 *
 * {
 *   "type": "sms" | "battery_low" | "power_connected" | "power_disconnected" | "heartbeat",
 *   "device_name": "String",
 *   "timestamp": Long,
 *   "data": { "sender"?, "body"?, "battery_level"? }
 * }
 */
@Serializable
data class HedwigPayload(
    val type: String,
    @SerialName("device_name") val deviceName: String,
    val timestamp: Long,
    val data: HedwigData = HedwigData(),
) {
    fun toJson(): String = JSON.encodeToString(this)

    companion object {
        val JSON = Json { encodeDefaults = true; explicitNulls = false }

        // 事件类型常量 / event type constants
        const val TYPE_SMS = "sms"
        const val TYPE_BATTERY_LOW = "battery_low"
        const val TYPE_POWER_CONNECTED = "power_connected"
        const val TYPE_POWER_DISCONNECTED = "power_disconnected"
        const val TYPE_HEARTBEAT = "heartbeat"
    }
}

@Serializable
data class HedwigData(
    val sender: String? = null,
    val body: String? = null,
    @SerialName("battery_level") val batteryLevel: Int? = null,
    /** 接收该短信的 SIM 描述（多卡识别）：如「卡2 · 中国移动 · 138****1234」 */
    val sim: String? = null,
)
