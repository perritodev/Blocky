package com.example.blocky.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PermanentBlockedNumberDao {
    @Query("SELECT * FROM permanent_blocked_numbers ORDER BY timestamp DESC")
    fun getAll(): Flow<List<PermanentBlockedNumber>>

    @Query("SELECT * FROM permanent_blocked_numbers")
    suspend fun getAllList(): List<PermanentBlockedNumber>

    @Query("SELECT EXISTS(SELECT 1 FROM permanent_blocked_numbers WHERE phoneNumber = :number)")
    suspend fun isBlocked(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(number: PermanentBlockedNumber)

    @Delete
    suspend fun delete(number: PermanentBlockedNumber)

    @Query("DELETE FROM permanent_blocked_numbers")
    suspend fun clearAll()
}
