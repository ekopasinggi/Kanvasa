package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "note_boards")
data class NoteBoardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val patternName: String = "DOTS",
    val elementsJson: String = "[]",
    val elementCount: Int = 0,
    val cameraScale: Float = 1f,
    val cameraPanX: Float = 0f,
    val cameraPanY: Float = 0f,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
