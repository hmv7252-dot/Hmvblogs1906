package com.example.engine

import com.example.model.BoardSize
import com.example.model.CampaignLevel
import com.example.model.Difficulty
import com.example.model.ImagePreset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.random.Random

object PuzzleGenerator {

    /**
     * Generates a solvable board by taking valid random reverse moves from the solved state.
     * This guarantees 100% solvability and lets us control difficulty precisely.
     */
    fun generateSolvableBoard(
        size: BoardSize,
        difficulty: Difficulty,
        customSteps: Int? = null,
        seed: Long? = null
    ): Pair<List<Int>, Int> {
        val total = size.totalTiles
        val solved = (1 until total).toMutableList().apply { add(0) }
        val random = if (seed != null) Random(seed) else Random.Default

        val baseMoves = customSteps ?: when (difficulty) {
            Difficulty.EASY -> size.dimension * 6
            Difficulty.MEDIUM -> size.dimension * 14
            Difficulty.HARD -> size.dimension * 26
            Difficulty.EXPERT -> size.dimension * 45
        }

        val current = solved.toMutableList()
        var lastMovedIndex = -1

        var actualMoves = 0
        for (step in 0 until baseMoves) {
            val emptyIndex = current.indexOf(0)
            val movables = PuzzleSolver.getMovableTileIndices(current, size)
                .filter { it != lastMovedIndex } // avoid immediate back-and-forth undo
            
            val chosenTileIndex = if (movables.isNotEmpty()) {
                movables[random.nextInt(movables.size)]
            } else {
                val anyMovable = PuzzleSolver.getMovableTileIndices(current, size)
                anyMovable[random.nextInt(anyMovable.size)]
            }

            // Swap
            current[emptyIndex] = current[chosenTileIndex]
            current[chosenTileIndex] = 0
            lastMovedIndex = emptyIndex
            actualMoves++
        }

        // If by any chance it ended up solved, do 2 extra swaps
        if (PuzzleSolver.isSolved(current)) {
            val emptyIndex = current.indexOf(0)
            val movables = PuzzleSolver.getMovableTileIndices(current, size)
            val chosen = movables[0]
            current[emptyIndex] = current[chosen]
            current[chosen] = 0
        }

        val optimal = PuzzleSolver.calculateOptimalMoves(current, size, actualMoves)
        return Pair(current.toList(), optimal)
    }

    /**
     * Generates a Campaign Level definition for any level from 1 to 1000+.
     */
    fun getCampaignLevel(levelNumber: Int): CampaignLevel {
        val clampedLevel = levelNumber.coerceAtLeast(1)
        val (boardSize, difficulty, chapter) = when {
            clampedLevel <= 100 -> {
                val diff = when {
                    clampedLevel <= 25 -> Difficulty.EASY
                    clampedLevel <= 60 -> Difficulty.MEDIUM
                    clampedLevel <= 85 -> Difficulty.HARD
                    else -> Difficulty.EXPERT
                }
                Triple(BoardSize.SIZE_3X3, diff, "Chapter I: Apprentice (3x3)")
            }
            clampedLevel <= 350 -> {
                val diff = when {
                    clampedLevel <= 160 -> Difficulty.EASY
                    clampedLevel <= 240 -> Difficulty.MEDIUM
                    clampedLevel <= 300 -> Difficulty.HARD
                    else -> Difficulty.EXPERT
                }
                Triple(BoardSize.SIZE_4X4, diff, "Chapter II: Adept (4x4)")
            }
            clampedLevel <= 650 -> {
                val diff = when {
                    clampedLevel <= 430 -> Difficulty.EASY
                    clampedLevel <= 520 -> Difficulty.MEDIUM
                    clampedLevel <= 600 -> Difficulty.HARD
                    else -> Difficulty.EXPERT
                }
                Triple(BoardSize.SIZE_5X5, diff, "Chapter III: Master (5x5)")
            }
            clampedLevel <= 850 -> {
                val diff = when {
                    clampedLevel <= 700 -> Difficulty.EASY
                    clampedLevel <= 760 -> Difficulty.MEDIUM
                    clampedLevel <= 810 -> Difficulty.HARD
                    else -> Difficulty.EXPERT
                }
                Triple(BoardSize.SIZE_6X6, diff, "Chapter IV: Grandmaster (6x6)")
            }
            else -> {
                val diff = when {
                    clampedLevel <= 900 -> Difficulty.EASY
                    clampedLevel <= 950 -> Difficulty.MEDIUM
                    clampedLevel <= 980 -> Difficulty.HARD
                    else -> Difficulty.EXPERT
                }
                Triple(BoardSize.SIZE_7X7, diff, "Chapter V: Legend (7x7)")
            }
        }

        val baseMoves = when (difficulty) {
            Difficulty.EASY -> boardSize.dimension * 8
            Difficulty.MEDIUM -> boardSize.dimension * 16
            Difficulty.HARD -> boardSize.dimension * 28
            Difficulty.EXPERT -> boardSize.dimension * 45
        }

        val target3Stars = (baseMoves * 1.3).toInt()
        val target2Stars = (baseMoves * 2.0).toInt()

        return CampaignLevel(
            levelNumber = clampedLevel,
            boardSize = boardSize,
            difficulty = difficulty,
            targetMoves3Stars = target3Stars,
            targetMoves2Stars = target2Stars,
            title = "Stage $clampedLevel",
            chapterName = chapter
        )
    }

    /**
     * Generates a deterministic daily challenge for a given date.
     */
    fun getDailySeed(dateString: String): Long {
        return dateString.hashCode().toLong()
    }

    fun getTodayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    /**
     * Built-in Image Presets with vivid geometric gradient patterns and themes.
     */
    val PRESET_IMAGES = listOf(
        ImagePreset(
            id = "cosmic_nebula",
            name = "Cosmic Nebula",
            category = "Space",
            colorsGradient = listOf(0xFF240046, 0xFF7B2CBF, 0xFFFF007F, 0xFF00F0FF)
        ),
        ImagePreset(
            id = "sunset_peaks",
            name = "Sunset Peaks",
            category = "Landscape",
            colorsGradient = listOf(0xFF1F1C2C, 0xFF928DAB, 0xFFFF512F, 0xFFF09819)
        ),
        ImagePreset(
            id = "cyber_city",
            name = "Cyberpunk Grid",
            category = "Futuristic",
            colorsGradient = listOf(0xFF0F0C29, 0xFF302B63, 0xFF24243E, 0xFF00FF87)
        ),
        ImagePreset(
            id = "zen_oasis",
            name = "Emerald Oasis",
            category = "Nature",
            colorsGradient = listOf(0xFF051937, 0xFF004D7A, 0xFF008793, 0xFF00BF72, 0xFFA8EB12)
        ),
        ImagePreset(
            id = "golden_geometry",
            name = "Golden Prisms",
            category = "Abstract",
            colorsGradient = listOf(0xFF141E30, 0xFF243B55, 0xFFFFD700, 0xFFFF8C00)
        ),
        ImagePreset(
            id = "neon_aurora",
            name = "Aurora Glow",
            category = "Atmosphere",
            colorsGradient = listOf(0xFF000428, 0xFF004E92, 0xFF00C9FF, 0xFF92FE9D)
        )
    )
}
