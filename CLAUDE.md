# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

```bash
# Set JAVA_HOME first — required on this machine
export JAVA_HOME="/d/Android Studio/jbr"

# Debug build
./gradlew assembleDebug

# Release build (minification enabled)
./gradlew assembleRelease

# Install on connected device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Push Gemma model to device (required before first run)
adb push gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.nutrilens.nutrilensai/files/gemma-4-E2B-it.litertlm

# Static analysis (no unit tests exist yet)
./gradlew lint
```

## Architecture

MVVM with a single `AnalysisScreen` backed by `AnalysisViewModel`. The full dependency graph:

```
AnalysisScreen (HorizontalPager — page 0: camera, page 1: analysis)
    └── AnalysisViewModel (AndroidViewModel)
            ├── GemmaRepository      — LiteRT-LM engine, all three inference flows
            ├── HealthReportDatabase — Room singleton (via NutriLensApplication)
            ├── OcrHelper            — ML Kit text recognition (suspend fun)
            └── DocumentReader       — PDF + TXT extraction (suspend fun, PdfBox)
```

**State model** — three independent `StateFlow`s drive the UI, all exposed via `.asStateFlow()`:

| Flow | Sealed class | States |
|---|---|---|
| `uiState` | `UiState` | `ModelNotFound → ModelLoading → Ready → Analyzing → Streaming(partialResponse) → Result(analysisResult) \| Error` |
| `ocrState` | `OcrState` | `Idle → Processing → Success(extractedText) \| Error` |
| `reportState` | `ReportState` | `NoReport → Extracting → Summarizing(partialSummary) → Loaded(fileName, summary, uploadedAt) \| Error` |

All sealed classes live in `model/` and are annotated `@Immutable`. Singleton subclasses use `data object`.

**Two analysis paths** both funnel through `AnalysisViewModel`:
1. **Vision path** — `analyzeFromImage(imagePath)` → `GemmaRepository.analyzeImageStream()` — the raw JPEG is passed directly to the multimodal model; OCR is skipped entirely.
2. **Text path** — `analyze(ingredients)` → `GemmaRepository.analyzeStream()` — typed or OCR-extracted text is sent as a prompt.

Both paths call `resolveHealthSummary()` first, which reads the stored `summary` column from Room (falls back to a generic nutrition guideline string if no report is uploaded).

**AI inference** — `GemmaRepository` wraps the LiteRT-LM `Engine`. Three flows:
- `analyzeImageStream(imagePath, healthSummary)` — vision + health safety verdict
- `analyzeStream(ingredients, healthSummary)` — text ingredients safety verdict
- `summarizeReportStream(rawText)` — condenses an uploaded health report into the compact profile format used in prompts (`PATIENT / ABNORMAL / RESTRICTIONS / NOTES`)

Key LiteRT-LM API pattern used throughout:
```kotlin
engine.createConversation(ConversationConfig(
    systemInstruction = Contents.of(systemPrompt),
    samplerConfig = SamplerConfig(topK, topP, temperature)
)).use { conversation ->
    conversation.sendMessageAsync(userMessage).collect { chunk -> emit(chunk.toString()) }
}
```
Engine access is guarded by a `Mutex` (`engineLock`) to prevent concurrent load/read. All flows use `flowOn(Dispatchers.IO)`.

**Health report persistence** — `data/db/` contains a Room database with a single-row table (`id = 1` always). The DAO exposes `observeReport(): Flow<HealthReportEntity?>`, `getReport()`, `saveReport()`, and `deleteReport()`. `HealthReportEntity` stores `fileName`, `rawText`, `summary`, and `uploadedAt`. On ViewModel init, `loadStoredReport()` restores the last uploaded report from the DB into `reportState`.

**Document extraction** — `DocumentReader.extractText(uri, context)` detects MIME type and dispatches to PdfBox (PDF) or a buffered reader (TXT). Throws `DocumentReader.UnsupportedFormatException` for other types.

**Constants** — all magic strings (model filename, FileProvider suffix, asset name, text limits) live in `Constants.kt`. Never hardcode them elsewhere.

**Camera** — uses `ActivityResultContracts.TakePicture` (system camera, no CameraX). `CameraHelper.createPhotoUri()` creates a temp `.jpg` in `externalCacheDir` and returns a FileProvider URI. Authority: `${packageName}.fileprovider`.

**Colors** — brand palette is `NutriPrimary` / `NutriPrimaryAccent` / `NutriPrimaryDark` / `NutriPrimaryLight` (indigo `#4F46E5` family). Verdict colors are `NutriSafe` (green), `NutriCaution` (amber), `NutriAvoid` (red). `AnalysisScreen.kt` defines its own private dark-mode surface constants (`ScreenBg`, `CardBg`, `CardBgHigh`, `BorderCol`, `TextPrimary`, `TextMuted`) — do not move them to `Color.kt`.

**Timber** — initialized in `NutriLensApplication.onCreate()` (debug builds only). Use `Timber.d/e` everywhere instead of `Log`.

## Version Pinning Constraints

This project has a hard version triangle driven by the Gradle wrapper (8.10.2):

| Constraint | Value |
|---|---|
| Gradle wrapper | 8.10.2 (max supports AGP 8.8.x) |
| AGP | 8.8.0 |
| Kotlin | **2.2.0 minimum** — LiteRT-LM 0.11.0 is compiled with Kotlin metadata 2.2.0 |
| core-ktx | 1.15.0 (1.16+ requires compileSdk 36 + AGP 8.9.1) |
| activity-compose | 1.9.3 (1.10+ same constraint) |
| lifecycle | 2.8.7 (2.9+ same constraint) |

Do **not** bump any of these without first upgrading the Gradle wrapper and AGP together.

## Model File

The Gemma model is **not** bundled in the APK. It must be manually pushed to the device at:
```
/sdcard/Android/data/com.nutrilens.nutrilensai/files/gemma-4-E2B-it.litertlm
```
Source: [huggingface.co/litert-community](https://huggingface.co/litert-community) — download `gemma-4-E2B-it-litert-lm`.

The model filename is defined in `Constants.MODEL_FILENAME`. If the model is renamed or a different model is used, update only that constant.

## Health Report

There are two sources for the health profile used in prompts:

1. **Built-in asset** — `app/src/main/assets/sample_health_report.txt` is a synthetic patient profile (Ananya Rao, 42F) with pre-diabetes, high triglycerides, elevated BP, reduced kidney function, and vitamin D deficiency. Used as a fallback only if no user report is stored. Asset name is in `Constants.HEALTH_REPORT_ASSET_NAME`.

2. **User-uploaded report** — any PDF or TXT file the user picks via the health report bottom sheet. `DocumentReader` extracts the raw text; `GemmaRepository.summarizeReportStream()` condenses it to ≤ `Constants.HEALTH_REPORT_SUMMARY_LIMIT` chars; the result is persisted in Room. `resolveHealthSummary()` in the ViewModel reads from Room first, falling back to the generic string if the table is empty. The built-in asset is **not** used once a user report is stored.
