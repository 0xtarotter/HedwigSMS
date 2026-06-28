package com.hedwig.sms.core

import android.content.Context

/**
 * 桌面小插件 / 磁贴用的状态快照 / status snapshot for the widget & QS tile.
 *
 * 前台服务把最新状态写入普通 SharedPreferences；小插件/磁贴(可能在独立进程或进程被回收后)
 * 读取它来渲染，无需依赖内存中的 HedwigState。
 * The foreground service writes the latest status here; the widget/tile read it to render
 * even after the process is recycled.
 */
object WidgetState {

    data class Snapshot(
        val running: Boolean,
        val phase: String,   // GUARDING / FORWARDING / FAILED
        val battery: Int,
        val count: Int,
    )

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences("hedwig_widget", Context.MODE_PRIVATE)

    fun save(c: Context, running: Boolean, phase: String, battery: Int, count: Int) {
        prefs(c).edit()
            .putBoolean("running", running)
            .putString("phase", phase)
            .putInt("battery", battery)
            .putInt("count", count)
            .apply()
    }

    fun read(c: Context): Snapshot {
        val p = prefs(c)
        return Snapshot(
            running = p.getBoolean("running", false),
            phase = p.getString("phase", "GUARDING") ?: "GUARDING",
            battery = p.getInt("battery", -1),
            count = p.getInt("count", 0),
        )
    }
}
