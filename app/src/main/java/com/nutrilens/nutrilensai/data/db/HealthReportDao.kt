package com.nutrilens.nutrilensai.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HealthReportDao {

    @Query("SELECT * FROM health_report WHERE id = 1")
    fun observeReport(): Flow<HealthReportEntity?>

    @Query("SELECT * FROM health_report WHERE id = 1")
    suspend fun getReport(): HealthReportEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveReport(report: HealthReportEntity)

    @Query("DELETE FROM health_report WHERE id = 1")
    suspend fun deleteReport()
}
