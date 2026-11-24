package com.example.data

import android.net.Uri
import com.example.model.CanvasElement
import com.example.model.CanvasPattern
import com.example.model.CanvasSerializer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class NoteRepository(
    private val dao: NoteBoardDao,
    val mediaStorage: MediaStorageManager,
    val contentStorage: BoardContentStorage
) {
    val allBoards: Flow<List<NoteBoardSummary>> = dao.observeAllBoardSummaries()

    suspend fun getBoardSummaryById(id: Long): NoteBoardSummary? = withContext(Dispatchers.IO) {
        dao.getBoardSummaryById(id)
    }

    /**
     * Loads full canvas elements from atomic GZIP file storage.
     * If the board was created by an older version (data still in SQLite `elementsJson`),
     * safely rescues it in 512 KB SQLite SUBSTR chunks (immune to 2 MB CursorWindow limits),
     * migrates it to file storage, and clears the legacy SQLite blob.
     */
    suspend fun loadBoardElements(boardId: Long): List<CanvasElement> {
        val fromFile = contentStorage.readBoardElements(boardId)
        if (fromFile != null) {
            return fromFile
        }

        val legacyJson = readLegacyJsonChunked(boardId)
        if (legacyJson.isBlank() || legacyJson == "[]") {
            return emptyList()
        }

        val decoded = withContext(Dispatchers.Default) {
            CanvasSerializer.decode(legacyJson)
        }
        // Migrate rescued legacy board into atomic file storage and shrink SQLite row
        runCatching {
            contentStorage.writeBoardElements(boardId, decoded)
            withContext(Dispatchers.IO) {
                dao.clearLegacyElementsJson(boardId)
            }
        }
        return decoded
    }

    /**
     * Loads lightweight downsampled preview elements for gallery cards asynchronously.
     */
    suspend fun loadBoardPreview(boardId: Long, elementCount: Int): List<CanvasElement> {
        if (elementCount <= 0) return emptyList()
        val cachedOrFile = contentStorage.readPreviewElements(boardId)
        if (cachedOrFile != null) {
            return cachedOrFile
        }
        // Generate & cache preview once for legacy boards
        val fullElements = loadBoardElements(boardId)
        return withContext(Dispatchers.Default) {
            CanvasSerializer.buildPreviewElements(fullElements)
        }
    }

    suspend fun createBoard(title: String, pattern: CanvasPattern): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = NoteBoardEntity(
            title = title.trim().ifBlank { "Catatan Tanpa Judul" },
            patternName = pattern.name,
            elementsJson = "",
            elementCount = 0,
            cameraScale = 1f,
            cameraPanX = 0f,
            cameraPanY = 0f,
            createdAt = now,
            updatedAt = now
        )
        val boardId = dao.insertBoard(entity)
        runCatching { contentStorage.writeBoardElements(boardId, emptyList()) }
        boardId
    }

    suspend fun renameBoard(board: NoteBoardSummary, newTitle: String, newPattern: CanvasPattern) =
        withContext(Dispatchers.IO) {
            dao.updateBoardTitleAndPattern(
                id = board.id,
                title = newTitle.trim().ifBlank { board.title },
                patternName = newPattern.name,
                updatedAt = System.currentTimeMillis()
            )
        }

    suspend fun saveBoardWorkspace(
        boardId: Long,
        title: String,
        pattern: CanvasPattern,
        elements: List<CanvasElement>,
        cameraScale: Float,
        cameraPanX: Float,
        cameraPanY: Float
    ) {
        // 1. Atomically write full compressed payload + preview file off the UI thread
        contentStorage.writeBoardElements(boardId, elements)

        // 2. Persist compact metadata only in SQLite (zero large blobs in Room!)
        withContext(Dispatchers.IO) {
            val existing = dao.getBoardSummaryById(boardId) ?: return@withContext
            dao.updateBoardMetadata(
                id = boardId,
                title = title.trim().ifBlank { existing.title },
                patternName = pattern.name,
                elementCount = elements.size,
                cameraScale = cameraScale,
                cameraPanX = cameraPanX,
                cameraPanY = cameraPanY,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    suspend fun deleteBoard(board: NoteBoardSummary) {
        val elements = runCatching { loadBoardElements(board.id) }.getOrDefault(emptyList())
        val imagePaths = elements.filterIsInstance<CanvasElement.ImageItem>().map { it.filePath }
        if (imagePaths.isNotEmpty()) {
            mediaStorage.deleteFiles(imagePaths)
        }
        contentStorage.deleteBoardFiles(board.id)
        withContext(Dispatchers.IO) {
            dao.deleteBoardById(board.id)
        }
    }

    suspend fun importImage(uri: Uri): ImportedImageResult? =
        mediaStorage.importImageFromUri(uri)

    /**
     * Reads legacy `elementsJson` in 512 KB chunks via SQLite SUBSTR so even a >2 MB
     * previously-stuck row can be recovered completely without SQLiteBlobTooBigException.
     */
    private suspend fun readLegacyJsonChunked(boardId: Long): String = withContext(Dispatchers.IO) {
        try {
            val totalLen = dao.getLegacyElementsJsonLength(boardId) ?: return@withContext ""
            if (totalLen <= 0) return@withContext ""
            val chunkSize = 512 * 1024
            val sb = StringBuilder(totalLen)
            var pos = 1 // SQLite SUBSTR is 1-indexed
            while (pos <= totalLen) {
                val chunk = dao.getLegacyElementsJsonChunk(boardId, pos, chunkSize) ?: break
                if (chunk.isEmpty()) break
                sb.append(chunk)
                pos += chunk.length
            }
            sb.toString()
        } catch (_: Throwable) {
            ""
        }
    }
}
