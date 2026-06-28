package com.hedwig.sms.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat

/**
 * 多卡识别 / Multi-SIM helper.
 *
 * 从短信广播携带的 subscription id 解析出「是哪张卡收到的」：卡槽、运营商、号码（若可得）。
 * Resolves which SIM received an SMS (slot, carrier, number if available) from the
 * subscription id carried by the SMS broadcast.
 *
 * 需要 READ_PHONE_STATE（号码另需 READ_PHONE_NUMBERS）；无权限时优雅降级为「卡N」或 null。
 */
object SimInfoUtil {

    /** 从短信 Intent 取 subscription id / extract subscription id from the SMS intent. */
    fun subIdFromIntent(extras: android.os.Bundle?): Int {
        if (extras == null) return -1
        for (key in listOf("subscription", "android.telephony.extra.SUBSCRIPTION_INDEX", "phone")) {
            if (extras.containsKey(key)) {
                val v = extras.getInt(key, -1)
                if (v >= 0) return v
            }
        }
        return -1
    }

    /**
     * 生成 SIM 描述，如「卡2 · 中国移动 · 138****1234」。无法识别返回 null。
     * Build a label like "SIM2 · Carrier · 138****1234". Returns null if unknown.
     */
    fun label(context: Context, subId: Int): String? {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return if (subId >= 0) null else null

        return try {
            val sm = ContextCompat.getSystemService(context, SubscriptionManager::class.java)
                ?: return null
            @Suppress("MissingPermission")
            val info = if (subId >= 0) sm.getActiveSubscriptionInfo(subId) else null
            if (info == null) return null

            val slot = info.simSlotIndex + 1
            val carrier = info.carrierName?.toString()?.takeIf { it.isNotBlank() }
            val number = readNumber(context, sm, info)
            buildString {
                append("卡$slot")
                if (carrier != null) append(" · $carrier")
                if (!number.isNullOrBlank()) append(" · ${maskNumber(number)}")
            }
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private fun readNumber(context: Context, sm: SubscriptionManager, info: android.telephony.SubscriptionInfo): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasNum = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.READ_PHONE_NUMBERS,
                ) == PackageManager.PERMISSION_GRANTED
                if (!hasNum) return null
                @Suppress("MissingPermission")
                sm.getPhoneNumber(info.subscriptionId).ifBlank { null }
            } else {
                @Suppress("DEPRECATION", "MissingPermission")
                info.number?.ifBlank { null }
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 号码脱敏：保留前 3 后 4 / mask the middle digits. */
    private fun maskNumber(n: String): String {
        val digits = n.filter { it.isDigit() || it == '+' }
        return if (digits.length >= 7) {
            digits.take(3) + "****" + digits.takeLast(4)
        } else {
            digits
        }
    }
}
