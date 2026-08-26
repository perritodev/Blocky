package com.example.blocky.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        BlockedCall::class, 
        WhitelistedNumber::class, 
        PermanentBlockedNumber::class,
        UnblockedNumber::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedCallDao(): BlockedCallDao
    abstract fun whitelistedNumberDao(): WhitelistedNumberDao
    abstract fun permanentBlockedNumberDao(): PermanentBlockedNumberDao
    abstract fun unblockedNumberDao(): UnblockedNumberDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "blocky_database",
                )
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
