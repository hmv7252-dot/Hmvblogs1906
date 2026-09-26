package com.example.model

import android.graphics.Bitmap

data class CampaignLevel(
    val levelNumber: Int,
    val boardSize: BoardSize,
    val difficulty: Difficulty,
    val targetMoves3Stars: Int,
    val targetMoves2Stars: Int,
    val title: String,
    val chapterName: String
)

data class ImagePreset(
    val id: String,
    val name: String,
    val category: String,
    val drawableId: Int? = null,
    val colorsGradient: List<Long> // High quality colorful gradients & geometric art patterns for presets
)

data class GameState(
    val boardSize: BoardSize = BoardSize.SIZE_3X3,
    val difficulty: Difficulty = Difficulty.MEDIUM,
    val gameMode: GameMode = GameMode.CAMPAIGN,
    val tiles: List<Int> = emptyList(), // 1..(N-1) for tiles, 0 for empty space
    val emptyIndex: Int = 0,
    val moveCount: Int = 0,
    val timerSeconds: Int = 0,
    val timeAttackRemainingSeconds: Int = 60,
    val isSolved: Boolean = false,
    val isPaused: Boolean = false,
    val optimalMoves: Int = 0,
    val initialBoard: List<Int> = emptyList(),
    val moveHistory: List<List<Int>> = emptyList(),
    val hintTileIndex: Int? = null,
    val hintTargetIndex: Int? = null,
    val campaignLevel: CampaignLevel? = null,
    val endlessLevelNumber: Int = 1,
    val endlessScore: Int = 0,
    val endlessStreak: Int = 0,
    val customImageBitmap: Bitmap? = null,
    val selectedImagePreset: ImagePreset? = null,
    val showNumberOverlay: Boolean = true,
    val starRatingEarned: Int = 0,
    val isDailyChallenge: Boolean = false,
    val dailyDateString: String = ""
) {
    val efficiencyPercent: Int
        get() {
            if (optimalMoves <= 0 || moveCount <= 0) return 100
            val ratio = (optimalMoves.toFloat() / moveCount.toFloat()) * 100f
            return ratio.toInt().coerceIn(10, 100)
        }

    val isPerfectSolve: Boolean
        get() = isSolved && moveCount <= optimalMoves
}
