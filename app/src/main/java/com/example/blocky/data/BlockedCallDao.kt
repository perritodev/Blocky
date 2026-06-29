package com.example.blocky.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedCallDao {
    @Query("SELECT * FROM blocked_calls ORDER BY timestamp DESC")
    fun getAllBlockedCalls(): Flow<List<BlockedCall>>

    @Insert
    suspend fun insert(blockedCall: BlockedCall)

    @Query("SELECT COUNT(*) FROM blocked_calls")
    fun getBlockedCount(): Flow<Int>

    @Delete
    suspend fun delete(blockedCall: BlockedCall)
}
