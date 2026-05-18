package com.nutrilens.nutrilensai.model

import androidx.compose.runtime.Immutable

@Immutable
sealed class UiState {
    data object ModelNotFound : UiState()
    data object ModelLoading : UiState()
    data object Ready : UiState()
    data object Analyzing : UiState()
    data class Streaming(val partialResponse: String) : UiState()
    data class Result(val analysisResult: AnalysisResult) : UiState()
    data class Error(val errorMessage: String) : UiState()
}
