package com.nutrilens.nutrilensai.model

import androidx.compose.runtime.Immutable

@Immutable
sealed class ReportState {
    data object NoReport : ReportState()
    data object Extracting : ReportState()
    data class Summarizing(val partialSummary: String = "") : ReportState()
    data class Loaded(
        val fileName: String,
        val summary: String,
        val uploadedAt: Long
    ) : ReportState()
    data class Error(val message: String) : ReportState()
}
