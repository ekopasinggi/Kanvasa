package com.example

import com.example.model.CanvasTool
import com.example.model.PalmRejectionMode
import com.example.model.PalmRejectionPolicy
import com.example.model.SingleTouchBehavior
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Local JVM unit tests for Kanvasa core domain logic.
 */
class ExampleUnitTest {
    @Test
    fun palmRejectionMode_parsesSafelyWithAutoDefault() {
        assertEquals(PalmRejectionMode.AUTO, PalmRejectionMode.fromName(null))
        assertEquals(PalmRejectionMode.AUTO, PalmRejectionMode.fromName("unknown"))
        assertEquals(PalmRejectionMode.STYLUS_ONLY, PalmRejectionMode.fromName("STYLUS_ONLY"))
        assertEquals(PalmRejectionMode.OFF, PalmRejectionMode.fromName("off"))
    }

    @Test
    fun palmRejectionPolicy_enforcesAutoStylusOnlyAndOffRules() {
        val drawingTools = listOf(
            CanvasTool.PEN,
            CanvasTool.HIGHLIGHTER,
            CanvasTool.ERASER,
            CanvasTool.SHAPE,
            CanvasTool.TEXT,
            CanvasTool.STICKY
        )
        val navTools = listOf(CanvasTool.PAN, CanvasTool.SELECT)

        // AUTO before stylus detected -> allows finger drawing
        assertFalse(PalmRejectionPolicy.isStylusOnlyActive(PalmRejectionMode.AUTO, false))
        (drawingTools + navTools).forEach {
            assertTrue(PalmRejectionPolicy.shouldAllowSingleTouchForTool(PalmRejectionMode.AUTO, false, it))
            assertEquals(
                SingleTouchBehavior.USE_ACTIVE_TOOL,
                PalmRejectionPolicy.resolveSingleTouchBehavior(PalmRejectionMode.AUTO, false, it)
            )
        }

        // AUTO after stylus detected -> 1-finger pans canvas for drawing/pan tools, SELECT remains interactive
        assertTrue(PalmRejectionPolicy.isStylusOnlyActive(PalmRejectionMode.AUTO, true))
        (drawingTools + CanvasTool.PAN).forEach {
            assertEquals(
                SingleTouchBehavior.PAN_CANVAS,
                PalmRejectionPolicy.resolveSingleTouchBehavior(PalmRejectionMode.AUTO, true, it)
            )
        }
        assertEquals(
            SingleTouchBehavior.USE_ACTIVE_TOOL,
            PalmRejectionPolicy.resolveSingleTouchBehavior(PalmRejectionMode.AUTO, true, CanvasTool.SELECT)
        )
        drawingTools.forEach {
            assertFalse(PalmRejectionPolicy.shouldAllowSingleTouchForTool(PalmRejectionMode.AUTO, true, it))
        }
        navTools.forEach {
            assertTrue(PalmRejectionPolicy.shouldAllowSingleTouchForTool(PalmRejectionMode.AUTO, true, it))
        }

        // STYLUS_ONLY -> 1-finger always pans canvas for drawing/pan tools immediately
        assertTrue(PalmRejectionPolicy.isStylusOnlyActive(PalmRejectionMode.STYLUS_ONLY, false))
        (drawingTools + CanvasTool.PAN).forEach {
            assertEquals(
                SingleTouchBehavior.PAN_CANVAS,
                PalmRejectionPolicy.resolveSingleTouchBehavior(PalmRejectionMode.STYLUS_ONLY, false, it)
            )
        }
        assertEquals(
            SingleTouchBehavior.USE_ACTIVE_TOOL,
            PalmRejectionPolicy.resolveSingleTouchBehavior(PalmRejectionMode.STYLUS_ONLY, false, CanvasTool.SELECT)
        )

        // OFF -> always uses active tool with finger
        assertFalse(PalmRejectionPolicy.isStylusOnlyActive(PalmRejectionMode.OFF, true))
        (drawingTools + navTools).forEach {
            assertTrue(PalmRejectionPolicy.shouldAllowSingleTouchForTool(PalmRejectionMode.OFF, true, it))
            assertEquals(
                SingleTouchBehavior.USE_ACTIVE_TOOL,
                PalmRejectionPolicy.resolveSingleTouchBehavior(PalmRejectionMode.OFF, true, it)
            )
        }
    }

    @Test
    fun hoverLockPolicy_blocksSingleTouchPanWhileStylusHoveringOrRecent() {
        assertTrue(
            PalmRejectionPolicy.shouldLockSingleTouchPanForStylusHover(
                isStylusHovering = true,
                nowMs = 5000L,
                lastStylusActivityMs = 1000L
            )
        )
        assertTrue(
            PalmRejectionPolicy.shouldLockSingleTouchPanForStylusHover(
                isStylusHovering = false,
                nowMs = 1250L,
                lastStylusActivityMs = 1000L
            )
        )
        assertFalse(
            PalmRejectionPolicy.shouldLockSingleTouchPanForStylusHover(
                isStylusHovering = false,
                nowMs = 1500L,
                lastStylusActivityMs = 1000L
            )
        )
        assertFalse(
            PalmRejectionPolicy.shouldLockSingleTouchPanForStylusHover(
                isStylusHovering = false,
                nowMs = 500L,
                lastStylusActivityMs = 0L
            )
        )
    }

    @Test
    fun strokeGeometry_preservesSharpCornersAndModulatesStylusPressure() {
        // Constant width when pressure < 0
        assertEquals(6f, com.example.canvas.StrokeGeometry.effectiveWidthForPressure(6f, -1f), 0.001f)
        // Dynamic stylus pressure scales monotonically
        val light = com.example.canvas.StrokeGeometry.effectiveWidthForPressure(10f, 0.1f)
        val medium = com.example.canvas.StrokeGeometry.effectiveWidthForPressure(10f, 0.6f)
        val heavy = com.example.canvas.StrokeGeometry.effectiveWidthForPressure(10f, 1.0f)
        assertTrue("Light pressure must be thinner than medium", light < medium)
        assertTrue("Heavy pressure must be thicker than medium", heavy > medium)
        assertEquals(13.5f, heavy, 0.05f)

        // Sharp 90-degree corner (like letter 'L' or 'r') MUST NOT be shaved or altered
        val sharpCornerStroke = listOf(
            com.example.model.PointFData(0f, 0f, 0.5f),
            com.example.model.PointFData(0f, 20f, 0.7f),
            com.example.model.PointFData(20f, 20f, 0.6f)
        )
        val smoothedCorner = com.example.canvas.StrokeGeometry.smoothCornerPreserving(sharpCornerStroke)
        assertEquals(0f, smoothedCorner[1].x, 0.001f)
        assertEquals(20f, smoothedCorner[1].y, 0.001f)
        assertEquals(0.7f, smoothedCorner[1].pressure, 0.001f)

        // Shallow curve jitter is gently damped without distorting endpoints
        val jitteryLine = listOf(
            com.example.model.PointFData(0f, 0f, 0.5f),
            com.example.model.PointFData(10f, 1.2f, 0.5f),
            com.example.model.PointFData(20f, 0f, 0.5f)
        )
        val smoothedLine = com.example.canvas.StrokeGeometry.smoothCornerPreserving(jitteryLine)
        assertEquals(0f, smoothedLine.first().y, 0.001f)
        assertEquals(0f, smoothedLine.last().y, 0.001f)
        assertTrue(
            "Shallow micro-jitter should be gently reduced",
            smoothedLine[1].y < jitteryLine[1].y && smoothedLine[1].y > 0.5f
        )
    }

    @Test
    fun partialEraser_preservesInterpolatedPressureOnCutStroke() {
        val stroke = com.example.model.CanvasElement.Stroke(
            id = "p_stroke",
            points = listOf(
                com.example.model.PointFData(0f, 0f, 0.2f),
                com.example.model.PointFData(100f, 0f, 1.0f)
            ),
            colorArgb = 0xFF1E293BL,
            strokeWidth = 6f,
            isHighlighter = false
        )
        var seq = 0
        val (result, modified) = com.example.canvas.StrokeEraserEngine.eraseAlongSegment(
            elements = listOf(stroke),
            fromX = 50f,
            fromY = -10f,
            toX = 50f,
            toY = 10f,
            eraserRadiusWorld = 10f,
            idGenerator = { "${it}_${++seq}" }
        )
        assertTrue(modified)
        assertEquals(2, result.size)
        val leftPiece = result[0] as com.example.model.CanvasElement.Stroke
        val rightPiece = result[1] as com.example.model.CanvasElement.Stroke
        assertTrue(leftPiece.points.last().pressure in 0.45f..0.60f)
        assertTrue(rightPiece.points.first().pressure in 0.60f..0.75f)
    }
}

