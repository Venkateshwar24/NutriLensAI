package com.nutrilens.nutrilensai.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ListAlt
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutrilens.nutrilensai.model.OcrState
import com.nutrilens.nutrilensai.model.UiState
import com.nutrilens.nutrilensai.ui.theme.*
import com.nutrilens.nutrilensai.util.CameraHelper
import com.nutrilens.nutrilensai.viewmodel.AnalysisViewModel
import java.io.File

// ─── Root Screen ──────────────────────────────────────────────────────────────

@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel = viewModel()) {
    val uiState  by viewModel.uiState.collectAsState()
    val ocrState by viewModel.ocrState.collectAsState()
    var ingredientsText by remember { mutableStateOf("") }
    val context = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoFile?.let { viewModel.analyzeFromImage(it.absolutePath) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, file) = CameraHelper.createPhotoUri(context)
            photoUri = uri
            photoFile = file
            cameraLauncher.launch(uri)
        }
    }
    val onScanClick: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val (uri, file) = CameraHelper.createPhotoUri(context)
            photoUri = uri
            photoFile = file
            cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Auto-open camera on first launch — delay lets the pager register its
    // gesture handlers before the camera activity transition begins.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(300)
        onScanClick()
    }

    // When OCR succeeds, populate the field and slide to the analysis page
    LaunchedEffect(ocrState) {
        if (ocrState is OcrState.Success) {
            ingredientsText = (ocrState as OcrState.Success).extractedText
            pagerState.animateScrollToPage(1)
        }
    }

    // Navigate to analysis page when multimodal analysis starts, or if model is missing
    LaunchedEffect(uiState) {
        when (uiState) {
            is UiState.ModelNotFound -> pagerState.animateScrollToPage(1)
            is UiState.Analyzing    -> pagerState.animateScrollToPage(1)
            else                    -> {}
        }
    }

    HorizontalPager(
        state    = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        when (page) {
            0 -> CameraPage(uiState = uiState, ocrState = ocrState, onCapture = onScanClick)
            else -> AnalysisPage(
                uiState         = uiState,
                ocrState        = ocrState,
                ingredientsText = ingredientsText,
                onChange        = { ingredientsText = it },
                onAnalyze       = { viewModel.analyze(ingredientsText) },
                onScan          = onScanClick,
                onClearOcr      = viewModel::clearOcr,
                onReset         = viewModel::reset,
                onRetry         = viewModel::checkAndLoadModel,
                modelPath       = viewModel.modelFilePath
            )
        }
    }
}

// ─── Camera Page (Page 0) ─────────────────────────────────────────────────────

@Composable
private fun CameraPage(uiState: UiState, ocrState: OcrState, onCapture: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0D12))
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App title
        Spacer(Modifier.height(36.dp))
        Text(
            text       = "NutriLens",
            color      = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = 24.sp,
            letterSpacing = (-0.5).sp
        )
        Text(
            text      = "AI Ingredient Scanner",
            color     = Color.White.copy(alpha = 0.45f),
            fontSize  = 13.sp,
            letterSpacing = 0.5.sp
        )

        // Viewfinder + state content
        Spacer(Modifier.weight(1f))
        Box(
            modifier        = Modifier.size(270.dp),
            contentAlignment = Alignment.Center
        ) {
            ViewfinderBrackets()
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val isAnalyzing = uiState is UiState.Analyzing || uiState is UiState.Streaming
                when {
                    isAnalyzing -> {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(34.dp),
                            color       = NutriGreen,
                            strokeWidth = 3.dp
                        )
                        Text("Analyzing label…", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                    }
                    uiState is UiState.Result -> {
                        Icon(Icons.Rounded.CheckCircle, null, tint = NutriSafe, modifier = Modifier.size(44.dp))
                        Text("Analysis complete!", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Swipe left to see result →", color = NutriGreen.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                    ocrState is OcrState.Processing -> {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(34.dp),
                            color       = NutriGreen,
                            strokeWidth = 3.dp
                        )
                        Text("Reading label…", color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp)
                    }
                    ocrState is OcrState.Success -> {
                        Icon(Icons.Rounded.CheckCircle, null, tint = NutriSafe, modifier = Modifier.size(44.dp))
                        Text("Text captured!", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Swipe left to analyze →", color = NutriGreen.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                    else -> {
                        Icon(Icons.Rounded.CameraAlt, null, tint = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(44.dp))
                        Text(
                            text      = "Point at an ingredient label",
                            color     = Color.White.copy(alpha = 0.45f),
                            fontSize  = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        // Swipe right hint
        Spacer(Modifier.height(20.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("swipe left to analyze", color = Color.White.copy(alpha = 0.25f), fontSize = 11.sp)
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.25f), modifier = Modifier.size(16.dp))
        }

        // Shutter + page dots
        Spacer(Modifier.weight(1f))
        ShutterButton(
            onClick  = onCapture,
            enabled  = ocrState !is OcrState.Processing &&
                       uiState !is UiState.Analyzing &&
                       uiState !is UiState.Streaming
        )
        Spacer(Modifier.height(14.dp))
        Text("Tap to capture", color = Color.White.copy(alpha = 0.3f), fontSize = 12.sp)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).background(Color.White, CircleShape))
            Box(Modifier.size(5.dp).background(Color.White.copy(alpha = 0.3f), CircleShape))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ViewfinderBrackets() {
    val color = Color.White.copy(alpha = 0.75f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val sw = 3.dp.toPx()
        val cl = 30.dp.toPx()
        val cap = StrokeCap.Round
        val w = size.width
        val h = size.height

        // Top-left
        drawLine(color, Offset(0f, 0f), Offset(cl, 0f), sw, cap)
        drawLine(color, Offset(0f, 0f), Offset(0f, cl), sw, cap)
        // Top-right
        drawLine(color, Offset(w, 0f), Offset(w - cl, 0f), sw, cap)
        drawLine(color, Offset(w, 0f), Offset(w, cl), sw, cap)
        // Bottom-left
        drawLine(color, Offset(0f, h), Offset(cl, h), sw, cap)
        drawLine(color, Offset(0f, h), Offset(0f, h - cl), sw, cap)
        // Bottom-right
        drawLine(color, Offset(w, h), Offset(w - cl, h), sw, cap)
        drawLine(color, Offset(w, h), Offset(w, h - cl), sw, cap)
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit, enabled: Boolean = true) {
    val alpha = if (enabled) 1f else 0.35f
    Box(
        modifier         = Modifier.size(80.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(4.dp, Color.White.copy(alpha = alpha), CircleShape)
                .clickable(enabled = enabled, onClick = onClick)
        )
        Box(
            modifier = Modifier
                .size(60.dp)
                .background(Color.White.copy(alpha = alpha), CircleShape)
        )
    }
}

// ─── Analysis Page (Page 1) ───────────────────────────────────────────────────

@Composable
private fun AnalysisPage(
    uiState: UiState,
    ocrState: OcrState,
    ingredientsText: String,
    onChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onScan: () -> Unit,
    onClearOcr: () -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
    modelPath: String
) {
    val isBusy = uiState is UiState.Analyzing || uiState is UiState.Streaming
    val showFab = uiState !is UiState.ModelNotFound && uiState !is UiState.ModelLoading

    Scaffold(
        topBar = { NutriTopBar() },
        floatingActionButton = {
            if (showFab) {
                FloatingActionButton(
                    onClick          = onScan,
                    containerColor   = NutriGreen,
                    contentColor     = Color.White,
                    shape            = CircleShape,
                    modifier         = Modifier.size(58.dp)
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = "Scan label", modifier = Modifier.size(24.dp))
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            when (val state = uiState) {
                is UiState.ModelNotFound ->
                    ModelNotFoundCard(
                        modelPath = modelPath,
                        onRetry   = onRetry,
                        modifier  = Modifier.padding(20.dp)
                    )

                is UiState.ModelLoading -> ModelLoadingCard()

                else -> Column(
                    modifier            = Modifier.padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Spacer(Modifier.height(6.dp))

                    // OCR result strip
                    when (ocrState) {
                        is OcrState.Success -> OcrResultStrip(text = ocrState.extractedText, onClear = onClearOcr)
                        is OcrState.Error   -> OcrErrorStrip(message = ocrState.errorMessage)
                        else                -> {}
                    }

                    // Ingredient input
                    SectionLabel(text = "Ingredient List", icon = Icons.AutoMirrored.Rounded.ListAlt)
                    IngredientField(value = ingredientsText, onChange = onChange, enabled = !isBusy)
                    GradientButton(
                        text    = if (isBusy) "Analyzing…" else "Analyze Ingredients",
                        icon    = Icons.Rounded.Science,
                        onClick = onAnalyze,
                        enabled = !isBusy && ingredientsText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    when (state) {
                        is UiState.Analyzing -> AnalyzingCard()
                        is UiState.Streaming -> StreamingCard(text = state.partialResponse)
                        is UiState.Result    -> {
                            SectionLabel(text = "Verdict", icon = Icons.Rounded.HealthAndSafety)
                            VerdictCard(
                                verdict     = state.analysisResult.verdict,
                                explanation = state.analysisResult.explanation,
                                onReset     = onReset
                            )
                        }
                        is UiState.Error -> NutriErrorCard(state.errorMessage, onReset)
                        else -> {}
                    }

                    // Bottom padding so content clears the FAB
                    Spacer(Modifier.height(88.dp))
                }
            }
        }
    }
}

// ─── OCR strips ──────────────────────────────────────────────────────────────

@Composable
private fun OcrResultStrip(text: String, onClear: () -> Unit) {
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = NutriGreenLight,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier                = Modifier.fillMaxWidth(),
                horizontalArrangement   = Arrangement.SpaceBetween,
                verticalAlignment       = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = NutriGreen, modifier = Modifier.size(15.dp))
                    Text("Scanned text", color = NutriGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                    Text("Clear", color = NutriGreen, fontSize = 12.sp)
                }
            }
            Text(
                text       = text.ifEmpty { "(no text detected)" },
                fontFamily = FontFamily.Monospace,
                fontSize   = 11.sp,
                lineHeight = 16.sp,
                color      = NutriTextSecondary,
                modifier   = Modifier.heightIn(max = 100.dp)
            )
        }
    }
}

@Composable
private fun OcrErrorStrip(message: String) {
    Surface(
        shape    = RoundedCornerShape(14.dp),
        color    = NutriAvoidLight,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier              = Modifier.padding(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = NutriAvoid, modifier = Modifier.size(18.dp))
            Text(message, color = NutriAvoidDark, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        }
    }
}

// ─── Ingredient Field ─────────────────────────────────────────────────────────

@Composable
private fun IngredientField(value: String, onChange: (String) -> Unit, enabled: Boolean) {
    OutlinedTextField(
        value         = value,
        onValueChange = onChange,
        modifier      = Modifier
            .fillMaxWidth()
            .heightIn(min = 140.dp),
        placeholder   = {
            Text(
                "Paste or type the ingredients list here…\n\ne.g. Sugar, Modified Starch, Sodium Chloride, Peanut Oil",
                color    = NutriTextHint,
                fontSize = 13.sp
            )
        },
        enabled   = enabled,
        shape     = RoundedCornerShape(16.dp),
        maxLines  = 12,
        colors    = OutlinedTextFieldDefaults.colors(
            focusedBorderColor   = NutriGreen,
            unfocusedBorderColor = Color(0xFFE2E8F0),
            cursorColor          = NutriGreen,
            disabledBorderColor  = Color(0xFFF1F5F9),
            disabledTextColor    = NutriTextSecondary
        )
    )
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NutriTopBar() {
    TopAppBar(
        modifier = Modifier.background(Brush.horizontalGradient(listOf(NutriGreen, NutriGreenDark))),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier         = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text("N", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("NutriLens AI", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, letterSpacing = (-0.3).sp)
                    Text("Smart Ingredient Checker", color = Color.White.copy(alpha = 0.75f), fontSize = 11.sp)
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

// ─── Section Label ───────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = null, tint = NutriGreen, modifier = Modifier.size(16.dp))
        Text(
            text          = text.uppercase(),
            color         = NutriGreen,
            fontWeight    = FontWeight.Bold,
            fontSize      = 11.sp,
            letterSpacing = 1.5.sp
        )
    }
}

// ─── Gradient Button ─────────────────────────────────────────────────────────

@Composable
private fun GradientButton(
    text: String,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val gradient = if (enabled)
        Brush.horizontalGradient(listOf(NutriGreen, NutriGreenAccent))
    else
        Brush.horizontalGradient(listOf(NutriTextHint, NutriTextHint))

    Box(
        modifier         = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(gradient)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Text(
                text          = text,
                color         = if (enabled) Color.White else Color.White.copy(alpha = 0.6f),
                fontWeight    = FontWeight.Bold,
                fontSize      = 15.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}

// ─── Analyzing Card ──────────────────────────────────────────────────────────

@Composable
private fun AnalyzingCard() {
    val infiniteTransition = rememberInfiniteTransition(label = "analyzing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.92f, targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "pulse"
    )
    NutriCard {
        Column(
            modifier                = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment     = Alignment.CenterHorizontally,
            verticalArrangement     = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(72.dp)
                    .scale(scale)
                    .background(NutriGreenLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Science, null, tint = NutriGreen, modifier = Modifier.size(36.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Analyzing Ingredients", style = MaterialTheme.typography.titleMedium, color = NutriTextPrimary)
                Text("Our AI is reading your ingredients…", style = MaterialTheme.typography.bodySmall, color = NutriTextSecondary)
            }
        }
    }
}

// ─── Streaming Card ──────────────────────────────────────────────────────────

@Composable
private fun StreamingCard(text: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "alpha"
    )
    NutriCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.size(9.dp).background(NutriGreen.copy(alpha = dotAlpha), CircleShape))
                Text("Generating response…", color = NutriGreen, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
            HorizontalDivider(color = NutriDivider)
            Text(
                text       = text,
                fontFamily = FontFamily.Monospace,
                fontSize   = 13.sp,
                lineHeight = 20.sp,
                color      = NutriTextPrimary
            )
        }
    }
}

// ─── Verdict Card ────────────────────────────────────────────────────────────

private data class VerdictConfig(
    val gradient: List<Color>,
    val icon: ImageVector,
    val label: String,
    val sublabel: String,
    val bodyTitle: String
)

@Composable
private fun VerdictCard(verdict: String, explanation: String, onReset: () -> Unit) {
    val config = when (verdict) {
        "SAFE"  -> VerdictConfig(listOf(NutriSafe, NutriSafeAccent),       Icons.Rounded.CheckCircle,  "Safe to Consume",    "This product suits your health profile",      "Why is this safe for you?")
        "AVOID" -> VerdictConfig(listOf(NutriAvoid, NutriAvoidAccent),     Icons.Rounded.Cancel,       "Avoid This Product", "Not recommended based on your health report", "Why should you avoid this?")
        else    -> VerdictConfig(listOf(NutriCaution, NutriCautionAccent), Icons.Rounded.WarningAmber, "Use With Caution",   "Moderate consumption may be okay",            "What should you watch out for?")
    }

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(6.dp),
        colors    = CardDefaults.cardColors(containerColor = NutriSurface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(config.gradient),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                )
                .padding(horizontal = 22.dp, vertical = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier         = Modifier
                        .size(62.dp)
                        .background(Color.White.copy(alpha = 0.22f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(config.icon, null, tint = Color.White, modifier = Modifier.size(36.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(config.label,    color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, letterSpacing = (-0.2).sp)
                    Text(config.sublabel, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                }
            }
        }
        Column(
            modifier            = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(config.bodyTitle, style = MaterialTheme.typography.titleSmall, color = NutriTextPrimary)
            Text(explanation, style = MaterialTheme.typography.bodyMedium, color = NutriTextSecondary, lineHeight = 22.sp)
            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick  = onReset,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(50.dp),
                border   = BorderStroke(1.5.dp, NutriGreen),
                colors   = ButtonDefaults.outlinedButtonColors(contentColor = NutriGreen)
            ) {
                Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Check Another Product", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Model Loading Card ──────────────────────────────────────────────────────

@Composable
private fun ModelLoadingCard() {
    Box(
        modifier         = Modifier
            .fillMaxWidth()
            .padding(20.dp)
            .padding(top = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            CircularProgressIndicator(color = NutriGreen, strokeWidth = 4.dp, modifier = Modifier.size(56.dp))
            Text("Loading AI Model", style = MaterialTheme.typography.titleMedium, color = NutriTextPrimary)
            Text("This may take up to 15 seconds…", style = MaterialTheme.typography.bodySmall, color = NutriTextSecondary)
        }
    }
}

// ─── Model Not Found Card ────────────────────────────────────────────────────

@Composable
private fun ModelNotFoundCard(modelPath: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    NutriCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier         = Modifier
                    .size(64.dp)
                    .background(NutriGreenLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SmartToy, null, tint = NutriGreen, modifier = Modifier.size(36.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gemma Model Required", style = MaterialTheme.typography.titleLarge, color = NutriTextPrimary)
                Text("Download the model and place it on your device to get started.", style = MaterialTheme.typography.bodyMedium, color = NutriTextSecondary)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Model path:", style = MaterialTheme.typography.labelSmall, color = NutriTextSecondary)
                Surface(shape = RoundedCornerShape(10.dp), color = NutriBackground, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text       = modelPath,
                        modifier   = Modifier.padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 11.sp,
                        color      = NutriTextPrimary,
                        lineHeight = 16.sp
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupStep(number = "1", text = "Visit huggingface.co/litert-community")
                SetupStep(number = "2", text = "Download gemma-4-E2B-it-litert-lm")
                SetupStep(number = "3", text = "Copy to the path shown above")
                SetupStep(number = "4", text = "Tap Retry below")
            }
            GradientButton(text = "Retry", icon = Icons.Rounded.Refresh, onClick = onRetry, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SetupStep(number: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier         = Modifier
                .size(26.dp)
                .background(NutriGreenLight, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = NutriGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = NutriTextSecondary)
    }
}

// ─── Error Card ──────────────────────────────────────────────────────────────

@Composable
private fun NutriErrorCard(message: String, onDismiss: () -> Unit) {
    NutriCard {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier         = Modifier
                    .size(40.dp)
                    .background(NutriAvoidLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = NutriAvoid, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Something went wrong", style = MaterialTheme.typography.titleSmall, color = NutriAvoidDark)
                Text(message, style = MaterialTheme.typography.bodySmall, color = NutriTextSecondary)
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("Dismiss", color = NutriAvoid, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─── Shared Card Shell ───────────────────────────────────────────────────────

@Composable
private fun NutriCard(
    modifier: Modifier = Modifier,
    elevation: Dp = 3.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors    = CardDefaults.cardColors(containerColor = NutriSurface)
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}
