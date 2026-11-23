package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteBoardDao {
    @Query("SELECT * FROM note_boards ORDER BY updatedAt DESC")
    fun observeAllBoards(): Flow<List<NoteBoardEntity>>

    @Query("SELECT * FROM note_boards WHERE id = :id LIMIT 1")
    suspend fun getBoardById(id: Long): NoteBoardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBoard(board: NoteBoardEntity): Long

    @Update
    suspend fun updateBoard(board: NoteBoardEntity)

    @Query("DELETE FROM note_boards WHERE id = :id")
    suspend fun deleteBoardById(id: Long)
}
