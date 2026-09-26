package com.example.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks player performance and personal bests specifically per Board Size (3x3, 4x4, 5x5, 6x6, 7x7).
 */
@Entity(tableName = "board_size_records")
data class BoardSizeRecord(
    @PrimaryKey val boardSizeName: String, // "SIZE_3X3", "SIZE_4X4", "SIZE_5X5", "SIZE_6X6", "SIZE_7X7"
    val displayName: String,               // "3x3", "4x4", etc.
    val bestTimeSeconds: Int = 0,         // 0 = no record yet; otherwise lowest seconds
    val bestMoves: Int = 0,               // 0 = no record yet; otherwise lowest move count
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val perfectSolves: Int = 0,
    val totalTimeSeconds: Long = 0L,
    val totalMoves: Long = 0L,
    val lastPlayedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "level_progress")
data class LevelProgress(
    @PrimaryKey val levelId: Int,
    val stars: Int, // 1 to 3 stars
    val bestMoves: Int,
    val bestTimeSeconds: Int,
    val isCompleted: Boolean = true,
    val completedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "game_stats")
data class GameStats(
    @PrimaryKey val modeKey: String, // e.g. "CAMPAIGN", "DAILY", "TIME_ATTACK", "ENDLESS", "ZEN", "IMAGE"
    val gamesPlayed: Int = 0,
    val gamesWon: Int = 0,
    val bestMoves: Int = 0,
    val bestTimeSeconds: Int = 0,
    val totalMoves: Long = 0L,
    val totalTimeSeconds: Long = 0L,
    val perfectSolves: Int = 0,
    val highestEndlessStreak: Int = 0
)

@Entity(tableName = "daily_records")
data class DailyRecord(
    @PrimaryKey val dateString: String, // "YYYY-MM-DD"
    val moves: Int,
    val timeSeconds: Int,
    val stars: Int,
    val isPerfectSolve: Boolean,
    val completedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "player_settings")
data class PlayerSettings(
    @PrimaryKey val id: Int = 1,
    val tileThemeName: String = "CYBER_NEON",
    val isDarkMode: Boolean = true,
    val isSfxEnabled: Boolean = true,
    val isBgmEnabled: Boolean = false,
    val showNumberOverlay: Boolean = true
)

