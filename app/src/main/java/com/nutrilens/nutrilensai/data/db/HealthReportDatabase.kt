package com.nutrilens.nutrilensai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [HealthReportEntity::class], version = 1, exportSchema = false)
abstract class HealthReportDatabase : RoomDatabase() {

    abstract fun healthReportDao(): HealthReportDao

    companion object {
        @Volatile private var INSTANCE: HealthReportDatabase? = null

        fun getInstance(context: Context): HealthReportDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    HealthReportDatabase::class.java,
                    "health_report.db"
                ).build().also { INSTANCE = it }
            }
    }
}
