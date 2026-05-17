<div align="center">

# 🥗 NutriLens AI

### Your Personal Ingredient Safety Checker

An Android app that scans product ingredient labels and gives you a personalised health verdict — powered entirely **on-device** using Google's Gemma model via LiteRT-LM.

![Android](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-4285F4?logo=jetpackcompose&logoColor=white)
![LiteRT-LM](https://img.shields.io/badge/AI-LiteRT--LM%20%28Gemma%29-00C07F)
![Min SDK](https://img.shields.io/badge/Min%20SDK-28%20(Android%209)-blue)

</div>

---

## ✨ Features

- 📷 **Camera Scan** — Point your camera at any product label; ML Kit reads the text on-device instantly
- 🤖 **On-Device AI** — Gemma runs entirely on your phone via LiteRT-LM. No data ever leaves your device
- 🩺 **Health-Aware Verdict** — AI cross-references the ingredients against your personal health report
- ⚡ **Streaming Response** — Watch the verdict generate token-by-token in real time
- 🎨 **Healthify-Style UI** — Clean green design system with gradient cards, pill buttons, and animated states

---

## 📱 App Flow

```
Open App
   │
   ├─► [Model not found] → Setup instructions shown
   │
   └─► [Model loaded]
           │
           ├─ 📷 Scan Label  →  ML Kit OCR  →  Extracted text shown on screen
           │                                        │
           │                         (auto-fills Ingredient field)
           │
           └─ ✏️  Type / edit ingredients
                        │
                        ▼
              [ Analyze Ingredients ]
                        │
                        ▼
              Gemma reads health report + ingredients
                        │
                        ▼
              ✅ SAFE  /  ⚠️ CAUTION  /  ❌ AVOID
              + Detailed explanation streamed live
```

---

## 🏗️ Architecture

```
com.nutrilens.nutrilensai/
├── MainActivity.kt                    # Single-activity host
├── ui/
│   ├── theme/
│   │   ├── Color.kt                   # NutriLens brand palette
│   │   ├── Theme.kt                   # MaterialTheme (no dynamic color)
│   │   └── Type.kt                    # Typography scale
│   └── screen/
│       └── AnalysisScreen.kt          # Full Compose UI (all states)
├── viewmodel/
│   └── AnalysisViewModel.kt           # UiState + OcrState via StateFlow
├── repository/
│   └── GemmaRepository.kt             # LiteRT-LM engine wrapper + prompt
└── util/
    ├── AssetReader.kt                  # Reads health report from assets
    └── OcrHelper.kt                   # ML Kit text recognition (suspend)

app/src/main/assets/
└── sample_health_report.txt           # Demo patient health profile
```

**Pattern:** MVVM · StateFlow · Repository · Coroutines/Flow

---

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Language | Kotlin 2.2.0 |
| UI | Jetpack Compose + Material 3 |
| AI Inference | [LiteRT-LM](https://ai.google.dev/edge/litert-lm) (`litertlm-android`) |
| OCR | [ML Kit Text Recognition](https://developers.google.com/ml-kit/vision/text-recognition) |
| Async | Kotlin Coroutines + Flow |
| Camera | `ActivityResultContracts.TakePicture` + FileProvider |
| Min SDK | 28 (Android 9 Pie) |

---

## 🚀 Getting Started

### 1. Clone the repo

```bash
git clone https://github.com/Venkateshwar24/NutriLensAI.git
cd NutriLensAI
```

### 2. Download the Gemma model

NutriLens AI runs Gemma entirely on your device. You need to download the model file once.

1. Visit [huggingface.co/litert-community](https://huggingface.co/litert-community)
2. Download **`gemma-4-E2B-it-litert-lm`** (the `.litertlm` file)
3. Copy it to your Android device at:

```
/sdcard/Android/data/com.nutrilens.nutrilensai/files/gemma-4-E2B-it.litertlm
```

> **Tip:** You can use Android Studio's Device Explorer or `adb push` to transfer the file.

```bash
adb push gemma-4-E2B-it.litertlm \
  /sdcard/Android/data/com.nutrilens.nutrilensai/files/gemma-4-E2B-it.litertlm
```

### 3. Build & run

Open in **Android Studio** (Ladybug or later) and run on a physical device.

> ⚠️ The Gemma model requires significant RAM (~3–4 GB free). A physical device is strongly recommended over an emulator.

---

## 🩺 Health Report

The app reads a health profile from `app/src/main/assets/sample_health_report.txt` to personalise the verdict. The included file is a synthetic demo report (Ananya Rao, 42F) with conditions including:

- Pre-diabetic HbA1c (7.1%)
- High triglycerides & LDL cholesterol
- Elevated blood pressure (146/92)
- Reduced kidney function (eGFR 68)
- Vitamin D deficiency

Replace this file with a real (or custom) health report to get personalised verdicts.

---

## 🤖 AI Prompt Design

The model receives a structured prompt combining the health report and scanned ingredients:

```
You are a clinical nutritionist AI. Given a patient's health profile and a 
food product's ingredient list, determine if it is safe for the patient.

PATIENT HEALTH PROFILE:
<contents of sample_health_report.txt>

PRODUCT INGREDIENTS:
<scanned or typed ingredient list>

Respond in this EXACT format:
VERDICT: [SAFE / CAUTION / AVOID]
REASON: [2-3 sentences mentioning specific ingredients of concern]
```

The response streams token-by-token to the UI via Kotlin `Flow`.

---

## 🗺️ Roadmap

| Phase | Feature | Status |
|---|---|---|
| 1 | Text input + static health report + on-device Gemma verdict | ✅ Done |
| 2 | Camera scan → ML Kit OCR → auto-fill ingredients | ✅ Done |
| 3 | Upload real PDF medical report (replace static file) | 🔜 Planned |
| 4 | User profile persistence + scan history | 🔜 Planned |
| 5 | Nutrition score visualisation + share results | 🔜 Planned |

---

## 📄 License

```
MIT License — free to use, modify, and distribute.
```

---

<div align="center">
Built with ❤️ using Jetpack Compose + Google LiteRT-LM
</div>
