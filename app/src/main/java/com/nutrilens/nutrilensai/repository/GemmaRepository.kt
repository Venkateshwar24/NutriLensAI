package com.nutrilens.nutrilensai.repository

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.nutrilens.nutrilensai.Constants
import com.nutrilens.nutrilensai.model.AnalysisResult
import com.nutrilens.nutrilensai.util.AssetReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

class GemmaRepository(private val context: Context) {

    private val engineLock = Mutex()
    private var engine: Engine? = null

    fun modelFile(): File = File(context.getExternalFilesDir(null), Constants.MODEL_FILENAME)

    fun isModelAvailable(): Boolean = modelFile().exists()

    suspend fun loadModel() = withContext(Dispatchers.IO) {
        Timber.d("Loading model from: %s", modelFile().absolutePath)
        val config = EngineConfig(
            modelPath = modelFile().absolutePath,
            backend = Backend.CPU()
        )
        val newEngine = Engine(config)
        newEngine.initialize()
        engineLock.withLock {
            engine?.close()
            engine = newEngine
        }
        Timber.d("Model loaded successfully")
    }

    fun analyzeStream(ingredients: String): Flow<String> = flow {
        val currentEngine = engineLock.withLock { engine } ?: error("Model not loaded")
        // Truncate to ~375 tokens to stay within the model's context window
        val healthReport = AssetReader.readHealthReport(context).take(1500)

        val systemInstruction = """
You are a clinical nutrition AI. Given a patient's health report and food ingredients, decide if the food is safe.
Rules: high sugar->flag if HbA1c>=6.5% or pre-diabetes; high sodium->flag if BP>130/80; high sat-fat->flag if LDL high; high-purine foods->flag if uric acid high; allergens->always avoid.
Respond ONLY in this exact format, no extra text:
VERDICT: [SAFE / CAUTION / AVOID]
REASON: [2-3 sentences naming the patient's specific condition and the specific ingredient or nutrient of concern]
        """.trimIndent()

        val userMessage = """
PATIENT HEALTH PROFILE:
$healthReport

PRODUCT INGREDIENTS:
$ingredients
        """.trimIndent()

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)
        )

        currentEngine.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessageAsync(userMessage)
                .collect { chunk -> emit(chunk.toString()) }
        }
    }.flowOn(Dispatchers.IO)

    fun parseResponse(response: String): AnalysisResult {
        val verdictLine = response.lines().firstOrNull { it.startsWith("VERDICT:") }
        val reasonLine = response.lines().firstOrNull { it.startsWith("REASON:") }

        val verdict = verdictLine?.removePrefix("VERDICT:")?.trim()
            ?.uppercase()
            ?.let {
                when {
                    it.contains("SAFE") -> "SAFE"
                    it.contains("AVOID") -> "AVOID"
                    else -> "CAUTION"
                }
            } ?: "CAUTION"

        val explanation = reasonLine?.removePrefix("REASON:")?.trim()
            ?: response.trim().ifEmpty { "Unable to parse the model response." }

        return AnalysisResult(verdict, explanation)
    }

    fun close() {
        val engineToClose = engine
        engine = null
        engineToClose?.close()
        Timber.d("GemmaRepository closed")
    }
}
