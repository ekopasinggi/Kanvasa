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
    val mediaStorage: MediaStorageManager
) {
    val allBoards: Flow<List<NoteBoardEntity>> = dao.observeAllBoards()

    suspend fun getBoardById(id: Long): NoteBoardEntity? = withContext(Dispatchers.IO) {
        dao.getBoardById(id)
    }

    suspend fun decodeElements(json: String): List<CanvasElement> = withContext(Dispatchers.Default) {
        CanvasSerializer.decode(json)
    }

    suspend fun createBoard(title: String, pattern: CanvasPattern): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val entity = NoteBoardEntity(
            title = title.trim().ifBlank { "Catatan Tanpa Judul" },
            patternName = pattern.name,
            elementsJson = "[]",
            elementCount = 0,
            cameraScale = 1f,
            cameraPanX = 0f,
            cameraPanY = 0f,
            createdAt = now,
            updatedAt = now
        )
        dao.insertBoard(entity)
    }

    suspend fun renameBoard(board: NoteBoardEntity, newTitle: String, newPattern: CanvasPattern) =
        withContext(Dispatchers.IO) {
            dao.updateBoard(
                board.copy(
                    title = newTitle.trim().ifBlank { board.title },
                    patternName = newPattern.name,
                    updatedAt = System.currentTimeMillis()
                )
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
        // Run heavy JSON serialization on Default dispatcher so UI/stylus input never stalls
        val encoded = withContext(Dispatchers.Default) {
            CanvasSerializer.encode(elements)
        }
        withContext(Dispatchers.IO) {
            val existing = dao.getBoardById(boardId) ?: return@withContext
            dao.updateBoard(
                existing.copy(
                    title = title.trim().ifBlank { existing.title },
                    patternName = pattern.name,
                    elementsJson = encoded,
                    elementCount = elements.size,
                    cameraScale = cameraScale,
                    cameraPanX = cameraPanX,
                    cameraPanY = cameraPanY,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteBoard(board: NoteBoardEntity) = withContext(Dispatchers.IO) {
        val elements = CanvasSerializer.decode(board.elementsJson)
        val imagePaths = elements.filterIsInstance<CanvasElement.ImageItem>().map { it.filePath }
        if (imagePaths.isNotEmpty()) {
            mediaStorage.deleteFiles(imagePaths)
        }
        dao.deleteBoardById(board.id)
    }

    suspend fun importImage(uri: Uri): ImportedImageResult? =
        mediaStorage.importImageFromUri(uri)
}
