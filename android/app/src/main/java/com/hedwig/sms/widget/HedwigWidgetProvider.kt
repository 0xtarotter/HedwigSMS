package com.hedwig.sms.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import com.hedwig.sms.R
import com.hedwig.sms.core.Constants
import com.hedwig.sms.core.WidgetState
import com.hedwig.sms.data.SecurePrefs
import com.hedwig.sms.service.HedwigForwardService
import com.hedwig.sms.ui.MainActivity

/**
 * 桌面小插件 / Home-screen App Widget.
 *
 * 展示运行状态、电量、累计转发；整块点按打开 App，开关按钮可启动/停止守护。
 * Shows status, battery and forwarded count; tapping opens the app, the button toggles guard.
 */
class HedwigWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { renderOne(context, mgr, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        mgr: AppWidgetManager,
        id: Int,
        newOptions: android.os.Bundle?,
    ) {
        renderOne(context, mgr, id)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Constants.ACTION_WIDGET_STOP) {
            // 停止守护（停止前台服务在后台是允许的）/ stopping a foreground service is allowed in bg.
            SecurePrefs(context).serviceEnabled = false
            context.stopService(Intent(context, HedwigForwardService::class.java))
            WidgetState.save(context, false, "GUARDING",
                WidgetState.read(context).battery, WidgetState.read(context).count)
            updateAll(context)
        }
    }

    private fun renderOne(context: Context, mgr: AppWidgetManager, id: Int) {
        val s = WidgetState.read(context)
        val rv = RemoteViews(context.packageName, R.layout.widget_hedwig)

        val (emoji, statusText) = when {
            !s.running -> "⚪" to context.getString(R.string.status_stopped)
            s.phase == "FORWARDING" -> "🔄" to context.getString(R.string.status_forwarding)
            s.phase == "FAILED" -> "⚠️" to context.getString(R.string.status_failed)
            else -> "🟢" to context.getString(R.string.status_guarding)
        }
        rv.setTextViewText(R.id.w_status, "$emoji  $statusText")
        val bat = if (s.battery in 0..100) "🔋 ${s.battery}%" else "🔋 —"
        rv.setTextViewText(R.id.w_stats, "$bat   📈 ${s.count}")

        // 转发路径：面积够大时显示已配置渠道 / show configured routes when tall enough
        val minH = runCatching {
            mgr.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        }.getOrDefault(0)
        val routes = channelSummary(context)
        if (minH >= 130 && routes.isNotEmpty()) {
            rv.setTextViewText(R.id.w_channels, routes)
            rv.setViewVisibility(R.id.w_channels, android.view.View.VISIBLE)
        } else {
            rv.setViewVisibility(R.id.w_channels, android.view.View.GONE)
        }

        // 整块点按 → 打开 App / whole widget → open app
        rv.setOnClickPendingIntent(R.id.w_root, openAppIntent(context, autostart = false))

        // 开关按钮 / toggle button
        if (s.running) {
            rv.setTextViewText(R.id.w_toggle, context.getString(R.string.widget_stop))
            rv.setOnClickPendingIntent(R.id.w_toggle, stopIntent(context))
        } else {
            rv.setTextViewText(R.id.w_toggle, context.getString(R.string.widget_start))
            rv.setOnClickPendingIntent(R.id.w_toggle, openAppIntent(context, autostart = true))
        }

        mgr.updateAppWidget(id, rv)
    }

    /** 已配置的转发路径摘要 / summary of configured forwarding routes. */
    private fun channelSummary(context: Context): String {
        val prefs = SecurePrefs(context)
        if (prefs.mode == SecurePrefs.MODE_DIRECT) {
            val labels = com.hedwig.sms.model.ChannelConfig.fromJson(prefs.channelConfigJson)
                .configuredIds().map {
                    when (it) {
                        com.hedwig.sms.model.ChannelConfig.ID_TELEGRAM -> "✈️ Telegram"
                        com.hedwig.sms.model.ChannelConfig.ID_FEISHU -> "🐦 飞书"
                        com.hedwig.sms.model.ChannelConfig.ID_DISCORD -> "🎮 Discord"
                        com.hedwig.sms.model.ChannelConfig.ID_SLACK -> "💬 Slack"
                        com.hedwig.sms.model.ChannelConfig.ID_CUSTOM -> "🔗 Webhook"
                        com.hedwig.sms.model.ChannelConfig.ID_EMAIL -> "✉️ Email"
                        com.hedwig.sms.model.ChannelConfig.ID_SMTP -> "📮 SMTP"
                        else -> it
                    }
                }
            return labels.joinToString("   ")
        }
        // 中转模式渠道在云端，App 不可知 / relay channels live in the cloud.
        return if (prefs.workerUrl.isNotBlank()) "☁️ 中转模式 Relay" else ""
    }

    private fun openAppIntent(context: Context, autostart: Boolean): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (autostart) putExtra(Constants.EXTRA_AUTOSTART, true)
        }
        return PendingIntent.getActivity(
            context, if (autostart) 11 else 10, intent, piFlags(),
        )
    }

    private fun stopIntent(context: Context): PendingIntent {
        val intent = Intent(context, HedwigWidgetProvider::class.java).apply {
            action = Constants.ACTION_WIDGET_STOP
        }
        return PendingIntent.getBroadcast(context, 12, intent, piFlags())
    }

    private fun piFlags(): Int {
        var f = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) f = f or PendingIntent.FLAG_IMMUTABLE
        return f
    }

    companion object {
        /** 供服务在状态变化时刷新全部小插件 / refresh all widgets when status changes. */
        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, HedwigWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val provider = HedwigWidgetProvider()
            provider.onUpdate(context, mgr, ids)
        }
    }
}
