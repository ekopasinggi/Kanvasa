package com.example.ui

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.TrendingFlat
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AdsClick
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoNotTouch
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FlipToFront
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Rectangle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.CanvasElement
import com.example.model.CanvasPattern
import com.example.model.CanvasTool
import com.example.model.ExportFormat
import com.example.model.PalmRejectionMode
import com.example.model.PalmRejectionPolicy
import com.example.model.ShapeKind
import com.example.model.StylusCustomBinding
import com.example.model.StylusHardwareSignal
import com.example.ui.theme.HighlighterSwatches
import com.example.ui.theme.InkSwatches
import com.example.viewmodel.CanvasViewModel
import com.example.viewmodel.ToolSwitchCue
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CanvasWorkspaceScreen(
    boardId: Long,
    viewModel: CanvasViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val hostView = LocalView.current
    val stylusBridge = LocalStylusHardwareBridge.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    var capturedCalibrationSignal by remember { mutableStateOf<StylusHardwareSignal?>(null) }
    var visibleToolCue by remember { mutableStateOf<ToolSwitchCue?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(boardId) {
        viewModel.loadBoard(boardId)
    }

    // Wire hardware stylus shortcut bridge while workspace is open
    DisposableEffect(stylusBridge) {
        stylusBridge?.isCanvasActive = true
        stylusBridge?.onTogglePenEraser = {
            viewModel.togglePenAndEraserFromStylus()
        }
        stylusBridge?.onCalibrationCaptured = { signal ->
            capturedCalibrationSignal = signal
            hostView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        onDispose {
            stylusBridge?.isCanvasActive = false
            stylusBridge?.isModalOpen = false
            stylusBridge?.isCalibrationMode = false
            stylusBridge?.onTogglePenEraser = null
            stylusBridge?.onCalibrationCaptured = null
        }
    }

    val isAnyModalOpen = showRenameDialog ||
        showCalibrationDialog ||
        state.textEditorDraft != null ||
        state.croppingImageElement != null ||
        state.isExportSheetOpen

    LaunchedEffect(stylusBridge, isAnyModalOpen, showCalibrationDialog, state.customStylusBinding) {
        stylusBridge?.isModalOpen = isAnyModalOpen
        stylusBridge?.isCalibrationMode = showCalibrationDialog
        stylusBridge?.customBinding = state.customStylusBinding
    }

    // Subtle haptic feedback + auto-dismissing floating indicator pill on stylus shortcut switch
    LaunchedEffect(state.toolSwitchCue?.eventId) {
        val cue = state.toolSwitchCue ?: return@LaunchedEffect
        visibleToolCue = cue
        hostView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (!cue.isTemporary) {
            delay(1150L)
            if (visibleToolCue?.eventId == cue.eventId) {
                visibleToolCue = null
            }
        }
    }

    LaunchedEffect(state.isTemporaryEraserActive) {
        if (!state.isTemporaryEraserActive && visibleToolCue?.isTemporary == true) {
            delay(280L)
            if (!state.isTemporaryEraserActive && visibleToolCue?.isTemporary == true) {
                visibleToolCue = null
            }
        }
    }

    DisposableEffect(lifecycleOwner, boardId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.flushPendingSaveAsync()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler {
        viewModel.flushAndExit(onNavigateBack)
    }

    // Zero-Permission Android Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importImage(uri) {
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.image_import_error)
                    )
                }
            }
        }
    }

    // Zero-Permission SAF Document Creator for PDF & PNG
    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            viewModel.saveExportToUri(context, uri) { success ->
                scope.launch {
                    val msg = if (success) {
                        context.getString(R.string.export_success_saved, "PDF")
                    } else {
                        context.getString(R.string.export_error_message)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val createPngLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri != null) {
            viewModel.saveExportToUri(context, uri) { success ->
                scope.launch {
                    val msg = if (success) {
                        context.getString(R.string.export_success_saved, "PNG")
                    } else {
                        context.getString(R.string.export_error_message)
                    }
                    snackbarHostState.showSnackbar(msg)
                }
            }
        }
    }

    val selectedElement = remember(state.elements, state.selectedElementId) {
        state.elements.firstOrNull { it.id == state.selectedElementId }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Full-Bleed Infinite Canvas Surface
        InfiniteCanvasSurface(
            pattern = state.pattern,
            elements = state.elements,
            activeTool = state.activeTool,
            penColorArgb = state.penColorArgb,
            penStrokeWidth = state.penStrokeWidth,
            highlighterColorArgb = state.highlighterColorArgb,
            highlighterStrokeWidth = state.highlighterStrokeWidth,
            eraserRadiusDp = state.eraserRadiusDp,
            palmRejectionMode = state.palmRejectionMode,
            isStylusSessionActive = state.isStylusSessionActive,
            activeShapeKind = state.activeShapeKind,
            selectedElementId = state.selectedElementId,
            cameraScale = state.cameraScale,
            cameraPanX = state.cameraPanX,
            cameraPanY = state.cameraPanY,
            bitmapVersion = state.bitmapVersion,
            bitmapProvider = { path -> viewModel.mediaStorage.getCachedBitmap(path) },
            onViewportSizeChanged = viewModel::updateViewportSize,
            onCameraTransform = viewModel::updateCamera,
            onCommitStroke = viewModel::commitStroke,
            onCommitShape = viewModel::commitShape,
            onBeginContinuousGesture = viewModel::beginContinuousMutationGesture,
            onEndContinuousGesture = viewModel::endContinuousMutationGesture,
            onCancelContinuousGesture = viewModel::cancelContinuousMutationGesture,
            onEraseSegment = viewModel::eraseSegment,
            onSelectElement = viewModel::selectElement,
            onMoveSelectedElement = viewModel::moveSelectedElement,
            onResizeSelectedElement = viewModel::resizeSelectedElement,
            onTapPlaceNote = viewModel::openNewTextEditorAt,
            onEditTextElement = viewModel::openEditorForElement,
            onStylusDetected = {
                val justActivated = viewModel.onStylusDetected()
                if (justActivated) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.palm_auto_activated_snackbar)
                        )
                    }
                }
            },
            isStylusButtonHeldProvider = { stylusBridge?.isStylusButtonHeld == true },
            onTemporaryEraserChange = viewModel::setTemporaryEraserFromStylus,
            onStylusTouchStateChange = { touching ->
                stylusBridge?.notifyStylusTouchActive(touching)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. Unified Adaptive Top Studio Bar + Slim Style Sub-Bar directly below
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val isWideTablet = maxWidth >= 840.dp

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                UnifiedTopWorkspaceBar(
                    title = state.boardTitle,
                    pattern = state.pattern,
                    activeTool = state.activeTool,
                    canUndo = state.canUndo,
                    canRedo = state.canRedo,
                    palmRejectionMode = state.palmRejectionMode,
                    isStylusSessionActive = state.isStylusSessionActive,
                    customStylusBinding = state.customStylusBinding,
                    isWideTablet = isWideTablet,
                    onBack = { viewModel.flushAndExit(onNavigateBack) },
                    onTitleClick = { showRenameDialog = true },
                    onSelectTool = viewModel::setActiveTool,
                    onPickImage = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onCyclePattern = viewModel::cycleCanvasPattern,
                    onSelectPalmMode = { mode ->
                        viewModel.setPalmRejectionMode(mode)
                        val msgRes = when (mode) {
                            PalmRejectionMode.AUTO -> R.string.palm_mode_auto_desc
                            PalmRejectionMode.STYLUS_ONLY -> R.string.palm_stylus_only_snackbar
                            PalmRejectionMode.OFF -> R.string.palm_off_snackbar
                        }
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(msgRes))
                        }
                    },
                    onOpenStylusCalibration = {
                        capturedCalibrationSignal = null
                        showCalibrationDialog = true
                    },
                    onOpenExport = { viewModel.openExportSheet(context) }
                )

                // Slim Secondary Style Sub-Bar directly below the unified top bar
                AnimatedVisibility(
                    visible = state.activeTool == CanvasTool.PEN ||
                        state.activeTool == CanvasTool.HIGHLIGHTER ||
                        state.activeTool == CanvasTool.SHAPE ||
                        state.activeTool == CanvasTool.ERASER,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 }
                ) {
                    ToolStyleSubBar(
                        activeTool = state.activeTool,
                        penColorArgb = state.penColorArgb,
                        penWidth = state.penStrokeWidth,
                        highlighterColorArgb = state.highlighterColorArgb,
                        highlighterWidth = state.highlighterStrokeWidth,
                        eraserRadiusDp = state.eraserRadiusDp,
                        activeShapeKind = state.activeShapeKind,
                        onPenColorChange = viewModel::setPenColor,
                        onPenWidthChange = viewModel::setPenWidth,
                        onHighlighterColorChange = viewModel::setHighlighterColor,
                        onHighlighterWidthChange = viewModel::setHighlighterWidth,
                        onEraserRadiusChange = viewModel::setEraserRadiusDp,
                        onShapeKindChange = viewModel::setShapeKind
                    )
                }

                // Tap-to-place hint banner for Text / Sticky tools
                AnimatedVisibility(
                    visible = state.activeTool == CanvasTool.TEXT || state.activeTool == CanvasTool.STICKY,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 }
                ) {
                    val modeLabel = stringResource(
                        if (state.activeTool == CanvasTool.STICKY) R.string.tool_sticky else R.string.tool_text
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 3.dp
                    ) {
                        Text(
                            text = stringResource(R.string.canvas_hint_tap_text, modeLabel),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Brief Floating Capsule Indicator on Stylus Shortcut Switch / Hold-to-Erase
                AnimatedVisibility(
                    visible = visibleToolCue != null,
                    enter = fadeIn() + scaleIn(initialScale = 0.92f),
                    exit = fadeOut() + scaleOut(targetScale = 0.95f)
                ) {
                    visibleToolCue?.let { cue ->
                        StylusShortcutCuePill(cue = cue)
                    }
                }
            }
        }

        // 3. Minimal Bottom HUD (Zoom Badge + Contextual Selection Actions only)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 960.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            ZoomBadgePill(
                scale = state.cameraScale,
                onResetZoom = viewModel::fitCameraToContent
            )

            if (selectedElement != null) {
                SelectionActionPill(
                    selectedElement = selectedElement,
                    onEditText = {
                        if (selectedElement is CanvasElement.TextNote) {
                            viewModel.openEditorForElement(selectedElement)
                        }
                    },
                    onCropImage = viewModel::openCropForSelectedImage,
                    onDuplicate = viewModel::duplicateSelectedElement,
                    onBringFront = viewModel::bringSelectedToFront,
                    onDelete = viewModel::deleteSelectedElement
                )
            }
        }

        // Snackbar Host
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 64.dp)
        )

        if (showCalibrationDialog) {
            StylusCalibrationDialog(
                currentBinding = state.customStylusBinding,
                capturedSignal = capturedCalibrationSignal,
                onSaveCaptured = { binding ->
                    viewModel.saveCustomStylusBinding(binding)
                    showCalibrationDialog = false
                    capturedCalibrationSignal = null
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.stylus_calibrate_saved_snackbar, binding.label)
                        )
                    }
                },
                onResetDefault = {
                    viewModel.clearCustomStylusBinding()
                    showCalibrationDialog = false
                    capturedCalibrationSignal = null
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.stylus_calibrate_reset_snackbar)
                        )
                    }
                },
                onDismiss = {
                    showCalibrationDialog = false
                    capturedCalibrationSignal = null
                }
            )
        }
    }

    if (showRenameDialog) {
        BoardConfigDialog(
            isRename = true,
            initialTitle = state.boardTitle,
            initialPattern = state.pattern,
            onDismiss = { showRenameDialog = false },
            onConfirm = { newTitle, newPattern ->
                viewModel.updateBoardTitleAndPattern(newTitle, newPattern)
                showRenameDialog = false
            }
        )
    }

    state.textEditorDraft?.let { draft ->
        TextStickyEditorSheet(
            draft = draft,
            onDismiss = viewModel::dismissTextEditor,
            onConfirm = viewModel::confirmTextNote
        )
    }

    state.croppingImageElement?.let { imgElement ->
        ImageCropperDialog(
            imageElement = imgElement,
            mediaStorage = viewModel.mediaStorage,
            isProcessing = state.isCroppingInProgress,
            onDismiss = viewModel::dismissImageCropper,
            onApplyRectangleCrop = { normRect ->
                viewModel.applyRectangleCropToImage(normRect) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.crop_error_failed)
                        )
                    }
                }
            },
            onApplyCustomCrop = { normPoints ->
                viewModel.applyCustomCropToImage(normPoints) {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.crop_error_failed)
                        )
                    }
                }
            },
            onShowMessage = { msg ->
                scope.launch { snackbarHostState.showSnackbar(msg) }
            }
        )
    }

    if (state.isExportSheetOpen) {
        ExportBottomSheet(
            format = state.exportFormat,
            scope = state.exportScope,
            previewBitmap = state.exportPreviewBitmap,
            isGeneratingPreview = state.isGeneratingPreview,
            isExporting = state.isExporting,
            onFormatChange = viewModel::setExportFormat,
            onScopeChange = { newScope -> viewModel.setExportScope(context, newScope) },
            onSaveToDevice = {
                val safeTitle = state.boardTitle
                    .trim()
                    .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                    .replace(Regex("\\s+"), "_")
                    .ifBlank { "Kanvasa_Catatan" }
                when (state.exportFormat) {
                    ExportFormat.PDF -> createPdfLauncher.launch("${safeTitle}.pdf")
                    ExportFormat.PNG -> createPngLauncher.launch("${safeTitle}.png")
                }
            },
            onShare = {
                viewModel.shareExport(
                    context = context,
                    onIntentReady = { shareIntent ->
                        context.startActivity(
                            Intent.createChooser(
                                shareIntent,
                                context.getString(R.string.export_share_now)
                            )
                        )
                    },
                    onError = {
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                context.getString(R.string.export_error_message)
                            )
                        }
                    }
                )
            },
            onDismiss = viewModel::closeExportSheet
        )
    }
}

@Composable
private fun UnifiedTopWorkspaceBar(
    title: String,
    pattern: CanvasPattern,
    activeTool: CanvasTool,
    canUndo: Boolean,
    canRedo: Boolean,
    palmRejectionMode: PalmRejectionMode,
    isStylusSessionActive: Boolean,
    customStylusBinding: StylusCustomBinding,
    isWideTablet: Boolean,
    onBack: () -> Unit,
    onTitleClick: () -> Unit,
    onSelectTool: (CanvasTool) -> Unit,
    onPickImage: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCyclePattern: () -> Unit,
    onSelectPalmMode: (PalmRejectionMode) -> Unit,
    onOpenStylusCalibration: () -> Unit,
    onOpenExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 5.dp,
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = if (isWideTablet) 1280.dp else 720.dp)
    ) {
        if (isWideTablet) {
            // Tablet / Wide Screen: Single Unified Top Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("workspace_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back_to_gallery)
                    )
                }

                BoardTitleTrigger(
                    title = title,
                    onClick = onTitleClick,
                    modifier = Modifier.widthIn(min = 84.dp, max = 164.dp)
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .width(1.dp)
                        .height(26.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    ToolSelectorStrip(
                        activeTool = activeTool,
                        compactSingleRow = true,
                        onSelectTool = onSelectTool,
                        onPickImage = onPickImage
                    )
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .width(1.dp)
                        .height(26.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                WorkspaceUtilityActions(
                    pattern = pattern,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    palmRejectionMode = palmRejectionMode,
                    isStylusSessionActive = isStylusSessionActive,
                    customStylusBinding = customStylusBinding,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onCyclePattern = onCyclePattern,
                    onSelectPalmMode = onSelectPalmMode,
                    onOpenStylusCalibration = onOpenStylusCalibration,
                    onOpenExport = onOpenExport
                )
            }
        } else {
            // Phone / Compact Screen: 2 Tidy Rows inside 1 Unified Top Panel
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("workspace_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back_to_gallery)
                        )
                    }

                    BoardTitleTrigger(
                        title = title,
                        onClick = onTitleClick,
                        modifier = Modifier.weight(1f)
                    )

                    WorkspaceUtilityActions(
                        pattern = pattern,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        palmRejectionMode = palmRejectionMode,
                        isStylusSessionActive = isStylusSessionActive,
                        customStylusBinding = customStylusBinding,
                        onUndo = onUndo,
                        onRedo = onRedo,
                        onCyclePattern = onCyclePattern,
                        onSelectPalmMode = onSelectPalmMode,
                        onOpenStylusCalibration = onOpenStylusCalibration,
                        onOpenExport = onOpenExport
                    )
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    ToolSelectorStrip(
                        activeTool = activeTool,
                        compactSingleRow = false,
                        onSelectTool = onSelectTool,
                        onPickImage = onPickImage
                    )
                }
            }
        }
    }
}

@Composable
private fun BoardTitleTrigger(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .testTag("workspace_title_button")
    ) {
        Text(
            text = title.ifBlank { stringResource(R.string.default_board_title) },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
private fun WorkspaceUtilityActions(
    pattern: CanvasPattern,
    canUndo: Boolean,
    canRedo: Boolean,
    palmRejectionMode: PalmRejectionMode,
    isStylusSessionActive: Boolean,
    customStylusBinding: StylusCustomBinding,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCyclePattern: () -> Unit,
    onSelectPalmMode: (PalmRejectionMode) -> Unit,
    onOpenStylusCalibration: () -> Unit,
    onOpenExport: () -> Unit
) {
    var palmMenuExpanded by remember { mutableStateOf(false) }
    val stylusRejectionActive = PalmRejectionPolicy.isStylusOnlyActive(
        mode = palmRejectionMode,
        isStylusSessionActive = isStylusSessionActive
    )

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onUndo,
            enabled = canUndo,
            modifier = Modifier.testTag("undo_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Undo,
                contentDescription = stringResource(R.string.cd_undo)
            )
        }

        IconButton(
            onClick = onRedo,
            enabled = canRedo,
            modifier = Modifier.testTag("redo_button")
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Redo,
                contentDescription = stringResource(R.string.cd_redo)
            )
        }

        IconButton(
            onClick = onCyclePattern,
            modifier = Modifier.testTag("cycle_pattern_button")
        ) {
            val patternIcon = when (pattern) {
                CanvasPattern.DOTS -> Icons.Default.Grain
                CanvasPattern.GRID -> Icons.Default.Grid4x4
                CanvasPattern.BLANK -> Icons.Outlined.CheckBoxOutlineBlank
            }
            Icon(
                imageVector = patternIcon,
                contentDescription = stringResource(R.string.cd_canvas_pattern)
            )
        }

        // Palm Rejection / Stylus Mode Menu Button
        Box {
            IconButton(
                onClick = { palmMenuExpanded = true },
                modifier = Modifier.testTag("palm_rejection_button")
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(
                            if (stylusRejectionActive) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            }
                        )
                ) {
                    Icon(
                        imageVector = if (palmRejectionMode == PalmRejectionMode.OFF) {
                            Icons.Default.TouchApp
                        } else {
                            Icons.Default.DoNotTouch
                        },
                        contentDescription = stringResource(R.string.cd_palm_rejection_menu),
                        tint = if (stylusRejectionActive) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = palmMenuExpanded,
                onDismissRequest = { palmMenuExpanded = false }
            ) {
                Text(
                    text = stringResource(R.string.palm_mode_menu_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
                PalmModeMenuItem(
                    title = stringResource(R.string.palm_mode_auto),
                    description = stringResource(R.string.palm_mode_auto_desc),
                    selected = palmRejectionMode == PalmRejectionMode.AUTO,
                    onClick = {
                        palmMenuExpanded = false
                        onSelectPalmMode(PalmRejectionMode.AUTO)
                    },
                    testTag = "palm_mode_auto_item"
                )
                PalmModeMenuItem(
                    title = stringResource(R.string.palm_mode_stylus_only),
                    description = stringResource(R.string.palm_mode_stylus_only_desc),
                    selected = palmRejectionMode == PalmRejectionMode.STYLUS_ONLY,
                    onClick = {
                        palmMenuExpanded = false
                        onSelectPalmMode(PalmRejectionMode.STYLUS_ONLY)
                    },
                    testTag = "palm_mode_stylus_only_item"
                )
                PalmModeMenuItem(
                    title = stringResource(R.string.palm_mode_off),
                    description = stringResource(R.string.palm_mode_off_desc),
                    selected = palmRejectionMode == PalmRejectionMode.OFF,
                    onClick = {
                        palmMenuExpanded = false
                        onSelectPalmMode(PalmRejectionMode.OFF)
                    },
                    testTag = "palm_mode_off_item"
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.stylus_calibrate_menu_title),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (customStylusBinding.isConfigured) {
                                    stringResource(
                                        R.string.stylus_calibrate_menu_custom,
                                        customStylusBinding.label.ifBlank { "key=${customStylusBinding.keyCode}" }
                                    )
                                } else {
                                    stringResource(R.string.stylus_calibrate_menu_default)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    onClick = {
                        palmMenuExpanded = false
                        onOpenStylusCalibration()
                    },
                    modifier = Modifier.testTag("stylus_calibrate_menu_item")
                )
            }
        }

        Button(
            onClick = onOpenExport,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ),
            modifier = Modifier
                .padding(end = 2.dp)
                .testTag("open_export_button")
        ) {
            Icon(
                imageVector = Icons.Default.IosShare,
                contentDescription = null,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.export_button_label),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun StylusShortcutCuePill(
    cue: ToolSwitchCue
) {
    val (icon, labelRes) = when {
        cue.isTemporary -> Icons.Default.AutoFixNormal to R.string.stylus_cue_temp_eraser
        cue.tool == CanvasTool.ERASER -> Icons.Default.AutoFixNormal to R.string.stylus_cue_eraser
        cue.tool == CanvasTool.HIGHLIGHTER -> Icons.Default.BorderColor to R.string.stylus_cue_highlighter
        else -> Icons.Default.Draw to R.string.stylus_cue_pen
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.94f),
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp,
        modifier = Modifier.testTag("stylus_shortcut_cue_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(labelRes),
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun ZoomBadgePill(
    scale: Float,
    onResetZoom: () -> Unit
) {
    Surface(
        onClick = onResetZoom,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier.testTag("reset_zoom_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CenterFocusStrong,
                contentDescription = stringResource(R.string.cd_reset_zoom),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "${(scale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SelectionActionPill(
    selectedElement: CanvasElement,
    onEditText: () -> Unit,
    onCropImage: () -> Unit,
    onDuplicate: () -> Unit,
    onBringFront: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        shadowElevation = 3.dp,
        modifier = Modifier.testTag("selection_action_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            if (selectedElement is CanvasElement.TextNote) {
                IconButton(
                    onClick = onEditText,
                    modifier = Modifier.testTag("selection_edit_text_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.action_edit_text),
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (selectedElement is CanvasElement.ImageItem) {
                Surface(
                    onClick = onCropImage,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .padding(start = 2.dp, end = 2.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("selection_crop_image_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Crop,
                            contentDescription = stringResource(R.string.action_crop_image),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = stringResource(R.string.action_crop_image),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
            IconButton(
                onClick = onDuplicate,
                modifier = Modifier.testTag("selection_duplicate_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.action_duplicate),
                    modifier = Modifier.size(19.dp)
                )
            }
            IconButton(
                onClick = onBringFront,
                modifier = Modifier.testTag("selection_bring_front_button")
            ) {
                Icon(
                    imageVector = Icons.Default.FlipToFront,
                    contentDescription = stringResource(R.string.action_bring_front),
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("selection_delete_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private data class SizePresetOption(
    val value: Float,
    val labelRes: Int,
    val previewDotDp: Int
)

@Composable
private fun ToolStyleSubBar(
    activeTool: CanvasTool,
    penColorArgb: Long,
    penWidth: Float,
    highlighterColorArgb: Long,
    highlighterWidth: Float,
    eraserRadiusDp: Float,
    activeShapeKind: ShapeKind,
    onPenColorChange: (Long) -> Unit,
    onPenWidthChange: (Float) -> Unit,
    onHighlighterColorChange: (Long) -> Unit,
    onHighlighterWidthChange: (Float) -> Unit,
    onEraserRadiusChange: (Float) -> Unit,
    onShapeKindChange: (ShapeKind) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 3.dp,
        modifier = Modifier.widthIn(max = 760.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            if (activeTool == CanvasTool.ERASER) {
                Icon(
                    imageVector = Icons.Default.AutoFixNormal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 2.dp, end = 2.dp)
                        .size(18.dp)
                )
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                val eraserPresets = listOf(
                    SizePresetOption(8f, R.string.size_s, 7),
                    SizePresetOption(16f, R.string.size_m, 11),
                    SizePresetOption(28f, R.string.size_l, 16),
                    SizePresetOption(42f, R.string.size_xl, 21)
                )
                eraserPresets.forEachIndexed { idx, preset ->
                    val isSelected = kotlin.math.abs(eraserRadiusDp - preset.value) < 1.5f
                    val sizeName = stringResource(preset.labelRes)
                    SizeSymbolButton(
                        accessibleLabel = stringResource(R.string.cd_eraser_size_option, sizeName),
                        dotSizeDp = preset.previewDotDp,
                        dotColor = if (isSelected) Color.White else MaterialTheme.colorScheme.error,
                        isHollowRing = true,
                        selected = isSelected,
                        onClick = { onEraserRadiusChange(preset.value) },
                        testTag = "eraser_size_option_$idx"
                    )
                }
            } else {
                if (activeTool == CanvasTool.SHAPE) {
                    val shapes = listOf(
                        Triple(ShapeKind.RECTANGLE, Icons.Outlined.Rectangle, R.string.shape_rectangle),
                        Triple(ShapeKind.CIRCLE, Icons.Default.RadioButtonUnchecked, R.string.shape_circle),
                        Triple(ShapeKind.LINE, Icons.Default.HorizontalRule, R.string.shape_line),
                        Triple(ShapeKind.ARROW, Icons.AutoMirrored.Filled.TrendingFlat, R.string.shape_arrow)
                    )
                    shapes.forEach { (kind, icon, labelRes) ->
                        FilterChip(
                            selected = activeShapeKind == kind,
                            onClick = { onShapeKindChange(kind) },
                            label = { Text(stringResource(labelRes)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            modifier = Modifier.testTag("shape_chip_${kind.name.lowercase()}")
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                }

                val swatches = if (activeTool == CanvasTool.HIGHLIGHTER) {
                    HighlighterSwatches
                } else {
                    InkSwatches
                }
                val selectedSwatch = if (activeTool == CanvasTool.HIGHLIGHTER) {
                    highlighterColorArgb
                } else {
                    penColorArgb
                }

                swatches.forEach { colorArgb ->
                    ColorCircleSwatch(
                        colorArgb = colorArgb,
                        selected = selectedSwatch == colorArgb,
                        onClick = {
                            if (activeTool == CanvasTool.HIGHLIGHTER) {
                                onHighlighterColorChange(colorArgb)
                            } else {
                                onPenColorChange(colorArgb)
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                val sizePresets = if (activeTool == CanvasTool.HIGHLIGHTER) {
                    listOf(
                        SizePresetOption(14f, R.string.size_s, 7),
                        SizePresetOption(22f, R.string.size_m, 11),
                        SizePresetOption(32f, R.string.size_l, 15),
                        SizePresetOption(44f, R.string.size_xl, 20)
                    )
                } else {
                    listOf(
                        SizePresetOption(2.5f, R.string.size_s, 5),
                        SizePresetOption(5f, R.string.size_m, 9),
                        SizePresetOption(9f, R.string.size_l, 14),
                        SizePresetOption(14f, R.string.size_xl, 19)
                    )
                }
                val currentWidth = if (activeTool == CanvasTool.HIGHLIGHTER) {
                    highlighterWidth
                } else {
                    penWidth
                }

                sizePresets.forEachIndexed { idx, preset ->
                    val isSelected = kotlin.math.abs(currentWidth - preset.value) < 1.1f
                    val sizeName = stringResource(preset.labelRes)
                    SizeSymbolButton(
                        accessibleLabel = stringResource(R.string.cd_brush_size_option, sizeName),
                        dotSizeDp = preset.previewDotDp,
                        dotColor = if (isSelected && selectedSwatch == 0xFF1E293BL) {
                            Color.White
                        } else {
                            Color(selectedSwatch)
                        },
                        isHollowRing = false,
                        selected = isSelected,
                        onClick = {
                            if (activeTool == CanvasTool.HIGHLIGHTER) {
                                onHighlighterWidthChange(preset.value)
                            } else {
                                onPenWidthChange(preset.value)
                            }
                        },
                        testTag = "stroke_width_option_$idx"
                    )
                }
            }
        }
    }
}

@Composable
private fun PalmModeMenuItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    DropdownMenuItem(
        text = {
            Column(modifier = Modifier.widthIn(max = 240.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = if (selected) {
                        MaterialTheme.colorScheme.secondary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingIcon = {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        },
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    )
}

@Composable
private fun SizeSymbolButton(
    accessibleLabel: String,
    dotSizeDp: Int,
    dotColor: Color,
    isHollowRing: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .semantics {
                contentDescription = accessibleLabel
                this.selected = selected
            }
            .testTag(testTag)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = BorderStroke(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                }
            ),
            shadowElevation = if (selected) 3.dp else 0.dp,
            modifier = Modifier.size(38.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isHollowRing) {
                    Surface(
                        shape = CircleShape,
                        color = dotColor.copy(alpha = 0.22f),
                        border = BorderStroke(1.8.dp, dotColor),
                        modifier = Modifier.size(dotSizeDp.dp)
                    ) {}
                } else {
                    Surface(
                        shape = CircleShape,
                        color = dotColor,
                        border = if (selected && dotColor != Color.White) {
                            BorderStroke(1.dp, Color.White.copy(alpha = 0.65f))
                        } else {
                            null
                        },
                        modifier = Modifier.size(dotSizeDp.dp)
                    ) {}
                }
            }
        }
    }
}

private data class ToolDockEntry(
    val tool: CanvasTool,
    val icon: ImageVector,
    val labelRes: Int
)

@Composable
private fun ToolSelectorStrip(
    activeTool: CanvasTool,
    compactSingleRow: Boolean,
    onSelectTool: (CanvasTool) -> Unit,
    onPickImage: () -> Unit
) {
    val entries = remember {
        listOf(
            ToolDockEntry(CanvasTool.PEN, Icons.Default.Draw, R.string.tool_pen),
            ToolDockEntry(CanvasTool.HIGHLIGHTER, Icons.Default.BorderColor, R.string.tool_highlighter),
            ToolDockEntry(CanvasTool.ERASER, Icons.Default.AutoFixNormal, R.string.tool_eraser),
            ToolDockEntry(CanvasTool.SELECT, Icons.Default.AdsClick, R.string.tool_select),
            ToolDockEntry(CanvasTool.TEXT, Icons.Default.TextFields, R.string.tool_text),
            ToolDockEntry(CanvasTool.STICKY, Icons.Default.StickyNote2, R.string.tool_sticky),
            ToolDockEntry(CanvasTool.SHAPE, Icons.Outlined.Rectangle, R.string.tool_shape),
            ToolDockEntry(CanvasTool.IMAGE, Icons.Default.Image, R.string.tool_image),
            ToolDockEntry(CanvasTool.PAN, Icons.Default.PanTool, R.string.tool_pan)
        )
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (compactSingleRow) 2.dp else 4.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .testTag("floating_tool_dock")
    ) {
        entries.forEach { entry ->
            val selected = activeTool == entry.tool
            val label = stringResource(entry.labelRes)
            Surface(
                onClick = {
                    if (entry.tool == CanvasTool.IMAGE) {
                        onPickImage()
                    } else {
                        onSelectTool(entry.tool)
                    }
                },
                shape = RoundedCornerShape(14.dp),
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .semantics {
                        contentDescription = label
                        this.selected = selected
                    }
                    .testTag("tool_button_${entry.tool.name.lowercase()}")
            ) {
                if (compactSingleRow) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(
                            horizontal = if (selected) 11.dp else 9.dp,
                            vertical = 8.dp
                        )
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        if (selected) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = entry.icon,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}
