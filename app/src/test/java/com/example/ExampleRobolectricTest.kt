package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.canvas.CanvasExporter
import com.example.data.MediaStorageManager
import com.example.model.CanvasElement
import com.example.model.CanvasPattern
import com.example.model.CanvasSerializer
import com.example.model.ExportFormat
import com.example.model.ExportScope
import com.example.model.PointFData
import com.example.model.ShapeKind
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app name matches Kanvasa`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Kanvasa", appName)
    }

    @Test
    fun `canvas elements serialize and deserialize with full fidelity`() {
        val original = listOf(
            CanvasElement.Stroke(
                id = "stk_1",
                points = listOf(PointFData(10f, 20f), PointFData(60f, 95f), PointFData(140f, 110f)),
                colorArgb = 0xFF1E293BL,
                strokeWidth = 5f,
                isHighlighter = false
            ),
            CanvasElement.Stroke(
                id = "stk_hi",
                points = listOf(PointFData(0f, 0f), PointFData(120f, 0f)),
                colorArgb = 0xFFFACC15L,
                strokeWidth = 22f,
                isHighlighter = true
            ),
            CanvasElement.Shape(
                id = "shp_arrow",
                kind = ShapeKind.ARROW,
                startX = 50f,
                startY = 50f,
                endX = 240f,
                endY = 180f,
                colorArgb = 0xFF2563EBL,
                strokeWidth = 4f
            ),
            CanvasElement.TextNote(
                id = "note_sticky",
                text = "Ide Arsitektur Kanvasa",
                x = 300f,
                y = 120f,
                width = 230f,
                height = 170f,
                fontSizeSp = 19f,
                textColorArgb = 0xFF1E293BL,
                cardColorArgb = 0xFFFEF08AL,
                isSticky = true
            )
        )

        val encoded = CanvasSerializer.encode(original)
        val decoded = CanvasSerializer.decode(encoded)
        assertEquals(4, decoded.size)

        val sticky = decoded[3] as CanvasElement.TextNote
        assertEquals("Ide Arsitektur Kanvasa", sticky.text)
        assertTrue(sticky.isSticky)
        assertTrue(sticky.hitTest(350f, 160f, 4f))

        val resized = sticky.resizedByHandle(600f, 360f) as CanvasElement.TextNote
        assertEquals(300f, resized.width, 0.1f)
        assertEquals(240f, resized.height, 0.1f)
    }

    @Test
    fun `exporter generates non-empty PDF and PNG for full content and active viewport`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mediaStorage = MediaStorageManager(context)
        val elements = listOf(
            CanvasElement.Stroke(
                id = "s1",
                points = listOf(PointFData(-200f, -150f), PointFData(450f, 620f)),
                colorArgb = 0xFF1E293BL,
                strokeWidth = 6f,
                isHighlighter = false
            ),
            CanvasElement.TextNote(
                id = "t1",
                text = "Catatan Kuliah Minggu 1",
                x = 500f,
                y = -100f,
                width = 240f,
                height = 160f,
                fontSizeSp = 18f,
                textColorArgb = 0xFF1E293BL,
                cardColorArgb = 0xFFBBF7D0L,
                isSticky = true
            )
        )

        val fullRect = CanvasExporter.computeExportWorldRect(
            elements = elements,
            scope = ExportScope.FULL_CONTENT,
            viewportWidthPx = 1080f,
            viewportHeightPx = 1920f,
            cameraScale = 1f,
            cameraPanX = 0f,
            cameraPanY = 0f
        )
        assertTrue(fullRect.width > 900f)

        val previewBmp = CanvasExporter.generatePreviewBitmap(
            context = context,
            elements = elements,
            pattern = CanvasPattern.DOTS,
            exportWorldRect = fullRect,
            mediaStorage = mediaStorage
        )
        assertNotNull(previewBmp)
        assertTrue(previewBmp.width > 100 && previewBmp.height > 100)

        val pdfFile = File(context.cacheDir, "test_export.pdf")
        val pngFile = File(context.cacheDir, "test_export.png")

        val pdfOk = CanvasExporter.writeExportToUri(
            context = context,
            destinationUri = Uri.fromFile(pdfFile),
            format = ExportFormat.PDF,
            elements = elements,
            pattern = CanvasPattern.GRID,
            exportWorldRect = fullRect,
            mediaStorage = mediaStorage
        )
        val pngOk = CanvasExporter.writeExportToUri(
            context = context,
            destinationUri = Uri.fromFile(pngFile),
            format = ExportFormat.PNG,
            elements = elements,
            pattern = CanvasPattern.DOTS,
            exportWorldRect = fullRect,
            mediaStorage = mediaStorage
        )

        assertTrue(pdfOk && pdfFile.exists() && pdfFile.length() > 100L)
        assertTrue(pngOk && pngFile.exists() && pngFile.length() > 100L)
    }

    @Test
    fun `partial eraser removes only touched segment and respects eraser tip radius`() {
        val originalStroke = CanvasElement.Stroke(
            id = "long_line",
            points = listOf(PointFData(0f, 100f), PointFData(400f, 100f)),
            colorArgb = 0xFF1E293BL,
            strokeWidth = 4f,
            isHighlighter = false
        )

        var seq = 0
        val (smallCutResult, smallModified) = com.example.canvas.StrokeEraserEngine.eraseAlongSegment(
            elements = listOf(originalStroke),
            fromX = 200f,
            fromY = 80f,
            toX = 200f,
            toY = 120f,
            eraserRadiusWorld = 15f,
            idGenerator = { "${it}_${++seq}" }
        )

        assertTrue(smallModified)
        assertEquals(2, smallCutResult.size)
        val leftPieceSmall = smallCutResult[0] as CanvasElement.Stroke
        val rightPieceSmall = smallCutResult[1] as CanvasElement.Stroke
        assertEquals(0f, leftPieceSmall.points.first().x, 0.5f)
        assertEquals(400f, rightPieceSmall.points.last().x, 0.5f)
        val smallGap = rightPieceSmall.points.first().x - leftPieceSmall.points.last().x

        val (largeCutResult, largeModified) = com.example.canvas.StrokeEraserEngine.eraseAlongSegment(
            elements = listOf(originalStroke),
            fromX = 200f,
            fromY = 80f,
            toX = 200f,
            toY = 120f,
            eraserRadiusWorld = 45f,
            idGenerator = { "${it}_${++seq}" }
        )

        assertTrue(largeModified)
        assertEquals(2, largeCutResult.size)
        val leftPieceLarge = largeCutResult[0] as CanvasElement.Stroke
        val rightPieceLarge = largeCutResult[1] as CanvasElement.Stroke
        val largeGap = rightPieceLarge.points.first().x - leftPieceLarge.points.last().x

        assertTrue("Larger eraser radius must erase a wider gap", largeGap > smallGap + 40f)
    }

    @Test
    fun `image cropper supports both rectangle and custom freehand cutout`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val mediaStorage = MediaStorageManager(context)

        // Create a 200x200 solid cobalt test image on disk
        val srcFile = File(context.cacheDir, "crop_source.png")
        val srcBmp = android.graphics.Bitmap.createBitmap(
            200,
            200,
            android.graphics.Bitmap.Config.ARGB_8888
        )
        srcBmp.eraseColor(0xFF2563EB.toInt())
        java.io.FileOutputStream(srcFile).use { out ->
            srcBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }

        // 1. Rectangle Crop (middle 50% -> ~100x100)
        val rectResult = mediaStorage.cropImageRectangle(
            sourceFilePath = srcFile.absolutePath,
            normRect = androidx.compose.ui.geometry.Rect(0.25f, 0.25f, 0.75f, 0.75f)
        )
        assertNotNull(rectResult)
        assertEquals(100, rectResult!!.widthPx)
        assertEquals(100, rectResult.heightPx)

        // 2. Custom Freehand Diamond Crop
        val diamondNorm = listOf(
            PointFData(0.5f, 0.1f),
            PointFData(0.9f, 0.5f),
            PointFData(0.5f, 0.9f),
            PointFData(0.1f, 0.5f)
        )
        val customResult = mediaStorage.cropImageCustomPath(
            sourceFilePath = srcFile.absolutePath,
            normPoints = diamondNorm
        )
        assertNotNull(customResult)
        val cutoutBmp = mediaStorage.getOrLoadBitmapSync(customResult!!.filePath)
        assertNotNull(cutoutBmp)
        // Top-left corner outside diamond must be transparent, center inside diamond must be opaque
        val cornerAlpha = android.graphics.Color.alpha(cutoutBmp!!.getPixel(2, 2))
        val centerAlpha = android.graphics.Color.alpha(
            cutoutBmp.getPixel(cutoutBmp.width / 2, cutoutBmp.height / 2)
        )
        assertEquals(0, cornerAlpha)
        assertEquals(255, centerAlpha)
    }

    @Test
    fun `palm rejection policy blocks finger drawing tools when stylus active or stylus-only`() {
        val drawTools = listOf(
            com.example.model.CanvasTool.PEN,
            com.example.model.CanvasTool.HIGHLIGHTER,
            com.example.model.CanvasTool.ERASER,
            com.example.model.CanvasTool.SHAPE,
            com.example.model.CanvasTool.TEXT,
            com.example.model.CanvasTool.STICKY
        )
        val navTools = listOf(
            com.example.model.CanvasTool.PAN,
            com.example.model.CanvasTool.SELECT
        )

        // AUTO before stylus detected: finger drawing allowed
        org.junit.Assert.assertFalse(
            com.example.model.PalmRejectionPolicy.isStylusOnlyActive(
                mode = com.example.model.PalmRejectionMode.AUTO,
                isStylusSessionActive = false
            )
        )
        for (tool in drawTools + navTools) {
            assertTrue(
                com.example.model.PalmRejectionPolicy.shouldAllowSingleTouchForTool(
                    mode = com.example.model.PalmRejectionMode.AUTO,
                    isStylusSessionActive = false,
                    tool = tool
                )
            )
        }

        // AUTO after stylus detected: finger drawing blocked, pan/select still allowed
        assertTrue(
            com.example.model.PalmRejectionPolicy.isStylusOnlyActive(
                mode = com.example.model.PalmRejectionMode.AUTO,
                isStylusSessionActive = true
            )
        )
        for (tool in drawTools) {
            org.junit.Assert.assertFalse(
                com.example.model.PalmRejectionPolicy.shouldAllowSingleTouchForTool(
                    mode = com.example.model.PalmRejectionMode.AUTO,
                    isStylusSessionActive = true,
                    tool = tool
                )
            )
        }
        for (tool in navTools) {
            assertTrue(
                com.example.model.PalmRejectionPolicy.shouldAllowSingleTouchForTool(
                    mode = com.example.model.PalmRejectionMode.AUTO,
                    isStylusSessionActive = true,
                    tool = tool
                )
            )
        }

        // STYLUS_ONLY: finger drawing always blocked, single touch pans canvas instead
        assertTrue(
            com.example.model.PalmRejectionPolicy.isStylusOnlyActive(
                mode = com.example.model.PalmRejectionMode.STYLUS_ONLY,
                isStylusSessionActive = false
            )
        )
        for (tool in drawTools) {
            org.junit.Assert.assertFalse(
                com.example.model.PalmRejectionPolicy.shouldAllowSingleTouchForTool(
                    mode = com.example.model.PalmRejectionMode.STYLUS_ONLY,
                    isStylusSessionActive = false,
                    tool = tool
                )
            )
            assertEquals(
                com.example.model.SingleTouchBehavior.PAN_CANVAS,
                com.example.model.PalmRejectionPolicy.resolveSingleTouchBehavior(
                    mode = com.example.model.PalmRejectionMode.STYLUS_ONLY,
                    isStylusSessionActive = false,
                    tool = tool
                )
            )
        }

        // OFF: finger drawing always allowed even when stylus was detected
        org.junit.Assert.assertFalse(
            com.example.model.PalmRejectionPolicy.isStylusOnlyActive(
                mode = com.example.model.PalmRejectionMode.OFF,
                isStylusSessionActive = true
            )
        )
        for (tool in drawTools) {
            assertTrue(
                com.example.model.PalmRejectionPolicy.shouldAllowSingleTouchForTool(
                    mode = com.example.model.PalmRejectionMode.OFF,
                    isStylusSessionActive = true,
                    tool = tool
                )
            )
            assertEquals(
                com.example.model.SingleTouchBehavior.USE_ACTIVE_TOOL,
                com.example.model.PalmRejectionPolicy.resolveSingleTouchBehavior(
                    mode = com.example.model.PalmRejectionMode.OFF,
                    isStylusSessionActive = true,
                    tool = tool
                )
            )
        }
    }

    @Test
    fun `canvas preferences persists palm rejection mode`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.data.CanvasPreferences(context)
        assertEquals(com.example.model.PalmRejectionMode.AUTO, prefs.getPalmRejectionMode())

        prefs.setPalmRejectionMode(com.example.model.PalmRejectionMode.STYLUS_ONLY)
        assertEquals(com.example.model.PalmRejectionMode.STYLUS_ONLY, prefs.getPalmRejectionMode())

        prefs.setPalmRejectionMode(com.example.model.PalmRejectionMode.OFF)
        assertEquals(com.example.model.PalmRejectionMode.OFF, prefs.getPalmRejectionMode())
    }
}

