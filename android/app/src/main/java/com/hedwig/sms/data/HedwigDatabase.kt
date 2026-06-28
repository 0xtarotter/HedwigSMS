package com.hedwig.sms.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ForwardLog::class], version = 1, exportSchema = false)
abstract class HedwigDatabase : RoomDatabase() {
    abstract fun forwardLogDao(): ForwardLogDao

    companion object {
        @Volatile
        private var INSTANCE: HedwigDatabase? = null

        fun get(context: Context): HedwigDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HedwigDatabase::class.java,
                    "hedwig.db",
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
