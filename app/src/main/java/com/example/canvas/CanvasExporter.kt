package com.example.canvas

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.compose.ui.geometry.Rect
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.data.MediaStorageManager
import com.example.model.CanvasElement
import com.example.model.CanvasPattern
import com.example.model.ExportFormat
import com.example.model.ExportScope
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CanvasExporter {

    fun computeExportWorldRect(
        elements: List<CanvasElement>,
        scope: ExportScope,
        viewportWidthPx: Float,
        viewportHeightPx: Float,
        cameraScale: Float,
        cameraPanX: Float,
        cameraPanY: Float
    ): Rect {
        val safeScale = cameraScale.coerceAtLeast(0.2f)
        val safeW = max(viewportWidthPx, 600f)
        val safeH = max(viewportHeightPx, 800f)

        val activeViewportRect = Rect(
            left = -cameraPanX / safeScale,
            top = -cameraPanY / safeScale,
            right = (safeW - cameraPanX) / safeScale,
            bottom = (safeH - cameraPanY) / safeScale
        )

        if (scope == ExportScope.ACTIVE_VIEWPORT || elements.isEmpty()) {
            return activeViewportRect
        }

        var minLeft = Float.POSITIVE_INFINITY
        var minTop = Float.POSITIVE_INFINITY
        var maxRight = Float.NEGATIVE_INFINITY
        var maxBottom = Float.NEGATIVE_INFINITY

        for (element in elements) {
            val b = element.bounds()
            if (b.left < minLeft) minLeft = b.left
            if (b.top < minTop) minTop = b.top
            if (b.right > maxRight) maxRight = b.right
            if (b.bottom > maxBottom) maxBottom = b.bottom
        }

        if (!minLeft.isFinite() || !minTop.isFinite()) {
            return activeViewportRect
        }

        val margin = 64f
        val paddedWidth = max(maxRight - minLeft + margin * 2f, 360f)
        val paddedHeight = max(maxBottom - minTop + margin * 2f, 280f)
        val centerX = (minLeft + maxRight) / 2f
        val centerY = (minTop + maxBottom) / 2f

        return Rect(
            left = centerX - paddedWidth / 2f,
            top = centerY - paddedHeight / 2f,
            right = centerX + paddedWidth / 2f,
            bottom = centerY + paddedHeight / 2f
        )
    }

    suspend fun generatePreviewBitmap(
        context: Context,
        elements: List<CanvasElement>,
        pattern: CanvasPattern,
        exportWorldRect: Rect,
        mediaStorage: MediaStorageManager,
        maxPreviewEdgePx: Int = 900
    ): Bitmap = withContext(Dispatchers.Default) {
        renderToBitmapInternal(
            context = context,
            elements = elements,
            pattern = pattern,
            worldRect = exportWorldRect,
            mediaStorage = mediaStorage,
            targetMaxEdgePx = maxPreviewEdgePx
        )
    }

    suspend fun writeExportToUri(
        context: Context,
        destinationUri: Uri,
        format: ExportFormat,
        elements: List<CanvasElement>,
        pattern: CanvasPattern,
        exportWorldRect: Rect,
        mediaStorage: MediaStorageManager
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val stream: OutputStream? = if (destinationUri.scheme == "file") {
                destinationUri.path?.let { path -> FileOutputStream(File(path)) }
            } else {
                context.contentResolver.openOutputStream(destinationUri)
            }
            stream?.use { outputStream ->
                writeExportToStream(
                    context = context,
                    outputStream = outputStream,
                    format = format,
                    elements = elements,
                    pattern = pattern,
                    worldRect = exportWorldRect,
                    mediaStorage = mediaStorage
                )
            } ?: return@withContext false
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun createShareIntent(
        context: Context,
        boardTitle: String,
        format: ExportFormat,
        elements: List<CanvasElement>,
        pattern: CanvasPattern,
        exportWorldRect: Rect,
        mediaStorage: MediaStorageManager
    ): Intent? = withContext(Dispatchers.IO) {
        try {
            val exportDir = File(context.cacheDir, "exports").apply {
                if (!exists()) mkdirs()
            }
            val safeName = boardTitle
                .trim()
                .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
                .replace(Regex("\\s+"), "_")
                .ifBlank { "Kanvasa_Catatan" }

            val outFile = File(exportDir, "${safeName}.${format.extension}")
            FileOutputStream(outFile).use { stream ->
                writeExportToStream(
                    context = context,
                    outputStream = stream,
                    format = format,
                    elements = elements,
                    pattern = pattern,
                    worldRect = exportWorldRect,
                    mediaStorage = mediaStorage
                )
            }

            val authority = "${context.packageName}.fileprovider"
            val contentUri = FileProvider.getUriForFile(context, authority, outFile)

            Intent(Intent.ACTION_SEND).apply {
                type = format.mimeType
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_SUBJECT, boardTitle)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun writeExportToStream(
        context: Context,
        outputStream: OutputStream,
        format: ExportFormat,
        elements: List<CanvasElement>,
        pattern: CanvasPattern,
        worldRect: Rect,
        mediaStorage: MediaStorageManager
    ) {
        val highResBitmap = renderToBitmapInternal(
            context = context,
            elements = elements,
            pattern = pattern,
            worldRect = worldRect,
            mediaStorage = mediaStorage,
            targetMaxEdgePx = 2400
        )

        when (format) {
            ExportFormat.PNG -> {
                highResBitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
            }
            ExportFormat.PDF -> {
                val pageWidth = highResBitmap.width
                val pageHeight = highResBitmap.height
                val nativePdfBuffer = java.io.ByteArrayOutputStream()

                runCatching {
                    val pdfDocument = PdfDocument()
                    try {
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
                        val page = pdfDocument.startPage(pageInfo)
                        page.canvas.drawBitmap(highResBitmap, 0f, 0f, null)
                        pdfDocument.finishPage(page)
                        pdfDocument.writeTo(nativePdfBuffer)
                    } finally {
                        pdfDocument.close()
                    }
                }

                if (nativePdfBuffer.size() > 0) {
                    nativePdfBuffer.writeTo(outputStream)
                } else {
                    writeStandardJpegPdf(highResBitmap, outputStream)
                }
                outputStream.flush()
            }
        }
    }

    private fun writeStandardJpegPdf(bitmap: Bitmap, out: OutputStream) {
        val jpegBuffer = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, jpegBuffer)
        val jpegBytes = jpegBuffer.toByteArray()
        val w = bitmap.width
        val h = bitmap.height

        val pdfBuf = java.io.ByteArrayOutputStream()
        val offsets = IntArray(6)
        fun writeAscii(s: String) = pdfBuf.write(s.toByteArray(Charsets.US_ASCII))

        writeAscii("%PDF-1.4\n")
        offsets[1] = pdfBuf.size()
        writeAscii("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        offsets[2] = pdfBuf.size()
        writeAscii("2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n")

        offsets[3] = pdfBuf.size()
        writeAscii(
            "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $w $h] " +
                "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n"
        )

        offsets[4] = pdfBuf.size()
        writeAscii(
            "4 0 obj\n<< /Type /XObject /Subtype /Image /Width $w /Height $h " +
                "/ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode " +
                "/Length ${jpegBytes.size} >>\nstream\n"
        )
        pdfBuf.write(jpegBytes)
        writeAscii("\nendstream\nendobj\n")

        val contentStream = "q $w 0 0 $h 0 0 cm /Im0 Do Q\n"
        offsets[5] = pdfBuf.size()
        writeAscii("5 0 obj\n<< /Length ${contentStream.length} >>\nstream\n$contentStream\nendstream\nendobj\n")

        val xrefOffset = pdfBuf.size()
        writeAscii("xref\n0 6\n0000000000 65535 f \n")
        for (i in 1..5) {
            writeAscii(String.format(java.util.Locale.US, "%010d 00000 n \n", offsets[i]))
        }
        writeAscii("trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n$xrefOffset\n%%EOF\n")
        pdfBuf.writeTo(out)
    }

    private fun renderToBitmapInternal(
        context: Context,
        elements: List<CanvasElement>,
        pattern: CanvasPattern,
        worldRect: Rect,
        mediaStorage: MediaStorageManager,
        targetMaxEdgePx: Int
    ): Bitmap {
        val bodyTypeface: Typeface? = runCatching {
            ResourcesCompat.getFont(context, R.font.plus_jakarta_sans)
        }.getOrNull()

        val worldW = worldRect.width.coerceAtLeast(100f)
        val worldH = worldRect.height.coerceAtLeast(100f)
        val maxEdge = max(worldW, worldH)
        val desiredScale = if (targetMaxEdgePx > 1200) 2.0f else 1.0f
        val pixelScale = min(desiredScale, targetMaxEdgePx.toFloat() / maxEdge).coerceAtLeast(0.15f)

        val bmpW = max(120, (worldW * pixelScale).roundToInt())
        val bmpH = max(120, (worldH * pixelScale).roundToInt())

        val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.save()
        canvas.scale(pixelScale, pixelScale)
        canvas.translate(-worldRect.left, -worldRect.top)
        CanvasRenderer.renderScene(
            canvas = canvas,
            worldViewport = worldRect,
            pattern = pattern,
            elements = elements,
            showSelectionOverlay = false,
            typeface = bodyTypeface,
            bitmapProvider = { path -> mediaStorage.getOrLoadBitmapSync(path) }
        )
        canvas.restore()
        return bitmap
    }
}
