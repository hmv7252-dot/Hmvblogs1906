package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entities.BoardSizeRecord
import com.example.data.entities.DailyRecord
import com.example.data.entities.GameStats
import com.example.data.entities.LevelProgress
import com.example.data.entities.PlayerSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface PuzzleDao {
    // Board Size Records (Best times, moves, and win stats for each grid size)
    @Query("SELECT * FROM board_size_records")
    fun getAllBoardSizeRecords(): Flow<List<BoardSizeRecord>>

    @Query("SELECT * FROM board_size_records WHERE boardSizeName = :boardSizeName")
    fun getBoardSizeRecordFlow(boardSizeName: String): Flow<BoardSizeRecord?>

    @Query("SELECT * FROM board_size_records WHERE boardSizeName = :boardSizeName")
    suspend fun getBoardSizeRecord(boardSizeName: String): BoardSizeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBoardSizeRecord(record: BoardSizeRecord)

    // Level Progress Queries
    @Query("SELECT * FROM level_progress ORDER BY levelId ASC")
    fun getAllLevelProgress(): Flow<List<LevelProgress>>

    @Query("SELECT * FROM level_progress WHERE levelId = :levelId")
    suspend fun getLevelProgress(levelId: Int): LevelProgress?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLevelProgress(progress: LevelProgress)

    @Query("SELECT COUNT(*) FROM level_progress WHERE isCompleted = 1")
    fun getCompletedLevelsCount(): Flow<Int>

    @Query("SELECT SUM(stars) FROM level_progress")
    fun getTotalStarsEarned(): Flow<Int?>

    // Stats Queries
    @Query("SELECT * FROM game_stats WHERE modeKey = :modeKey")
    fun getStats(modeKey: String): Flow<GameStats?>

    @Query("SELECT * FROM game_stats")
    fun getAllStats(): Flow<List<GameStats>>

    @Query("SELECT * FROM game_stats WHERE modeKey = :modeKey")
    suspend fun getGameStatsDirect(modeKey: String): GameStats?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStats(stats: GameStats)

    // Daily Records
    @Query("SELECT * FROM daily_records WHERE dateString = :dateString")
    suspend fun getDailyRecord(dateString: String): DailyRecord?

    @Query("SELECT * FROM daily_records ORDER BY dateString DESC")
    fun getAllDailyRecords(): Flow<List<DailyRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyRecord(record: DailyRecord)

    // Player Settings
    @Query("SELECT * FROM player_settings WHERE id = 1")
    fun getPlayerSettings(): Flow<PlayerSettings?>

    @Query("SELECT * FROM player_settings WHERE id = 1")
    suspend fun getPlayerSettingsDirect(): PlayerSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdatePlayerSettings(settings: PlayerSettings)
}

