package com.example.viewmodel

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.canvas.CanvasExporter
import com.example.canvas.StrokeEraserEngine
import com.example.data.CanvasPreferences
import com.example.data.NoteRepository
import com.example.model.CanvasElement
import com.example.model.CanvasPattern
import com.example.model.CanvasSerializer
import com.example.model.CanvasTool
import com.example.model.ExportFormat
import com.example.model.ExportScope
import com.example.model.PalmRejectionMode
import com.example.model.PointFData
import com.example.model.ShapeKind
import com.example.model.StylusCustomBinding
import com.example.model.StylusShortcutPolicy
import com.example.ui.theme.HighlighterSwatches
import com.example.ui.theme.InkSwatches
import com.example.ui.theme.StickyNoteSwatches
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TextEditorDraft(
    val editingElementId: String? = null,
    val worldX: Float = 0f,
    val worldY: Float = 0f,
    val initialText: String = "",
    val fontSizeSp: Float = 18f,
    val textColorArgb: Long = InkSwatches.first(),
    val cardColorArgb: Long = StickyNoteSwatches.first(),
    val isSticky: Boolean = false
)

data class ToolSwitchCue(
    val tool: CanvasTool,
    val isTemporary: Boolean = false,
    val eventId: Long = 0L
)

data class CanvasUiState(
    val boardId: Long = 0L,
    val boardTitle: String = "",
    val pattern: CanvasPattern = CanvasPattern.DOTS,
    val elements: List<CanvasElement> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val activeTool: CanvasTool = CanvasTool.PEN,
    val lastDrawingTool: CanvasTool = CanvasTool.PEN,
    val isTemporaryEraserActive: Boolean = false,
    val toolSwitchCue: ToolSwitchCue? = null,
    val customStylusBinding: StylusCustomBinding = StylusCustomBinding(),
    val penColorArgb: Long = InkSwatches.first(),
    val penStrokeWidth: Float = 5f,
    val highlighterColorArgb: Long = HighlighterSwatches.first(),
    val highlighterStrokeWidth: Float = 22f,
    val eraserRadiusDp: Float = 16f,
    val palmRejectionMode: PalmRejectionMode = PalmRejectionMode.AUTO,
    val isStylusSessionActive: Boolean = false,
    val activeShapeKind: ShapeKind = ShapeKind.RECTANGLE,
    val selectedElementId: String? = null,
    val cameraScale: Float = 1f,
    val cameraPanX: Float = 0f,
    val cameraPanY: Float = 0f,
    val viewportWidthPx: Float = 1080f,
    val viewportHeightPx: Float = 1920f,
    val bitmapVersion: Int = 0,
    val textEditorDraft: TextEditorDraft? = null,
    val croppingImageElement: CanvasElement.ImageItem? = null,
    val isCroppingInProgress: Boolean = false,
    val isExportSheetOpen: Boolean = false,
    val exportFormat: ExportFormat = ExportFormat.PDF,
    val exportScope: ExportScope = ExportScope.FULL_CONTENT,
    val exportPreviewBitmap: Bitmap? = null,
    val isGeneratingPreview: Boolean = false,
    val isExporting: Boolean = false,
    val isLoading: Boolean = true
)

class CanvasViewModel(
    private val repository: NoteRepository,
    private val preferences: CanvasPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CanvasUiState(
            palmRejectionMode = preferences.getPalmRejectionMode(),
            customStylusBinding = preferences.getStylusCustomBinding()
        )
    )
    val uiState: StateFlow<CanvasUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<List<CanvasElement>>()
    private val redoStack = ArrayDeque<List<CanvasElement>>()
    private val saveMutex = Mutex()
    private var autoSaveJob: Job? = null
    private var gestureSnapshotSaved = false
    private var cueSequence = 0L

    val mediaStorage get() = repository.mediaStorage

    fun loadBoard(boardId: Long) {
        if (_uiState.value.boardId == boardId && !_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, boardId = boardId) }
            undoStack.clear()
            redoStack.clear()

            val summary = runCatching { repository.getBoardSummaryById(boardId) }.getOrNull()
            if (summary == null) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }

            val decodedElements = runCatching {
                repository.loadBoardElements(boardId)
            }.getOrDefault(emptyList())
            val pattern = CanvasPattern.fromName(summary.patternName)

            _uiState.update {
                it.copy(
                    boardId = summary.id,
                    boardTitle = summary.title,
                    pattern = pattern,
                    elements = decodedElements,
                    canUndo = false,
                    canRedo = false,
                    selectedElementId = null,
                    cameraScale = summary.cameraScale.coerceIn(0.25f, 4.0f),
                    cameraPanX = summary.cameraPanX,
                    cameraPanY = summary.cameraPanY,
                    isLoading = false
                )
            }

            val imagePaths = decodedElements
                .filterIsInstance<CanvasElement.ImageItem>()
                .map { it.filePath }
            if (imagePaths.isNotEmpty()) {
                val loaded = runCatching {
                    repository.mediaStorage.preloadBitmaps(imagePaths)
                }.getOrDefault(0)
                if (loaded > 0) {
                    _uiState.update { it.copy(bitmapVersion = it.bitmapVersion + 1) }
                }
            }
        }
    }

    fun updateViewportSize(widthPx: Float, heightPx: Float) {
        if (widthPx <= 0f || heightPx <= 0f) return
        val current = _uiState.value
        val wasUninitialized =
            current.cameraPanX == 0f && current.cameraPanY == 0f && current.elements.isEmpty()
        _uiState.update {
            it.copy(
                viewportWidthPx = widthPx,
                viewportHeightPx = heightPx,
                cameraPanX = if (wasUninitialized) widthPx * 0.1f else it.cameraPanX,
                cameraPanY = if (wasUninitialized) heightPx * 0.1f else it.cameraPanY
            )
        }
    }

    fun setActiveTool(tool: CanvasTool) {
        _uiState.update {
            val updatedLastDrawing = if (tool == CanvasTool.PEN || tool == CanvasTool.HIGHLIGHTER) {
                tool
            } else {
                it.lastDrawingTool
            }
            it.copy(
                activeTool = tool,
                lastDrawingTool = updatedLastDrawing,
                isTemporaryEraserActive = false,
                selectedElementId = if (tool == CanvasTool.SELECT) it.selectedElementId else null
            )
        }
    }

    fun togglePenAndEraserFromStylus() {
        _uiState.update { current ->
            val (nextTool, rememberedDrawing) = StylusShortcutPolicy.resolveToggleTool(
                activeTool = current.activeTool,
                lastDrawingTool = current.lastDrawingTool
            )
            cueSequence++
            current.copy(
                activeTool = nextTool,
                lastDrawingTool = rememberedDrawing,
                isTemporaryEraserActive = false,
                selectedElementId = null,
                toolSwitchCue = ToolSwitchCue(
                    tool = nextTool,
                    isTemporary = false,
                    eventId = cueSequence
                )
            )
        }
    }

    fun setTemporaryEraserFromStylus(active: Boolean) {
        _uiState.update { current ->
            if (current.isTemporaryEraserActive == active) return@update current
            if (active && current.activeTool != CanvasTool.ERASER) {
                cueSequence++
                current.copy(
                    isTemporaryEraserActive = true,
                    toolSwitchCue = ToolSwitchCue(
                        tool = CanvasTool.ERASER,
                        isTemporary = true,
                        eventId = cueSequence
                    )
                )
            } else {
                current.copy(isTemporaryEraserActive = false)
            }
        }
    }

    fun saveCustomStylusBinding(binding: StylusCustomBinding) {
        preferences.setStylusCustomBinding(binding)
        _uiState.update { it.copy(customStylusBinding = binding) }
    }

    fun clearCustomStylusBinding() {
        preferences.clearStylusCustomBinding()
        _uiState.update { it.copy(customStylusBinding = StylusCustomBinding()) }
    }

    fun setPenColor(colorArgb: Long) {
        _uiState.update { it.copy(penColorArgb = colorArgb) }
    }

    fun setPenWidth(width: Float) {
        _uiState.update { it.copy(penStrokeWidth = width) }
    }

    fun setHighlighterColor(colorArgb: Long) {
        _uiState.update { it.copy(highlighterColorArgb = colorArgb) }
    }

    fun setHighlighterWidth(width: Float) {
        _uiState.update { it.copy(highlighterStrokeWidth = width) }
    }

    fun setEraserRadiusDp(radiusDp: Float) {
        _uiState.update { it.copy(eraserRadiusDp = radiusDp) }
    }

    fun setPalmRejectionMode(mode: PalmRejectionMode) {
        preferences.setPalmRejectionMode(mode)
        _uiState.update {
            it.copy(
                palmRejectionMode = mode,
                isStylusSessionActive = if (mode == PalmRejectionMode.OFF) false else it.isStylusSessionActive
            )
        }
    }

    fun onStylusDetected(): Boolean {
        val current = _uiState.value
        if (current.palmRejectionMode == PalmRejectionMode.AUTO && !current.isStylusSessionActive) {
            _uiState.update { it.copy(isStylusSessionActive = true) }
            return true
        }
        return false
    }

    fun setShapeKind(kind: ShapeKind) {
        _uiState.update { it.copy(activeShapeKind = kind) }
    }

    fun cycleCanvasPattern() {
        val next = when (_uiState.value.pattern) {
            CanvasPattern.DOTS -> CanvasPattern.GRID
            CanvasPattern.GRID -> CanvasPattern.BLANK
            CanvasPattern.BLANK -> CanvasPattern.DOTS
        }
        _uiState.update { it.copy(pattern = next) }
        scheduleAutoSave()
    }

    fun updateBoardTitleAndPattern(newTitle: String, newPattern: CanvasPattern) {
        val trimmed = newTitle.trim().ifBlank { _uiState.value.boardTitle }
        _uiState.update {
            it.copy(boardTitle = trimmed, pattern = newPattern)
        }
        scheduleAutoSave()
    }

    fun updateCamera(scale: Float, panX: Float, panY: Float) {
        _uiState.update {
            it.copy(
                cameraScale = scale.coerceIn(0.25f, 4.0f),
                cameraPanX = panX,
                cameraPanY = panY
            )
        }
        scheduleAutoSave()
    }

    fun fitCameraToContent() {
        val state = _uiState.value
        val viewW = max(state.viewportWidthPx, 360f)
        val viewH = max(state.viewportHeightPx, 640f)

        if (state.elements.isEmpty()) {
            updateCamera(1f, 0f, 0f)
            return
        }

        val bounds = CanvasExporter.computeExportWorldRect(
            elements = state.elements,
            scope = ExportScope.FULL_CONTENT,
            viewportWidthPx = viewW,
            viewportHeightPx = viewH,
            cameraScale = state.cameraScale,
            cameraPanX = state.cameraPanX,
            cameraPanY = state.cameraPanY
        )

        val paddedViewW = viewW * 0.82f
        val paddedViewH = viewH * 0.72f
        val targetScale = min(
            paddedViewW / max(bounds.width, 120f),
            paddedViewH / max(bounds.height, 120f)
        ).coerceIn(0.3f, 2.0f)

        val newPanX = (viewW - bounds.width * targetScale) / 2f - bounds.left * targetScale
        val newPanY = (viewH - bounds.height * targetScale) / 2f - bounds.top * targetScale
        updateCamera(targetScale, newPanX, newPanY)
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        val current = _uiState.value.elements
        val previous = undoStack.removeLast()
        redoStack.addLast(current)
        _uiState.update {
            it.copy(
                elements = previous,
                selectedElementId = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        scheduleAutoSave()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val current = _uiState.value.elements
        val next = redoStack.removeLast()
        undoStack.addLast(current)
        _uiState.update {
            it.copy(
                elements = next,
                selectedElementId = null,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty()
            )
        }
        scheduleAutoSave()
    }

    private fun recordUndoSnapshot() {
        val current = _uiState.value.elements
        undoStack.addLast(current)
        if (undoStack.size > 25) {
            undoStack.removeFirst()
        }
        redoStack.clear()
        _uiState.update {
            it.copy(canUndo = undoStack.isNotEmpty(), canRedo = false)
        }
    }

    fun commitStroke(points: List<PointFData>, isHighlighter: Boolean) {
        if (points.isEmpty()) return
        val state = _uiState.value
        val stroke = CanvasElement.Stroke(
            id = newElementId("stk"),
            points = points,
            colorArgb = if (isHighlighter) state.highlighterColorArgb else state.penColorArgb,
            strokeWidth = if (isHighlighter) state.highlighterStrokeWidth else state.penStrokeWidth,
            isHighlighter = isHighlighter
        )
        recordUndoSnapshot()
        _uiState.update {
            it.copy(elements = it.elements + stroke)
        }
        scheduleAutoSave()
    }

    fun commitShape(startX: Float, startY: Float, endX: Float, endY: Float) {
        val dx = kotlin.math.abs(endX - startX)
        val dy = kotlin.math.abs(endY - startY)
        if (dx < 8f && dy < 8f) return

        val state = _uiState.value
        val shape = CanvasElement.Shape(
            id = newElementId("shp"),
            kind = state.activeShapeKind,
            startX = startX,
            startY = startY,
            endX = endX,
            endY = endY,
            colorArgb = state.penColorArgb,
            strokeWidth = state.penStrokeWidth
        )
        recordUndoSnapshot()
        _uiState.update {
            it.copy(elements = it.elements + shape)
        }
        scheduleAutoSave()
    }

    fun beginContinuousMutationGesture() {
        gestureSnapshotSaved = false
    }

    fun endContinuousMutationGesture() {
        if (gestureSnapshotSaved) {
            gestureSnapshotSaved = false
            scheduleAutoSave()
        }
    }

    fun cancelContinuousMutationGesture() {
        if (gestureSnapshotSaved && undoStack.isNotEmpty()) {
            val restored = undoStack.removeLast()
            gestureSnapshotSaved = false
            _uiState.update {
                it.copy(
                    elements = restored,
                    selectedElementId = null,
                    canUndo = undoStack.isNotEmpty()
                )
            }
        } else {
            gestureSnapshotSaved = false
        }
    }

    fun eraseSegment(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        eraserRadiusWorld: Float
    ) {
        val currentElements = _uiState.value.elements
        if (currentElements.isEmpty()) return

        val (updatedElements, wasModified) = StrokeEraserEngine.eraseAlongSegment(
            elements = currentElements,
            fromX = fromX,
            fromY = fromY,
            toX = toX,
            toY = toY,
            eraserRadiusWorld = eraserRadiusWorld,
            idGenerator = ::newElementId
        )

        if (wasModified) {
            if (!gestureSnapshotSaved) {
                recordUndoSnapshot()
                gestureSnapshotSaved = true
            }
            _uiState.update { it.copy(elements = updatedElements, selectedElementId = null) }
        }
    }

    fun selectElement(elementId: String?) {
        _uiState.update { it.copy(selectedElementId = elementId) }
    }

    fun moveSelectedElement(dxWorld: Float, dyWorld: Float) {
        val selectedId = _uiState.value.selectedElementId ?: return
        if (dxWorld == 0f && dyWorld == 0f) return
        if (!gestureSnapshotSaved) {
            recordUndoSnapshot()
            gestureSnapshotSaved = true
        }
        _uiState.update { state ->
            state.copy(
                elements = state.elements.map { el ->
                    if (el.id == selectedId) el.translatedBy(dxWorld, dyWorld) else el
                }
            )
        }
    }

    fun resizeSelectedElement(newRightWorld: Float, newBottomWorld: Float) {
        val selectedId = _uiState.value.selectedElementId ?: return
        if (!gestureSnapshotSaved) {
            recordUndoSnapshot()
            gestureSnapshotSaved = true
        }
        _uiState.update { state ->
            state.copy(
                elements = state.elements.map { el ->
                    if (el.id == selectedId) el.resizedByHandle(newRightWorld, newBottomWorld) else el
                }
            )
        }
    }

    fun deleteSelectedElement() {
        val selectedId = _uiState.value.selectedElementId ?: return
        recordUndoSnapshot()
        _uiState.update { state ->
            state.copy(
                elements = state.elements.filterNot { it.id == selectedId },
                selectedElementId = null
            )
        }
        scheduleAutoSave()
    }

    fun duplicateSelectedElement() {
        val state = _uiState.value
        val selected = state.elements.firstOrNull { it.id == state.selectedElementId } ?: return
        val newId = newElementId("dup")
        val duplicated = when (selected) {
            is CanvasElement.Stroke -> selected.copy(id = newId).translatedBy(28f, 28f)
            is CanvasElement.Shape -> selected.copy(id = newId).translatedBy(28f, 28f)
            is CanvasElement.TextNote -> selected.copy(id = newId).translatedBy(28f, 28f)
            is CanvasElement.ImageItem -> selected.copy(id = newId).translatedBy(28f, 28f)
        }
        recordUndoSnapshot()
        _uiState.update {
            it.copy(
                elements = it.elements + duplicated,
                selectedElementId = newId
            )
        }
        scheduleAutoSave()
    }

    fun bringSelectedToFront() {
        val state = _uiState.value
        val selected = state.elements.firstOrNull { it.id == state.selectedElementId } ?: return
        if (state.elements.lastOrNull()?.id == selected.id) return
        recordUndoSnapshot()
        _uiState.update {
            it.copy(elements = it.elements.filterNot { el -> el.id == selected.id } + selected)
        }
        scheduleAutoSave()
    }

    fun openNewTextEditorAt(worldX: Float, worldY: Float, isSticky: Boolean) {
        val state = _uiState.value
        _uiState.update {
            it.copy(
                textEditorDraft = TextEditorDraft(
                    editingElementId = null,
                    worldX = worldX,
                    worldY = worldY,
                    initialText = "",
                    fontSizeSp = 18f,
                    textColorArgb = if (isSticky) InkSwatches.first() else state.penColorArgb,
                    cardColorArgb = StickyNoteSwatches.first(),
                    isSticky = isSticky
                )
            )
        }
    }

    fun openEditorForElement(element: CanvasElement.TextNote) {
        _uiState.update {
            it.copy(
                textEditorDraft = TextEditorDraft(
                    editingElementId = element.id,
                    worldX = element.x,
                    worldY = element.y,
                    initialText = element.text,
                    fontSizeSp = element.fontSizeSp,
                    textColorArgb = element.textColorArgb,
                    cardColorArgb = element.cardColorArgb,
                    isSticky = element.isSticky
                )
            )
        }
    }

    fun dismissTextEditor() {
        _uiState.update { it.copy(textEditorDraft = null) }
    }

    fun confirmTextNote(
        text: String,
        fontSizeSp: Float,
        textColorArgb: Long,
        cardColorArgb: Long
    ) {
        val draft = _uiState.value.textEditorDraft ?: return
        val cleaned = text.trim()
        if (cleaned.isEmpty()) {
            dismissTextEditor()
            return
        }

        recordUndoSnapshot()
        if (draft.editingElementId != null) {
            _uiState.update { state ->
                state.copy(
                    elements = state.elements.map { el ->
                        if (el is CanvasElement.TextNote && el.id == draft.editingElementId) {
                            val minHeight = estimateNoteHeight(cleaned, el.width, fontSizeSp, el.isSticky)
                            el.copy(
                                text = cleaned,
                                fontSizeSp = fontSizeSp,
                                textColorArgb = textColorArgb,
                                cardColorArgb = cardColorArgb,
                                height = max(el.height, minHeight)
                            )
                        } else {
                            el
                        }
                    },
                    textEditorDraft = null
                )
            }
        } else {
            val defaultWidth = if (draft.isSticky) 230f else 250f
            val defaultHeight = estimateNoteHeight(cleaned, defaultWidth, fontSizeSp, draft.isSticky)
            val newId = newElementId(if (draft.isSticky) "stk_note" else "txt")
            val note = CanvasElement.TextNote(
                id = newId,
                text = cleaned,
                x = draft.worldX - defaultWidth / 2f,
                y = draft.worldY - defaultHeight / 3f,
                width = defaultWidth,
                height = defaultHeight,
                fontSizeSp = fontSizeSp,
                textColorArgb = textColorArgb,
                cardColorArgb = cardColorArgb,
                isSticky = draft.isSticky
            )
            _uiState.update { state ->
                state.copy(
                    elements = state.elements + note,
                    activeTool = CanvasTool.SELECT,
                    selectedElementId = newId,
                    textEditorDraft = null
                )
            }
        }
        scheduleAutoSave()
    }

    private fun estimateNoteHeight(
        text: String,
        width: Float,
        fontSizeSp: Float,
        isSticky: Boolean
    ): Float {
        val charsPerLine = max(10, ((width - 36f) / (fontSizeSp * 0.55f)).toInt())
        var lineCount = 0
        for (paragraph in text.split('\n')) {
            lineCount += max(1, (paragraph.length + charsPerLine - 1) / charsPerLine)
        }
        val textHeight = lineCount * (fontSizeSp * 1.38f) + (if (isSticky) 48f else 24f)
        return if (isSticky) max(165f, textHeight) else max(68f, textHeight)
    }

    fun importImage(uri: Uri, onError: () -> Unit) {
        viewModelScope.launch {
            val imported = repository.importImage(uri)
            if (imported == null) {
                onError()
                return@launch
            }
            val state = _uiState.value
            val safeScale = state.cameraScale.coerceAtLeast(0.25f)
            val centerWorldX = (state.viewportWidthPx / 2f - state.cameraPanX) / safeScale
            val centerWorldY = (state.viewportHeightPx / 2f - state.cameraPanY) / safeScale

            val targetWidth = 320f
            val targetHeight = (targetWidth / imported.aspectRatio.coerceAtLeast(0.2f))
                .coerceIn(100f, 520f)
            val actualWidth = targetHeight * imported.aspectRatio.coerceAtLeast(0.2f)

            val newId = newElementId("img")
            val imageItem = CanvasElement.ImageItem(
                id = newId,
                filePath = imported.filePath,
                x = centerWorldX - actualWidth / 2f,
                y = centerWorldY - targetHeight / 2f,
                width = actualWidth,
                height = targetHeight,
                aspectRatio = imported.aspectRatio
            )
            recordUndoSnapshot()
            _uiState.update {
                it.copy(
                    elements = it.elements + imageItem,
                    activeTool = CanvasTool.SELECT,
                    selectedElementId = newId,
                    bitmapVersion = it.bitmapVersion + 1
                )
            }
            scheduleAutoSave()
        }
    }

    fun openCropForSelectedImage() {
        val state = _uiState.value
        val img = state.elements.firstOrNull { it.id == state.selectedElementId } as? CanvasElement.ImageItem
            ?: return
        _uiState.update { it.copy(croppingImageElement = img) }
    }

    fun dismissImageCropper() {
        _uiState.update { it.copy(croppingImageElement = null, isCroppingInProgress = false) }
    }

    fun applyRectangleCropToImage(normRect: Rect, onError: () -> Unit) {
        val target = _uiState.value.croppingImageElement ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCroppingInProgress = true) }
            val result = repository.mediaStorage.cropImageRectangle(target.filePath, normRect)
            if (result == null) {
                _uiState.update { it.copy(isCroppingInProgress = false) }
                onError()
                return@launch
            }
            applyCroppedResult(target, result, isCustomCutout = target.isCustomCutout)
        }
    }

    fun applyCustomCropToImage(normPoints: List<PointFData>, onError: () -> Unit) {
        val target = _uiState.value.croppingImageElement ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isCroppingInProgress = true) }
            val result = repository.mediaStorage.cropImageCustomPath(target.filePath, normPoints)
            if (result == null) {
                _uiState.update { it.copy(isCroppingInProgress = false) }
                onError()
                return@launch
            }
            applyCroppedResult(target, result, isCustomCutout = true)
        }
    }

    private fun applyCroppedResult(
        target: CanvasElement.ImageItem,
        cropped: com.example.data.ImportedImageResult,
        isCustomCutout: Boolean
    ) {
        val norm = cropped.normalizedCropBounds
        val newX = target.x + norm.left * target.width
        val newY = target.y + norm.top * target.height
        val newWidth = max(56f, norm.width * target.width)
        val newHeight = max(56f, newWidth / cropped.aspectRatio.coerceAtLeast(0.1f))

        recordUndoSnapshot()
        _uiState.update { state ->
            state.copy(
                elements = state.elements.map { el ->
                    if (el.id == target.id && el is CanvasElement.ImageItem) {
                        el.copy(
                            filePath = cropped.filePath,
                            x = newX,
                            y = newY,
                            width = newWidth,
                            height = newHeight,
                            aspectRatio = cropped.aspectRatio,
                            isCustomCutout = isCustomCutout
                        )
                    } else {
                        el
                    }
                },
                croppingImageElement = null,
                isCroppingInProgress = false,
                bitmapVersion = state.bitmapVersion + 1
            )
        }
        scheduleAutoSave()
    }

    fun openExportSheet(context: Context) {
        _uiState.update {
            it.copy(isExportSheetOpen = true, selectedElementId = null)
        }
        refreshExportPreview(context)
    }

    fun closeExportSheet() {
        _uiState.update {
            it.copy(isExportSheetOpen = false, exportPreviewBitmap = null)
        }
    }

    fun setExportFormat(format: ExportFormat) {
        _uiState.update { it.copy(exportFormat = format) }
    }

    fun setExportScope(context: Context, scope: ExportScope) {
        if (_uiState.value.exportScope == scope) return
        _uiState.update { it.copy(exportScope = scope) }
        refreshExportPreview(context)
    }

    private fun refreshExportPreview(context: Context) {
        val snapshot = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isGeneratingPreview = true) }
            val exportRect = CanvasExporter.computeExportWorldRect(
                elements = snapshot.elements,
                scope = snapshot.exportScope,
                viewportWidthPx = snapshot.viewportWidthPx,
                viewportHeightPx = snapshot.viewportHeightPx,
                cameraScale = snapshot.cameraScale,
                cameraPanX = snapshot.cameraPanX,
                cameraPanY = snapshot.cameraPanY
            )
            val preview = CanvasExporter.generatePreviewBitmap(
                context = context,
                elements = snapshot.elements,
                pattern = snapshot.pattern,
                exportWorldRect = exportRect,
                mediaStorage = repository.mediaStorage
            )
            _uiState.update {
                it.copy(exportPreviewBitmap = preview, isGeneratingPreview = false)
            }
        }
    }

    fun saveExportToUri(
        context: Context,
        destinationUri: Uri,
        onComplete: (Boolean) -> Unit
    ) {
        val snapshot = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val exportRect = CanvasExporter.computeExportWorldRect(
                elements = snapshot.elements,
                scope = snapshot.exportScope,
                viewportWidthPx = snapshot.viewportWidthPx,
                viewportHeightPx = snapshot.viewportHeightPx,
                cameraScale = snapshot.cameraScale,
                cameraPanX = snapshot.cameraPanX,
                cameraPanY = snapshot.cameraPanY
            )
            val success = CanvasExporter.writeExportToUri(
                context = context,
                destinationUri = destinationUri,
                format = snapshot.exportFormat,
                elements = snapshot.elements,
                pattern = snapshot.pattern,
                exportWorldRect = exportRect,
                mediaStorage = repository.mediaStorage
            )
            _uiState.update {
                it.copy(isExporting = false, isExportSheetOpen = if (success) false else true)
            }
            onComplete(success)
        }
    }

    fun shareExport(
        context: Context,
        onIntentReady: (Intent) -> Unit,
        onError: () -> Unit
    ) {
        val snapshot = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isExporting = true) }
            val exportRect = CanvasExporter.computeExportWorldRect(
                elements = snapshot.elements,
                scope = snapshot.exportScope,
                viewportWidthPx = snapshot.viewportWidthPx,
                viewportHeightPx = snapshot.viewportHeightPx,
                cameraScale = snapshot.cameraScale,
                cameraPanX = snapshot.cameraPanX,
                cameraPanY = snapshot.cameraPanY
            )
            val intent = CanvasExporter.createShareIntent(
                context = context,
                boardTitle = snapshot.boardTitle,
                format = snapshot.exportFormat,
                elements = snapshot.elements,
                pattern = snapshot.pattern,
                exportWorldRect = exportRect,
                mediaStorage = repository.mediaStorage
            )
            _uiState.update { it.copy(isExporting = false) }
            if (intent != null) {
                onIntentReady(intent)
            } else {
                onError()
            }
        }
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(1200L)
            persistWorkspaceNow()
        }
    }

    fun flushPendingSaveAsync() {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            persistWorkspaceNow()
        }
    }

    fun flushAndExit(onSaved: () -> Unit) {
        autoSaveJob?.cancel()
        viewModelScope.launch {
            persistWorkspaceNow()
            onSaved()
        }
    }

    private suspend fun persistWorkspaceNow() {
        saveMutex.withLock {
            val state = _uiState.value
            if (state.boardId <= 0L || state.isLoading) return
            runCatching {
                repository.saveBoardWorkspace(
                    boardId = state.boardId,
                    title = state.boardTitle,
                    pattern = state.pattern,
                    elements = state.elements,
                    cameraScale = state.cameraScale,
                    cameraPanX = state.cameraPanX,
                    cameraPanY = state.cameraPanY
                )
            }
        }
    }

    private fun newElementId(prefix: String): String =
        "${prefix}_${UUID.randomUUID().toString().take(8)}"

    companion object {
        fun provideFactory(
            repository: NoteRepository,
            preferences: CanvasPreferences
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CanvasViewModel(repository, preferences) as T
                }
            }
    }
}
