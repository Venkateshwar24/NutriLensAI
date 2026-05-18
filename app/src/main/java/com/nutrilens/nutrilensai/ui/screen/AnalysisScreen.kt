package com.nutrilens.nutrilensai.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nutrilens.nutrilensai.Constants
import com.nutrilens.nutrilensai.model.OcrState
import com.nutrilens.nutrilensai.model.ReportState
import com.nutrilens.nutrilensai.model.UiState
import com.nutrilens.nutrilensai.ui.theme.*
import com.nutrilens.nutrilensai.util.CameraHelper
import com.nutrilens.nutrilensai.viewmodel.AnalysisViewModel
import java.io.File
import kotlinx.coroutines.delay

// ── Screen-wide dark palette ──────────────────────────────────────────────────
private val ScreenBg    = Color(0xFF0D1117)
private val CardBg      = Color(0xFF161B22)
private val CardBgHigh  = Color(0xFF1C2128)
private val BorderCol   = Color(0xFF30363D)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextMuted   = Color(0xFF8B949E)

// ─── Root Screen ──────────────────────────────────────────────────────────────

@Composable
fun AnalysisScreen(viewModel: AnalysisViewModel = viewModel()) {
    val uiState     by viewModel.uiState.collectAsState()
    val ocrState    by viewModel.ocrState.collectAsState()
    val reportState by viewModel.reportState.collectAsState()
    var ingredientsText by remember { mutableStateOf("") }
    var showReportSheet by remember { mutableStateOf(false) }
    val context    = LocalContext.current
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    val reportPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.uploadReport(it) } }

    var photoUri  by remember { mutableStateOf<Uri?>(null) }
    var photoFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) photoFile?.let { viewModel.analyzeFromImage(it.absolutePath) }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val (uri, file) = CameraHelper.createPhotoUri(context)
            photoUri = uri; photoFile = file; cameraLauncher.launch(uri)
        }
    }
    val onScanClick: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val (uri, file) = CameraHelper.createPhotoUri(context)
            photoUri = uri; photoFile = file; cameraLauncher.launch(uri)
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(Unit) {
        delay(300)
        onScanClick()
    }
    LaunchedEffect(ocrState) {
        if (ocrState is OcrState.Success) {
            ingredientsText = (ocrState as OcrState.Success).extractedText
            pagerState.animateScrollToPage(1)
        }
    }
    // snapshotFlow prevents LaunchedEffect(uiState) from relaunching on every
    // Streaming token (data class inequality) which would cancel the animation.
    LaunchedEffect("nav") {
        snapshotFlow { uiState }.collect { state ->
            when (state) {
                is UiState.ModelNotFound -> pagerState.animateScrollToPage(1)
                is UiState.Analyzing    -> pagerState.animateScrollToPage(1)
                else                    -> {}
            }
        }
    }

    HorizontalPager(
        state    = pagerState,
        modifier = Modifier.fillMaxSize().background(ScreenBg)
    ) { page ->
        when (page) {
            0    -> CameraPage(uiState = uiState, ocrState = ocrState, onCapture = onScanClick)
            else -> AnalysisPage(
                uiState         = uiState,
                ocrState        = ocrState,
                reportState     = reportState,
                ingredientsText = ingredientsText,
                onChange        = { ingredientsText = it },
                onAnalyze       = { viewModel.analyze(ingredientsText) },
                onScan          = onScanClick,
                onClearOcr      = viewModel::clearOcr,
                onReset         = viewModel::reset,
                onRetry         = viewModel::checkAndLoadModel,
                onReportClick   = { showReportSheet = true },
                modelPath       = viewModel.modelFilePath
            )
        }
    }

    if (showReportSheet) {
        HealthReportBottomSheet(
            reportState   = reportState,
            onDismiss     = { showReportSheet = false },
            onUploadClick = { reportPickerLauncher.launch(Constants.HEALTH_REPORT_MIME_TYPES) }
        )
    }
}

// ─── Camera Page (Page 0) ─────────────────────────────────────────────────────

@Composable
private fun CameraPage(uiState: UiState, ocrState: OcrState, onCapture: () -> Unit) {
    val isAnalyzing = uiState is UiState.Analyzing || uiState is UiState.Streaming
    val isDone      = uiState is UiState.Result
    val isIdle      = !isAnalyzing && !isDone &&
                      ocrState !is OcrState.Processing && ocrState !is OcrState.Success

    // Scanline sweeps up and down in the idle state
    val scanTrans = rememberInfiniteTransition(label = "scan")
    val scanY by scanTrans.animateFloat(
        initialValue  = 0.05f,
        targetValue   = 0.95f,
        animationSpec = infiniteRepeatable(tween(1900, easing = EaseInOutSine), RepeatMode.Reverse),
        label         = "scanY"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ScreenBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))

        // Title
        Text(
            "NutriLens",
            color         = Color.White,
            fontWeight    = FontWeight.ExtraBold,
            fontSize      = 26.sp,
            letterSpacing = (-0.8).sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "AI  ·  On-Device  ·  Private",
            color         = TextMuted,
            fontSize      = 11.sp,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.weight(1f))

        // Viewfinder
        Box(
            modifier         = Modifier.size(272.dp),
            contentAlignment = Alignment.Center
        ) {
            // Corner bracket frame
            ViewfinderBrackets()

            // Scanning line (idle only)
            if (isIdle) {
                Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                    val y = size.height * scanY
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                NutriPrimary.copy(alpha = 0.5f),
                                NutriPrimary.copy(alpha = 0.9f),
                                NutriPrimary.copy(alpha = 0.5f),
                                Color.Transparent
                            )
                        ),
                        start       = Offset(0f, y),
                        end         = Offset(size.width, y),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    // Subtle horizontal reflection below the line
                    drawLine(
                        brush = Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                NutriPrimary.copy(alpha = 0.15f),
                                NutriPrimary.copy(alpha = 0.25f),
                                NutriPrimary.copy(alpha = 0.15f),
                                Color.Transparent
                            )
                        ),
                        start       = Offset(0f, y + 3.dp.toPx()),
                        end         = Offset(size.width, y + 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }

            // State overlay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    isAnalyzing -> {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(36.dp),
                            color       = NutriPrimary,
                            strokeWidth = 3.dp,
                            trackColor  = BorderCol
                        )
                        Text("Analyzing…", color = TextMuted, fontSize = 13.sp)
                    }
                    isDone -> {
                        Icon(Icons.Rounded.CheckCircle, null, tint = NutriSafe, modifier = Modifier.size(46.dp))
                        Text("Done!", color = NutriSafe, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Swipe left →", color = NutriPrimary.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    ocrState is OcrState.Processing -> {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(36.dp),
                            color       = NutriPrimary,
                            strokeWidth = 3.dp,
                            trackColor  = BorderCol
                        )
                        Text("Reading label…", color = TextMuted, fontSize = 13.sp)
                    }
                    ocrState is OcrState.Success -> {
                        Icon(Icons.Rounded.CheckCircle, null, tint = NutriSafe, modifier = Modifier.size(46.dp))
                        Text("Captured!", color = NutriSafe, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Swipe left →", color = NutriPrimary.copy(alpha = 0.85f), fontSize = 12.sp)
                    }
                    else -> { /* Scanline is the only visual in idle */ }
                }
            }
        }

        // Swipe hint
        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("swipe left to analyze", color = Color.White.copy(alpha = 0.18f), fontSize = 11.sp)
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.18f), modifier = Modifier.size(14.dp))
        }

        Spacer(Modifier.weight(1f))

        ShutterButton(
            onClick  = onCapture,
            enabled  = isIdle
        )
        Spacer(Modifier.height(10.dp))
        Text("Tap to capture", color = Color.White.copy(alpha = 0.25f), fontSize = 12.sp)

        // Tech badges
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            TechBadge("Gemma 4")
            TechBadge("On-Device")
            TechBadge("Personalized")
        }

        // Page indicator dots
        Spacer(Modifier.height(16.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Box(Modifier.size(7.dp).background(NutriPrimary, CircleShape))
            Box(Modifier.size(5.dp).background(Color.White.copy(alpha = 0.22f), CircleShape))
        }
        Spacer(Modifier.height(22.dp))
    }
}

@Composable
private fun TechBadge(label: String) {
    Box(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(50.dp))
            .border(1.dp, Color.White.copy(alpha = 0.09f), RoundedCornerShape(50.dp))
            .padding(horizontal = 11.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color         = TextMuted,
            fontSize      = 10.sp,
            fontWeight    = FontWeight.Medium,
            letterSpacing = 0.3.sp
        )
    }
}

@Composable
private fun ViewfinderBrackets() {
    val color = NutriPrimary.copy(alpha = 0.80f)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth  = 3.dp.toPx()
        val cornerLength = 34.dp.toPx()
        val cap          = StrokeCap.Round
        val width        = size.width
        val height       = size.height
        drawLine(color, Offset(0f, 0f),         Offset(cornerLength, 0f),                 strokeWidth, cap)
        drawLine(color, Offset(0f, 0f),         Offset(0f, cornerLength),                 strokeWidth, cap)
        drawLine(color, Offset(width, 0f),      Offset(width - cornerLength, 0f),         strokeWidth, cap)
        drawLine(color, Offset(width, 0f),      Offset(width, cornerLength),              strokeWidth, cap)
        drawLine(color, Offset(0f, height),     Offset(cornerLength, height),             strokeWidth, cap)
        drawLine(color, Offset(0f, height),     Offset(0f, height - cornerLength),        strokeWidth, cap)
        drawLine(color, Offset(width, height),  Offset(width - cornerLength, height),     strokeWidth, cap)
        drawLine(color, Offset(width, height),  Offset(width, height - cornerLength),     strokeWidth, cap)
    }
}

@Composable
private fun ShutterButton(onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        modifier         = Modifier.size(82.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(
                    width = 3.5.dp,
                    brush = if (enabled)
                        Brush.sweepGradient(listOf(NutriPrimary, NutriPrimaryAccent, NutriPrimary))
                    else
                        Brush.sweepGradient(listOf(BorderCol, BorderCol)),
                    shape = CircleShape
                )
                .clickable(enabled = enabled, onClick = onClick)
        )
        Box(
            modifier = Modifier
                .size(62.dp)
                .background(
                    if (enabled)
                        Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0.88f)))
                    else
                        Brush.radialGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.22f))),
                    CircleShape
                )
        )
    }
}

// ─── Analysis Page (Page 1) ───────────────────────────────────────────────────

@Composable
private fun AnalysisPage(
    uiState: UiState,
    ocrState: OcrState,
    reportState: ReportState,
    ingredientsText: String,
    onChange: (String) -> Unit,
    onAnalyze: () -> Unit,
    onScan: () -> Unit,
    onClearOcr: () -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
    onReportClick: () -> Unit,
    modelPath: String
) {
    val isBusy          = uiState is UiState.Analyzing || uiState is UiState.Streaming
    val isImageAnalysis = isBusy && ingredientsText.isBlank()
    val showFab         = uiState !is UiState.ModelNotFound && uiState !is UiState.ModelLoading

    Scaffold(
        containerColor               = ScreenBg,
        topBar                       = { NutriTopBar(isReportLoaded = reportState is ReportState.Loaded, onReportClick = onReportClick) },
        floatingActionButton         = {
            if (showFab) {
                FloatingActionButton(
                    onClick        = onScan,
                    containerColor = NutriPrimary,
                    contentColor   = Color.White,
                    shape          = CircleShape,
                    modifier       = Modifier.size(58.dp)
                ) {
                    Icon(Icons.Rounded.CameraAlt, "Scan label", modifier = Modifier.size(24.dp))
                }
            }
        },
        floatingActionButtonPosition = FabPosition.Center
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
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

                    when (ocrState) {
                        is OcrState.Success -> OcrResultStrip(text = ocrState.extractedText, onClear = onClearOcr)
                        is OcrState.Error   -> OcrErrorStrip(message = ocrState.errorMessage)
                        else                -> {}
                    }

                    if (reportState is ReportState.NoReport) {
                        ReportStatusChip(onReportClick = onReportClick)
                    }

                    if (!isImageAnalysis) {
                        SectionLabel("Ingredient List", Icons.AutoMirrored.Rounded.ListAlt)
                        IngredientField(value = ingredientsText, onChange = onChange, enabled = !isBusy)
                        GradientButton(
                            text     = if (isBusy) "Analyzing…" else "Analyze Ingredients",
                            icon     = Icons.Rounded.Science,
                            onClick  = onAnalyze,
                            enabled  = !isBusy && ingredientsText.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    when (state) {
                        is UiState.Analyzing -> AnalyzingCard(fromImage = isImageAnalysis)
                        is UiState.Streaming -> StreamingCard(text = state.partialResponse)
                        is UiState.Result    -> {
                            if (state.analysisResult.verdict == "RESCAN") {
                                RescanCard(
                                    reason  = state.analysisResult.explanation,
                                    onScan  = onScan,
                                    onReset = onReset
                                )
                            } else {
                                SectionLabel("Verdict", Icons.Rounded.HealthAndSafety)
                                VerdictCard(
                                    verdict     = state.analysisResult.verdict,
                                    explanation = state.analysisResult.explanation,
                                    onReset     = onReset
                                )
                            }
                        }
                        is UiState.Error -> NutriErrorCard(state.errorMessage, onReset)
                        else             -> {}
                    }

                    Spacer(Modifier.height(88.dp))
                }
            }
        }
    }
}

// ─── OCR Strips ──────────────────────────────────────────────────────────────

@Composable
private fun OcrResultStrip(text: String, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, NutriPrimary.copy(alpha = 0.22f), RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.CheckCircle, null, tint = NutriPrimary, modifier = Modifier.size(13.dp))
                Text("Scanned text", color = NutriPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            TextButton(onClick = onClear, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)) {
                Text("Clear", color = TextMuted, fontSize = 12.sp)
            }
        }
        Text(
            text       = text.ifEmpty { "(no text detected)" },
            fontFamily = FontFamily.Monospace,
            fontSize   = 11.sp,
            lineHeight = 16.sp,
            color      = TextMuted,
            modifier   = Modifier.heightIn(max = 100.dp)
        )
    }
}

@Composable
private fun OcrErrorStrip(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(CardBg)
            .border(1.dp, NutriAvoid.copy(alpha = 0.30f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Rounded.ErrorOutline, null, tint = NutriAvoid, modifier = Modifier.size(18.dp))
        Text(message, color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
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
                "Paste or type the ingredients list…\n\ne.g. Sugar, Modified Starch, Sodium Chloride",
                color    = TextMuted.copy(alpha = 0.45f),
                fontSize = 13.sp
            )
        },
        enabled  = enabled,
        shape    = RoundedCornerShape(16.dp),
        maxLines = 12,
        colors   = OutlinedTextFieldDefaults.colors(
            focusedBorderColor      = NutriPrimary,
            unfocusedBorderColor    = BorderCol,
            cursorColor             = NutriPrimary,
            focusedTextColor        = TextPrimary,
            unfocusedTextColor      = TextPrimary,
            focusedContainerColor   = CardBg,
            unfocusedContainerColor = CardBg,
            disabledContainerColor  = CardBg,
            disabledTextColor       = TextMuted,
            disabledBorderColor     = BorderCol.copy(alpha = 0.45f)
        )
    )
}

// ─── Top Bar ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NutriTopBar(isReportLoaded: Boolean, onReportClick: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            Brush.linearGradient(listOf(NutriPrimary, NutriPrimaryAccent)),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("N", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        "NutriLens AI",
                        color         = TextPrimary,
                        fontWeight    = FontWeight.ExtraBold,
                        fontSize      = 18.sp,
                        letterSpacing = (-0.3).sp
                    )
                    Text("Smart Ingredient Checker", color = TextMuted, fontSize = 11.sp)
                }
            }
        },
        actions = {
            IconButton(onClick = onReportClick) {
                BadgedBox(
                    badge = {
                        if (!isReportLoaded) {
                            Badge(containerColor = NutriCaution)
                        }
                    }
                ) {
                    Icon(
                        Icons.Rounded.HealthAndSafety,
                        contentDescription = "Health Report",
                        tint     = if (isReportLoaded) NutriPrimary else TextMuted,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = ScreenBg)
    )
}

// ─── Section Label ───────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, icon: ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, tint = NutriPrimary, modifier = Modifier.size(14.dp))
        Text(
            text.uppercase(),
            color         = TextMuted,
            fontWeight    = FontWeight.Bold,
            fontSize      = 10.sp,
            letterSpacing = 1.8.sp
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
        Brush.horizontalGradient(listOf(NutriPrimary, NutriPrimaryAccent))
    else
        Brush.horizontalGradient(listOf(BorderCol, BorderCol))

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
                text,
                color         = if (enabled) Color.White else TextMuted,
                fontWeight    = FontWeight.Bold,
                fontSize      = 15.sp,
                letterSpacing = 0.3.sp
            )
        }
    }
}

// ─── Analyzing Card ──────────────────────────────────────────────────────────

@Composable
private fun AnalyzingCard(fromImage: Boolean = false) {
    val eqTransition = rememberInfiniteTransition(label = "eq")
    val bar0 by eqTransition.animateFloat(0.22f, 1f, infiniteRepeatable(tween(420, easing = EaseInOutSine), RepeatMode.Reverse), label = "b0")
    val bar1 by eqTransition.animateFloat(0.22f, 1f, infiniteRepeatable(tween(500, easing = EaseInOutSine), RepeatMode.Reverse), label = "b1")
    val bar2 by eqTransition.animateFloat(0.22f, 1f, infiniteRepeatable(tween(370, easing = EaseInOutSine), RepeatMode.Reverse), label = "b2")
    val bar3 by eqTransition.animateFloat(0.22f, 1f, infiniteRepeatable(tween(560, easing = EaseInOutSine), RepeatMode.Reverse), label = "b3")
    val bar4 by eqTransition.animateFloat(0.22f, 1f, infiniteRepeatable(tween(440, easing = EaseInOutSine), RepeatMode.Reverse), label = "b4")

    DarkCard {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Equalizer bars
            Row(
                modifier              = Modifier.height(44.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment     = Alignment.Bottom
            ) {
                listOf(bar0, bar1, bar2, bar3, bar4).forEach { h ->
                    Box(
                        modifier = Modifier
                            .width(7.dp)
                            .fillMaxHeight(h)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.verticalGradient(listOf(NutriPrimaryAccent, NutriPrimary)))
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    if (fromImage) "Reading Food Label" else "Analyzing Ingredients",
                    color      = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 16.sp
                )
                Text(
                    if (fromImage) "Gemma 4 Vision is scanning your image…" else "AI is reading your ingredients…",
                    color    = TextMuted,
                    fontSize = 13.sp
                )
            }
        }
    }
}

// ─── Streaming Card ──────────────────────────────────────────────────────────

@Composable
private fun StreamingCard(text: String) {
    val cursorBlink by rememberInfiniteTransition(label = "cursor").animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(530), RepeatMode.Reverse),
        label = "cursorBlink"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(NutriPrimary.copy(alpha = 0.55f), NutriPrimaryAccent.copy(alpha = 0.18f), BorderCol)
                ),
                RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier            = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(Modifier.size(8.dp).background(NutriPrimary, CircleShape))
                Text(
                    "Generating…",
                    color      = NutriPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    modifier   = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .background(NutriPrimary.copy(alpha = 0.10f), RoundedCornerShape(50.dp))
                        .border(1.dp, NutriPrimary.copy(alpha = 0.20f), RoundedCornerShape(50.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Gemma 4", color = NutriPrimary.copy(alpha = 0.70f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderCol))

            Text(
                text       = text + if (cursorBlink > 0.5f) "▊" else " ",
                fontFamily = FontFamily.Monospace,
                fontSize   = 13.sp,
                lineHeight = 21.sp,
                color      = TextPrimary
            )
        }
    }
}

// ─── Verdict Card ────────────────────────────────────────────────────────────

@Composable
private fun VerdictCard(verdict: String, explanation: String, onReset: () -> Unit) {
    val verdictColor   = when (verdict) { "SAFE" -> NutriSafe; "AVOID" -> NutriAvoid; else -> NutriCaution }
    val verdictIcon    = when (verdict) { "SAFE" -> Icons.Rounded.CheckCircle; "AVOID" -> Icons.Rounded.Cancel; else -> Icons.Rounded.WarningAmber }
    val verdictLabel   = when (verdict) { "SAFE" -> "SAFE TO EAT"; "AVOID" -> "AVOID"; else -> "USE CAUTION" }
    val verdictSubline = when (verdict) { "SAFE" -> "No concerns for your health profile"; "AVOID" -> "Not suitable for your health profile"; else -> "Moderate concerns — read the analysis" }

    // Single breathing glow — inhale/exhale feel, no janky rings
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glowRadius by pulse.animateFloat(0.68f, 1.08f, infiniteRepeatable(tween(1900, easing = EaseInOutSine), RepeatMode.Reverse), label = "glowRadius")
    val glowAlpha by pulse.animateFloat(0.16f, 0.36f, infiniteRepeatable(tween(1900, easing = EaseInOutSine), RepeatMode.Reverse), label = "glowAlpha")

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 18.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column {
            // ── Hero ─────────────────────────────────────────────────────────
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(224.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width  / 2f
                    val cy = size.height / 2f
                    val radius = size.minDimension * 0.52f * glowRadius

                    // Breathing radial glow disc
                    drawCircle(
                        brush  = Brush.radialGradient(
                            listOf(verdictColor.copy(alpha = glowAlpha), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = radius
                        ),
                        radius = radius,
                        center = Offset(cx, cy)
                    )
                    // Quiet ambient ring
                    drawCircle(
                        color  = verdictColor.copy(alpha = 0.10f),
                        radius = size.minDimension * 0.40f,
                        center = Offset(cx, cy),
                        style  = Stroke(width = 1.dp.toPx())
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Double-ring icon badge
                    Box(
                        modifier = Modifier
                            .size(94.dp)
                            .background(
                                Brush.radialGradient(listOf(verdictColor.copy(alpha = 0.20f), Color.Transparent)),
                                CircleShape
                            )
                            .border(1.5.dp, verdictColor.copy(alpha = 0.42f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(70.dp)
                                .background(verdictColor.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(verdictIcon, null, tint = verdictColor, modifier = Modifier.size(42.dp))
                        }
                    }

                    Text(
                        verdictLabel,
                        color         = Color.White,
                        fontWeight    = FontWeight.Black,
                        fontSize      = 28.sp,
                        letterSpacing = 3.5.sp
                    )

                    Box(
                        modifier = Modifier
                            .background(verdictColor.copy(alpha = 0.13f), RoundedCornerShape(50.dp))
                            .border(1.dp, verdictColor.copy(alpha = 0.33f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(verdictSubline, color = verdictColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // ── Analysis ─────────────────────────────────────────────────────
            Column(
                modifier            = Modifier
                    .background(CardBgHigh)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(verdictColor, RoundedCornerShape(2.dp))
                    )
                    Text("AI Analysis", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text("Gemma 4 Vision", color = TextMuted, fontSize = 10.sp, letterSpacing = 0.4.sp)
                    }
                }

                Text(explanation, color = Color(0xFFCBD5E1), fontSize = 14.sp, lineHeight = 23.sp)

                Box(Modifier.fillMaxWidth().height(1.dp).background(BorderCol))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(verdictColor.copy(alpha = 0.09f))
                        .border(1.dp, verdictColor.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
                        .clickable { onReset() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, null, tint = verdictColor, modifier = Modifier.size(17.dp))
                        Text("Scan Another Product", color = verdictColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
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
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator(
                color       = NutriPrimary,
                strokeWidth = 3.dp,
                modifier    = Modifier.size(52.dp),
                trackColor  = BorderCol
            )
            Text("Loading Gemma 4 Model", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text("This may take up to 15 seconds…", color = TextMuted, fontSize = 13.sp)
        }
    }
}

// ─── Model Not Found Card ────────────────────────────────────────────────────

@Composable
private fun ModelNotFoundCard(modelPath: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    DarkCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .background(NutriPrimary.copy(alpha = 0.10f), CircleShape)
                    .border(1.dp, NutriPrimary.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SmartToy, null, tint = NutriPrimary, modifier = Modifier.size(32.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Gemma Model Required", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text(
                    "Download the model file and push it to your device to get started.",
                    color      = TextMuted,
                    fontSize   = 13.sp,
                    lineHeight = 20.sp
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Model path:", color = TextMuted, fontSize = 11.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(CardBgHigh)
                        .border(1.dp, BorderCol, RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        modelPath,
                        fontFamily = FontFamily.Monospace,
                        fontSize   = 11.sp,
                        color      = NutriPrimary.copy(alpha = 0.80f),
                        lineHeight = 16.sp
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SetupStep("1", "Visit huggingface.co/litert-community")
                SetupStep("2", "Download gemma-4-E2B-it-litert-lm")
                SetupStep("3", "Copy to the path shown above")
                SetupStep("4", "Tap Retry below")
            }
            GradientButton("Retry", Icons.Rounded.Refresh, onRetry, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SetupStep(number: String, text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(NutriPrimary.copy(alpha = 0.10f), CircleShape)
                .border(1.dp, NutriPrimary.copy(alpha = 0.25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = NutriPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
        Text(text, color = TextMuted, fontSize = 13.sp)
    }
}

// ─── Error Card ──────────────────────────────────────────────────────────────

@Composable
private fun NutriErrorCard(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .border(1.dp, NutriAvoid.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            verticalAlignment     = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier         = Modifier
                    .size(38.dp)
                    .background(NutriAvoid.copy(alpha = 0.10f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = NutriAvoid, modifier = Modifier.size(20.dp))
            }
            Column(
                modifier            = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("Something went wrong", color = NutriAvoid, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text(message, color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp)
                TextButton(onClick = onDismiss, contentPadding = PaddingValues(0.dp)) {
                    Text("Dismiss", color = TextMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─── Rescan Card ─────────────────────────────────────────────────────────────

@Composable
private fun RescanCard(reason: String, onScan: () -> Unit, onReset: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "rescanPulse")
    val iconAlpha by pulse.animateFloat(
        0.45f, 1f,
        infiniteRepeatable(tween(1100, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "iconAlpha"
    )

    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
        colors    = CardDefaults.cardColors(containerColor = CardBg)
    ) {
        Column {
            // Hero
            Box(
                modifier         = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawCircle(
                        brush  = Brush.radialGradient(
                            listOf(NutriCaution.copy(alpha = 0.18f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = size.minDimension * 0.55f
                        ),
                        radius = size.minDimension * 0.55f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color  = NutriCaution.copy(alpha = 0.12f),
                        radius = size.minDimension * 0.40f,
                        center = Offset(cx, cy),
                        style  = Stroke(width = 1.dp.toPx())
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(94.dp)
                            .background(
                                Brush.radialGradient(listOf(NutriCaution.copy(alpha = 0.18f), Color.Transparent)),
                                CircleShape
                            )
                            .border(1.5.dp, NutriCaution.copy(alpha = 0.40f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier         = Modifier
                                .size(70.dp)
                                .background(NutriCaution.copy(alpha = 0.14f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.CameraAlt,
                                null,
                                tint     = NutriCaution.copy(alpha = iconAlpha),
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }

                    Text(
                        "COULDN'T READ LABEL",
                        color         = Color.White,
                        fontWeight    = FontWeight.Black,
                        fontSize      = 22.sp,
                        letterSpacing = 2.5.sp,
                        textAlign     = TextAlign.Center
                    )

                    Box(
                        modifier = Modifier
                            .background(NutriCaution.copy(alpha = 0.13f), RoundedCornerShape(50.dp))
                            .border(1.dp, NutriCaution.copy(alpha = 0.33f), RoundedCornerShape(50.dp))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text("Try scanning again", color = NutriCaution, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Details + actions
            Column(
                modifier            = Modifier
                    .background(CardBgHigh)
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(20.dp)
                            .background(NutriCaution, RoundedCornerShape(2.dp))
                    )
                    Text("What happened", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }

                Text(
                    reason.ifEmpty { "The image was unclear or no food label was detected." },
                    color      = Color(0xFFCBD5E1),
                    fontSize   = 14.sp,
                    lineHeight = 23.sp
                )

                Box(Modifier.fillMaxWidth().height(1.dp).background(BorderCol))

                // Tips row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("SCANNING TIPS", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    listOf(
                        "Hold the camera steady and close to the label",
                        "Ensure good lighting — avoid glare and shadows",
                        "Fit the full ingredient panel in the frame"
                    ).forEach { tip ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(5.dp)
                                    .background(NutriCaution.copy(alpha = 0.60f), CircleShape)
                            )
                            Text(tip, color = TextMuted, fontSize = 13.sp, lineHeight = 20.sp)
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().height(1.dp).background(BorderCol))

                // Scan again button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(NutriCaution.copy(alpha = 0.09f))
                        .border(1.dp, NutriCaution.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
                        .clickable { onScan() }
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, null, tint = NutriCaution, modifier = Modifier.size(17.dp))
                        Text("Scan Again", color = NutriCaution, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                // Secondary dismiss
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onReset() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("Dismiss", color = TextMuted, fontSize = 13.sp)
                }
            }
        }
    }
}

// ─── Report Status Chip ──────────────────────────────────────────────────────

@Composable
private fun ReportStatusChip(onReportClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(NutriCaution.copy(alpha = 0.06f))
            .border(1.dp, NutriCaution.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
            .clickable { onReportClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Rounded.HealthAndSafety, null, tint = NutriCaution, modifier = Modifier.size(16.dp))
        Text(
            "No health profile",
            color      = NutriCaution,
            fontWeight = FontWeight.Medium,
            fontSize   = 12.sp,
            modifier   = Modifier.weight(1f)
        )
        Text("Add report →", color = NutriCaution.copy(alpha = 0.70f), fontSize = 11.sp)
    }
}

// ─── Health Report Bottom Sheet ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HealthReportBottomSheet(
    reportState: ReportState,
    onDismiss: () -> Unit,
    onUploadClick: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor   = CardBg,
        tonalElevation   = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(NutriPrimary.copy(alpha = 0.12f), CircleShape)
                        .border(1.dp, NutriPrimary.copy(alpha = 0.25f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.HealthAndSafety, null, tint = NutriPrimary, modifier = Modifier.size(22.dp))
                }
                Column {
                    Text("Health Report", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Text("Personalize your food analysis", color = TextMuted, fontSize = 12.sp)
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(BorderCol))

            when (val state = reportState) {
                is ReportState.NoReport -> {
                    Column(
                        modifier            = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Icon(Icons.Rounded.UploadFile, null, tint = TextMuted, modifier = Modifier.size(48.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("No report uploaded", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(
                                "Upload your lab report and Gemma 4 will create a personal health profile for food analysis.",
                                color     = TextMuted,
                                fontSize  = 13.sp,
                                lineHeight = 19.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                        GradientButton(
                            text     = "Upload Report",
                            icon     = Icons.Rounded.UploadFile,
                            onClick  = onUploadClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text("Supports PDF and TXT files", color = TextMuted, fontSize = 11.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                }

                is ReportState.Extracting -> {
                    Column(
                        modifier            = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        CircularProgressIndicator(
                            color       = NutriPrimary,
                            strokeWidth = 3.dp,
                            modifier    = Modifier.size(44.dp),
                            trackColor  = BorderCol
                        )
                        Text("Reading document…", color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Extracting text from your file", color = TextMuted, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                    }
                }

                is ReportState.Summarizing -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color       = NutriPrimary,
                                strokeWidth = 2.5.dp,
                                modifier    = Modifier.size(18.dp),
                                trackColor  = BorderCol
                            )
                            Text(
                                "Gemma 4 is summarizing your report…",
                                color      = NutriPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize   = 14.sp
                            )
                        }
                        if (state.partialSummary.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(CardBgHigh)
                                    .border(1.dp, BorderCol, RoundedCornerShape(14.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    state.partialSummary,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 12.sp,
                                    lineHeight = 18.sp,
                                    color      = TextMuted
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                is ReportState.Loaded -> {
                    val dateStr = remember(state.uploadedAt) {
                        java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                            .format(java.util.Date(state.uploadedAt))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NutriSafe.copy(alpha = 0.07f))
                                .border(1.dp, NutriSafe.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                                .padding(14.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Rounded.CheckCircle, null, tint = NutriSafe, modifier = Modifier.size(18.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(state.fileName, color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Uploaded $dateStr", color = TextMuted, fontSize = 11.sp)
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("AI SUMMARY", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(CardBgHigh)
                                    .border(1.dp, BorderCol, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                Text(
                                    state.summary.take(200).let { if (state.summary.length > 200) "$it…" else it },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize   = 12.sp,
                                    lineHeight = 18.sp,
                                    color      = TextMuted
                                )
                            }
                        }
                        GradientButton(
                            text     = "Change Report",
                            icon     = Icons.Rounded.UploadFile,
                            onClick  = onUploadClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is ReportState.Error -> {
                    Column(
                        modifier            = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(8.dp))
                        Icon(Icons.Rounded.ErrorOutline, null, tint = NutriAvoid, modifier = Modifier.size(44.dp))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Upload failed", color = NutriAvoid, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(state.message, color = TextMuted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center)
                        }
                        GradientButton(
                            text     = "Try Again",
                            icon     = Icons.Rounded.Refresh,
                            onClick  = onUploadClick,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(4.dp))
                    }
                }
            }
        }
    }
}

// ─── Dark Card Shell ─────────────────────────────────────────────────────────

@Composable
private fun DarkCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .border(1.dp, BorderCol, RoundedCornerShape(20.dp))
    ) {
        Column(modifier = Modifier.padding(18.dp), content = content)
    }
}
