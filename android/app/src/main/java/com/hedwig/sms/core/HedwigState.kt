package com.hedwig.sms.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 运行时共享状态 / Runtime shared state.
 *
 * 前台服务写入、Compose UI 观察，实现「状态可视化」。
 * Written by the foreground service, observed by the Compose UI for live status.
 */
object HedwigState {

    enum class Phase { GUARDING, FORWARDING, FAILED }

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running

    private val _phase = MutableStateFlow(Phase.GUARDING)
    val phase: StateFlow<Phase> = _phase

    private val _batteryLevel = MutableStateFlow(-1)
    val batteryLevel: StateFlow<Int> = _batteryLevel

    private val _successCount = MutableStateFlow(0)
    val successCount: StateFlow<Int> = _successCount

    /** 当前正在转发的发件人/事件描述 / who/what is being forwarded right now. */
    private val _forwardingLabel = MutableStateFlow("")
    val forwardingLabel: StateFlow<String> = _forwardingLabel

    fun setRunning(v: Boolean) { _running.value = v }
    fun setPhase(v: Phase) { _phase.value = v }
    fun setBattery(v: Int) { _batteryLevel.value = v }
    fun setSuccessCount(v: Int) { _successCount.value = v }
    fun setForwardingLabel(v: String) { _forwardingLabel.value = v }
}
