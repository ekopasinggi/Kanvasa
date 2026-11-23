package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [NoteBoardEntity::class],
    version = 1,
    exportSchema = false
)
abstract class KanvasaDatabase : RoomDatabase() {
    abstract fun noteBoardDao(): NoteBoardDao

    companion object {
        @Volatile
        private var INSTANCE: KanvasaDatabase? = null

        fun getInstance(context: Context): KanvasaDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    KanvasaDatabase::class.java,
                    "kanvasa_notes.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
