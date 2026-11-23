package com.example.canvas

import com.example.model.PointFData
import kotlin.math.hypot

/**
 * Corner-preserving light stroke smoothing & stylus pressure width utilities.
 * Keeps real handwriting shapes (sharp turns, hooks, small loops) faithful to
 * the user's true stylus trajectory while removing micro digitizer jitter.
 */
object StrokeGeometry {

    // Turning angle cosine threshold (~35 degrees: cos(35°) ≈ 0.819).
    // Points sharper than this angle are kept 100% untouched.
    private const val SHARP_CORNER_COS_THRESHOLD = 0.82f

    // Width multiplier bounds relative to the user's selected pen width.
    // Normal writing pressure (~0.6) maps cleanly to ~1.0x selected width.
    const val MIN_PRESSURE_WIDTH_FACTOR = 0.45f
    const val MAX_PRESSURE_WIDTH_FACTOR = 1.35f

    /**
     * Maps a stylus pressure value in [0f, 1f] to a smooth ergonomic stroke width.
     * Returns [baseWidth] unchanged when [pressure] < 0f (constant-width mode).
     */
    fun effectiveWidthForPressure(baseWidth: Float, pressure: Float): Float {
        if (pressure < 0f) return baseWidth
        val clamped = pressure.coerceIn(0f, 1f)
        val factor = MIN_PRESSURE_WIDTH_FACTOR +
            (MAX_PRESSURE_WIDTH_FACTOR - MIN_PRESSURE_WIDTH_FACTOR) * clamped
        return (baseWidth * factor).coerceAtLeast(0.8f)
    }

    /**
     * Applies an ultra-light 3-point weighted filter (0.15 / 0.70 / 0.15) ONLY
     * to smooth shallow segments, preserving endpoints and sharp handwriting corners.
     */
    fun smoothCornerPreserving(points: List<PointFData>): List<PointFData> {
        val count = points.size
        if (count < 3) return points

        val out = ArrayList<PointFData>(count)
        out.add(points[0])

        for (i in 1 until count - 1) {
            val prev = points[i - 1]
            val curr = points[i]
            val next = points[i + 1]

            val v1x = curr.x - prev.x
            val v1y = curr.y - prev.y
            val v2x = next.x - curr.x
            val v2y = next.y - curr.y
            val len1 = hypot(v1x, v1y)
            val len2 = hypot(v2x, v2y)

            if (len1 < 0.001f || len2 < 0.001f) {
                out.add(curr)
                continue
            }

            val cosAngle = (v1x * v2x + v1y * v2y) / (len1 * len2)
            if (cosAngle <= SHARP_CORNER_COS_THRESHOLD) {
                // Sharp turn or hook: preserve exact stylus coordinate
                out.add(curr)
            } else {
                // Gentle curve: apply subtle jitter damping
                out.add(
                    PointFData(
                        x = prev.x * 0.15f + curr.x * 0.70f + next.x * 0.15f,
                        y = prev.y * 0.15f + curr.y * 0.70f + next.y * 0.15f,
                        pressure = curr.pressure
                    )
                )
            }
        }

        out.add(points[count - 1])
        return out
    }
}
