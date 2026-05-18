package com.nutrilens.nutrilensai.repository

import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.nutrilens.nutrilensai.Constants
import com.nutrilens.nutrilensai.model.AnalysisResult
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
            backend = Backend.CPU(),
            visionBackend = Backend.CPU()
        )
        val newEngine = Engine(config)
        newEngine.initialize()
        engineLock.withLock {
            engine?.close()
            engine = newEngine
        }
        Timber.d("Model loaded successfully")
    }

    fun analyzeStream(ingredients: String, healthSummary: String): Flow<String> = flow {
        val currentEngine = engineLock.withLock { engine } ?: error("Model not loaded")

        val systemInstruction = """
You are a clinical nutrition AI. Analyze the food ingredients against the patient's health profile and give a safety verdict using your full nutritional knowledge.
Prioritize flagging: excess sugar/refined carbs for diabetics or pre-diabetics; high sodium for hypertension; saturated fat for high LDL; high-purine foods for high uric acid; known allergens. For ingredients outside these priorities, apply your general knowledge of nutrition and health impacts.
Always give a verdict based on what you know — never say the data is insufficient or rules don't apply.
Respond ONLY in this exact format, no extra text:
VERDICT: [SAFE / CAUTION / AVOID]
REASON: [2-3 sentences explaining the key nutritional concern or why the food is safe for this patient]
        """.trimIndent()

        val userMessage = """
PATIENT HEALTH PROFILE:
$healthSummary

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

    fun analyzeImageStream(imagePath: String, healthSummary: String): Flow<String> = flow {
        val currentEngine = engineLock.withLock { engine } ?: error("Model not loaded")

        val systemInstruction = """
You are a clinical nutrition AI. Analyze the food label image for this patient using the following steps:
1. Try to read ingredients and nutrition facts from the image.
2. If ingredients are found, analyze them against the patient's health profile.
3. If no ingredient list is visible but you can identify the food or product, use your general nutritional knowledge about it to give a verdict.
4. Only if you cannot identify any food, product, or ingredients at all (blurry, not food-related, obscured), respond with VERDICT: RESCAN.
Prioritize flagging: excess sugar/refined carbs for diabetics; high sodium for hypertension; saturated fat for high LDL; high-purine foods for high uric acid; known allergens. For anything else, apply general nutrition knowledge.
Respond ONLY in this exact format, no extra text:
VERDICT: [SAFE / CAUTION / AVOID / RESCAN]
REASON: [2-3 sentences. For RESCAN: briefly explain what was unclear and give one practical tip for a better scan.]
        """.trimIndent()

        val userText = """
PATIENT HEALTH PROFILE:
$healthSummary

Read the ingredients and nutrition facts from the food label in the image, then analyze whether this product is safe for the patient above.
        """.trimIndent()

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)
        )

        currentEngine.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessageAsync(
                Contents.of(Content.ImageFile(imagePath), Content.Text(userText))
            ).collect { chunk -> emit(chunk.toString()) }
        }
    }.flowOn(Dispatchers.IO)

    fun summarizeReportStream(rawText: String): Flow<String> = flow {
        val currentEngine = engineLock.withLock { engine } ?: error("Model not loaded")

        val systemInstruction = """
You are a medical data extractor. Read the health report and output a compact patient profile for food safety analysis.
Output ONLY this structure, no extra text:
PATIENT: [age]y [sex] BMI:[value]
ABNORMAL: [param=value pairs, e.g. LDL=136,TG=210,BP=146/92,HbA1c=5.0,UricAcid=6.4,eGFR=68,VitD=21]
RESTRICTIONS: [comma-separated flags from: HIGH_SODIUM,HIGH_SAT_FAT,HIGH_SUGAR,HIGH_PURINE,NUT_ALLERGY,NONE]
NOTES: [one sentence on the most critical dietary concern, or NONE]
        """.trimIndent()

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(systemInstruction),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.2)
        )

        currentEngine.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessageAsync(
                "HEALTH REPORT:\n${rawText.take(Constants.HEALTH_REPORT_RAW_TEXT_LIMIT)}"
            ).collect { chunk -> emit(chunk.toString()) }
        }
    }.flowOn(Dispatchers.IO)

    fun parseResponse(response: String): AnalysisResult {
        val verdictLine = response.lines().firstOrNull { it.startsWith("VERDICT:") }
        val reasonLine = response.lines().firstOrNull { it.startsWith("REASON:") }

        val verdict = verdictLine?.removePrefix("VERDICT:")?.trim()
            ?.uppercase()
            ?.let {
                when {
                    it.contains("RESCAN") -> "RESCAN"
                    it.contains("SAFE")   -> "SAFE"
                    it.contains("AVOID")  -> "AVOID"
                    else                  -> "CAUTION"
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
