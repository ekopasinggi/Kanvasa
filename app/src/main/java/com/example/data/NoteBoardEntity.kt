package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_boards")
data class NoteBoardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val patternName: String = "DOTS",
    val elementsJson: String = "",
    val elementCount: Int = 0,
    val cameraScale: Float = 1f,
    val cameraPanX: Float = 0f,
    val cameraPanY: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Metadata-only Room projection that never selects the legacy `elementsJson` column,
 * guaranteeing that gallery listing and board loading can NEVER hit Android's 2 MB
 * SQLite CursorWindow limit (SQLiteBlobTooBigException).
 */
data class NoteBoardSummary(
    val id: Long,
    val title: String,
    val patternName: String,
    val elementCount: Int,
    val cameraScale: Float,
    val cameraPanX: Float,
    val cameraPanY: Float,
    val createdAt: Long,
    val updatedAt: Long
)
