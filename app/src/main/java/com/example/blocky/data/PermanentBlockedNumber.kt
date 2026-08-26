package com.example.blocky.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "permanent_blocked_numbers",
    indices = [Index(value = ["phoneNumber"], unique = true)]
)
data class PermanentBlockedNumber(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val phoneNumber: String,
    val timestamp: Long = System.currentTimeMillis()
)
