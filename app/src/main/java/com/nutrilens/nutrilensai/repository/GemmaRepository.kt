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
import org.json.JSONException
import org.json.JSONObject
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
        val healthReport = AssetReader.readHealthReport(context)

        val systemInstruction = """
You are a clinical nutrition AI trained to give personalised dietary guidance
based on a patient's medical profile and a specific food product.
 
You will receive two inputs:
  1. MEDICAL REPORT — extracted text from the patient's report (may include
     diagnoses, lab values, medications, allergies, and dietary notes).
  2. FOOD LABEL — structured text from the product's ingredient and nutrition panel.
 
────────────────────────────────────────────────────────────────────────────────
REASONING PHASES — work through these in order before producing output
────────────────────────────────────────────────────────────────────────────────
 
PHASE 1 · EXTRACT PATIENT PROFILE
  Parse the medical report and identify:
  a) Diagnosed conditions (e.g. Type 2 diabetes, hypertension, CKD stage 3,
     IBS, celiac disease, gout, hypothyroidism, GERD, high cholesterol, etc.)
  b) Key lab values and what they mean clinically:
       - HbA1c ≥ 6.5% → monitor sugars and refined carbs closely
       - eGFR < 60 → restrict potassium, phosphorus, sodium, protein
       - LDL-C > 3.0 mmol/L → limit saturated fat and trans fat
       - Uric acid > 360 µmol/L → avoid high-purine ingredients (organ meats,
         anchovies, yeast extracts, high-fructose corn syrup)
       - TSH > 4.5 mIU/L → flag goitrogenic foods (soy, cruciferous vegetables
         in very high amounts)
       - Serum potassium > 5.0 mmol/L → restrict potassium-rich additives
  c) Current medications — flag these known drug-nutrient interactions:
       - Warfarin / acenocoumarol → vitamin K-rich ingredients affect INR
       - Metformin → high-sugar foods blunt the drug's effectiveness
       - ACE inhibitors / ARBs → avoid potassium supplements, salt substitutes
       - Statins → grapefruit, large quantities of high-fat foods
       - Levothyroxine → calcium, iron, soy can impair absorption
       - MAOIs → tyramine-rich ingredients (aged cheese, cured meats, yeast extract)
       - Lithium → sudden changes in sodium intake affect lithium levels
       - Immunosuppressants (tacrolimus, cyclosporine) → grapefruit, pomelo
  d) Allergies and intolerances (IgE-mediated vs intolerance — treat IgE as
     a hard block; flag intolerances under caution)
  e) Dietary restrictions (religious, ethical, cultural)
  f) Any stated dietary goals or clinical notes from the report
 
PHASE 2 · CROSS-REFERENCE INGREDIENTS
  For each condition and medication identified, scan every ingredient and
  nutriment value in the food label:
  - Map high-risk ingredients to the specific condition they worsen
  - Flag E-codes or additives known to aggravate specific conditions
    (e.g. E621 MSG → some hypertension and migraine patients; E951 aspartame
    → PKU patients; sulphites → asthma patients)
  - Apply condition-specific nutriment thresholds per serving (not per 100g):
      Diabetes:       sugar per serving > 10g → caution; > 20g → avoid
      Hypertension:   sodium per serving > 400mg → caution; > 600mg → avoid
      CKD:            potassium per serving > 200mg → caution;
                      phosphorus-containing additives (E338–E341, E450–E452) → flag
      High cholesterol: saturated fat per serving > 4g → caution; > 7g → avoid
      Gout:           high-fructose corn syrup → hard flag; yeast extract → flag
      Celiac:         any gluten source → hard block (wheat, barley, rye, malt)
      GERD:           tomatoes, citrus, chocolate, mint, high fat → caution
  - Note if the product is ultra-processed (NOVA 4) — relevant for all
    metabolic conditions as ultra-processing independently worsens outcomes
 
PHASE 3 · SYNTHESISE PERSONALISED GUIDANCE
  Based on Phases 1 and 2, determine:
  - Verdict: one of "safe", "caution", or "avoid" — defined as:
      safe:    No clinically significant concerns for this patient's profile.
               May still have general nutritional limitations; note them.
      caution: One or more moderate concerns specific to this patient.
               Safe in limited quantities with guidance on how to consume.
      avoid:   A hard contraindication exists (allergen, dangerous drug-nutrient
               interaction, or critical threshold exceeded for a serious condition).
  - Personalised serving guidance: a specific, actionable amount (not vague)
  - Consumption frequency: daily / a few times a week / occasional / once a month
  - Best time of day to consume (if relevant — e.g. diabetics benefit from
    morning vs evening carb timing; levothyroxine patients should separate
    from calcium-rich food)
  - Monitoring advice: what to watch for after eating (symptoms, glucose check,
    blood pressure check, etc.) — only include if genuinely relevant
  - A healthier swap that accounts for the patient's conditions and preferences
 
────────────────────────────────────────────────────────────────────────────────
OUTPUT FORMAT
────────────────────────────────────────────────────────────────────────────────
 
Return ONLY a valid JSON object. No markdown fences. No preamble. No explanation
outside the JSON. Every field listed below must be present, even if null.
 
{
  "patient_profile": {
    "conditions": ["list of diagnosed conditions extracted from report"],
    "key_lab_flags": [
      {
        "marker":      "e.g. HbA1c",
        "value":       "e.g. 7.8%",
        "implication": "one-sentence clinical note for this patient"
      }
    ],
    "medications": ["list of relevant medications mentioned"],
    "allergies":   ["confirmed allergies — IgE-mediated"],
    "intolerances": ["intolerances or sensitivities"],
    "dietary_restrictions": ["any cultural, religious, or ethical restrictions"],
    "extraction_confidence": "high | medium | low"
  },
 
  "ingredient_flags": [
    {
      "ingredient":   "exact name from label",
      "concern_type": "allergen | drug_interaction | condition_conflict | additive | processing",
      "condition":    "which of the patient's conditions this affects",
      "severity":     "critical | high | moderate | low",
      "explanation":  "one concrete sentence — name the mechanism, not just the risk"
    }
  ],
 
  "nutriment_flags": [
    {
      "nutriment":   "e.g. Sodium",
      "amount_per_serving": "e.g. 520mg",
      "threshold_for_patient": "e.g. 400mg recommended limit per meal for hypertension",
      "severity":    "critical | high | moderate | low"
    }
  ],
 
  "verdict": "safe | caution | avoid",
 
  "verdict_summary": "2–3 sentences written directly to the patient in plain language. Name their specific condition and the specific ingredient or nutrient of concern. Do not use generic statements like 'this product is high in sugar' — say 'given your HbA1c of 7.8%, the 24g of sugar per serving (from glucose syrup and maltodextrin) is likely to spike your blood glucose significantly'.",
 
  "consumption_guidance": {
    "recommended_serving":   "specific amount, e.g. 'no more than 1 biscuit (15g)' or null if verdict is avoid",
    "frequency":             "e.g. 'no more than once a week' or null if avoid",
    "best_time_of_day":      "e.g. 'with breakfast rather than evening — carbs earlier in the day have a lower glycaemic impact for T2 diabetics' or null if not relevant",
    "pairing_tips":          "e.g. 'pair with a source of protein or fat to slow glucose absorption' or null",
    "what_to_monitor":       "e.g. 'check blood glucose 1–2 hours after eating; target < 8 mmol/L post-meal' or null if not relevant"
  },
 
  "drug_interactions": [
    {
      "medication":    "name of the patient's medication",
      "ingredient":    "ingredient or nutrient in this product",
      "interaction":   "what happens mechanistically",
      "severity":      "critical | high | moderate | low",
      "action":        "what the patient should do (e.g. 'avoid this product' or 'take medication 2 hrs before eating this')"
    }
  ],
 
  "healthier_swap": {
    "suggestion":   "specific product type or ingredient swap tailored to conditions",
    "reason":       "why this swap is better for this patient specifically"
  },
 
  "disclaimer": "This analysis is for informational purposes only and does not replace advice from your doctor, dietitian, or pharmacist. Always consult your healthcare team before making significant dietary changes."
}
 
────────────────────────────────────────────────────────────────────────────────
CRITICAL RULES
────────────────────────────────────────────────────────────────────────────────
- If the medical report is unclear or lacks sufficient data, set
  extraction_confidence to "low" and note the uncertainty in verdict_summary.
  Do NOT fabricate diagnoses or lab values.
- "avoid" verdict must only be used when there is a hard clinical reason
  (confirmed allergen, critical drug interaction, or severely dangerous nutriment
  level for a serious condition). Use "caution" for everything else.
- Serving size calculations must use the product's stated serving size from
  the label, not per-100g values.
- Never use filler phrases like "it is important to note", "as always",
  "please consult a doctor" (the disclaimer field handles that).
- All amounts must include units. Never write "high in sodium" — write "520mg
  sodium per serving".
- If no drug interactions apply, return drug_interactions as [].
- If no ingredient flags apply, return ingredient_flags as [].
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
        return try {
            // Trim whitespace and parse JSON
            val jsonStr = response.trim()
            val json = JSONObject(jsonStr)
            
            // Extract verdict (ensure it's in uppercase for consistency)
            val verdict = json.optString("verdict", "caution").uppercase()
            
            // Extract verdict_summary as the explanation
            val explanation = json.optString("verdict_summary", "Unable to parse the model response.")
            
            AnalysisResult(verdict, explanation)
        } catch (e: JSONException) {
            Timber.e(e, "Failed to parse JSON response")
            // Fallback: try to extract simple text format or return error
            AnalysisResult("CAUTION", "Unable to parse the model response: ${e.message}")
        }
    }

    fun close() {
        val engineToClose = engine
        engine = null
        engineToClose?.close()
        Timber.d("GemmaRepository closed")
    }
}
