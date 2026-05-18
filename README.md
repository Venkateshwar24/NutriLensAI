<div align="center">

# 🥗 NutriLens AI

### On-Device Food Safety Checker, Personalised to Your Health

An Android app that scans product ingredient labels and delivers a personalised health verdict — powered entirely **on-device** by Google's **Gemma 4 multimodal model** via LiteRT-LM. No cloud. No data leaves your phone.

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)
![LiteRT-LM](https://img.shields.io/badge/AI-LiteRT--LM%20%28Gemma%204%29-00C07F)
![Min SDK](https://img.shields.io/badge/Min%20SDK-28%20(Android%209)-blue)

</div>

---

## ✨ Features

- 📷 **Direct Vision Scan** — Point your camera at a label; Gemma 4 reads the ingredients *from the image* and gives a verdict in one step
- 📄 **Health Report Upload** — Upload your own PDF or TXT lab report; Gemma 4 summarises it into a compact health profile and persists it across sessions
- 🩺 **Personalised Verdicts** — Every analysis is cross-referenced against *your* health profile (pre-diabetes, cholesterol, BP, kidney function, allergies, etc.)
- ✅ / ⚠️ / ❌ **Three-level verdict** — SAFE · CAUTION · AVOID, with a detailed AI explanation for each
- 🔄 **RESCAN guidance** — If the label is unreadable, the app explains why and gives practical scanning tips
- ⚡ **Streaming output** — Watch the verdict generate token-by-token in real time
- 🔒 **Fully on-device** — Gemma 4 runs on the CPU via LiteRT-LM. No internet required, no data ever leaves the device

---

## 📱 App Flow

```
Open App
   │
   ├─► [Model not found] → Setup instructions shown
   │
   └─► [Model loaded]
           │
           ├─ PAGE 0: Camera
           │      │
           │      └─ Tap shutter → system camera opens
           │                │
           │         ┌──────┴──────────────┐
           │         │                     │
           │    [Vision mode]         [OCR mode]
           │    Gemma 4 reads         ML Kit extracts text
           │    image directly   →    auto-fills ingredient field
           │         │
           │         ▼
           │   SAFE / CAUTION / AVOID / RESCAN
           │
           └─ PAGE 1: Analysis
                  │
                  ├─ 🩺 Upload health report (PDF / TXT)
                  │      └─ Gemma 4 summarises → saved to Room DB
                  │
                  ├─ ✏️  Type or edit ingredient list manually
                  │
                  └─ [ Analyze Ingredients ]
                              │
                              ▼
                    Gemma 4 + your health profile
                              │
                              ▼
                    ✅ SAFE  /  ⚠️ CAUTION  /  ❌ AVOID
                    + streamed explanation
```

---

## 🏗️ Architecture

```
com.nutrilens.nutrilensai/
├── MainActivity.kt                        # Single-activity host
├── NutriLensApplication.kt               # App class — Timber, Room init
├── Constants.kt                           # All magic strings
│
├── data/db/
│   ├── HealthReportEntity.kt             # Room entity (singleton row)
│   ├── HealthReportDao.kt                # observe / get / save / delete
│   └── HealthReportDatabase.kt           # Room singleton
│
├── model/
│   ├── UiState.kt                        # ModelNotFound → Streaming → Result
│   ├── OcrState.kt                       # Idle → Processing → Success
│   ├── ReportState.kt                    # NoReport → Summarizing → Loaded
│   └── AnalysisResult.kt                 # verdict + explanation
│
├── repository/
│   └── GemmaRepository.kt               # LiteRT-LM engine, three prompt flows
│                                         #   analyzeStream (text)
│                                         #   analyzeImageStream (vision)
│                                         #   summarizeReportStream (report)
│
├── viewmodel/
│   └── AnalysisViewModel.kt             # StateFlow × 3, orchestrates all flows
│
├── ui/
│   ├── theme/
│   │   ├── Color.kt                     # NutriPrimary (indigo) + verdict palette
│   │   ├── Theme.kt                     # MaterialTheme (light + dark)
│   │   └── Type.kt                      # Typography scale
│   └── screen/
│       └── AnalysisScreen.kt            # HorizontalPager, all composables
│
└── util/
    ├── AssetReader.kt                    # Reads built-in health report from assets
    ├── CameraHelper.kt                   # FileProvider URI for camera temp file
    ├── OcrHelper.kt                      # ML Kit text recognition (suspend)
    └── DocumentReader.kt                 # PDF (PdfBox) + TXT extraction (suspend)
```

**Pattern:** MVVM · StateFlow · Repository · Coroutines / Flow · Room

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2.0 |
| UI | Jetpack Compose + Material 3 |
| AI Inference | [LiteRT-LM](https://ai.google.dev/edge/litert-lm) — Gemma 4 multimodal |
| OCR | [ML Kit Text Recognition v2](https://developers.google.com/ml-kit/vision/text-recognition) |
| Persistence | Room 2.7.x |
| PDF parsing | PdfBox Android (tom-roush fork) |
| Async | Kotlin Coroutines + Flow |
| Camera | `ActivityResultContracts.TakePicture` + FileProvider |
| Logging | Timber (debug builds only) |
| Min SDK | 28 (Android 9 Pie) |

---

## 🚀 Getting Started

### 1. Clone the repo

```bash
git clone https://github.com/Venkateshwar24/NutriLensAI.git
cd NutriLensAI
```

### 2. Download the Gemma 4 model

NutriLens AI runs Gemma 4 entirely on your device. The model is not bundled in the APK — download it once and push it to the device.

1. Visit [huggingface.co/litert-community](https://huggingface.co/litert-community)
2. Download **`gemma-4-E2B-it-litert-lm`** (the `.litertlm` file, ~2 GB)
3. Push it to your device:

```bash
adb push gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.nutrilens.nutrilensai/files/gemma-4-E2B-it.litertlm
```

> ⚠️ The model requires ~3–4 GB of free RAM. Run on a **physical device** — emulators will not have enough memory.

### 3. Build & run

Open in **Android Studio Ladybug (2024.2)** or later and run on a connected device.

```bash
# Or build from the command line (set JAVA_HOME first)
export JAVA_HOME="/path/to/jbr"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 🩺 Health Report

On first launch the app uses a built-in demo profile (`assets/sample_health_report.txt`) — a synthetic patient (Ananya Rao, 42F) with:

- Pre-diabetic HbA1c · High triglycerides & LDL
- Elevated BP (146/92) · Reduced kidney function (eGFR 68)
- Vitamin D deficiency

**To use your own report:** tap the health icon in the top-right corner, upload any **PDF or TXT** lab report. Gemma 4 will summarise it into a compact health profile and store it in the local Room database. All subsequent scans use your profile.

---

## 🤖 AI Prompt Design

Three separate inference flows run through `GemmaRepository`, each with a purpose-built system prompt:

| Flow | Input | Output |
|---|---|---|
| `analyzeImageStream` | Food label image + health profile | SAFE / CAUTION / AVOID / RESCAN + reason |
| `analyzeStream` | Ingredient text + health profile | SAFE / CAUTION / AVOID + reason |
| `summarizeReportStream` | Raw lab report text | Compact clinical profile (PATIENT / ABNORMAL / RESTRICTIONS / NOTES) |

All responses stream token-by-token via Kotlin `Flow<String>` on `Dispatchers.IO`.

---

## 🗺️ Roadmap

| Phase | Feature | Status |
|---|---|---|
| 1 | Text input · static health report · on-device Gemma verdict | ✅ Done |
| 2 | Camera scan · ML Kit OCR · Gemma 4 Vision direct image analysis | ✅ Done |
| 3 | PDF / TXT health report upload · AI summarisation · Room persistence | ✅ Done |
| 4 | Scan history & past verdicts | 🔜 Planned |
| 5 | Nutrition score visualisation · share results | 🔜 Planned |

---

## 📄 License

```
MIT License — free to use, modify, and distribute.
```

---

<div align="center">
Built with ❤️ using Jetpack Compose · Gemma 4 · Google LiteRT-LM
</div>
