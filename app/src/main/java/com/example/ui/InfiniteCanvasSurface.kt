package com.example.ui

import android.graphics.Bitmap
import android.graphics.Typeface
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.canvas.CanvasRenderer
import com.example.model.CanvasElement
import com.example.model.CanvasPattern
import com.example.model.CanvasTool
import com.example.model.PalmRejectionMode
import com.example.model.PalmRejectionPolicy
import com.example.model.PointFData
import com.example.model.ShapeKind
import com.example.model.SingleTouchBehavior
import kotlin.math.hypot

private enum class SelectGestureAction {
    NONE,
    MOVE_ELEMENT,
    RESIZE_ELEMENT,
    PAN_EMPTY_SPACE
}

private enum class GestureChannel {
    STYLUS,
    TOUCH_TOOL,
    TOUCH_PAN,
    TOUCH_REJECTED,
    MULTI_TOUCH_NAV
}

private class StylusHoverTracker {
    @Volatile
    var isHovering: Boolean = false

    @Volatile
    var lastStylusActivityMs: Long = 0L
}

private fun PointerInputChange.isStylusLike(): Boolean =
    type == PointerType.Stylus || type == PointerType.Eraser

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun InfiniteCanvasSurface(
    pattern: CanvasPattern,
    elements: List<CanvasElement>,
    activeTool: CanvasTool,
    penColorArgb: Long,
    penStrokeWidth: Float,
    highlighterColorArgb: Long,
    highlighterStrokeWidth: Float,
    eraserRadiusDp: Float,
    palmRejectionMode: PalmRejectionMode,
    isStylusSessionActive: Boolean,
    activeShapeKind: ShapeKind,
    selectedElementId: String?,
    cameraScale: Float,
    cameraPanX: Float,
    cameraPanY: Float,
    bitmapVersion: Int,
    bitmapProvider: (String) -> Bitmap?,
    onViewportSizeChanged: (Float, Float) -> Unit,
    onCameraTransform: (scale: Float, panX: Float, panY: Float) -> Unit,
    onCommitStroke: (points: List<PointFData>, isHighlighter: Boolean) -> Unit,
    onCommitShape: (startX: Float, startY: Float, endX: Float, endY: Float) -> Unit,
    onBeginContinuousGesture: () -> Unit,
    onEndContinuousGesture: () -> Unit,
    onCancelContinuousGesture: () -> Unit,
    onEraseSegment: (fromX: Float, fromY: Float, toX: Float, toY: Float, radiusWorld: Float) -> Unit,
    onSelectElement: (String?) -> Unit,
    onMoveSelectedElement: (dxWorld: Float, dyWorld: Float) -> Unit,
    onResizeSelectedElement: (newRightWorld: Float, newBottomWorld: Float) -> Unit,
    onTapPlaceNote: (worldX: Float, worldY: Float, isSticky: Boolean) -> Unit,
    onEditTextElement: (CanvasElement.TextNote) -> Unit,
    onStylusDetected: () -> Unit,
    isStylusButtonHeldProvider: () -> Boolean = { false },
    onTemporaryEraserChange: (Boolean) -> Unit = {},
    onStylusTouchStateChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val canvasTypeface: Typeface? = remember(context) {
        runCatching { ResourcesCompat.getFont(context, R.font.plus_jakarta_sans) }.getOrNull()
    }

    // Live stroke mutable buffer + invalidation tick: zero List copies per pointer move!
    val liveStrokePoints = remember { ArrayList<PointFData>(256) }
    var liveDraftElement by remember { mutableStateOf<CanvasElement?>(null) }
    var liveDraftTick by remember { mutableLongStateOf(0L) }
    var eraserScreenCursor by remember { mutableStateOf<Offset?>(null) }
    var lastTappedElementId by remember { mutableStateOf<String?>(null) }
    var lastTapTimestampMs by remember { mutableLongStateOf(0L) }
    val stylusHoverTracker = remember { StylusHoverTracker() }

    val latestTool by rememberUpdatedState(activeTool)
    val latestElements by rememberUpdatedState(elements)
    val latestSelectedId by rememberUpdatedState(selectedElementId)
    val latestScale by rememberUpdatedState(cameraScale)
    val latestPanX by rememberUpdatedState(cameraPanX)
    val latestPanY by rememberUpdatedState(cameraPanY)
    val latestPenColor by rememberUpdatedState(penColorArgb)
    val latestPenWidth by rememberUpdatedState(penStrokeWidth)
    val latestHiColor by rememberUpdatedState(highlighterColorArgb)
    val latestHiWidth by rememberUpdatedState(highlighterStrokeWidth)
    val latestEraserRadiusDp by rememberUpdatedState(eraserRadiusDp)
    val latestPalmMode by rememberUpdatedState(palmRejectionMode)
    val latestStylusSession by rememberUpdatedState(isStylusSessionActive)
    val latestShapeKind by rememberUpdatedState(activeShapeKind)

    val latestCameraTransform by rememberUpdatedState(onCameraTransform)
    val latestCommitStroke by rememberUpdatedState(onCommitStroke)
    val latestCommitShape by rememberUpdatedState(onCommitShape)
    val latestBeginContinuous by rememberUpdatedState(onBeginContinuousGesture)
    val latestEndContinuous by rememberUpdatedState(onEndContinuousGesture)
    val latestCancelContinuous by rememberUpdatedState(onCancelContinuousGesture)
    val latestEraseSegment by rememberUpdatedState(onEraseSegment)
    val latestSelectElement by rememberUpdatedState(onSelectElement)
    val latestMoveSelected by rememberUpdatedState(onMoveSelectedElement)
    val latestResizeSelected by rememberUpdatedState(onResizeSelectedElement)
    val latestTapPlaceNote by rememberUpdatedState(onTapPlaceNote)
    val latestEditTextElement by rememberUpdatedState(onEditTextElement)
    val latestOnStylusDetected by rememberUpdatedState(onStylusDetected)
    val latestIsStylusButtonHeld by rememberUpdatedState(isStylusButtonHeldProvider)
    val latestOnTemporaryEraserChange by rememberUpdatedState(onTemporaryEraserChange)
    val latestOnStylusTouchStateChange by rememberUpdatedState(onStylusTouchStateChange)

    Box(modifier = modifier.fillMaxSize()) {
        // Layer A (Bottom): GPU-cached committed scene.
        // Only redraws when committed elements, camera transform, pattern, or selection change—
        // NEVER re-rasterized during live stylus strokes!
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer()
        ) {
            val unusedVersionRead = bitmapVersion
            if (unusedVersionRead < 0) return@Canvas

            val safeScale = cameraScale.coerceIn(0.25f, 4.0f)
            val worldViewport = Rect(
                left = -cameraPanX / safeScale,
                top = -cameraPanY / safeScale,
                right = (size.width - cameraPanX) / safeScale,
                bottom = (size.height - cameraPanY) / safeScale
            )

            val nativeCanvas = drawContext.canvas.nativeCanvas
            nativeCanvas.save()
            nativeCanvas.translate(cameraPanX, cameraPanY)
            nativeCanvas.scale(safeScale, safeScale)

            CanvasRenderer.renderCommittedScene(
                canvas = nativeCanvas,
                worldViewport = worldViewport,
                pattern = pattern,
                elements = elements,
                selectedElementId = selectedElementId,
                cameraScale = safeScale,
                showSelectionOverlay = true,
                typeface = canvasTypeface,
                preferVectorPattern = false,
                bitmapProvider = bitmapProvider
            )

            nativeCanvas.restore()
        }

        // Layer B (Top): Ultra-low-latency live ink overlay & stylus/touch gesture router
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("infinite_canvas_surface")
                .onSizeChanged { intSize ->
                    onViewportSizeChanged(intSize.width.toFloat(), intSize.height.toFloat())
                }
                // Passive stylus hover/approach detector:
                // 1) Activates Auto Palm Rejection before the palm touches glass.
                // 2) Maintains Hover-Lock window so resting palms never pan the canvas while writing.
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val stylusChange = event.changes.firstOrNull { it.isStylusLike() }
                            if (stylusChange != null) {
                                stylusHoverTracker.lastStylusActivityMs = SystemClock.uptimeMillis()
                                stylusHoverTracker.isHovering = when (event.type) {
                                    PointerEventType.Exit -> false
                                    PointerEventType.Enter,
                                    PointerEventType.Move,
                                    PointerEventType.Press -> true
                                    PointerEventType.Release -> stylusChange.pressed
                                    else -> stylusHoverTracker.isHovering
                                }
                                latestOnStylusDetected()
                            }
                        }
                    }
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        var localScale = latestScale.coerceIn(0.25f, 4.0f)
                        var localPanX = latestPanX
                        var localPanY = latestPanY

                        fun syncLocalCamera() {
                            localScale = latestScale.coerceIn(0.25f, 4.0f)
                            localPanX = latestPanX
                            localPanY = latestPanY
                        }

                        fun toWorld(screen: Offset): Pair<Float, Float> = Pair(
                            (screen.x - localPanX) / localScale,
                            (screen.y - localPanY) / localScale
                        )

                        var channel: GestureChannel
                        var activePointerId: PointerId? = null
                        var activeGestureTool: CanvasTool = latestTool
                        var activePointerIsStylus = false
                        var smoothedPressure = -1f
                        var startScreen = down.position
                        var prevSingleScreen = startScreen
                        var panSlopPassed = false
                        var panAccumulatedPx = 0f
                        var startWorld = PointFData(0f, 0f)
                        var prevEraseWorld = PointFData(0f, 0f)
                        var totalSingleDragPx = 0f
                        var selectAction = SelectGestureAction.NONE
                        var tappedCandidateNote: CanvasElement.TextNote? = null
                        var isTemporaryEraserActive = false

                        fun resolveStylusEffectiveTool(pointer: PointerInputChange): Pair<CanvasTool, Boolean> {
                            val buttonOrTailErase =
                                pointer.type == PointerType.Eraser || latestIsStylusButtonHeld()
                            val isTemp = buttonOrTailErase && latestTool != CanvasTool.ERASER
                            val tool = if (buttonOrTailErase) CanvasTool.ERASER else latestTool
                            return tool to isTemp
                        }

                        fun updateTemporaryEraserFlag(active: Boolean) {
                            if (isTemporaryEraserActive != active) {
                                isTemporaryEraserActive = active
                                latestOnTemporaryEraserChange(active)
                            }
                        }

                        fun smoothStylusPressure(rawPressure: Float, prev: Float): Float {
                            val safeRaw = if (rawPressure > 0.005f) {
                                rawPressure.coerceIn(0.05f, 1.0f)
                            } else if (prev >= 0f) {
                                prev
                            } else {
                                0.55f
                            }
                            return if (prev < 0f) safeRaw else prev + (safeRaw - prev) * 0.35f
                        }

                        fun appendStrokeSample(
                            screenPos: Offset,
                            pressure: Float,
                            isHighlighter: Boolean
                        ): Boolean {
                            val (wx, wy) = toWorld(screenPos)
                            val lastPt = liveStrokePoints.lastOrNull()
                            val minWorldStep = 0.5f / localScale
                            if (lastPt == null ||
                                hypot(wx - lastPt.x, wy - lastPt.y) >= minWorldStep
                            ) {
                                liveStrokePoints.add(
                                    PointFData(
                                        x = wx,
                                        y = wy,
                                        pressure = if (isHighlighter) -1f else pressure
                                    )
                                )
                                return true
                            }
                            return false
                        }

                        fun beginToolAction(pointer: PointerInputChange, tool: CanvasTool) {
                            syncLocalCamera()
                            activePointerId = pointer.id
                            activeGestureTool = tool
                            activePointerIsStylus = pointer.isStylusLike()
                            startScreen = pointer.position
                            prevSingleScreen = startScreen
                            val (swx, swy) = toWorld(startScreen)
                            startWorld = PointFData(swx, swy)
                            prevEraseWorld = startWorld
                            totalSingleDragPx = 0f
                            liveStrokePoints.clear()
                            selectAction = SelectGestureAction.NONE
                            tappedCandidateNote = null

                            val useDynamicPressure =
                                activePointerIsStylus && tool == CanvasTool.PEN
                            smoothedPressure = if (useDynamicPressure) {
                                smoothStylusPressure(pointer.pressure, -1f)
                            } else {
                                -1f
                            }

                            when (tool) {
                                CanvasTool.PEN, CanvasTool.HIGHLIGHTER -> {
                                    val isHi = tool == CanvasTool.HIGHLIGHTER
                                    val hist = pointer.historical
                                    for (h in hist) {
                                        appendStrokeSample(h.position, smoothedPressure, isHi)
                                    }
                                    appendStrokeSample(pointer.position, smoothedPressure, isHi)
                                    liveDraftElement = CanvasElement.Stroke(
                                        id = "draft_stroke",
                                        points = liveStrokePoints,
                                        colorArgb = if (isHi) latestHiColor else latestPenColor,
                                        strokeWidth = if (isHi) latestHiWidth else latestPenWidth,
                                        isHighlighter = isHi
                                    )
                                    liveDraftTick++
                                }
                                CanvasTool.SHAPE -> {
                                    liveDraftElement = CanvasElement.Shape(
                                        id = "draft_shape",
                                        kind = latestShapeKind,
                                        startX = startWorld.x,
                                        startY = startWorld.y,
                                        endX = startWorld.x,
                                        endY = startWorld.y,
                                        colorArgb = latestPenColor,
                                        strokeWidth = latestPenWidth
                                    )
                                    liveDraftTick++
                                }
                                CanvasTool.ERASER -> {
                                    latestBeginContinuous()
                                    eraserScreenCursor = startScreen
                                    val eraserWorldRadius = latestEraserRadiusDp.dp.toPx() / localScale
                                    latestEraseSegment(
                                        startWorld.x,
                                        startWorld.y,
                                        startWorld.x,
                                        startWorld.y,
                                        eraserWorldRadius
                                    )
                                }
                                CanvasTool.SELECT -> {
                                    val currentSelected = latestElements.firstOrNull {
                                        it.id == latestSelectedId
                                    }
                                    val handleHit = if (currentSelected != null) {
                                        val (hx, hy) = CanvasRenderer.getResizeHandleCenter(
                                            currentSelected.bounds(),
                                            localScale
                                        )
                                        hypot(startWorld.x - hx, startWorld.y - hy) <= (24f / localScale)
                                    } else {
                                        false
                                    }

                                    if (handleHit) {
                                        selectAction = SelectGestureAction.RESIZE_ELEMENT
                                        latestBeginContinuous()
                                    } else {
                                        val hitElement = latestElements.asReversed().firstOrNull { el ->
                                            el.hitTest(startWorld.x, startWorld.y, 12f / localScale)
                                        }
                                        if (hitElement != null) {
                                            latestSelectElement(hitElement.id)
                                            selectAction = SelectGestureAction.MOVE_ELEMENT
                                            latestBeginContinuous()
                                            if (hitElement is CanvasElement.TextNote) {
                                                tappedCandidateNote = hitElement
                                            }
                                        } else {
                                            latestSelectElement(null)
                                            selectAction = SelectGestureAction.PAN_EMPTY_SPACE
                                        }
                                    }
                                }
                                else -> Unit
                            }
                        }

                        fun updateToolAction(pointer: PointerInputChange) {
                            val currentScreen = pointer.position
                            val deltaScreen = currentScreen - prevSingleScreen
                            totalSingleDragPx += hypot(deltaScreen.x, deltaScreen.y)
                            prevSingleScreen = currentScreen
                            val (cwx, cwy) = toWorld(currentScreen)

                            when (activeGestureTool) {
                                CanvasTool.PAN -> {
                                    if (deltaScreen != Offset.Zero) {
                                        localPanX += deltaScreen.x
                                        localPanY += deltaScreen.y
                                        latestCameraTransform(localScale, localPanX, localPanY)
                                    }
                                }
                                CanvasTool.PEN, CanvasTool.HIGHLIGHTER -> {
                                    val isHi = activeGestureTool == CanvasTool.HIGHLIGHTER
                                    val useDynamicPressure = activePointerIsStylus && !isHi
                                    val prevP = smoothedPressure
                                    val targetP = if (useDynamicPressure) {
                                        smoothStylusPressure(pointer.pressure, prevP)
                                    } else {
                                        -1f
                                    }
                                    smoothedPressure = targetP

                                    var addedAny = false
                                    val hist = pointer.historical
                                    if (hist.isNotEmpty()) {
                                        val steps = hist.size + 1
                                        for (i in hist.indices) {
                                            val frac = (i + 1).toFloat() / steps.toFloat()
                                            val interpP = if (useDynamicPressure && prevP >= 0f) {
                                                prevP + (targetP - prevP) * frac
                                            } else {
                                                targetP
                                            }
                                            if (appendStrokeSample(hist[i].position, interpP, isHi)) {
                                                addedAny = true
                                            }
                                        }
                                    }
                                    if (appendStrokeSample(currentScreen, targetP, isHi)) {
                                        addedAny = true
                                    }
                                    if (addedAny) {
                                        liveDraftTick++
                                    }
                                }
                                CanvasTool.SHAPE -> {
                                    liveDraftElement = CanvasElement.Shape(
                                        id = "draft_shape",
                                        kind = latestShapeKind,
                                        startX = startWorld.x,
                                        startY = startWorld.y,
                                        endX = cwx,
                                        endY = cwy,
                                        colorArgb = latestPenColor,
                                        strokeWidth = latestPenWidth
                                    )
                                    liveDraftTick++
                                }
                                CanvasTool.ERASER -> {
                                    eraserScreenCursor = currentScreen
                                    val eraserWorldRadius = latestEraserRadiusDp.dp.toPx() / localScale
                                    val hist = pointer.historical
                                    for (h in hist) {
                                        val (hwx, hwy) = toWorld(h.position)
                                        latestEraseSegment(
                                            prevEraseWorld.x,
                                            prevEraseWorld.y,
                                            hwx,
                                            hwy,
                                            eraserWorldRadius
                                        )
                                        prevEraseWorld = PointFData(hwx, hwy)
                                    }
                                    latestEraseSegment(
                                        prevEraseWorld.x,
                                        prevEraseWorld.y,
                                        cwx,
                                        cwy,
                                        eraserWorldRadius
                                    )
                                    prevEraseWorld = PointFData(cwx, cwy)
                                }
                                CanvasTool.SELECT -> {
                                    when (selectAction) {
                                        SelectGestureAction.MOVE_ELEMENT -> {
                                            if (deltaScreen != Offset.Zero) {
                                                latestMoveSelected(
                                                    deltaScreen.x / localScale,
                                                    deltaScreen.y / localScale
                                                )
                                            }
                                        }
                                        SelectGestureAction.RESIZE_ELEMENT -> {
                                            latestResizeSelected(cwx, cwy)
                                        }
                                        SelectGestureAction.PAN_EMPTY_SPACE -> {
                                            if (deltaScreen != Offset.Zero) {
                                                localPanX += deltaScreen.x
                                                localPanY += deltaScreen.y
                                                latestCameraTransform(localScale, localPanX, localPanY)
                                            }
                                        }
                                        SelectGestureAction.NONE -> Unit
                                    }
                                }
                                CanvasTool.TEXT, CanvasTool.STICKY -> {
                                    if (totalSingleDragPx > viewConfiguration.touchSlop * 1.3f && deltaScreen != Offset.Zero) {
                                        localPanX += deltaScreen.x
                                        localPanY += deltaScreen.y
                                        latestCameraTransform(localScale, localPanX, localPanY)
                                    }
                                }
                                CanvasTool.IMAGE -> Unit
                            }
                        }

                        fun finishToolAction() {
                            eraserScreenCursor = null
                            when (activeGestureTool) {
                                CanvasTool.PEN, CanvasTool.HIGHLIGHTER -> {
                                    val completedPoints = liveStrokePoints.toList()
                                    liveDraftElement = null
                                    liveStrokePoints.clear()
                                    liveDraftTick++
                                    if (completedPoints.isNotEmpty()) {
                                        latestCommitStroke(
                                            completedPoints,
                                            activeGestureTool == CanvasTool.HIGHLIGHTER
                                        )
                                    }
                                }
                                CanvasTool.SHAPE -> {
                                    val finalShape = liveDraftElement as? CanvasElement.Shape
                                    liveDraftElement = null
                                    liveDraftTick++
                                    if (finalShape != null) {
                                        latestCommitShape(
                                            finalShape.startX,
                                            finalShape.startY,
                                            finalShape.endX,
                                            finalShape.endY
                                        )
                                    }
                                }
                                CanvasTool.ERASER -> {
                                    latestEndContinuous()
                                }
                                CanvasTool.SELECT -> {
                                    latestEndContinuous()
                                    val note = tappedCandidateNote
                                    if (note != null && totalSingleDragPx < viewConfiguration.touchSlop * 1.2f) {
                                        val now = System.currentTimeMillis()
                                        if (lastTappedElementId == note.id && (now - lastTapTimestampMs) < 360L) {
                                            latestEditTextElement(note)
                                            lastTappedElementId = null
                                        } else {
                                            lastTappedElementId = note.id
                                            lastTapTimestampMs = now
                                        }
                                    }
                                }
                                CanvasTool.TEXT, CanvasTool.STICKY -> {
                                    if (totalSingleDragPx < viewConfiguration.touchSlop * 1.3f) {
                                        latestTapPlaceNote(
                                            startWorld.x,
                                            startWorld.y,
                                            activeGestureTool == CanvasTool.STICKY
                                        )
                                    }
                                }
                                else -> Unit
                            }
                            activePointerId = null
                            activePointerIsStylus = false
                            smoothedPressure = -1f
                        }

                        fun cancelActiveTouchAction() {
                            liveDraftElement = null
                            liveStrokePoints.clear()
                            liveDraftTick++
                            eraserScreenCursor = null
                            latestCancelContinuous()
                            activePointerId = null
                            activePointerIsStylus = false
                            smoothedPressure = -1f
                        }

                        // Classify initial pointer down
                        when {
                            down.isStylusLike() -> {
                                stylusHoverTracker.lastStylusActivityMs = SystemClock.uptimeMillis()
                                latestOnStylusDetected()
                                latestOnStylusTouchStateChange(true)
                                channel = GestureChannel.STYLUS
                                val (effectiveTool, isTempErase) = resolveStylusEffectiveTool(down)
                                updateTemporaryEraserFlag(isTempErase)
                                beginToolAction(down, effectiveTool)
                            }
                            down.type == PointerType.Mouse -> {
                                channel = GestureChannel.TOUCH_TOOL
                                beginToolAction(down, latestTool)
                            }
                            PalmRejectionPolicy.resolveSingleTouchBehavior(
                                mode = latestPalmMode,
                                isStylusSessionActive = latestStylusSession,
                                tool = latestTool
                            ) == SingleTouchBehavior.PAN_CANVAS -> {
                                val hoverLocked = PalmRejectionPolicy.shouldLockSingleTouchPanForStylusHover(
                                    isStylusHovering = stylusHoverTracker.isHovering,
                                    nowMs = SystemClock.uptimeMillis(),
                                    lastStylusActivityMs = stylusHoverTracker.lastStylusActivityMs
                                )
                                if (hoverLocked) {
                                    // Stylus is hovering/near glass: ignore resting palm completely
                                    channel = GestureChannel.TOUCH_REJECTED
                                } else {
                                    // Stylus is away from glass: 1 finger directly pans the infinite canvas
                                    channel = GestureChannel.TOUCH_PAN
                                    activePointerId = down.id
                                    startScreen = down.position
                                    prevSingleScreen = down.position
                                    panSlopPassed = false
                                    panAccumulatedPx = 0f
                                }
                            }
                            else -> {
                                channel = GestureChannel.TOUCH_TOOL
                                beginToolAction(down, latestTool)
                            }
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break

                            val stylusPointer = pressed.firstOrNull { it.isStylusLike() }

                            if (stylusPointer != null) {
                                // Stylus ALWAYS takes absolute priority over any finger/palm touch!
                                stylusHoverTracker.lastStylusActivityMs = SystemClock.uptimeMillis()
                                latestOnStylusDetected()
                                latestOnStylusTouchStateChange(true)

                                if (channel == GestureChannel.TOUCH_TOOL) {
                                    // Cancel any accidental palm stroke that started right before stylus landed
                                    cancelActiveTouchAction()
                                } else if (channel == GestureChannel.TOUCH_PAN ||
                                    channel == GestureChannel.MULTI_TOUCH_NAV
                                ) {
                                    liveDraftElement = null
                                    liveStrokePoints.clear()
                                    liveDraftTick++
                                    eraserScreenCursor = null
                                    activePointerId = null
                                }

                                channel = GestureChannel.STYLUS
                                val (effectiveStylusTool, isTempErase) =
                                    resolveStylusEffectiveTool(stylusPointer)
                                updateTemporaryEraserFlag(isTempErase)

                                if (activePointerId != stylusPointer.id ||
                                    activeGestureTool != effectiveStylusTool
                                ) {
                                    if (activePointerId != null) {
                                        finishToolAction()
                                    }
                                    beginToolAction(stylusPointer, effectiveStylusTool)
                                } else {
                                    updateToolAction(stylusPointer)
                                }
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            } else {
                                // No stylus currently touching the glass
                                if (channel == GestureChannel.STYLUS) {
                                    // Stylus just lifted while a palm may still be resting on the screen!
                                    // Commit the stylus stroke immediately and ignore remaining resting palm touches
                                    // until all pointers lift or the stylus lands again for the next stroke.
                                    stylusHoverTracker.lastStylusActivityMs = SystemClock.uptimeMillis()
                                    latestOnStylusTouchStateChange(false)
                                    updateTemporaryEraserFlag(false)
                                    if (activePointerId != null) {
                                        finishToolAction()
                                    }
                                    channel = GestureChannel.TOUCH_REJECTED
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    continue
                                }

                                val touchPointers = pressed.filter { it.type == PointerType.Touch }
                                if (touchPointers.size >= 2) {
                                    if (channel == GestureChannel.TOUCH_TOOL) {
                                        cancelActiveTouchAction()
                                    }
                                    channel = GestureChannel.MULTI_TOUCH_NAV
                                    activePointerId = null

                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val centroid = event.calculateCentroid(useCurrent = false)

                                    if (zoomChange != 1f || panChange != Offset.Zero) {
                                        val oldScale = localScale
                                        val newScale = (oldScale * zoomChange).coerceIn(0.25f, 4.0f)
                                        val effectiveZoom = newScale / oldScale
                                        val c = if (centroid == Offset.Unspecified) startScreen else centroid
                                        localPanX = c.x - (c.x - localPanX) * effectiveZoom + panChange.x
                                        localPanY = c.y - (c.y - localPanY) * effectiveZoom + panChange.y
                                        localScale = newScale
                                        latestCameraTransform(localScale, localPanX, localPanY)
                                    }
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else if (channel == GestureChannel.TOUCH_PAN) {
                                    // If stylus entered hover mid-pan, stop panning immediately to prevent palm drift
                                    if (stylusHoverTracker.isHovering) {
                                        channel = GestureChannel.TOUCH_REJECTED
                                        activePointerId = null
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                        continue
                                    }
                                    val tracked = touchPointers.firstOrNull { it.id == activePointerId }
                                    if (tracked != null) {
                                        val deltaScreen = tracked.position - prevSingleScreen
                                        prevSingleScreen = tracked.position
                                        if (!panSlopPassed) {
                                            panAccumulatedPx += hypot(deltaScreen.x, deltaScreen.y)
                                            if (panAccumulatedPx >= viewConfiguration.touchSlop * 0.45f) {
                                                panSlopPassed = true
                                                val totalDelta = tracked.position - startScreen
                                                localPanX += totalDelta.x
                                                localPanY += totalDelta.y
                                                latestCameraTransform(localScale, localPanX, localPanY)
                                            }
                                        } else if (deltaScreen != Offset.Zero) {
                                            localPanX += deltaScreen.x
                                            localPanY += deltaScreen.y
                                            latestCameraTransform(localScale, localPanX, localPanY)
                                        }
                                        if (tracked.positionChanged()) tracked.consume()
                                    } else {
                                        val replacement = touchPointers.firstOrNull()
                                        if (replacement != null) {
                                            activePointerId = replacement.id
                                            prevSingleScreen = replacement.position
                                        }
                                    }
                                } else if (channel == GestureChannel.MULTI_TOUCH_NAV &&
                                    touchPointers.size == 1 &&
                                    !stylusHoverTracker.isHovering &&
                                    PalmRejectionPolicy.resolveSingleTouchBehavior(
                                        mode = latestPalmMode,
                                        isStylusSessionActive = latestStylusSession,
                                        tool = latestTool
                                    ) == SingleTouchBehavior.PAN_CANVAS
                                ) {
                                    // Seamlessly transition from 2-finger pinch to 1-finger pan without jumping
                                    val remaining = touchPointers.first()
                                    channel = GestureChannel.TOUCH_PAN
                                    activePointerId = remaining.id
                                    startScreen = remaining.position
                                    prevSingleScreen = remaining.position
                                    panSlopPassed = true
                                    if (remaining.positionChanged()) remaining.consume()
                                } else if (channel == GestureChannel.TOUCH_TOOL) {
                                    val tracked = pressed.firstOrNull { it.id == activePointerId }
                                    if (tracked != null) {
                                        updateToolAction(tracked)
                                        if (tracked.positionChanged()) tracked.consume()
                                    }
                                } else {
                                    // TOUCH_REJECTED or non-panning drop from 2 fingers to 1 finger
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        }

                        // All pointers are up
                        latestOnStylusTouchStateChange(false)
                        updateTemporaryEraserFlag(false)
                        if ((channel == GestureChannel.STYLUS || channel == GestureChannel.TOUCH_TOOL) &&
                            activePointerId != null
                        ) {
                            finishToolAction()
                        } else {
                            liveDraftElement = null
                            liveStrokePoints.clear()
                            liveDraftTick++
                            eraserScreenCursor = null
                        }
                    }
                }
        ) {
            // Read liveDraftTick so live stroke updates invalidate ONLY this lightweight overlay!
            val currentTick = liveDraftTick
            if (currentTick < 0L) return@Canvas

            val draft = liveDraftElement
            if (draft != null) {
                val safeScale = cameraScale.coerceIn(0.25f, 4.0f)
                val nativeCanvas = drawContext.canvas.nativeCanvas
                nativeCanvas.save()
                nativeCanvas.translate(cameraPanX, cameraPanY)
                nativeCanvas.scale(safeScale, safeScale)

                CanvasRenderer.renderLiveDraft(
                    canvas = nativeCanvas,
                    draftElement = draft,
                    typeface = canvasTypeface,
                    bitmapProvider = bitmapProvider
                )

                nativeCanvas.restore()
            }

            eraserScreenCursor?.let { cursor ->
                val cursorRadiusPx = eraserRadiusDp.dp.toPx()
                drawCircle(
                    color = Color(0x33DC2626),
                    radius = cursorRadiusPx,
                    center = cursor
                )
                drawCircle(
                    color = Color(0xFFDC2626),
                    radius = cursorRadiusPx,
                    center = cursor,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }
    }
}
