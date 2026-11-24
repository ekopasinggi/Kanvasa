package com.example.model

import androidx.compose.ui.geometry.Rect
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import org.json.JSONArray
import org.json.JSONObject

enum class CanvasPattern {
    DOTS,
    GRID,
    BLANK;

    companion object {
        fun fromName(value: String?): CanvasPattern =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: DOTS
    }
}

enum class CanvasTool {
    PAN,
    SELECT,
    PEN,
    HIGHLIGHTER,
    ERASER,
    SHAPE,
    TEXT,
    STICKY,
    IMAGE
}

enum class ShapeKind {
    RECTANGLE,
    CIRCLE,
    LINE,
    ARROW;

    companion object {
        fun fromName(value: String?): ShapeKind =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: RECTANGLE
    }
}

enum class ExportFormat(val extension: String, val mimeType: String) {
    PDF("pdf", "application/pdf"),
    PNG("png", "image/png")
}

enum class ExportScope {
    FULL_CONTENT,
    ACTIVE_VIEWPORT
}

enum class PalmRejectionMode {
    AUTO,
    STYLUS_ONLY,
    OFF;

    companion object {
        fun fromName(value: String?): PalmRejectionMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: AUTO
    }
}

enum class SingleTouchBehavior {
    USE_ACTIVE_TOOL,
    PAN_CANVAS
}

object PalmRejectionPolicy {
    fun isStylusOnlyActive(
        mode: PalmRejectionMode,
        isStylusSessionActive: Boolean
    ): Boolean = when (mode) {
        PalmRejectionMode.STYLUS_ONLY -> true
        PalmRejectionMode.AUTO -> isStylusSessionActive
        PalmRejectionMode.OFF -> false
    }

    fun shouldAllowSingleTouchForTool(
        mode: PalmRejectionMode,
        isStylusSessionActive: Boolean,
        tool: CanvasTool
    ): Boolean {
        if (!isStylusOnlyActive(mode, isStylusSessionActive)) return true
        return tool == CanvasTool.PAN || tool == CanvasTool.SELECT
    }

    const val STYLUS_HOVER_PAN_LOCK_MS = 400L

    fun resolveSingleTouchBehavior(
        mode: PalmRejectionMode,
        isStylusSessionActive: Boolean,
        tool: CanvasTool
    ): SingleTouchBehavior {
        if (!isStylusOnlyActive(mode, isStylusSessionActive)) {
            return SingleTouchBehavior.USE_ACTIVE_TOOL
        }
        return if (tool == CanvasTool.SELECT) {
            SingleTouchBehavior.USE_ACTIVE_TOOL
        } else {
            SingleTouchBehavior.PAN_CANVAS
        }
    }

    /**
     * Locks 1-finger panning while the stylus is actively hovering near the screen
     * or within [lockWindowMs] after its last hover/touch event so resting palms
     * never accidentally shift the canvas while writing.
     */
    fun shouldLockSingleTouchPanForStylusHover(
        isStylusHovering: Boolean,
        nowMs: Long,
        lastStylusActivityMs: Long,
        lockWindowMs: Long = STYLUS_HOVER_PAN_LOCK_MS
    ): Boolean {
        if (isStylusHovering) return true
        if (lastStylusActivityMs <= 0L) return false
        val elapsed = nowMs - lastStylusActivityMs
        return elapsed in 0L until lockWindowMs
    }
}

data class PointFData(
    val x: Float,
    val y: Float,
    val pressure: Float = -1f
)

sealed interface CanvasElement {
    val id: String

    fun bounds(): Rect
    fun translatedBy(dx: Float, dy: Float): CanvasElement
    fun resizedByHandle(newRight: Float, newBottom: Float): CanvasElement
    fun hitTest(worldX: Float, worldY: Float, tolerance: Float): Boolean

    data class Stroke(
        override val id: String,
        val points: List<PointFData>,
        val colorArgb: Long,
        val strokeWidth: Float,
        val isHighlighter: Boolean
    ) : CanvasElement {
        override fun bounds(): Rect {
            if (points.isEmpty()) return Rect.Zero
            var minX = Float.POSITIVE_INFINITY
            var minY = Float.POSITIVE_INFINITY
            var maxX = Float.NEGATIVE_INFINITY
            var maxY = Float.NEGATIVE_INFINITY
            for (pt in points) {
                if (pt.x < minX) minX = pt.x
                if (pt.y < minY) minY = pt.y
                if (pt.x > maxX) maxX = pt.x
                if (pt.y > maxY) maxY = pt.y
            }
            val pad = strokeWidth * 0.8f + 4f
            return Rect(minX - pad, minY - pad, maxX + pad, maxY + pad)
        }

        override fun translatedBy(dx: Float, dy: Float): CanvasElement =
            copy(points = points.map { PointFData(it.x + dx, it.y + dy, it.pressure) })

        override fun resizedByHandle(newRight: Float, newBottom: Float): CanvasElement {
            val b = bounds()
            val oldW = max(b.width, 16f)
            val oldH = max(b.height, 16f)
            val targetW = max(newRight - b.left, 24f)
            val targetH = max(newBottom - b.top, 24f)
            val scaleX = targetW / oldW
            val scaleY = targetH / oldH
            return copy(
                points = points.map { pt ->
                    PointFData(
                        x = b.left + (pt.x - b.left) * scaleX,
                        y = b.top + (pt.y - b.top) * scaleY,
                        pressure = pt.pressure
                    )
                }
            )
        }

        override fun hitTest(worldX: Float, worldY: Float, tolerance: Float): Boolean {
            val hitRadius = max(strokeWidth * 0.7f, 8f) + tolerance
            if (!bounds().inflate(tolerance).contains(androidx.compose.ui.geometry.Offset(worldX, worldY))) {
                return false
            }
            if (points.size == 1) {
                return hypot(worldX - points[0].x, worldY - points[0].y) <= hitRadius
            }
            for (i in 0 until points.size - 1) {
                val p1 = points[i]
                val p2 = points[i + 1]
                if (distanceToSegment(worldX, worldY, p1.x, p1.y, p2.x, p2.y) <= hitRadius) {
                    return true
                }
            }
            return false
        }
    }

    data class Shape(
        override val id: String,
        val kind: ShapeKind,
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float,
        val colorArgb: Long,
        val strokeWidth: Float
    ) : CanvasElement {
        override fun bounds(): Rect {
            val pad = strokeWidth + 6f
            return Rect(
                left = min(startX, endX) - pad,
                top = min(startY, endY) - pad,
                right = max(startX, endX) + pad,
                bottom = max(startY, endY) + pad
            )
        }

        override fun translatedBy(dx: Float, dy: Float): CanvasElement =
            copy(
                startX = startX + dx,
                startY = startY + dy,
                endX = endX + dx,
                endY = endY + dy
            )

        override fun resizedByHandle(newRight: Float, newBottom: Float): CanvasElement {
            val left = min(startX, endX)
            val top = min(startY, endY)
            val clampedRight = max(newRight, left + 28f)
            val clampedBottom = max(newBottom, top + 28f)
            return if (kind == ShapeKind.LINE || kind == ShapeKind.ARROW) {
                val startIsLeft = startX <= endX
                val startIsTop = startY <= endY
                copy(
                    startX = if (startIsLeft) left else clampedRight,
                    startY = if (startIsTop) top else clampedBottom,
                    endX = if (startIsLeft) clampedRight else left,
                    endY = if (startIsTop) clampedBottom else top
                )
            } else {
                copy(
                    startX = left,
                    startY = top,
                    endX = clampedRight,
                    endY = clampedBottom
                )
            }
        }

        override fun hitTest(worldX: Float, worldY: Float, tolerance: Float): Boolean {
            val hitPad = max(strokeWidth, 10f) + tolerance
            return when (kind) {
                ShapeKind.LINE, ShapeKind.ARROW ->
                    distanceToSegment(worldX, worldY, startX, startY, endX, endY) <= hitPad
                ShapeKind.RECTANGLE, ShapeKind.CIRCLE -> {
                    val outer = bounds().inflate(tolerance)
                    outer.contains(androidx.compose.ui.geometry.Offset(worldX, worldY))
                }
            }
        }
    }

    data class TextNote(
        override val id: String,
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val fontSizeSp: Float,
        val textColorArgb: Long,
        val cardColorArgb: Long,
        val isSticky: Boolean
    ) : CanvasElement {
        override fun bounds(): Rect = Rect(x, y, x + width, y + height)

        override fun translatedBy(dx: Float, dy: Float): CanvasElement =
            copy(x = x + dx, y = y + dy)

        override fun resizedByHandle(newRight: Float, newBottom: Float): CanvasElement {
            val minW = if (isSticky) 140f else 110f
            val minH = if (isSticky) 120f else 56f
            return copy(
                width = max(newRight - x, minW),
                height = max(newBottom - y, minH)
            )
        }

        override fun hitTest(worldX: Float, worldY: Float, tolerance: Float): Boolean =
            bounds().inflate(tolerance).contains(androidx.compose.ui.geometry.Offset(worldX, worldY))
    }

    data class ImageItem(
        override val id: String,
        val filePath: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val aspectRatio: Float,
        val isCustomCutout: Boolean = false
    ) : CanvasElement {
        override fun bounds(): Rect = Rect(x, y, x + width, y + height)

        override fun translatedBy(dx: Float, dy: Float): CanvasElement =
            copy(x = x + dx, y = y + dy)

        override fun resizedByHandle(newRight: Float, newBottom: Float): CanvasElement {
            val safeRatio = if (aspectRatio > 0.05f) aspectRatio else 1f
            val newW = max(newRight - x, 80f)
            val newH = max(newW / safeRatio, 60f)
            return copy(width = newW, height = newH)
        }

        override fun hitTest(worldX: Float, worldY: Float, tolerance: Float): Boolean =
            bounds().inflate(tolerance).contains(androidx.compose.ui.geometry.Offset(worldX, worldY))
    }
}

private fun distanceToSegment(
    px: Float,
    py: Float,
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float
): Float {
    val dx = x2 - x1
    val dy = y2 - y1
    val lenSq = dx * dx + dy * dy
    if (lenSq < 0.0001f) return hypot(px - x1, py - y1)
    val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
    val projX = x1 + t * dx
    val projY = y1 + t * dy
    return hypot(px - projX, py - projY)
}

object CanvasSerializer {
    private fun safeDouble(value: Float, fallback: Double = 0.0): Double =
        if (value.isFinite()) value.toDouble() else fallback

    private fun safeRounded(value: Float, scale: Double, fallback: Double = 0.0): Double =
        if (value.isFinite()) kotlin.math.round(value * scale) / scale else fallback

    fun encode(elements: List<CanvasElement>): String {
        val array = JSONArray()
        for (element in elements) {
            val obj = JSONObject()
            obj.put("id", element.id)
            when (element) {
                is CanvasElement.Stroke -> {
                    obj.put("type", "stroke")
                    obj.put("colorArgb", element.colorArgb)
                    obj.put("strokeWidth", safeRounded(element.strokeWidth, 100.0, 4.0))
                    obj.put("isHighlighter", element.isHighlighter)
                    val hasPressure = !element.isHighlighter && element.points.any { it.pressure.isFinite() && it.pressure >= 0f }
                    val pts = JSONArray()
                    if (hasPressure) {
                        for (pt in element.points) {
                            pts.put(safeRounded(pt.x, 100.0, 0.0))
                            pts.put(safeRounded(pt.y, 100.0, 0.0))
                            pts.put(
                                if (pt.pressure.isFinite() && pt.pressure >= 0f) {
                                    safeRounded(pt.pressure, 1000.0, 0.5)
                                } else {
                                    -1.0
                                }
                            )
                        }
                        obj.put("pts3", pts)
                    } else {
                        for (pt in element.points) {
                            pts.put(safeRounded(pt.x, 100.0, 0.0))
                            pts.put(safeRounded(pt.y, 100.0, 0.0))
                        }
                        obj.put("pts2", pts)
                    }
                }
                is CanvasElement.Shape -> {
                    obj.put("type", "shape")
                    obj.put("kind", element.kind.name)
                    obj.put("startX", safeDouble(element.startX))
                    obj.put("startY", safeDouble(element.startY))
                    obj.put("endX", safeDouble(element.endX, 100.0))
                    obj.put("endY", safeDouble(element.endY, 100.0))
                    obj.put("colorArgb", element.colorArgb)
                    obj.put("strokeWidth", safeDouble(element.strokeWidth, 4.0))
                }
                is CanvasElement.TextNote -> {
                    obj.put("type", "text_note")
                    obj.put("text", element.text)
                    obj.put("x", safeDouble(element.x))
                    obj.put("y", safeDouble(element.y))
                    obj.put("width", safeDouble(element.width, 220.0))
                    obj.put("height", safeDouble(element.height, 140.0))
                    obj.put("fontSizeSp", safeDouble(element.fontSizeSp, 18.0))
                    obj.put("textColorArgb", element.textColorArgb)
                    obj.put("cardColorArgb", element.cardColorArgb)
                    obj.put("isSticky", element.isSticky)
                }
                is CanvasElement.ImageItem -> {
                    obj.put("type", "image")
                    obj.put("filePath", element.filePath)
                    obj.put("x", safeDouble(element.x))
                    obj.put("y", safeDouble(element.y))
                    obj.put("width", safeDouble(element.width, 260.0))
                    obj.put("height", safeDouble(element.height, 200.0))
                    obj.put("aspectRatio", safeDouble(element.aspectRatio, 1.3))
                    obj.put("isCustomCutout", element.isCustomCutout)
                }
            }
            array.put(obj)
        }
        return array.toString()
    }

    /**
     * Produces a lightweight downsampled subset of elements (max 24 items, max 28 pts/stroke)
     * so gallery thumbnails load instantaneously with tiny memory footprint.
     */
    fun buildPreviewElements(elements: List<CanvasElement>): List<CanvasElement> {
        if (elements.isEmpty()) return emptyList()
        val maxItems = 24
        val step = max(1, elements.size / maxItems)
        val sampled = ArrayList<CanvasElement>(min(elements.size, maxItems))
        var idx = 0
        while (idx < elements.size && sampled.size < maxItems) {
            val el = elements[idx]
            if (el is CanvasElement.Stroke && el.points.size > 28) {
                val pts = el.points
                val pStep = max(1, pts.size / 26)
                val reduced = ArrayList<PointFData>(28)
                var pIdx = 0
                while (pIdx < pts.size - 1) {
                    val p = pts[pIdx]
                    reduced.add(PointFData(p.x, p.y, -1f))
                    pIdx += pStep
                }
                val last = pts.last()
                reduced.add(PointFData(last.x, last.y, -1f))
                sampled.add(el.copy(points = reduced))
            } else {
                sampled.add(el)
            }
            idx += step
        }
        return sampled
    }

    fun encodePreview(elements: List<CanvasElement>): String =
        encode(buildPreviewElements(elements))

    fun decode(json: String): List<CanvasElement> {
        if (json.isBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            val result = ArrayList<CanvasElement>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").ifBlank { "el_$i" }
                when (obj.optString("type")) {
                    "stroke" -> {
                        val pts = decodeStrokePoints(obj)
                        if (pts.isNotEmpty()) {
                            result.add(
                                CanvasElement.Stroke(
                                    id = id,
                                    points = pts,
                                    colorArgb = obj.optLong("colorArgb", 0xFF1E293BL),
                                    strokeWidth = obj.optDouble("strokeWidth", 4.0).toFloat(),
                                    isHighlighter = obj.optBoolean("isHighlighter", false)
                                )
                            )
                        }
                    }
                    "shape" -> {
                        result.add(
                            CanvasElement.Shape(
                                id = id,
                                kind = ShapeKind.fromName(obj.optString("kind")),
                                startX = obj.optDouble("startX", 0.0).toFloat(),
                                startY = obj.optDouble("startY", 0.0).toFloat(),
                                endX = obj.optDouble("endX", 100.0).toFloat(),
                                endY = obj.optDouble("endY", 100.0).toFloat(),
                                colorArgb = obj.optLong("colorArgb", 0xFF1E293BL),
                                strokeWidth = obj.optDouble("strokeWidth", 4.0).toFloat()
                            )
                        )
                    }
                    "text_note" -> {
                        result.add(
                            CanvasElement.TextNote(
                                id = id,
                                text = obj.optString("text", ""),
                                x = obj.optDouble("x", 0.0).toFloat(),
                                y = obj.optDouble("y", 0.0).toFloat(),
                                width = obj.optDouble("width", 220.0).toFloat(),
                                height = obj.optDouble("height", 140.0).toFloat(),
                                fontSizeSp = obj.optDouble("fontSizeSp", 18.0).toFloat(),
                                textColorArgb = obj.optLong("textColorArgb", 0xFF1E293BL),
                                cardColorArgb = obj.optLong("cardColorArgb", 0xFFFEF08AL),
                                isSticky = obj.optBoolean("isSticky", false)
                            )
                        )
                    }
                    "image" -> {
                        val path = obj.optString("filePath", "")
                        if (path.isNotBlank()) {
                            result.add(
                                CanvasElement.ImageItem(
                                    id = id,
                                    filePath = path,
                                    x = obj.optDouble("x", 0.0).toFloat(),
                                    y = obj.optDouble("y", 0.0).toFloat(),
                                    width = obj.optDouble("width", 260.0).toFloat(),
                                    height = obj.optDouble("height", 200.0).toFloat(),
                                    aspectRatio = obj.optDouble("aspectRatio", 1.3).toFloat(),
                                    isCustomCutout = obj.optBoolean("isCustomCutout", false)
                                )
                            )
                        }
                    }
                }
            }
            result
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun decodeStrokePoints(obj: JSONObject): List<PointFData> {
        val pts3 = obj.optJSONArray("pts3")
        if (pts3 != null) {
            val count = pts3.length() / 3
            val list = ArrayList<PointFData>(count)
            var i = 0
            while (i + 2 < pts3.length()) {
                list.add(
                    PointFData(
                        x = pts3.optDouble(i, 0.0).toFloat(),
                        y = pts3.optDouble(i + 1, 0.0).toFloat(),
                        pressure = pts3.optDouble(i + 2, -1.0).toFloat()
                    )
                )
                i += 3
            }
            return list
        }

        val pts2 = obj.optJSONArray("pts2")
        if (pts2 != null) {
            val count = pts2.length() / 2
            val list = ArrayList<PointFData>(count)
            var i = 0
            while (i + 1 < pts2.length()) {
                list.add(
                    PointFData(
                        x = pts2.optDouble(i, 0.0).toFloat(),
                        y = pts2.optDouble(i + 1, 0.0).toFloat(),
                        pressure = -1f
                    )
                )
                i += 2
            }
            return list
        }

        // Legacy object-array format: [{"x":..,"y":..,"p":..}]
        val ptsArr = obj.optJSONArray("points") ?: return emptyList()
        val pts = ArrayList<PointFData>(ptsArr.length())
        for (j in 0 until ptsArr.length()) {
            val p = ptsArr.optJSONObject(j) ?: continue
            val pressure = if (p.has("p")) {
                p.optDouble("p", -1.0).toFloat()
            } else {
                -1f
            }
            pts.add(
                PointFData(
                    x = p.optDouble("x", 0.0).toFloat(),
                    y = p.optDouble("y", 0.0).toFloat(),
                    pressure = pressure
                )
            )
        }
        return pts
    }
}
