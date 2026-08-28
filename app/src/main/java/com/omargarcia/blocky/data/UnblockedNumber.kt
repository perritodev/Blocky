package com.omargarcia.blocky.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "unblocked_numbers",
    indices = [Index(value = ["phoneNumber"], unique = true)]
)
data class UnblockedNumber(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val timestamp: Long = System.currentTimeMillis()
)
