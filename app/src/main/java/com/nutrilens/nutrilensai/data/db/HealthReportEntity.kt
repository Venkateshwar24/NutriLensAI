package com.nutrilens.nutrilensai.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_report")
data class HealthReportEntity(
    @PrimaryKey val id: Int = 1,
    @ColumnInfo(name = "file_name")   val fileName: String,
    @ColumnInfo(name = "raw_text")    val rawText: String,
    @ColumnInfo(name = "summary")     val summary: String,
    @ColumnInfo(name = "uploaded_at") val uploadedAt: Long
)
