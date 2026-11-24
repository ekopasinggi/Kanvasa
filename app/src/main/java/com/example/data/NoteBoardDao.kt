package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteBoardDao {
    @Query(
        """
        SELECT id, title, patternName, elementCount, cameraScale, cameraPanX, cameraPanY, createdAt, updatedAt
        FROM note_boards
        ORDER BY updatedAt DESC
        """
    )
    fun observeAllBoardSummaries(): Flow<List<NoteBoardSummary>>

    @Query(
        """
        SELECT id, title, patternName, elementCount, cameraScale, cameraPanX, cameraPanY, createdAt, updatedAt
        FROM note_boards
        WHERE id = :id
        LIMIT 1
        """
    )
    suspend fun getBoardSummaryById(id: Long): NoteBoardSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoard(board: NoteBoardEntity): Long

    @Query(
        """
        UPDATE note_boards
        SET title = :title,
            patternName = :patternName,
            updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updateBoardTitleAndPattern(
        id: Long,
        title: String,
        patternName: String,
        updatedAt: Long
    )

    @Query(
        """
        UPDATE note_boards
        SET title = :title,
            patternName = :patternName,
            elementCount = :elementCount,
            cameraScale = :cameraScale,
            cameraPanX = :cameraPanX,
            cameraPanY = :cameraPanY,
            updatedAt = :updatedAt,
            elementsJson = ''
        WHERE id = :id
        """
    )
    suspend fun updateBoardMetadata(
        id: Long,
        title: String,
        patternName: String,
        elementCount: Int,
        cameraScale: Float,
        cameraPanX: Float,
        cameraPanY: Float,
        updatedAt: Long
    )

    // Safe chunked rescue queries for migrating any legacy or oversized (>2MB) SQLite row
    @Query("SELECT LENGTH(elementsJson) FROM note_boards WHERE id = :id LIMIT 1")
    suspend fun getLegacyElementsJsonLength(id: Long): Int?

    @Query("SELECT SUBSTR(elementsJson, :startPos, :chunkLength) FROM note_boards WHERE id = :id LIMIT 1")
    suspend fun getLegacyElementsJsonChunk(id: Long, startPos: Int, chunkLength: Int): String?

    @Query("UPDATE note_boards SET elementsJson = '' WHERE id = :id")
    suspend fun clearLegacyElementsJson(id: Long)

    @Query("DELETE FROM note_boards WHERE id = :id")
    suspend fun deleteBoardById(id: Long)
}
