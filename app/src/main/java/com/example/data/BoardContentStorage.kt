package com.example.data

import android.content.Context
import com.example.model.CanvasElement
import com.example.model.CanvasSerializer
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Atomic, GZIP-compressed file storage for infinite-canvas board payloads.
 *
 * Storing stroke data in dedicated files rather than SQLite TEXT rows eliminates
 * Android's 2 MB SQLite CursorWindow limit (SQLiteBlobTooBigException) so notes
 * can grow arbitrarily large with millions of stylus points without ever crashing
 * or locking the user out on app launch.
 */
class BoardContentStorage(private val boardsDir: File) {

    constructor(context: Context) : this(File(context.applicationContext.filesDir, "canvas_boards"))

    private val previewCache = ConcurrentHashMap<Long, List<CanvasElement>>()
    private val fileLocks = ConcurrentHashMap<Long, Any>()

    private fun lockFor(boardId: Long): Any = fileLocks.computeIfAbsent(boardId) { Any() }

    private fun ensureDir(): File {
        if (!boardsDir.exists()) {
            boardsDir.mkdirs()
        }
        return boardsDir
    }

    private fun primaryFile(boardId: Long): File = File(ensureDir(), "board_${boardId}.json.gz")
    private fun backupFile(boardId: Long): File = File(ensureDir(), "board_${boardId}.json.gz.bak")
    private fun tempFile(boardId: Long): File = File(ensureDir(), "board_${boardId}.json.gz.tmp")
    private fun previewFile(boardId: Long): File = File(ensureDir(), "board_${boardId}.preview.json")
    private fun previewTempFile(boardId: Long): File = File(ensureDir(), "board_${boardId}.preview.json.tmp")

    fun hasStoredFile(boardId: Long): Boolean {
        val primary = File(boardsDir, "board_${boardId}.json.gz")
        val backup = File(boardsDir, "board_${boardId}.json.gz.bak")
        return primary.exists() || backup.exists()
    }

    /**
     * Atomically writes the full board payload (GZIP-compressed) and a tiny preview file.
     * Preserves the previous good version as `.bak` so power loss or process death mid-write
     * never corrupts user notes.
     */
    suspend fun writeBoardElements(boardId: Long, elements: List<CanvasElement>) {
        val encodedFull = withContext(Dispatchers.Default) {
            CanvasSerializer.encode(elements)
        }
        val previewElements = withContext(Dispatchers.Default) {
            CanvasSerializer.buildPreviewElements(elements)
        }
        val encodedPreview = withContext(Dispatchers.Default) {
            CanvasSerializer.encode(previewElements)
        }

        withContext(Dispatchers.IO) {
            synchronized(lockFor(boardId)) {
                val primary = primaryFile(boardId)
                val backup = backupFile(boardId)
                val tmp = tempFile(boardId)

                // 1. Write full compressed JSON to temp file and fsync before atomic swap
                FileOutputStream(tmp).use { fos ->
                    val buffered = BufferedOutputStream(fos, 16 * 1024)
                    val gzip = GZIPOutputStream(buffered)
                    try {
                        gzip.write(encodedFull.toByteArray(StandardCharsets.UTF_8))
                        gzip.finish()
                        buffered.flush()
                        runCatching { fos.fd.sync() }
                    } finally {
                        gzip.close()
                    }
                }

                // 2. Rotate previous valid file into backup
                if (primary.exists() && primary.length() > 0L) {
                    if (backup.exists()) backup.delete()
                    if (!primary.renameTo(backup)) {
                        runCatching { primary.copyTo(backup, overwrite = true) }
                        primary.delete()
                    }
                }

                // 3. Atomically promote temp file to primary
                if (!tmp.renameTo(primary)) {
                    tmp.copyTo(primary, overwrite = true)
                    tmp.delete()
                }

                // 4. Write lightweight gallery preview atomically
                val prevTmp = previewTempFile(boardId)
                val prevTarget = previewFile(boardId)
                runCatching {
                    prevTmp.writeText(encodedPreview, StandardCharsets.UTF_8)
                    if (prevTarget.exists()) prevTarget.delete()
                    if (!prevTmp.renameTo(prevTarget)) {
                        prevTmp.copyTo(prevTarget, overwrite = true)
                        prevTmp.delete()
                    }
                }
                previewCache[boardId] = previewElements
            }
        }
    }

    /**
     * Reads full board elements from disk. Automatically falls back to `.bak` if primary
     * was interrupted or damaged. Returns `null` if no file exists yet (legacy Room row).
     */
    suspend fun readBoardElements(boardId: Long): List<CanvasElement>? {
        val rawJson = withContext(Dispatchers.IO) {
            synchronized(lockFor(boardId)) {
                val primary = File(boardsDir, "board_${boardId}.json.gz")
                val backup = File(boardsDir, "board_${boardId}.json.gz.bak")
                if (!primary.exists() && !backup.exists()) {
                    return@withContext null
                }

                if (primary.exists()) {
                    val primaryJson = readGzipTextSafely(primary)
                    if (primaryJson != null) {
                        return@withContext primaryJson
                    }
                }

                if (backup.exists()) {
                    val backupJson = readGzipTextSafely(backup)
                    if (backupJson != null) {
                        // Self-heal primary from valid backup
                        runCatching { backup.copyTo(primary, overwrite = true) }
                        return@withContext backupJson
                    }
                }

                "[]"
            }
        } ?: return null

        return withContext(Dispatchers.Default) {
            CanvasSerializer.decode(rawJson)
        }
    }

    suspend fun readPreviewElements(boardId: Long): List<CanvasElement>? {
        previewCache[boardId]?.let { return it }
        return withContext(Dispatchers.IO) {
            synchronized(lockFor(boardId)) {
                val prev = File(boardsDir, "board_${boardId}.preview.json")
                if (!prev.exists()) return@withContext null
                val text = runCatching { prev.readText(StandardCharsets.UTF_8) }.getOrNull()
                    ?: return@withContext null
                val decoded = CanvasSerializer.decode(text)
                previewCache[boardId] = decoded
                decoded
            }
        }
    }

    suspend fun deleteBoardFiles(boardId: Long) = withContext(Dispatchers.IO) {
        synchronized(lockFor(boardId)) {
            previewCache.remove(boardId)
            listOf(
                File(boardsDir, "board_${boardId}.json.gz"),
                File(boardsDir, "board_${boardId}.json.gz.bak"),
                File(boardsDir, "board_${boardId}.json.gz.tmp"),
                File(boardsDir, "board_${boardId}.preview.json"),
                File(boardsDir, "board_${boardId}.preview.json.tmp")
            ).forEach { file ->
                if (file.exists()) {
                    runCatching { file.delete() }
                }
            }
        }
    }

    private fun readGzipTextSafely(file: File): String? {
        return try {
            if (!file.exists() || file.length() == 0L) return null
            GZIPInputStream(BufferedInputStream(FileInputStream(file), 16 * 1024)).use { gzip ->
                gzip.bufferedReader(StandardCharsets.UTF_8).readText()
            }
        } catch (_: Throwable) {
            null
        }
    }
}
