package com.example.blocky.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WhitelistedNumberDao {
    @Query("SELECT * FROM whitelisted_numbers ORDER BY timestamp DESC")
    fun getAllWhitelisted(): Flow<List<WhitelistedNumber>>

    @Query("SELECT * FROM whitelisted_numbers")
    suspend fun getAllList(): List<WhitelistedNumber>

    @Query("SELECT EXISTS(SELECT 1 FROM whitelisted_numbers WHERE phoneNumber = :number)")
    suspend fun isWhitelisted(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(number: WhitelistedNumber)

    @Delete
    suspend fun delete(number: WhitelistedNumber)

    @Query("DELETE FROM whitelisted_numbers WHERE phoneNumber = :number")
    suspend fun deleteByNumber(number: String)
}

