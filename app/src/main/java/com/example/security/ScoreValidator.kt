package com.example.security

import com.example.model.BoardSize

/**
 * Validates in-game mathematical bounds, move rates, solve speeds,
 * and level progression to prevent impossible client submissions.
 */
object ScoreValidator {

    // Physically impossible for a human player to consistently exceed 20 sliding moves/second
    private const val MAX_MOVES_PER_SECOND = 22.0

    // Absolute minimum solve time in seconds for a legitimate scrambled board
    private const val MIN_SOLVE_SECONDS_3X3 = 1
    private const val MIN_SOLVE_SECONDS_4X4_PLUS = 2

    /**
     * Verifies that the solve time and move count represent a physically plausible game.
     */
    fun validateSolve(
        boardSize: BoardSize,
        moves: Int,
        timeSeconds: Int,
        optimalMoves: Int
    ): Boolean {
        if (moves <= 0) {
            SecurityManager.logSecurityEvent("SCORE_VALIDATION_FAIL", "Zero or negative moves reported: $moves")
            return false
        }

        if (timeSeconds < 0) {
            SecurityManager.logSecurityEvent("SCORE_VALIDATION_FAIL", "Negative solve time reported: $timeSeconds")
            return false
        }

        val minSeconds = if (boardSize.dimension <= 3) MIN_SOLVE_SECONDS_3X3 else MIN_SOLVE_SECONDS_4X4_PLUS
        if (moves >= 5 && timeSeconds < minSeconds) {
            SecurityManager.logSecurityEvent(
                "SPEED_HACK_DETECTED",
                "Solve time ($timeSeconds s) below physical minimum for $moves moves."
            )
            return false
        }

        // Check move frequency (moves / max(1, timeSeconds))
        val effectiveSeconds = maxOf(1, timeSeconds)
        val moveRate = moves.toDouble() / effectiveSeconds.toDouble()
        if (moves > 10 && moveRate > MAX_MOVES_PER_SECOND) {
            SecurityManager.logSecurityEvent(
                "IMPOSSIBLE_SPEED",
                "Move rate ($moveRate moves/s) exceeded physical limits."
            )
            return false
        }

        // Sanity check: moves cannot be lower than half of optimal calculation for scrambled board
        if (optimalMoves > 10 && moves < (optimalMoves / 2)) {
            SecurityManager.logSecurityEvent(
                "IMPOSSIBLE_MOVES",
                "Reported moves ($moves) drastically lower than optimal ($optimalMoves)."
            )
            return false
        }

        return true
    }

    /**
     * Validates star rating bounds (1 to 3).
     */
    fun validateStarRating(stars: Int): Int {
        return stars.coerceIn(1, 3)
    }

    /**
     * Prevents impossible campaign level skips.
     * Players can only attempt levels up to completedLevelsCount + 1.
     */
    fun validateCampaignLevelAccess(requestedLevel: Int, completedLevels: Int): Boolean {
        val maxAllowed = completedLevels + 1
        if (requestedLevel < 1 || requestedLevel > maxAllowed) {
            SecurityManager.logSecurityEvent(
                "INVALID_LEVEL_ACCESS",
                "Requested level $requestedLevel when max unlocked is $maxAllowed"
            )
            return false
        }
        return true
    }

    /**
     * Validates Endless mode score and streak increments.
     */
    fun validateEndlessScoreJump(
        previousStreak: Int,
        newStreak: Int,
        previousScore: Int,
        newScore: Int,
        moves: Int,
        optimalMoves: Int
    ): Boolean {
        // Streak must increment by exactly 1
        if (newStreak != previousStreak + 1) {
            SecurityManager.logSecurityEvent(
                "INVALID_STREAK",
                "Streak jumped from $previousStreak to $newStreak"
            )
            return false
        }

        // Expected score formula: base 500 + (streak * 100) + max(0, (optimal * 2 - moves) * 10)
        val maxPossibleBonus = 500 + (newStreak * 100) + (optimalMoves * 20) + 500
        val scoreDelta = newScore - previousScore

        if (scoreDelta <= 0 || scoreDelta > maxPossibleBonus) {
            SecurityManager.logSecurityEvent(
                "IMPOSSIBLE_SCORE_JUMP",
                "Score delta $scoreDelta outside legitimate range (max: $maxPossibleBonus)"
            )
            return false
        }

        return true
    }
}
