package com.hedwig.sms.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 本地转发日志 / Local forwarding log entry.
 *
 * 记录每一次转发动作：时间、类型、成功/失败、错误码与简要详情。
 * Records each forwarding action: time, type, success/failure, error code & detail.
 */
@Entity(tableName = "forward_logs")
data class ForwardLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val success: Boolean,
    /** HTTP 状态码或 0（网络异常）/ HTTP status code, or 0 for network errors. */
    val statusCode: Int = 0,
    /** 简要详情：发件人 / 内容摘要 / 错误信息 / brief detail. */
    val detail: String = "",
)
