package com.hedwig.sms.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ForwardLogDao {

    /** 实时观察最近 N 条日志（供 UI 列表）/ observe the latest N logs for the UI. */
    @Query("SELECT * FROM forward_logs ORDER BY timestamp DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<ForwardLog>>

    /** 成功转发计数 / count of successful forwards. */
    @Query("SELECT COUNT(*) FROM forward_logs WHERE success = 1")
    fun observeSuccessCount(): Flow<Int>

    @Insert
    suspend fun insert(log: ForwardLog): Long

    /** 仅保留最近 maxKeep 条，删除其余 / keep only the most recent maxKeep rows. */
    @Query(
        "DELETE FROM forward_logs WHERE id NOT IN " +
            "(SELECT id FROM forward_logs ORDER BY timestamp DESC LIMIT :maxKeep)",
    )
    suspend fun trim(maxKeep: Int)

    @Query("DELETE FROM forward_logs")
    suspend fun clear()
}
