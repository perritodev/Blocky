package com.omargarcia.blocky.data

import androidx.room.*

@Dao
interface UnblockedNumberDao {
    @Query("SELECT * FROM unblocked_numbers")
    suspend fun getAllList(): List<UnblockedNumber>

    @Query("SELECT EXISTS(SELECT 1 FROM unblocked_numbers WHERE phoneNumber = :number)")
    suspend fun isUnblocked(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(number: UnblockedNumber)

    @Query("DELETE FROM unblocked_numbers WHERE phoneNumber = :number")
    suspend fun deleteByNumber(number: String)
    
    @Query("DELETE FROM unblocked_numbers")
    suspend fun clearAll()
}
