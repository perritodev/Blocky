package com.omargarcia.blocky.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCallDao {
    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BlockedCall>>

    @Query("SELECT * FROM blocked_calls WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getBlockedCallsSince(since: Long): Flow<List<BlockedCall>>

    @Query("SELECT * FROM blocked_calls WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getBlockedCallsSinceList(since: Long): List<BlockedCall>

    @Query("SELECT COUNT(*) FROM blocked_calls WHERE timestamp >= :since")
    fun getDailyBlockedCount(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM blocked_calls")
    fun getTotalBlockedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(call: BlockedCall)

    @Delete
    suspend fun delete(call: BlockedCall)

    @Query("DELETE FROM blocked_calls WHERE phoneNumber = :phoneNumber")
    suspend fun deleteByNumber(phoneNumber: String)

    @Query("DELETE FROM blocked_calls")
    suspend fun clearAll()
}
