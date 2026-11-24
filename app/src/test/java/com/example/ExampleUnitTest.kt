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

    @Test
    fun previewBuilder_boundsLargeBoardsForFastGalleryLoading() {
        val denseBoard = (1..120).map { strokeIdx ->
            val pts = (0 until 400).map { p ->
                com.example.model.PointFData(
                    x = strokeIdx * 10f + p * 0.5f,
                    y = p * 0.25f,
                    pressure = 0.6f
                )
            }
            com.example.model.CanvasElement.Stroke(
                id = "s_$strokeIdx",
                points = pts,
                colorArgb = 0xFF1E293BL,
                strokeWidth = 5f,
                isHighlighter = false
            )
        }

        val preview = com.example.model.CanvasSerializer.buildPreviewElements(denseBoard)
        assertTrue("Preview must cap element count to <= 24", preview.size in 1..24)
        preview.forEach { el ->
            val stroke = el as com.example.model.CanvasElement.Stroke
            assertTrue(
                "Each preview stroke must be downsampled to <= 30 points, got ${stroke.points.size}",
                stroke.points.size <= 30
            )
        }
    }

    @Test
    fun boardContentStorage_handlesMultiMegabytePayloadAndRecoversFromBackup() {
        val tempDir = java.nio.file.Files.createTempDirectory("kanvasa_storage_test").toFile()
        try {
            val storage = com.example.data.BoardContentStorage(tempDir)
            // Verify org.json is usable on this JVM classpath before asserting round-trip JSON
            val jsonAvailable = try {
                val arr = org.json.JSONArray()
                arr.put(1)
                arr.length() == 1
            } catch (_: Throwable) {
                false
            }
            if (!jsonAvailable) return

            kotlinx.coroutines.runBlocking {
                // 1. Write initial valid snapshot (exceeding Android's 2 MB SQLite CursorWindow limit when serialized)
                val hugeStrokes = (1..260).map { idx ->
                    val pts = (0 until 400).map { p ->
                        com.example.model.PointFData(
                            x = idx * 12.25f + p * 0.75f,
                            y = idx * 4.5f + p * 0.5f,
                            pressure = 0.35f + (p % 50) * 0.01f
                        )
                    }
                    com.example.model.CanvasElement.Stroke(
                        id = "huge_$idx",
                        points = pts,
                        colorArgb = 0xFF1E293BL,
                        strokeWidth = 4.5f,
                        isHighlighter = false
                    )
                }
                val rawJsonSize = com.example.model.CanvasSerializer.encode(hugeStrokes).length
                assertTrue(
                    "Test payload should exceed 2 MB raw JSON to prove SQLite 2 MB bypass (was $rawJsonSize bytes)",
                    rawJsonSize > 2_000_000
                )

                storage.writeBoardElements(42L, hugeStrokes)
                assertTrue(storage.hasStoredFile(42L))

                // Read back and verify full fidelity
                val loaded = storage.readBoardElements(42L)
                assertEquals(260, loaded?.size)
                val firstLoaded = loaded?.first() as com.example.model.CanvasElement.Stroke
                assertEquals(400, firstLoaded.points.size)
                assertEquals(0.35f, firstLoaded.points.first().pressure, 0.01f)

                // 2. Write a second snapshot so `.bak` contains the huge snapshot, then corrupt primary
                val updated = listOf(
                    com.example.model.CanvasElement.TextNote(
                        id = "note_1",
                        text = "Catatan aman",
                        x = 10f,
                        y = 20f,
                        width = 240f,
                        height = 140f,
                        fontSizeSp = 18f,
                        textColorArgb = 0xFF1E293BL,
                        cardColorArgb = 0xFFFEF08AL,
                        isSticky = true
                    )
                )
                storage.writeBoardElements(42L, updated)
                val primaryFile = java.io.File(tempDir, "board_42.json.gz")
                primaryFile.writeBytes(byteArrayOf(0x00, 0x01, 0x02, 0x7F)) // Simulate truncated/corrupted primary

                // Reading must automatically self-heal from `.bak` (the 260-stroke snapshot) instead of crashing
                val recovered = storage.readBoardElements(42L)
                assertEquals(260, recovered?.size)

                // 3. Verify legacy object-array JSON decoding compatibility
                val legacyJson = """[{"id":"old_1","type":"stroke","colorArgb":4280166715,"strokeWidth":5,"isHighlighter":false,"points":[{"x":1.5,"y":2.5,"p":0.8},{"x":10.0,"y":20.0}]}]"""
                val decodedLegacy = com.example.model.CanvasSerializer.decode(legacyJson)
                assertEquals(1, decodedLegacy.size)
                val legacyStroke = decodedLegacy.first() as com.example.model.CanvasElement.Stroke
                assertEquals(2, legacyStroke.points.size)
                assertEquals(0.8f, legacyStroke.points[0].pressure, 0.01f)
                assertEquals(-1f, legacyStroke.points[1].pressure, 0.01f)

                // 4. Verify delete cleans up all files
                storage.deleteBoardFiles(42L)
                assertFalse(storage.hasStoredFile(42L))
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun stylusShortcutPolicy_togglesLastDrawingToolAndEraserWithDebounce() {
        // From PEN -> ERASER (remembers PEN)
        val (toEraserFromPen, rememberedPen) = com.example.model.StylusShortcutPolicy.resolveToggleTool(
            activeTool = CanvasTool.PEN,
            lastDrawingTool = CanvasTool.PEN
        )
        assertEquals(CanvasTool.ERASER, toEraserFromPen)
        assertEquals(CanvasTool.PEN, rememberedPen)

        // From ERASER -> back to PEN
        val (backToPen, _) = com.example.model.StylusShortcutPolicy.resolveToggleTool(
            activeTool = CanvasTool.ERASER,
            lastDrawingTool = rememberedPen
        )
        assertEquals(CanvasTool.PEN, backToPen)

        // From HIGHLIGHTER -> ERASER (remembers HIGHLIGHTER) -> back to HIGHLIGHTER
        val (toEraserFromHi, rememberedHi) = com.example.model.StylusShortcutPolicy.resolveToggleTool(
            activeTool = CanvasTool.HIGHLIGHTER,
            lastDrawingTool = CanvasTool.PEN
        )
        assertEquals(CanvasTool.ERASER, toEraserFromHi)
        assertEquals(CanvasTool.HIGHLIGHTER, rememberedHi)

        val (backToHi, _) = com.example.model.StylusShortcutPolicy.resolveToggleTool(
            activeTool = CanvasTool.ERASER,
            lastDrawingTool = rememberedHi
        )
        assertEquals(CanvasTool.HIGHLIGHTER, backToHi)

        // Debounce prevents accidental double toggle from rapid bounce
        assertTrue(com.example.model.StylusShortcutPolicy.shouldAcceptToggle(nowMs = 1000L, lastToggleMs = 0L))
        assertFalse(com.example.model.StylusShortcutPolicy.shouldAcceptToggle(nowMs = 1150L, lastToggleMs = 1000L))
        assertTrue(com.example.model.StylusShortcutPolicy.shouldAcceptToggle(nowMs = 1250L, lastToggleMs = 1000L))
    }

    @Test
    fun stylusShortcutPolicy_matchesStandardHidAndCustomCalibratedSignalsSafely() {
        val emptyBinding = com.example.model.StylusCustomBinding()

        // Standard Android API 34 stylus button keycodes
        assertTrue(
            com.example.model.StylusShortcutPolicy.isStylusShortcutKeyEvent(
                keyCode = com.example.model.StylusShortcutPolicy.KEYCODE_STYLUS_BUTTON_PRIMARY,
                scanCode = 0,
                isFromStylusSource = false,
                customBinding = emptyBinding
            )
        )
        // Linux HID BTN_STYLUS scan code
        assertTrue(
            com.example.model.StylusShortcutPolicy.isStylusShortcutKeyEvent(
                keyCode = 0,
                scanCode = com.example.model.StylusShortcutPolicy.SCAN_BTN_STYLUS,
                isFromStylusSource = false,
                customBinding = emptyBinding
            )
        )
        // OEM F-key from stylus source
        assertTrue(
            com.example.model.StylusShortcutPolicy.isStylusShortcutKeyEvent(
                keyCode = 333, // F20
                scanCode = 0,
                isFromStylusSource = true,
                customBinding = emptyBinding
            )
        )
        // Must NEVER hijack reserved system keys (e.g. Back = 4, Volume = 24)
        assertFalse(
            com.example.model.StylusShortcutPolicy.isStylusShortcutKeyEvent(
                keyCode = 4,
                scanCode = 0,
                isFromStylusSource = true,
                customBinding = com.example.model.StylusCustomBinding(keyCode = 4)
            )
        )
        // Custom calibrated OEM binding (e.g. Huawei HarmonyOS custom keycode/scancode)
        val custom = com.example.model.StylusCustomBinding(keyCode = 299, scanCode = 711, label = "M-Pencil")
        assertTrue(
            com.example.model.StylusShortcutPolicy.isStylusShortcutKeyEvent(
                keyCode = 299,
                scanCode = 0,
                isFromStylusSource = false,
                customBinding = custom
            )
        )
        assertTrue(
            com.example.model.StylusShortcutPolicy.isStylusShortcutKeyEvent(
                keyCode = 0,
                scanCode = 711,
                isFromStylusSource = false,
                customBinding = custom
            )
        )
        // MotionEvent stylus primary/secondary button masks
        assertTrue(
            com.example.model.StylusShortcutPolicy.isStylusShortcutMotionButton(
                buttonMask = com.example.model.StylusShortcutPolicy.BUTTON_STYLUS_PRIMARY,
                customBinding = emptyBinding
            )
        )
        assertFalse(
            com.example.model.StylusShortcutPolicy.isStylusShortcutMotionButton(
                buttonMask = 1, // regular mouse primary button
                customBinding = emptyBinding
            )
        )
    }
}

