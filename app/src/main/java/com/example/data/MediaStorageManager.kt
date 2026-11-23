package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.geometry.Rect
import com.example.model.PointFData
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportedImageResult(
    val filePath: String,
    val widthPx: Int,
    val heightPx: Int,
    val aspectRatio: Float,
    val normalizedCropBounds: Rect = Rect(0f, 0f, 1f, 1f)
)

class MediaStorageManager(private val context: Context) {

    private val imageDir: File by lazy {
        File(context.filesDir, "canvas_images").apply {
            if (!exists()) mkdirs()
        }
    }

    private val bitmapCache = object : LruCache<String, Bitmap>(28 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return max(1, value.allocationByteCount / 1024)
        }
    }

    suspend fun importImageFromUri(uri: Uri): ImportedImageResult? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, boundsOptions)
            }
            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) {
                return@withContext null
            }

            val maxEdge = 1600
            var sampleSize = 1
            while (
                boundsOptions.outWidth / sampleSize > maxEdge ||
                boundsOptions.outHeight / sampleSize > maxEdge
            ) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return@withContext null

            saveProcessedBitmap(decoded, Rect(0f, 0f, 1f, 1f))
        } catch (_: Exception) {
            null
        }
    }

    suspend fun cropImageRectangle(
        sourceFilePath: String,
        normRect: Rect
    ): ImportedImageResult? = withContext(Dispatchers.IO) {
        try {
            val src = getOrLoadBitmapSync(sourceFilePath) ?: return@withContext null
            val clampedLeft = normRect.left.coerceIn(0f, 0.95f)
            val clampedTop = normRect.top.coerceIn(0f, 0.95f)
            val clampedRight = normRect.right.coerceIn(clampedLeft + 0.04f, 1f)
            val clampedBottom = normRect.bottom.coerceIn(clampedTop + 0.04f, 1f)

            val pxLeft = (clampedLeft * src.width).roundToInt().coerceIn(0, src.width - 2)
            val pxTop = (clampedTop * src.height).roundToInt().coerceIn(0, src.height - 2)
            val pxRight = (clampedRight * src.width).roundToInt().coerceIn(pxLeft + 2, src.width)
            val pxBottom = (clampedBottom * src.height).roundToInt().coerceIn(pxTop + 2, src.height)

            val cropW = pxRight - pxLeft
            val cropH = pxBottom - pxTop
            if (cropW < 8 || cropH < 8) return@withContext null

            val cropped = Bitmap.createBitmap(src, pxLeft, pxTop, cropW, cropH)
            saveProcessedBitmap(
                bitmap = cropped,
                normalizedBounds = Rect(clampedLeft, clampedTop, clampedRight, clampedBottom)
            )
        } catch (_: Exception) {
            null
        }
    }

    suspend fun cropImageCustomPath(
        sourceFilePath: String,
        normPoints: List<PointFData>
    ): ImportedImageResult? = withContext(Dispatchers.IO) {
        if (normPoints.size < 3) return@withContext null
        try {
            val src = getOrLoadBitmapSync(sourceFilePath) ?: return@withContext null

            var minNormX = 1f
            var minNormY = 1f
            var maxNormX = 0f
            var maxNormY = 0f
            for (pt in normPoints) {
                val nx = pt.x.coerceIn(0f, 1f)
                val ny = pt.y.coerceIn(0f, 1f)
                if (nx < minNormX) minNormX = nx
                if (ny < minNormY) minNormY = ny
                if (nx > maxNormX) maxNormX = nx
                if (ny > maxNormY) maxNormY = ny
            }

            val pxLeft = (minNormX * src.width).roundToInt().coerceIn(0, src.width - 2)
            val pxTop = (minNormY * src.height).roundToInt().coerceIn(0, src.height - 2)
            val pxRight = (maxNormX * src.width).roundToInt().coerceIn(pxLeft + 2, src.width)
            val pxBottom = (maxNormY * src.height).roundToInt().coerceIn(pxTop + 2, src.height)

            val cropW = pxRight - pxLeft
            val cropH = pxBottom - pxTop
            if (cropW < 10 || cropH < 10) return@withContext null

            val output = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)

            val maskPath = Path()
            normPoints.forEachIndexed { index, pt ->
                val localX = pt.x.coerceIn(0f, 1f) * src.width - pxLeft
                val localY = pt.y.coerceIn(0f, 1f) * src.height - pxTop
                if (index == 0) {
                    maskPath.moveTo(localX, localY)
                } else {
                    maskPath.lineTo(localX, localY)
                }
            }
            maskPath.close()

            val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val shaderMatrix = Matrix()
                shaderMatrix.setTranslate(-pxLeft.toFloat(), -pxTop.toFloat())
                setLocalMatrix(shaderMatrix)
            }
            val shaderPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                style = Paint.Style.FILL
                this.shader = shader
            }
            canvas.drawPath(maskPath, shaderPaint)

            saveProcessedBitmap(
                bitmap = output,
                normalizedBounds = Rect(
                    minNormX,
                    minNormY,
                    max(minNormX + 0.02f, min(1f, maxNormX)),
                    max(minNormY + 0.02f, min(1f, maxNormY))
                )
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun saveProcessedBitmap(
        bitmap: Bitmap,
        normalizedBounds: Rect
    ): ImportedImageResult {
        val fileName = "img_${UUID.randomUUID().toString().replace("-", "")}.png"
        val targetFile = File(imageDir, fileName)
        FileOutputStream(targetFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val path = targetFile.absolutePath
        bitmapCache.put(path, bitmap)
        val ratio = bitmap.width.toFloat() / max(1, bitmap.height).toFloat()
        return ImportedImageResult(
            filePath = path,
            widthPx = bitmap.width,
            heightPx = bitmap.height,
            aspectRatio = ratio,
            normalizedCropBounds = normalizedBounds
        )
    }

    fun getCachedBitmap(filePath: String): Bitmap? {
        return bitmapCache.get(filePath)
    }

    suspend fun preloadBitmaps(filePaths: List<String>): Int = withContext(Dispatchers.IO) {
        var loadedCount = 0
        for (path in filePaths) {
            if (path.isNotBlank() && bitmapCache.get(path) == null) {
                val bmp = decodeFromDisk(path)
                if (bmp != null) {
                    bitmapCache.put(path, bmp)
                    loadedCount++
                }
            }
        }
        loadedCount
    }

    fun getOrLoadBitmapSync(filePath: String): Bitmap? {
        bitmapCache.get(filePath)?.let { return it }
        val decoded = decodeFromDisk(filePath) ?: return null
        bitmapCache.put(filePath, decoded)
        return decoded
    }

    suspend fun deleteFiles(filePaths: List<String>) = withContext(Dispatchers.IO) {
        for (path in filePaths) {
            bitmapCache.remove(path)
            runCatching {
                val file = File(path)
                if (file.exists()) file.delete()
            }
        }
    }

    private fun decodeFromDisk(filePath: String): Bitmap? {
        return try {
            val file = File(filePath)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }
}
