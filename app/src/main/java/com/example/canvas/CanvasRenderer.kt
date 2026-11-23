package com.example.canvas

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.LruCache
import androidx.compose.ui.geometry.Rect
import com.example.model.CanvasElement
import com.example.model.CanvasPattern
import com.example.model.PointFData
import com.example.model.ShapeKind
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object CanvasRenderer {

    private const val PAPER_BG_COLOR = 0xFFFAF9F6.toInt()
    private const val DOT_COLOR = 0xFFD2CEC3.toInt()
    private const val GRID_MINOR_COLOR = 0xFFE6E3DA.toInt()
    private const val GRID_MAJOR_COLOR = 0xFFD6D2C6.toInt()
    private const val SELECTION_COLOR = 0xFF2563EB.toInt()

    private data class CachedStrokePath(
        val path: Path,
        val isFilledVariableWidth: Boolean
    )

    // Cache built Paths for committed strokes so pan/zoom/redraw never re-tessellates unchanged ink
    private val strokePathCache = LruCache<String, CachedStrokePath>(512)

    // Cache repeating GPU tile shaders for DOTS / GRID backgrounds (1 drawRect call per frame)
    private val patternShaderCache = HashMap<String, BitmapShader>()

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = PAPER_BG_COLOR
    }

    fun renderScene(
        canvas: Canvas,
        worldViewport: Rect,
        pattern: CanvasPattern,
        elements: List<CanvasElement>,
        draftElement: CanvasElement? = null,
        selectedElementId: String? = null,
        cameraScale: Float = 1f,
        showSelectionOverlay: Boolean = true,
        typeface: Typeface? = null,
        preferVectorPattern: Boolean = false,
        bitmapProvider: (String) -> Bitmap?
    ) {
        renderCommittedScene(
            canvas = canvas,
            worldViewport = worldViewport,
            pattern = pattern,
            elements = elements,
            selectedElementId = selectedElementId,
            cameraScale = cameraScale,
            showSelectionOverlay = showSelectionOverlay,
            typeface = typeface,
            preferVectorPattern = preferVectorPattern,
            bitmapProvider = bitmapProvider
        )

        if (draftElement != null) {
            renderLiveDraft(
                canvas = canvas,
                draftElement = draftElement,
                typeface = typeface,
                bitmapProvider = bitmapProvider
            )
        }
    }

    /**
     * Renders the static committed canvas layer (background pattern + committed elements + selection).
     * Performs viewport culling so off-screen elements incur zero drawing cost.
     */
    fun renderCommittedScene(
        canvas: Canvas,
        worldViewport: Rect,
        pattern: CanvasPattern,
        elements: List<CanvasElement>,
        selectedElementId: String? = null,
        cameraScale: Float = 1f,
        showSelectionOverlay: Boolean = true,
        typeface: Typeface? = null,
        preferVectorPattern: Boolean = false,
        bitmapProvider: (String) -> Bitmap?
    ) {
        drawPaperAndPattern(canvas, worldViewport, pattern, preferVectorPattern)

        val cullRect = worldViewport.inflate(64f)
        for (element in elements) {
            if (element.bounds().overlaps(cullRect)) {
                drawElement(
                    canvas = canvas,
                    element = element,
                    typeface = typeface,
                    useStrokeCache = true,
                    bitmapProvider = bitmapProvider
                )
            }
        }

        if (showSelectionOverlay && selectedElementId != null) {
            val selected = elements.firstOrNull { it.id == selectedElementId }
            if (selected != null) {
                drawSelectionFrame(canvas, selected.bounds(), cameraScale)
            }
        }
    }

    /**
     * Ultra-lightweight renderer for the active in-progress stroke or shape overlay.
     */
    fun renderLiveDraft(
        canvas: Canvas,
        draftElement: CanvasElement,
        typeface: Typeface? = null,
        bitmapProvider: (String) -> Bitmap? = { null }
    ) {
        drawElement(
            canvas = canvas,
            element = draftElement,
            typeface = typeface,
            useStrokeCache = false,
            bitmapProvider = bitmapProvider
        )
    }

    private fun drawPaperAndPattern(
        canvas: Canvas,
        worldRect: Rect,
        pattern: CanvasPattern,
        preferVectorPattern: Boolean
    ) {
        canvas.drawRect(
            worldRect.left,
            worldRect.top,
            worldRect.right,
            worldRect.bottom,
            bgPaint
        )

        if (pattern == CanvasPattern.BLANK) return

        val spanMax = max(worldRect.width, worldRect.height)
        if (!preferVectorPattern) {
            val shader = getOrCreatePatternShader(pattern, spanMax)
            if (shader != null) {
                val tilePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    style = Paint.Style.FILL
                    this.shader = shader
                }
                canvas.drawRect(
                    worldRect.left,
                    worldRect.top,
                    worldRect.right,
                    worldRect.bottom,
                    tilePaint
                )
                return
            }
        }

        // Vector fallback (used for crisp PDF export or if shader allocation fails)
        when (pattern) {
            CanvasPattern.BLANK -> Unit
            CanvasPattern.DOTS -> {
                val baseStep = dotStepForSpan(spanMax)
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.FILL
                    color = DOT_COLOR
                }
                val radius = if (baseStep > 32f) 2.4f else 1.8f
                val startX = floor(worldRect.left / baseStep) * baseStep
                val startY = floor(worldRect.top / baseStep) * baseStep
                var x = startX
                while (x <= worldRect.right) {
                    var y = startY
                    while (y <= worldRect.bottom) {
                        canvas.drawCircle(x, y, radius, dotPaint)
                        y += baseStep
                    }
                    x += baseStep
                }
            }
            CanvasPattern.GRID -> {
                val minorStep = gridStepForSpan(spanMax)
                val majorStep = minorStep * 5f
                val minorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1f
                    color = GRID_MINOR_COLOR
                }
                val majorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.6f
                    color = GRID_MAJOR_COLOR
                }

                val startX = floor(worldRect.left / minorStep) * minorStep
                var x = startX
                while (x <= worldRect.right) {
                    val isMajor = abs(x % majorStep) < 0.5f
                    canvas.drawLine(
                        x,
                        worldRect.top,
                        x,
                        worldRect.bottom,
                        if (isMajor) majorPaint else minorPaint
                    )
                    x += minorStep
                }

                val startY = floor(worldRect.top / minorStep) * minorStep
                var y = startY
                while (y <= worldRect.bottom) {
                    val isMajor = abs(y % majorStep) < 0.5f
                    canvas.drawLine(
                        worldRect.left,
                        y,
                        worldRect.right,
                        y,
                        if (isMajor) majorPaint else minorPaint
                    )
                    y += minorStep
                }
            }
        }
    }

    private fun dotStepForSpan(spanMax: Float): Float = when {
        spanMax > 5200f -> 96f
        spanMax > 2600f -> 64f
        else -> 32f
    }

    private fun gridStepForSpan(spanMax: Float): Float = when {
        spanMax > 5200f -> 120f
        spanMax > 2600f -> 80f
        else -> 40f
    }

    private fun getOrCreatePatternShader(pattern: CanvasPattern, spanMax: Float): BitmapShader? {
        val stepInt = when (pattern) {
            CanvasPattern.DOTS -> dotStepForSpan(spanMax).toInt()
            CanvasPattern.GRID -> gridStepForSpan(spanMax).toInt()
            CanvasPattern.BLANK -> return null
        }
        val key = "${pattern.name}_$stepInt"
        synchronized(patternShaderCache) {
            patternShaderCache[key]?.let { return it }
        }

        return runCatching {
            val shader = when (pattern) {
                CanvasPattern.DOTS -> {
                    val bmp = Bitmap.createBitmap(stepInt, stepInt, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = DOT_COLOR
                    }
                    val radius = if (stepInt > 32) 2.4f else 1.8f
                    c.drawCircle(stepInt * 0.5f, stepInt * 0.5f, radius, p)
                    BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                }
                CanvasPattern.GRID -> {
                    val tileSize = stepInt * 5
                    val bmp = Bitmap.createBitmap(tileSize, tileSize, Bitmap.Config.ARGB_8888)
                    val c = Canvas(bmp)
                    val minorP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1f
                        color = GRID_MINOR_COLOR
                    }
                    val majorP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 1.6f
                        color = GRID_MAJOR_COLOR
                    }
                    // Major border lines on 0
                    c.drawLine(0.8f, 0f, 0.8f, tileSize.toFloat(), majorP)
                    c.drawLine(0f, 0.8f, tileSize.toFloat(), 0.8f, majorP)
                    // 4 minor interior lines
                    for (i in 1 until 5) {
                        val pos = (i * stepInt).toFloat()
                        c.drawLine(pos, 0f, pos, tileSize.toFloat(), minorP)
                        c.drawLine(0f, pos, tileSize.toFloat(), pos, minorP)
                    }
                    BitmapShader(bmp, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                }
                CanvasPattern.BLANK -> return null
            }
            synchronized(patternShaderCache) {
                patternShaderCache[key] = shader
            }
            shader
        }.getOrNull()
    }

    private fun drawElement(
        canvas: Canvas,
        element: CanvasElement,
        typeface: Typeface?,
        useStrokeCache: Boolean,
        bitmapProvider: (String) -> Bitmap?
    ) {
        when (element) {
            is CanvasElement.Stroke -> drawStroke(canvas, element, useStrokeCache)
            is CanvasElement.Shape -> drawShape(canvas, element)
            is CanvasElement.TextNote -> drawTextNote(canvas, element, typeface)
            is CanvasElement.ImageItem -> drawImageItem(canvas, element, bitmapProvider)
        }
    }

    private fun drawStroke(
        canvas: Canvas,
        stroke: CanvasElement.Stroke,
        useCache: Boolean
    ) {
        val pts = stroke.points
        if (pts.isEmpty()) return

        val baseColor = stroke.colorArgb.toInt()
        if (pts.size == 1) {
            val p0 = pts[0]
            val radius = if (!stroke.isHighlighter && p0.pressure >= 0f) {
                StrokeGeometry.effectiveWidthForPressure(stroke.strokeWidth, p0.pressure) * 0.5f
            } else {
                stroke.strokeWidth * 0.5f
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = baseColor
                if (stroke.isHighlighter) alpha = 95
            }
            canvas.drawCircle(p0.x, p0.y, radius, dotPaint)
            return
        }

        val cached = if (useCache) {
            val cacheKey = strokeCacheKey(stroke)
            strokePathCache.get(cacheKey) ?: buildStrokeGeometry(stroke).also {
                strokePathCache.put(cacheKey, it)
            }
        } else {
            buildStrokeGeometry(stroke)
        }

        if (cached.isFilledVariableWidth) {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = baseColor
            }
            canvas.drawPath(cached.path, fillPaint)
        } else {
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = stroke.strokeWidth
                strokeCap = if (stroke.isHighlighter) Paint.Cap.SQUARE else Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = baseColor
                if (stroke.isHighlighter) {
                    alpha = 95
                }
            }
            canvas.drawPath(cached.path, strokePaint)
        }
    }

    private fun strokeCacheKey(stroke: CanvasElement.Stroke): String {
        val pts = stroke.points
        val first = pts.first()
        val last = pts.last()
        return "${stroke.id}_${pts.size}_${first.x.toInt()}_${first.y.toInt()}_${last.x.toInt()}_${last.y.toInt()}_${stroke.strokeWidth}_${stroke.isHighlighter}"
    }

    private fun buildStrokeGeometry(stroke: CanvasElement.Stroke): CachedStrokePath {
        val smoothed = StrokeGeometry.smoothCornerPreserving(stroke.points)
        val hasPressure = !stroke.isHighlighter && smoothed.any { it.pressure >= 0f }

        if (hasPressure) {
            val path = buildVariablePressurePath(smoothed, stroke.strokeWidth)
            return CachedStrokePath(path = path, isFilledVariableWidth = true)
        }

        // Dense faithful polyline path with round joins — preserves sharp corners accurately
        val path = Path()
        path.moveTo(smoothed[0].x, smoothed[0].y)
        for (i in 1 until smoothed.size) {
            path.lineTo(smoothed[i].x, smoothed[i].y)
        }
        return CachedStrokePath(path = path, isFilledVariableWidth = false)
    }

    /**
     * Builds a single clockwise-wound filled Path from variable-pressure points using
     * rounded vertex caps and tapered connecting trapezoids so Skia renders the whole
     * pressure-sensitive pen stroke in 1 draw call with zero winding artifacts.
     */
    private fun buildVariablePressurePath(
        points: List<PointFData>,
        baseWidth: Float
    ): Path {
        val path = Path().apply {
            fillType = Path.FillType.WINDING
        }

        var prev = points[0]
        var prevR = StrokeGeometry.effectiveWidthForPressure(baseWidth, prev.pressure) * 0.5f
        path.addCircle(prev.x, prev.y, prevR, Path.Direction.CW)

        for (i in 1 until points.size) {
            val curr = points[i]
            val currR = StrokeGeometry.effectiveWidthForPressure(baseWidth, curr.pressure) * 0.5f
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val dist = hypot(dx, dy)

            if (dist > 0.05f && dist > abs(currR - prevR)) {
                val nx = -dy / dist
                val ny = dx / dist
                // Strictly clockwise in Android Y-down coordinates so WINDING never cancels
                path.moveTo(prev.x - nx * prevR, prev.y - ny * prevR)
                path.lineTo(curr.x - nx * currR, curr.y - ny * currR)
                path.lineTo(curr.x + nx * currR, curr.y + ny * currR)
                path.lineTo(prev.x + nx * prevR, prev.y + ny * prevR)
                path.close()
            }

            path.addCircle(curr.x, curr.y, currR, Path.Direction.CW)
            prev = curr
            prevR = currR
        }
        return path
    }

    private fun drawShape(canvas: Canvas, shape: CanvasElement.Shape) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = shape.strokeWidth
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = shape.colorArgb.toInt()
        }

        val left = min(shape.startX, shape.endX)
        val top = min(shape.startY, shape.endY)
        val right = max(shape.startX, shape.endX)
        val bottom = max(shape.startY, shape.endY)
        val rectF = RectF(left, top, right, bottom)

        when (shape.kind) {
            ShapeKind.RECTANGLE -> {
                canvas.drawRoundRect(rectF, 10f, 10f, paint)
            }
            ShapeKind.CIRCLE -> {
                canvas.drawOval(rectF, paint)
            }
            ShapeKind.LINE -> {
                canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
            }
            ShapeKind.ARROW -> {
                canvas.drawLine(shape.startX, shape.startY, shape.endX, shape.endY, paint)
                val angle = atan2(
                    (shape.endY - shape.startY).toDouble(),
                    (shape.endX - shape.startX).toDouble()
                )
                val headLength = max(16f, shape.strokeWidth * 4.2f)
                val wingAngle = Math.toRadians(28.0)
                val x1 = shape.endX - (headLength * cos(angle - wingAngle)).toFloat()
                val y1 = shape.endY - (headLength * sin(angle - wingAngle)).toFloat()
                val x2 = shape.endX - (headLength * cos(angle + wingAngle)).toFloat()
                val y2 = shape.endY - (headLength * sin(angle + wingAngle)).toFloat()

                val arrowHead = Path().apply {
                    moveTo(x1, y1)
                    lineTo(shape.endX, shape.endY)
                    lineTo(x2, y2)
                }
                canvas.drawPath(arrowHead, paint)
            }
        }
    }

    private fun drawTextNote(
        canvas: Canvas,
        note: CanvasElement.TextNote,
        customTypeface: Typeface?
    ) {
        val padding = if (note.isSticky) 18f else 8f
        val rect = RectF(note.x, note.y, note.x + note.width, note.y + note.height)

        if (note.isSticky) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0x1E0F172A
            }
            canvas.drawRoundRect(
                RectF(rect.left + 3f, rect.top + 6f, rect.right + 3f, rect.bottom + 6f),
                14f,
                14f,
                shadowPaint
            )

            val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = note.cardColorArgb.toInt()
            }
            canvas.drawRoundRect(rect, 14f, 14f, cardPaint)

            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1.2f
                color = 0x220F172A
            }
            canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

            val topAccentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0x140F172A
            }
            canvas.save()
            val clipPath = Path().apply {
                addRoundRect(rect, 14f, 14f, Path.Direction.CW)
            }
            canvas.clipPath(clipPath)
            canvas.drawRect(rect.left, rect.top, rect.right, rect.top + 10f, topAccentPaint)
            canvas.restore()
        }

        if (note.text.isBlank()) return

        val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = note.textColorArgb.toInt()
            textSize = note.fontSizeSp
            if (customTypeface != null) {
                typeface = customTypeface
            }
        }

        val availableWidth = max(40, (note.width - padding * 2f).toInt())
        val staticLayout = StaticLayout.Builder
            .obtain(note.text, 0, note.text.length, textPaint, availableWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.22f)
            .setIncludePad(false)
            .build()

        canvas.save()
        canvas.clipRect(rect)
        canvas.translate(
            note.x + padding,
            note.y + padding + (if (note.isSticky) 4f else 0f)
        )
        staticLayout.draw(canvas)
        canvas.restore()
    }

    private fun drawImageItem(
        canvas: Canvas,
        image: CanvasElement.ImageItem,
        bitmapProvider: (String) -> Bitmap?
    ) {
        val rect = RectF(image.x, image.y, image.x + image.width, image.y + image.height)
        val bmp = bitmapProvider(image.filePath)

        if (image.isCustomCutout) {
            if (bmp != null && !bmp.isRecycled) {
                val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                canvas.drawBitmap(bmp, null, rect, bmpPaint)
            } else {
                val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 1.5f
                    color = 0x441E293B
                }
                canvas.drawRoundRect(rect, 12f, 12f, placeholderPaint)
            }
            return
        }

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x1A0F172A
        }
        canvas.drawRoundRect(
            RectF(rect.left + 2f, rect.top + 5f, rect.right + 2f, rect.bottom + 5f),
            12f,
            12f,
            shadowPaint
        )

        val frameBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
        }
        canvas.drawRoundRect(rect, 12f, 12f, frameBgPaint)

        if (bmp != null && !bmp.isRecycled) {
            val bmpPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.save()
            val clip = Path().apply {
                addRoundRect(rect, 12f, 12f, Path.Direction.CW)
            }
            canvas.clipPath(clip)
            canvas.drawBitmap(bmp, null, rect, bmpPaint)
            canvas.restore()
        } else {
            val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = 0xFFE6E4DD.toInt()
            }
            canvas.drawRoundRect(rect, 12f, 12f, placeholderPaint)
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            color = 0x261E293B
        }
        canvas.drawRoundRect(rect, 12f, 12f, borderPaint)
    }

    fun getResizeHandleCenter(bounds: Rect, cameraScale: Float): Pair<Float, Float> {
        val pad = 6f / cameraScale.coerceAtLeast(0.25f)
        return Pair(bounds.right + pad, bounds.bottom + pad)
    }

    private fun drawSelectionFrame(
        canvas: Canvas,
        bounds: Rect,
        cameraScale: Float
    ) {
        val safeScale = cameraScale.coerceAtLeast(0.25f)
        val pad = 6f / safeScale
        val strokeW = 2f / safeScale
        val dashLen = 7f / safeScale

        val frameRect = RectF(
            bounds.left - pad,
            bounds.top - pad,
            bounds.right + pad,
            bounds.bottom + pad
        )

        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = SELECTION_COLOR
            pathEffect = DashPathEffect(floatArrayOf(dashLen, dashLen * 0.7f), 0f)
        }
        canvas.drawRoundRect(frameRect, 8f / safeScale, 8f / safeScale, framePaint)

        val cornerRadius = 4.5f / safeScale
        val cornerFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
        }
        val cornerBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = SELECTION_COLOR
        }

        val corners = listOf(
            Pair(frameRect.left, frameRect.top),
            Pair(frameRect.right, frameRect.top),
            Pair(frameRect.left, frameRect.bottom)
        )
        for ((cx, cy) in corners) {
            canvas.drawCircle(cx, cy, cornerRadius, cornerFill)
            canvas.drawCircle(cx, cy, cornerRadius, cornerBorder)
        }

        // Prominent bottom-right Resize Handle
        val handleRadius = 10f / safeScale
        val handleFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = SELECTION_COLOR
        }
        val handleInner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFFFFFF.toInt()
        }
        canvas.drawCircle(frameRect.right, frameRect.bottom, handleRadius, handleFill)
        canvas.drawCircle(frameRect.right, frameRect.bottom, handleRadius * 0.42f, handleInner)
    }
}
