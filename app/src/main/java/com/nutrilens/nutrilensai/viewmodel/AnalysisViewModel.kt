package com.nutrilens.nutrilensai.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nutrilens.nutrilensai.model.OcrState
import com.nutrilens.nutrilensai.model.UiState
import com.nutrilens.nutrilensai.repository.GemmaRepository
import com.nutrilens.nutrilensai.util.OcrHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GemmaRepository(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.ModelNotFound)
    val uiState: StateFlow<UiState> = _uiState

    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Idle)
    val ocrState: StateFlow<OcrState> = _ocrState

    val modelFilePath: String get() = repository.modelFile().absolutePath

    init {
        checkAndLoadModel()
    }

    fun checkAndLoadModel() {
        if (!repository.isModelAvailable()) {
            _uiState.value = UiState.ModelNotFound
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.ModelLoading
            try {
                repository.loadModel()
                _uiState.value = UiState.Ready
            } catch (e: Exception) {
                Timber.e(e, "Failed to load model")
                _uiState.value = UiState.Error("Failed to load model: ${e.message}")
            }
        }
    }

    fun runOcr(uri: Uri) {
        viewModelScope.launch {
            _ocrState.value = OcrState.Processing
            try {
                val extractedText = OcrHelper.extractText(uri, getApplication())
                _ocrState.value = OcrState.Success(extractedText)
            } catch (e: Exception) {
                Timber.e(e, "OCR failed for uri: %s", uri)
                _ocrState.value = OcrState.Error("OCR failed: ${e.message}")
            }
        }
    }

    fun clearOcr() {
        _ocrState.value = OcrState.Idle
    }

    fun analyzeFromImage(imagePath: String) {
        if (!java.io.File(imagePath).exists()) {
            _uiState.value = UiState.Error("Image file not found")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Analyzing
            val accumulated = StringBuilder()
            try {
                repository.analyzeImageStream(imagePath).collect { chunk ->
                    accumulated.append(chunk)
                    _uiState.value = UiState.Streaming(accumulated.toString())
                }
                _uiState.value = UiState.Result(repository.parseResponse(accumulated.toString()))
            } catch (e: Exception) {
                Timber.e(e, "Image analysis failed")
                _uiState.value = UiState.Error("Image analysis failed: ${e.message}")
            }
        }
    }

    fun analyze(ingredients: String) {
        if (ingredients.isBlank()) {
            _uiState.value = UiState.Error("Please enter an ingredient list before analyzing.")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Analyzing
            val accumulated = StringBuilder()
            try {
                repository.analyzeStream(ingredients).collect { chunk ->
                    accumulated.append(chunk)
                    _uiState.value = UiState.Streaming(accumulated.toString())
                }
                _uiState.value = UiState.Result(repository.parseResponse(accumulated.toString()))
            } catch (e: Exception) {
                Timber.e(e, "Analysis failed")
                _uiState.value = UiState.Error("Analysis failed: ${e.message}")
            }
        }
    }

    fun reset() {
        _uiState.value = UiState.Ready
    }

    override fun onCleared() {
        super.onCleared()
        repository.close()
    }
}
