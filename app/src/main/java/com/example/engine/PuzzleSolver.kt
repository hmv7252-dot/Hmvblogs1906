package com.example.engine

import com.example.model.BoardSize
import kotlin.math.abs

object PuzzleSolver {

    /**
     * Checks if a sliding puzzle configuration is solvable.
     * Rule:
     * - If board width is odd (e.g. 3x3, 5x5, 7x7): Solvable if number of inversions is EVEN.
     * - If board width is even (e.g. 4x4, 6x6): Solvable if:
     *     - blank on odd row from bottom (1st, 3rd, 5th, etc.) and inversions is EVEN
     *     - blank on even row from bottom (2nd, 4th, 6th, etc.) and inversions is ODD
     */
    fun isSolvable(tiles: List<Int>, size: BoardSize): Boolean {
        val n = size.dimension
        var inversions = 0
        val nonZeroTiles = tiles.filter { it != 0 }

        for (i in 0 until nonZeroTiles.size) {
            for (j in i + 1 until nonZeroTiles.size) {
                if (nonZeroTiles[i] > nonZeroTiles[j]) {
                    inversions++
                }
            }
        }

        return if (n % 2 != 0) {
            inversions % 2 == 0
        } else {
            val emptyIndex = tiles.indexOf(0)
            val emptyRowFromTop = emptyIndex / n
            val emptyRowFromBottom = n - emptyRowFromTop
            if (emptyRowFromBottom % 2 != 0) {
                inversions % 2 == 0
            } else {
                inversions % 2 != 0
            }
        }
    }

    /**
     * Checks if the board is solved: [1, 2, 3, ..., N-1, 0]
     */
    fun isSolved(tiles: List<Int>): Boolean {
        if (tiles.isEmpty()) return false
        for (i in 0 until tiles.size - 1) {
            if (tiles[i] != i + 1) return false
        }
        return tiles.last() == 0
    }

    /**
     * Calculates the Manhattan Distance of the board state to the solved state.
     */
    fun calculateManhattanDistance(tiles: List<Int>, size: BoardSize): Int {
        val n = size.dimension
        var distance = 0
        for (index in tiles.indices) {
            val tile = tiles[index]
            if (tile != 0) {
                val targetIndex = tile - 1
                val currentRow = index / n
                val currentCol = index % n
                val targetRow = targetIndex / n
                val targetCol = targetIndex % n
                distance += abs(currentRow - targetRow) + abs(currentCol - targetCol)
            }
        }
        return distance
    }

    /**
     * Finds adjacent movable tile indices around the empty space (0).
     */
    fun getMovableTileIndices(tiles: List<Int>, size: BoardSize): List<Int> {
        val emptyIndex = tiles.indexOf(0)
        if (emptyIndex < 0) return emptyList()
        val n = size.dimension
        val row = emptyIndex / n
        val col = emptyIndex % n
        val movable = mutableListOf<Int>()

        if (row > 0) movable.add(emptyIndex - n) // Up
        if (row < n - 1) movable.add(emptyIndex + n) // Down
        if (col > 0) movable.add(emptyIndex - 1) // Left
        if (col < n - 1) movable.add(emptyIndex + 1) // Right

        return movable
    }

    /**
     * Finds the best next move hint.
     * Uses IDA* for 3x3 boards and heuristic lookahead for larger boards.
     */
    fun findBestNextMove(tiles: List<Int>, size: BoardSize): Int? {
        if (isSolved(tiles)) return null
        val emptyIndex = tiles.indexOf(0)
        if (emptyIndex < 0) return null

        val movable = getMovableTileIndices(tiles, size)
        if (movable.isEmpty()) return null

        if (size == BoardSize.SIZE_3X3) {
            // Run quick IDA* search
            val solution = solveIDAStar(tiles, size, maxDepth = 30)
            if (solution != null && solution.isNotEmpty()) {
                return solution.first()
            }
        }

        // 2-ply Lookahead heuristic for larger boards (or if IDA* times out)
        var bestIndex: Int? = null
        var minScore = Int.MAX_VALUE

        for (tileIndex in movable) {
            val swapped = tiles.toMutableList()
            swapped[emptyIndex] = swapped[tileIndex]
            swapped[tileIndex] = 0

            val directDist = calculateManhattanDistance(swapped, size)
            val secondaryMovable = getMovableTileIndices(swapped, size)
            var secondaryMinDist = directDist

            for (secIndex in secondaryMovable) {
                if (secIndex == emptyIndex) continue // don't reverse
                val secSwapped = swapped.toMutableList()
                secSwapped[tileIndex] = secSwapped[secIndex]
                secSwapped[secIndex] = 0
                val secDist = calculateManhattanDistance(secSwapped, size)
                if (secDist < secondaryMinDist) {
                    secondaryMinDist = secDist
                }
            }

            val score = directDist * 2 + secondaryMinDist
            if (score < minScore) {
                minScore = score
                bestIndex = tileIndex
            }
        }

        return bestIndex ?: movable.firstOrNull()
    }

    /**
     * Computes the estimated minimum/optimal moves required to solve the puzzle.
     */
    fun calculateOptimalMoves(tiles: List<Int>, size: BoardSize, shuffleCount: Int): Int {
        if (size == BoardSize.SIZE_3X3) {
            val solution = solveIDAStar(tiles, size, maxDepth = 32)
            if (solution != null) return solution.size
        }
        val manhattan = calculateManhattanDistance(tiles, size)
        // Manhattan distance with linear conflict calibration provides lower bound;
        // calibrate with shuffle depth to give a fair and rewarding optimal target
        val estimated = maxOf(manhattan, (shuffleCount * 0.75).toInt())
        return maxOf(manhattan, estimated)
    }

    /**
     * IDA* Solver implementation for sliding puzzles
     */
    private fun solveIDAStar(
        initialTiles: List<Int>,
        size: BoardSize,
        maxDepth: Int
    ): List<Int>? {
        var threshold = calculateManhattanDistance(initialTiles, size)
        val path = mutableListOf<List<Int>>()
        path.add(initialTiles)
        val moveIndices = mutableListOf<Int>()

        while (threshold <= maxDepth) {
            val result = searchIDA(path, moveIndices, 0, threshold, size)
            if (result == -1) {
                return moveIndices.toList()
            }
            if (result == Int.MAX_VALUE) return null
            threshold = result
        }
        return null
    }

    private fun searchIDA(
        path: MutableList<List<Int>>,
        moves: MutableList<Int>,
        g: Int,
        threshold: Int,
        size: BoardSize
    ): Int {
        val current = path.last()
        val h = calculateManhattanDistance(current, size)
        val f = g + h

        if (f > threshold) return f
        if (h == 0 && isSolved(current)) return -1

        var min = Int.MAX_VALUE
        val emptyIndex = current.indexOf(0)
        val movable = getMovableTileIndices(current, size)

        for (tileIndex in movable) {
            val nextState = current.toMutableList()
            nextState[emptyIndex] = nextState[tileIndex]
            nextState[tileIndex] = 0

            // Avoid cycles in path
            if (path.any { it == nextState }) continue

            path.add(nextState)
            moves.add(tileIndex)

            val t = searchIDA(path, moves, g + 1, threshold, size)
            if (t == -1) return -1
            if (t < min) min = t

            path.removeAt(path.size - 1)
            moves.removeAt(moves.size - 1)
        }
        return min
    }
}
