package com.nutrilens.nutrilensai.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nutrilens.nutrilensai.Constants
import com.nutrilens.nutrilensai.NutriLensApplication
import com.nutrilens.nutrilensai.data.db.HealthReportEntity
import com.nutrilens.nutrilensai.model.OcrState
import com.nutrilens.nutrilensai.model.ReportState
import com.nutrilens.nutrilensai.model.UiState
import com.nutrilens.nutrilensai.repository.GemmaRepository
import com.nutrilens.nutrilensai.util.DocumentReader
import com.nutrilens.nutrilensai.util.OcrHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = GemmaRepository(application)
    private val database = (application as NutriLensApplication).database

    private val _uiState = MutableStateFlow<UiState>(UiState.ModelNotFound)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _ocrState = MutableStateFlow<OcrState>(OcrState.Idle)
    val ocrState: StateFlow<OcrState> = _ocrState.asStateFlow()

    private val _reportState = MutableStateFlow<ReportState>(ReportState.NoReport)
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    val modelFilePath: String get() = repository.modelFile().absolutePath

    init {
        checkAndLoadModel()
        loadStoredReport()
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

    private fun loadStoredReport() {
        viewModelScope.launch {
            val stored = database.healthReportDao().getReport()
            _reportState.value = if (stored != null) {
                ReportState.Loaded(stored.fileName, stored.summary, stored.uploadedAt)
            } else {
                ReportState.NoReport
            }
        }
    }

    fun uploadReport(uri: Uri) {
        viewModelScope.launch {
            _reportState.value = ReportState.Extracting
            try {
                val rawText = DocumentReader.extractText(uri, getApplication())
                _reportState.value = ReportState.Summarizing()
                val accumulated = StringBuilder()
                repository.summarizeReportStream(rawText).collect { chunk ->
                    accumulated.append(chunk)
                    _reportState.value = ReportState.Summarizing(accumulated.toString())
                }
                val summary = accumulated.toString().trim()
                val fileName = uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.substringAfterLast('%').orEmpty()
                    .ifEmpty { "health_report" }
                val entity = HealthReportEntity(
                    fileName = fileName,
                    rawText = rawText,
                    summary = summary,
                    uploadedAt = System.currentTimeMillis()
                )
                database.healthReportDao().saveReport(entity)
                _reportState.value = ReportState.Loaded(fileName, summary, entity.uploadedAt)
                Timber.d("Report uploaded and summarized: %s", fileName)
            } catch (e: DocumentReader.UnsupportedFormatException) {
                Timber.e(e, "Unsupported document format")
                _reportState.value = ReportState.Error(e.message ?: "Unsupported format")
            } catch (e: Exception) {
                Timber.e(e, "Failed to upload report")
                _reportState.value = ReportState.Error("Failed to process report: ${e.message}")
            }
        }
    }

    private suspend fun resolveHealthSummary(): String {
        val stored = database.healthReportDao().getReport()
        return stored?.summary?.take(Constants.HEALTH_REPORT_SUMMARY_LIMIT)
            ?: "No personal health data. Apply general nutritional guidelines."
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
        if (!File(imagePath).exists()) {
            _uiState.value = UiState.Error("Image file not found")
            return
        }
        viewModelScope.launch {
            _uiState.value = UiState.Analyzing
            val accumulated = StringBuilder()
            try {
                val summary = resolveHealthSummary()
                repository.analyzeImageStream(imagePath, summary).collect { chunk ->
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
                val summary = resolveHealthSummary()
                repository.analyzeStream(ingredients, summary).collect { chunk ->
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
