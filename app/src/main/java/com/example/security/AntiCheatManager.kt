package com.example.security

import com.example.model.BoardSize
import com.example.model.GameState
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real-time AntiCheat monitoring engine for Slidecraft.
 * Detects speed hacks, impossible moves, rapid automated clicking,
 * replay attacks, and save tampering.
 */
object AntiCheatManager {

    private const val MIN_INTER_MOVE_INTERVAL_MS = 25L // Human swipe/tap physical limit
    private var lastMoveTimestamp = 0L
    private val suspicionCounter = AtomicInteger(0)

    /**
     * Validates whether a tile move is physically possible and legally adjacent on the board.
     */
    fun validateMoveAttempt(
        tiles: List<Int>,
        emptyIndex: Int,
        tileIndex: Int,
        boardSize: BoardSize
    ): Boolean {
        val now = System.currentTimeMillis()

        // 1. Minimum inter-move latency check
        if (lastMoveTimestamp != 0L && (now - lastMoveTimestamp) < MIN_INTER_MOVE_INTERVAL_MS) {
            SecurityManager.logSecurityEvent(
                "SPEED_ANOMALY",
                "Move latency ${now - lastMoveTimestamp} ms below physical threshold."
            )
            suspicionCounter.incrementAndGet()
            // Do not immediately block single quick inputs, but track suspicion
        }
        lastMoveTimestamp = now

        // 2. Board adjacency validation
        val dim = boardSize.dimension
        val rowEmpty = emptyIndex / dim
        val colEmpty = emptyIndex % dim
        val rowTile = tileIndex / dim
        val colTile = tileIndex % dim

        val rowDiff = kotlin.math.abs(rowEmpty - rowTile)
        val colDiff = kotlin.math.abs(colEmpty - colTile)

        val isAdjacent = (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1)
        if (!isAdjacent) {
            SecurityManager.logSecurityEvent(
                "ILLEGAL_MOVE",
                "Tile $tileIndex is not adjacent to empty index $emptyIndex on ${dim}x${dim} board."
            )
            suspicionCounter.incrementAndGet()
            return false
        }

        return true
    }

    /**
     * Comprehensive validation of a completed game state before persisting records.
     */
    fun validateGameCompletion(
        state: GameState,
        moves: Int,
        timeSeconds: Int,
        stars: Int
    ): Boolean {
        // 1. Math and rate sanity checks
        val isSolvePlausible = ScoreValidator.validateSolve(
            boardSize = state.boardSize,
            moves = moves,
            timeSeconds = timeSeconds,
            optimalMoves = state.optimalMoves
        )

        if (!isSolvePlausible) {
            SecurityManager.notifySecurityWarning("Impossible completion parameters.")
            return false
        }

        // 2. Star rating bounds check
        if (stars < 1 || stars > 3) {
            SecurityManager.notifySecurityWarning("Invalid star rating: $stars.")
            return false
        }

        return true
    }

    /**
     * Validates whether a bonus star award is legitimate and allowed to be added to balance.
     */
    fun validateBonusStarAward(requestedAmount: Int): Int {
        val sanitized = RewardValidator.validateBonusStars(requestedAmount)
        if (sanitized <= 0) {
            SecurityManager.notifySecurityWarning("Reward award rejected.")
            return 0
        }
        return sanitized
    }

    /**
     * Resets round tracking metrics on new game start or restart.
     */
    fun onNewGameStarted() {
        lastMoveTimestamp = 0L
    }

    fun getSuspicionCount(): Int = suspicionCounter.get()
}
