package com.nutrilens.nutrilensai.model

import androidx.compose.runtime.Immutable

@Immutable
sealed class OcrState {
    data object Idle : OcrState()
    data object Processing : OcrState()
    data class Success(val extractedText: String) : OcrState()
    data class Error(val errorMessage: String) : OcrState()
}
