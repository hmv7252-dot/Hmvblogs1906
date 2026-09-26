package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.dao.PuzzleDao
import com.example.data.entities.BoardSizeRecord
import com.example.data.entities.DailyRecord
import com.example.data.entities.GameStats
import com.example.data.entities.LevelProgress
import com.example.data.entities.PlayerSettings

@Database(
    entities = [
        BoardSizeRecord::class,
        LevelProgress::class,
        GameStats::class,
        DailyRecord::class,
        PlayerSettings::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun puzzleDao(): PuzzleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "slide_master_database"
                ).fallbackToDestructiveMigration(dropAllTables = true).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
