package com.hedwig.sms.service

import android.content.Context
import android.os.Build
import com.hedwig.sms.core.BatteryUtil
import com.hedwig.sms.data.SecurePrefs
import com.hedwig.sms.model.ChannelConfig
import com.hedwig.sms.model.HedwigData
import com.hedwig.sms.model.HedwigPayload
import com.hedwig.sms.net.DirectDispatcher
import com.hedwig.sms.net.HedwigApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 守护开启/停止时主动告知 / notify when guard starts or stops.
 *
 * 用独立的进程级协程作用域，不依赖前台服务生命周期 —— 这样「停止」消息也能在服务结束后发完。
 * Uses a process-level scope independent of the service lifecycle, so the "stopped"
 * message still completes after the service is gone.
 */
object ServiceStatusNotifier {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun send(context: Context, on: Boolean) {
        val appCtx = context.applicationContext
        val prefs = SecurePrefs(appCtx)
        if (!prefs.isConfigured()) return

        val payload = HedwigPayload(
            type = if (on) HedwigPayload.TYPE_SERVICE_ON else HedwigPayload.TYPE_SERVICE_OFF,
            deviceName = prefs.deviceName.ifBlank { Build.MODEL ?: "Android" },
            timestamp = System.currentTimeMillis(),
            data = HedwigData(batteryLevel = BatteryUtil.currentLevel(appCtx)),
        )

        if (prefs.mode == SecurePrefs.MODE_DIRECT) {
            val cfg = ChannelConfig.fromJson(prefs.channelConfigJson)
            scope.launch { runCatching { DirectDispatcher.dispatch(cfg, payload) } }
        } else {
            // OkHttp enqueue 使用自有线程池，独立于服务 / OkHttp's own pool, independent of the service.
            HedwigApiClient.send(prefs.buildRequestUrl(), payload) { }
        }
    }
}
