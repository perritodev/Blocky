package com.example.blocky.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCallDao {
    @Query("SELECT * FROM blocked_calls WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getBlockedCallsSince(since: Long): Flow<List<BlockedCall>>

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE timestamp >= :since")
    fun getDailyBlockedCount(since: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: BlockedCall)

    @Query("DELETE FROM blocked_calls")
    suspend fun clearAll()
}
