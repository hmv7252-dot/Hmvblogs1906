package com.example.data.repository

import com.example.data.dao.PuzzleDao
import com.example.data.entities.BoardSizeRecord
import com.example.data.entities.DailyRecord
import com.example.data.entities.GameStats
import com.example.data.entities.LevelProgress
import com.example.data.entities.PlayerSettings
import com.example.model.BoardSize
import kotlinx.coroutines.flow.Flow

class PuzzleRepository(private val dao: PuzzleDao) {

    // Board Size Records
    val allBoardSizeRecords: Flow<List<BoardSizeRecord>> = dao.getAllBoardSizeRecords()
    val allLevelProgress: Flow<List<LevelProgress>> = dao.getAllLevelProgress()
    val totalStars: Flow<Int?> = dao.getTotalStarsEarned()
    val completedLevelsCount: Flow<Int> = dao.getCompletedLevelsCount()
    val allStats: Flow<List<GameStats>> = dao.getAllStats()
    val allDailyRecords: Flow<List<DailyRecord>> = dao.getAllDailyRecords()
    val playerSettings: Flow<PlayerSettings?> = dao.getPlayerSettings()

    suspend fun getBoardSizeRecord(boardSize: BoardSize): BoardSizeRecord? {
        return dao.getBoardSizeRecord(boardSize.name)
    }

    suspend fun recordBoardSolve(
        boardSize: BoardSize,
        moves: Int,
        timeSeconds: Int,
        isPerfect: Boolean
    ) {
        val existing = dao.getBoardSizeRecord(boardSize.name)
        val bestMoves = if (existing == null || existing.bestMoves <= 0) {
            moves
        } else {
            minOf(existing.bestMoves, moves)
        }

        val bestTime = if (existing == null || existing.bestTimeSeconds <= 0) {
            timeSeconds
        } else {
            minOf(existing.bestTimeSeconds, timeSeconds)
        }

        val gamesPlayed = (existing?.gamesPlayed ?: 0) + 1
        val gamesWon = (existing?.gamesWon ?: 0) + 1
        val perfectSolves = (existing?.perfectSolves ?: 0) + if (isPerfect) 1 else 0
        val totalTime = (existing?.totalTimeSeconds ?: 0L) + timeSeconds
        val totalMoves = (existing?.totalMoves ?: 0L) + moves

        dao.insertOrUpdateBoardSizeRecord(
            BoardSizeRecord(
                boardSizeName = boardSize.name,
                displayName = boardSize.label,
                bestTimeSeconds = bestTime,
                bestMoves = bestMoves,
                gamesPlayed = gamesPlayed,
                gamesWon = gamesWon,
                perfectSolves = perfectSolves,
                totalTimeSeconds = totalTime,
                totalMoves = totalMoves,
                lastPlayedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordGameStarted(boardSize: BoardSize) {
        val existing = dao.getBoardSizeRecord(boardSize.name)
        val current = existing ?: BoardSizeRecord(
            boardSizeName = boardSize.name,
            displayName = boardSize.label
        )
        dao.insertOrUpdateBoardSizeRecord(
            current.copy(
                gamesPlayed = current.gamesPlayed + 1,
                lastPlayedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun getLevelProgress(levelId: Int): LevelProgress? = dao.getLevelProgress(levelId)

    suspend fun saveLevelProgress(levelId: Int, stars: Int, moves: Int, timeSeconds: Int) {
        val existing = dao.getLevelProgress(levelId)
        val bestStars = if (existing != null) maxOf(existing.stars, stars) else stars
        val bestMoves = if (existing != null && existing.bestMoves > 0) minOf(existing.bestMoves, moves) else moves
        val bestTime = if (existing != null && existing.bestTimeSeconds > 0) minOf(existing.bestTimeSeconds, timeSeconds) else timeSeconds

        dao.insertLevelProgress(
            LevelProgress(
                levelId = levelId,
                stars = bestStars,
                bestMoves = bestMoves,
                bestTimeSeconds = bestTime,
                isCompleted = true,
                completedTimestamp = System.currentTimeMillis()
            )
        )
    }

    suspend fun recordGameModeWin(
        modeKey: String,
        moves: Int,
        timeSeconds: Int,
        isPerfect: Boolean,
        endlessStreak: Int = 0
    ) {
        val existing = dao.getGameStatsDirect(modeKey)
        val bestMoves = if (existing == null || existing.bestMoves <= 0) moves else minOf(existing.bestMoves, moves)
        val bestTime = if (existing == null || existing.bestTimeSeconds <= 0) timeSeconds else minOf(existing.bestTimeSeconds, timeSeconds)
        val highestStreak = if (existing != null) maxOf(existing.highestEndlessStreak, endlessStreak) else endlessStreak

        dao.insertOrUpdateStats(
            GameStats(
                modeKey = modeKey,
                gamesPlayed = (existing?.gamesPlayed ?: 0) + 1,
                gamesWon = (existing?.gamesWon ?: 0) + 1,
                bestMoves = bestMoves,
                bestTimeSeconds = bestTime,
                totalMoves = (existing?.totalMoves ?: 0L) + moves,
                totalTimeSeconds = (existing?.totalTimeSeconds ?: 0L) + timeSeconds,
                perfectSolves = (existing?.perfectSolves ?: 0) + if (isPerfect) 1 else 0,
                highestEndlessStreak = highestStreak
            )
        )
    }

    suspend fun saveDailyRecord(record: DailyRecord) {
        dao.insertDailyRecord(record)
    }

    suspend fun getDailyRecord(dateString: String): DailyRecord? {
        return dao.getDailyRecord(dateString)
    }

    suspend fun savePlayerSettings(settings: PlayerSettings) {
        dao.insertOrUpdatePlayerSettings(settings)
    }

    suspend fun getPlayerSettings(): PlayerSettings? {
        return dao.getPlayerSettingsDirect()
    }
}

