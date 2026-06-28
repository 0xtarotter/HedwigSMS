package com.hedwig.sms.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.hedwig.sms.R
import com.hedwig.sms.core.Constants
import com.hedwig.sms.core.WidgetState
import com.hedwig.sms.data.SecurePrefs
import com.hedwig.sms.service.HedwigForwardService
import com.hedwig.sms.ui.MainActivity

/**
 * 下拉快捷开关磁贴 / Quick Settings tile.
 *
 * 在系统下拉的「快捷设置」中常驻一个开关：显示守护状态，点按启动/停止。
 * A persistent toggle in the system Quick Settings: shows guard status, tap to start/stop.
 *
 * 说明：Android 14+ 限制后台启动前台服务，因此「启动」走打开 App 自动启动（前台启动，合规）；
 * 「停止」可直接停止。
 */
class HedwigTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = SecurePrefs(this)
        if (WidgetState.read(this).running) {
            // 停止守护 / stop guard
            prefs.serviceEnabled = false
            stopService(Intent(this, HedwigForwardService::class.java))
            val s = WidgetState.read(this)
            WidgetState.save(this, false, s.phase, s.battery, s.count)
            updateTile()
        } else {
            // 启动：打开 App 并自动启动（前台合规）/ start via opening the app (foreground-compliant)
            openApp(autostart = prefs.isConfigured())
        }
    }

    private fun openApp(autostart: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (autostart) putExtra(Constants.EXTRA_AUTOSTART, true)
        }
        var flags = PendingIntent.FLAG_UPDATE_CURRENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags = flags or PendingIntent.FLAG_IMMUTABLE
        val pi = PendingIntent.getActivity(this, 20, intent, flags)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION", "StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile() {
        val tile: Tile = qsTile ?: return
        val running = WidgetState.read(this).running
        tile.state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(
                if (running) R.string.status_guarding else R.string.status_stopped,
            )
        }
        tile.updateTile()
    }
}
