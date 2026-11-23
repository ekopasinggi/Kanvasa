package com.example.ui

import android.graphics.Bitmap
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.data.MediaStorageManager
import com.example.model.CanvasElement
import com.example.model.PointFData
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ImageCropMode {
    RECTANGLE,
    CUSTOM
}

private enum class RectDragTarget {
    NONE,
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    MOVE_INSIDE
}

@Composable
fun ImageCropperDialog(
    imageElement: CanvasElement.ImageItem,
    mediaStorage: MediaStorageManager,
    isProcessing: Boolean,
    onDismiss: () -> Unit,
    onApplyRectangleCrop: (Rect) -> Unit,
    onApplyCustomCrop: (List<PointFData>) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val sourceBitmap by produceState<Bitmap?>(initialValue = null, imageElement.filePath) {
        value = withContext(Dispatchers.IO) {
            mediaStorage.getOrLoadBitmapSync(imageElement.filePath)
        }
    }

    var cropMode by remember { mutableStateOf(ImageCropMode.RECTANGLE) }
    var rectNorm by remember { mutableStateOf(Rect(0.08f, 0.08f, 0.92f, 0.92f)) }
    var customPointsNorm by remember { mutableStateOf<List<PointFData>>(emptyList()) }
    var stageSize by remember { mutableStateOf(IntSize.Zero) }

    val emptyPathMessage = stringResource(R.string.crop_error_empty_path)

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F172A))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                // Top Bar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    IconButton(
                        onClick = onDismiss,
                        enabled = !isProcessing,
                        modifier = Modifier.testTag("crop_cancel_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel),
                            tint = Color.White
                        )
                    }

                    Text(
                        text = stringResource(R.string.crop_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )

                    Button(
                        onClick = {
                            when (cropMode) {
                                ImageCropMode.RECTANGLE -> onApplyRectangleCrop(rectNorm)
                                ImageCropMode.CUSTOM -> {
                                    if (customPointsNorm.size < 3) {
                                        onShowMessage(emptyPathMessage)
                                    } else {
                                        onApplyCustomCrop(customPointsNorm)
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing && sourceBitmap != null,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("crop_apply_button")
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(stringResource(R.string.crop_apply))
                        }
                    }
                }

                // Mode Switcher Bar
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    FilterChip(
                        selected = cropMode == ImageCropMode.RECTANGLE,
                        onClick = { cropMode = ImageCropMode.RECTANGLE },
                        label = { Text(stringResource(R.string.crop_mode_rectangle)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Crop,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1),
                            iconColor = Color(0xFFCBD5E1)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = cropMode == ImageCropMode.RECTANGLE,
                            borderColor = Color(0xFF334155),
                            selectedBorderColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("crop_mode_rectangle_chip")
                    )

                    FilterChip(
                        selected = cropMode == ImageCropMode.CUSTOM,
                        onClick = { cropMode = ImageCropMode.CUSTOM },
                        label = { Text(stringResource(R.string.crop_mode_custom)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Gesture,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = Color.White,
                            selectedLeadingIconColor = Color.White,
                            containerColor = Color(0xFF1E293B),
                            labelColor = Color(0xFFCBD5E1),
                            iconColor = Color(0xFFCBD5E1)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = cropMode == ImageCropMode.CUSTOM,
                            borderColor = Color(0xFF334155),
                            selectedBorderColor = MaterialTheme.colorScheme.secondary
                        ),
                        modifier = Modifier.testTag("crop_mode_custom_chip")
                    )
                }

                // Interactive Crop Canvas Stage
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .onSizeChanged { stageSize = it }
                ) {
                    val bmp = sourceBitmap
                    if (bmp == null) {
                        CircularProgressIndicator(color = Color.White)
                    } else if (stageSize.width > 0 && stageSize.height > 0) {
                        val displayRect = remember(bmp, stageSize) {
                            computeFittedRect(
                                bmpWidth = bmp.width.toFloat(),
                                bmpHeight = bmp.height.toFloat(),
                                boxWidth = stageSize.width.toFloat(),
                                boxHeight = stageSize.height.toFloat(),
                                marginPx = 36f
                            )
                        }

                        InteractiveCropStage(
                            bitmap = bmp,
                            displayRect = displayRect,
                            mode = cropMode,
                            rectNorm = rectNorm,
                            customPointsNorm = customPointsNorm,
                            onRectNormChange = { rectNorm = it },
                            onCustomPointsChange = { customPointsNorm = it }
                        )
                    }
                }

                // Bottom Guidance & Reset Footer
                Surface(
                    color = Color(0xFF1E293B),
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    border = BorderStroke(1.dp, Color(0xFF334155)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (cropMode == ImageCropMode.RECTANGLE) {
                                    R.string.crop_rect_hint
                                } else {
                                    R.string.crop_custom_hint
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0),
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        OutlinedButton(
                            onClick = {
                                if (cropMode == ImageCropMode.RECTANGLE) {
                                    rectNorm = Rect(0.08f, 0.08f, 0.92f, 0.92f)
                                } else {
                                    customPointsNorm = emptyList()
                                }
                            },
                            shape = CircleShape,
                            border = BorderStroke(1.dp, Color(0xFF64748B)),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("crop_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.crop_reset_area),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InteractiveCropStage(
    bitmap: Bitmap,
    displayRect: Rect,
    mode: ImageCropMode,
    rectNorm: Rect,
    customPointsNorm: List<PointFData>,
    onRectNormChange: (Rect) -> Unit,
    onCustomPointsChange: (List<PointFData>) -> Unit
) {
    val imageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val latestMode by rememberUpdatedState(mode)
    val latestRectNorm by rememberUpdatedState(rectNorm)
    val latestDisplayRect by rememberUpdatedState(displayRect)
    val latestOnRectChange by rememberUpdatedState(onRectNormChange)
    val latestOnCustomChange by rememberUpdatedState(onCustomPointsChange)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .testTag("crop_interactive_canvas")
            .pointerInput(displayRect) {
                val cornerHitRadiusPx = 32.dp.toPx()
                val minCustomPointStepPx = 4.dp.toPx()

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()

                    val dRect = latestDisplayRect
                    if (dRect.width <= 1f || dRect.height <= 1f) return@awaitEachGesture

                    fun screenToNorm(pos: Offset): PointFData = PointFData(
                        x = ((pos.x - dRect.left) / dRect.width).coerceIn(0f, 1f),
                        y = ((pos.y - dRect.top) / dRect.height).coerceIn(0f, 1f)
                    )

                    fun normToScreen(nx: Float, ny: Float): Offset = Offset(
                        x = dRect.left + nx * dRect.width,
                        y = dRect.top + ny * dRect.height
                    )

                    val activeMode = latestMode
                    var prevScreen = down.position

                    if (activeMode == ImageCropMode.RECTANGLE) {
                        val r = latestRectNorm
                        val tl = normToScreen(r.left, r.top)
                        val tr = normToScreen(r.right, r.top)
                        val bl = normToScreen(r.left, r.bottom)
                        val br = normToScreen(r.right, r.bottom)
                        val screenCropRect = Rect(tl.x, tl.y, br.x, br.y)

                        val dragTarget = when {
                            hypot(down.position.x - tl.x, down.position.y - tl.y) <= cornerHitRadiusPx ->
                                RectDragTarget.TOP_LEFT
                            hypot(down.position.x - tr.x, down.position.y - tr.y) <= cornerHitRadiusPx ->
                                RectDragTarget.TOP_RIGHT
                            hypot(down.position.x - bl.x, down.position.y - bl.y) <= cornerHitRadiusPx ->
                                RectDragTarget.BOTTOM_LEFT
                            hypot(down.position.x - br.x, down.position.y - br.y) <= cornerHitRadiusPx ->
                                RectDragTarget.BOTTOM_RIGHT
                            screenCropRect.contains(down.position) ->
                                RectDragTarget.MOVE_INSIDE
                            else -> RectDragTarget.NONE
                        }

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.firstOrNull { it.pressed } ?: break
                            val curr = pressed.position
                            val dxNorm = (curr.x - prevScreen.x) / dRect.width
                            val dyNorm = (curr.y - prevScreen.y) / dRect.height
                            prevScreen = curr

                            val curR = latestRectNorm
                            val minSpan = 0.08f
                            val updated = when (dragTarget) {
                                RectDragTarget.TOP_LEFT -> Rect(
                                    left = (curR.left + dxNorm).coerceIn(0f, curR.right - minSpan),
                                    top = (curR.top + dyNorm).coerceIn(0f, curR.bottom - minSpan),
                                    right = curR.right,
                                    bottom = curR.bottom
                                )
                                RectDragTarget.TOP_RIGHT -> Rect(
                                    left = curR.left,
                                    top = (curR.top + dyNorm).coerceIn(0f, curR.bottom - minSpan),
                                    right = (curR.right + dxNorm).coerceIn(curR.left + minSpan, 1f),
                                    bottom = curR.bottom
                                )
                                RectDragTarget.BOTTOM_LEFT -> Rect(
                                    left = (curR.left + dxNorm).coerceIn(0f, curR.right - minSpan),
                                    top = curR.top,
                                    right = curR.right,
                                    bottom = (curR.bottom + dyNorm).coerceIn(curR.top + minSpan, 1f)
                                )
                                RectDragTarget.BOTTOM_RIGHT -> Rect(
                                    left = curR.left,
                                    top = curR.top,
                                    right = (curR.right + dxNorm).coerceIn(curR.left + minSpan, 1f),
                                    bottom = (curR.bottom + dyNorm).coerceIn(curR.top + minSpan, 1f)
                                )
                                RectDragTarget.MOVE_INSIDE -> {
                                    val w = curR.width
                                    val h = curR.height
                                    val newLeft = (curR.left + dxNorm).coerceIn(0f, 1f - w)
                                    val newTop = (curR.top + dyNorm).coerceIn(0f, 1f - h)
                                    Rect(newLeft, newTop, newLeft + w, newTop + h)
                                }
                                RectDragTarget.NONE -> curR
                            }
                            if (updated != curR) {
                                latestOnRectChange(updated)
                            }
                            if (pressed.positionChanged()) pressed.consume()
                        }
                    } else {
                        // Custom Freehand Lasso Mode
                        val pts = ArrayList<PointFData>()
                        pts.add(screenToNorm(down.position))
                        latestOnCustomChange(pts.toList())
                        var lastRecordedScreen = down.position

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.firstOrNull { it.pressed } ?: break
                            val curr = pressed.position
                            if (hypot(curr.x - lastRecordedScreen.x, curr.y - lastRecordedScreen.y) >= minCustomPointStepPx) {
                                pts.add(screenToNorm(curr))
                                lastRecordedScreen = curr
                                latestOnCustomChange(pts.toList())
                            }
                            if (pressed.positionChanged()) pressed.consume()
                        }
                    }
                }
            }
    ) {
        // Draw the fitted source image
        drawImage(
            image = imageBitmap,
            dstOffset = IntOffset(displayRect.left.roundToInt(), displayRect.top.roundToInt()),
            dstSize = IntSize(displayRect.width.roundToInt(), displayRect.height.roundToInt())
        )

        val nativeCanvas = drawContext.canvas.nativeCanvas

        if (mode == ImageCropMode.RECTANGLE) {
            val cropLeft = displayRect.left + rectNorm.left * displayRect.width
            val cropTop = displayRect.top + rectNorm.top * displayRect.height
            val cropRight = displayRect.left + rectNorm.right * displayRect.width
            val cropBottom = displayRect.top + rectNorm.bottom * displayRect.height

            val scrimPath = Path().apply {
                fillType = Path.FillType.EVEN_ODD
                addRect(
                    displayRect.left,
                    displayRect.top,
                    displayRect.right,
                    displayRect.bottom,
                    Path.Direction.CW
                )
                addRect(cropLeft, cropTop, cropRight, cropBottom, Path.Direction.CW)
            }
            val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xAA0F172A.toInt()
            }
            nativeCanvas.drawPath(scrimPath, scrimPaint)

            // Rule of thirds lines
            val thirdW = (cropRight - cropLeft) / 3f
            val thirdH = (cropBottom - cropTop) / 3f
            val gridColor = Color.White.copy(alpha = 0.4f)
            for (i in 1..2) {
                drawLine(
                    color = gridColor,
                    start = Offset(cropLeft + thirdW * i, cropTop),
                    end = Offset(cropLeft + thirdW * i, cropBottom),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = gridColor,
                    start = Offset(cropLeft, cropTop + thirdH * i),
                    end = Offset(cropRight, cropTop + thirdH * i),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Crisp Crop Frame
            drawRect(
                color = Color.White,
                topLeft = Offset(cropLeft, cropTop),
                size = Size(cropRight - cropLeft, cropBottom - cropTop),
                style = Stroke(width = 2.dp.toPx())
            )

            // 4 Draggable Corner Handles
            val handleRadius = 9.dp.toPx()
            val corners = listOf(
                Offset(cropLeft, cropTop),
                Offset(cropRight, cropTop),
                Offset(cropLeft, cropBottom),
                Offset(cropRight, cropBottom)
            )
            corners.forEach { pt ->
                drawCircle(color = Color(0xFF2563EB), radius = handleRadius, center = pt)
                drawCircle(color = Color.White, radius = handleRadius * 0.48f, center = pt)
            }
        } else {
            // Custom Freehand Lasso Overlay
            if (customPointsNorm.size >= 3) {
                val lassoPath = Path()
                customPointsNorm.forEachIndexed { idx, pt ->
                    val sx = displayRect.left + pt.x * displayRect.width
                    val sy = displayRect.top + pt.y * displayRect.height
                    if (idx == 0) lassoPath.moveTo(sx, sy) else lassoPath.lineTo(sx, sy)
                }
                lassoPath.close()

                val scrimPath = Path().apply {
                    fillType = Path.FillType.EVEN_ODD
                    addRect(
                        displayRect.left,
                        displayRect.top,
                        displayRect.right,
                        displayRect.bottom,
                        Path.Direction.CW
                    )
                    addPath(lassoPath)
                }
                val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = 0xAA0F172A.toInt()
                }
                nativeCanvas.drawPath(scrimPath, scrimPaint)

                val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 4.dp.toPx()
                    color = android.graphics.Color.WHITE
                    strokeJoin = Paint.Join.ROUND
                    strokeCap = Paint.Cap.ROUND
                }
                val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 2.2.dp.toPx()
                    color = 0xFF2563EB.toInt()
                    pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
                }
                nativeCanvas.drawPath(lassoPath, haloPaint)
                nativeCanvas.drawPath(lassoPath, dashPaint)
            } else {
                // Dim slightly while awaiting user's custom loop
                drawRect(
                    color = Color(0x440F172A),
                    topLeft = Offset(displayRect.left, displayRect.top),
                    size = Size(displayRect.width, displayRect.height)
                )
            }
        }
    }
}

private fun computeFittedRect(
    bmpWidth: Float,
    bmpHeight: Float,
    boxWidth: Float,
    boxHeight: Float,
    marginPx: Float
): Rect {
    val availW = (boxWidth - marginPx * 2f).coerceAtLeast(60f)
    val availH = (boxHeight - marginPx * 2f).coerceAtLeast(60f)
    val scale = min(availW / bmpWidth.coerceAtLeast(1f), availH / bmpHeight.coerceAtLeast(1f))
    val drawW = bmpWidth * scale
    val drawH = bmpHeight * scale
    val left = (boxWidth - drawW) / 2f
    val top = (boxHeight - drawH) / 2f
    return Rect(left, top, left + drawW, top + drawH)
}
