package com.example.canvas

import androidx.compose.ui.geometry.Rect
import com.example.model.CanvasElement
import com.example.model.PointFData
import com.example.model.ShapeKind
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

object StrokeEraserEngine {

    /**
     * Erases only the exact portions of strokes and shapes touched along the eraser sweep
     * from (fromX, fromY) to (toX, toY) with radius [eraserRadiusWorld].
     */
    fun eraseAlongSegment(
        elements: List<CanvasElement>,
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        eraserRadiusWorld: Float,
        idGenerator: (String) -> String
    ): Pair<List<CanvasElement>, Boolean> {
        val safeRadius = eraserRadiusWorld.coerceAtLeast(2f)
        val dist = hypot(toX - fromX, toY - fromY)
        val stepLen = max(2f, safeRadius * 0.45f)
        val steps = max(1, (dist / stepLen).toInt() + 1)

        var currentList = elements
        var anyModified = false

        for (step in 0..steps) {
            val t = if (steps == 0) 1f else step.toFloat() / steps.toFloat()
            val cx = fromX + (toX - fromX) * t
            val cy = fromY + (toY - fromY) * t
            val (nextList, modifiedAtStep) = eraseAtDisk(
                elements = currentList,
                cx = cx,
                cy = cy,
                eraserRadius = safeRadius,
                idGenerator = idGenerator
            )
            if (modifiedAtStep) {
                currentList = nextList
                anyModified = true
            }
        }
        return Pair(currentList, anyModified)
    }

    private fun eraseAtDisk(
        elements: List<CanvasElement>,
        cx: Float,
        cy: Float,
        eraserRadius: Float,
        idGenerator: (String) -> String
    ): Pair<List<CanvasElement>, Boolean> {
        val diskBounds = Rect(
            cx - eraserRadius,
            cy - eraserRadius,
            cx + eraserRadius,
            cy + eraserRadius
        )
        var modified = false
        val output = ArrayList<CanvasElement>(elements.size + 4)

        for (element in elements) {
            when (element) {
                is CanvasElement.Stroke -> {
                    val effectiveRadius = eraserRadius + element.strokeWidth * 0.3f
                    val expandedDisk = diskBounds.inflate(element.strokeWidth * 0.6f)
                    if (!element.bounds().overlaps(expandedDisk)) {
                        output.add(element)
                    } else {
                        val (pieces, wasCut) = splitPointsOutsideCircle(
                            points = element.points,
                            cx = cx,
                            cy = cy,
                            radius = effectiveRadius
                        )
                        if (!wasCut) {
                            output.add(element)
                        } else {
                            modified = true
                            pieces.forEachIndexed { idx, piecePts ->
                                output.add(
                                    element.copy(
                                        id = if (idx == 0) element.id else idGenerator("stk"),
                                        points = piecePts
                                    )
                                )
                            }
                        }
                    }
                }
                is CanvasElement.Shape -> {
                    val effectiveRadius = eraserRadius + element.strokeWidth * 0.3f
                    val expandedDisk = diskBounds.inflate(element.strokeWidth + 4f)
                    if (!element.bounds().overlaps(expandedDisk)) {
                        output.add(element)
                    } else {
                        val chains = shapeToPolylines(element)
                        var shapeTouched = false
                        val survivingChains = ArrayList<List<PointFData>>()
                        for (chain in chains) {
                            val (pieces, wasCut) = splitPointsOutsideCircle(
                                points = chain,
                                cx = cx,
                                cy = cy,
                                radius = effectiveRadius
                            )
                            if (wasCut) shapeTouched = true
                            survivingChains.addAll(pieces)
                        }

                        if (!shapeTouched) {
                            output.add(element)
                        } else {
                            modified = true
                            for (piecePts in survivingChains) {
                                output.add(
                                    CanvasElement.Stroke(
                                        id = idGenerator("stk"),
                                        points = piecePts,
                                        colorArgb = element.colorArgb,
                                        strokeWidth = element.strokeWidth,
                                        isHighlighter = false
                                    )
                                )
                            }
                        }
                    }
                }
                is CanvasElement.TextNote, is CanvasElement.ImageItem -> {
                    output.add(element)
                }
            }
        }
        return Pair(output, modified)
    }

    /**
     * Clips a polyline against a circle (cx, cy, radius), returning only the surviving
     * sub-polylines outside the circle and whether any portion was removed.
     */
    private fun splitPointsOutsideCircle(
        points: List<PointFData>,
        cx: Float,
        cy: Float,
        radius: Float
    ): Pair<List<List<PointFData>>, Boolean> {
        if (points.isEmpty()) return Pair(emptyList(), false)

        if (points.size == 1) {
            val p = points[0]
            return if (hypot(p.x - cx, p.y - cy) <= radius) {
                Pair(emptyList(), true)
            } else {
                Pair(listOf(points), false)
            }
        }

        var wasCut = false
        val resultChains = ArrayList<List<PointFData>>()
        var activeChain = ArrayList<PointFData>()

        fun flushActiveChain() {
            if (activeChain.size >= 2 && polylineLength(activeChain) >= 1.2f) {
                resultChains.add(activeChain)
            }
            activeChain = ArrayList()
        }

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val intervals = clipSegmentOutsideCircle(p1, p2, cx, cy, radius)

            if (intervals.size == 1 && intervals[0].first == 0f && intervals[0].second == 1f) {
                // Entire segment is outside eraser disk
                if (activeChain.isEmpty()) {
                    activeChain.add(p1)
                }
                activeChain.add(p2)
            } else {
                wasCut = true
                if (intervals.isEmpty()) {
                    // Entire segment is inside eraser disk
                    flushActiveChain()
                } else {
                    for ((tStart, tEnd) in intervals) {
                        val segStart = interpolate(p1, p2, tStart)
                        val segEnd = interpolate(p1, p2, tEnd)
                        if (tStart <= 0.0001f) {
                            // Connected to p1
                            if (activeChain.isEmpty()) {
                                activeChain.add(p1)
                            }
                            activeChain.add(segEnd)
                            if (tEnd < 0.9999f) {
                                flushActiveChain()
                            }
                        } else {
                            // Starts after exiting the circle
                            flushActiveChain()
                            activeChain.add(segStart)
                            activeChain.add(segEnd)
                            if (tEnd < 0.9999f) {
                                flushActiveChain()
                            }
                        }
                    }
                }
            }
        }

        flushActiveChain()

        if (!wasCut) {
            return Pair(listOf(points), false)
        }
        return Pair(resultChains, true)
    }

    /**
     * Returns parameter intervals [tStart, tEnd] within [0, 1] where segment P1 -> P2
     * lies strictly outside the circle centered at (cx, cy) with radius r.
     */
    private fun clipSegmentOutsideCircle(
        p1: PointFData,
        p2: PointFData,
        cx: Float,
        cy: Float,
        r: Float
    ): List<Pair<Float, Float>> {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val fx = p1.x - cx
        val fy = p1.y - cy

        val a = dx * dx + dy * dy
        val rSq = r * r
        if (a < 0.0001f) {
            return if (fx * fx + fy * fy <= rSq) emptyList() else listOf(0f to 1f)
        }

        val b = 2f * (fx * dx + fy * dy)
        val c = (fx * fx + fy * fy) - rSq
        val discriminant = b * b - 4f * a * c

        if (discriminant <= 0f) {
            // No crossing or tangent: check midpoint
            val midX = fx + 0.5f * dx
            val midY = fy + 0.5f * dy
            return if (midX * midX + midY * midY <= rSq) emptyList() else listOf(0f to 1f)
        }

        val sqrtD = sqrt(discriminant)
        val t1 = (-b - sqrtD) / (2f * a)
        val t2 = (-b + sqrtD) / (2f * a)

        // Segment inside circle is [t1, t2]. If [t1, t2] does not intersect (0, 1), wholly outside!
        if (t2 <= 0f || t1 >= 1f) {
            return listOf(0f to 1f)
        }

        val result = ArrayList<Pair<Float, Float>>(2)
        if (t1 > 0.001f) {
            result.add(0f to min(1f, t1))
        }
        if (t2 < 0.999f) {
            result.add(max(0f, t2) to 1f)
        }
        return result
    }

    private fun interpolate(p1: PointFData, p2: PointFData, t: Float): PointFData {
        val pressure = when {
            p1.pressure >= 0f && p2.pressure >= 0f -> p1.pressure + (p2.pressure - p1.pressure) * t
            p1.pressure >= 0f -> p1.pressure
            p2.pressure >= 0f -> p2.pressure
            else -> -1f
        }
        return PointFData(
            x = p1.x + (p2.x - p1.x) * t,
            y = p1.y + (p2.y - p1.y) * t,
            pressure = pressure
        )
    }

    private fun polylineLength(pts: List<PointFData>): Float {
        var len = 0f
        for (i in 0 until pts.size - 1) {
            len += hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
        }
        return len
    }

    private fun shapeToPolylines(shape: CanvasElement.Shape): List<List<PointFData>> {
        val left = min(shape.startX, shape.endX)
        val top = min(shape.startY, shape.endY)
        val right = max(shape.startX, shape.endX)
        val bottom = max(shape.startY, shape.endY)

        return when (shape.kind) {
            ShapeKind.LINE -> listOf(
                listOf(
                    PointFData(shape.startX, shape.startY),
                    PointFData(shape.endX, shape.endY)
                )
            )
            ShapeKind.ARROW -> {
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

                listOf(
                    listOf(
                        PointFData(shape.startX, shape.startY),
                        PointFData(shape.endX, shape.endY)
                    ),
                    listOf(
                        PointFData(x1, y1),
                        PointFData(shape.endX, shape.endY),
                        PointFData(x2, y2)
                    )
                )
            }
            ShapeKind.RECTANGLE -> {
                listOf(
                    listOf(
                        PointFData(left, top),
                        PointFData(right, top),
                        PointFData(right, bottom),
                        PointFData(left, bottom),
                        PointFData(left, top)
                    )
                )
            }
            ShapeKind.CIRCLE -> {
                val cx = (left + right) / 2f
                val cy = (top + bottom) / 2f
                val rx = (right - left) / 2f
                val ry = (bottom - top) / 2f
                val segments = 48
                val pts = ArrayList<PointFData>(segments + 1)
                for (i in 0..segments) {
                    val theta = (2.0 * Math.PI * i) / segments
                    pts.add(
                        PointFData(
                            x = cx + (rx * cos(theta)).toFloat(),
                            y = cy + (ry * sin(theta)).toFloat()
                        )
                    )
                }
                listOf(pts)
            }
        }
    }
}
