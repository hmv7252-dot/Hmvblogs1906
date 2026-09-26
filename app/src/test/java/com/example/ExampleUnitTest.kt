package com.example

import com.example.engine.PuzzleGenerator
import com.example.engine.PuzzleSolver
import com.example.model.BoardSize
import com.example.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testBoardSolvabilityAndSize() {
        BoardSize.entries.forEach { size ->
            val (board, optimal) = PuzzleGenerator.generateSolvableBoard(size, Difficulty.EASY)
            assertEquals(size.totalTiles, board.size)
            assertTrue(board.contains(0))
            assertTrue(optimal > 0)
        }
    }

    @Test
    fun testSolverMovableTiles() {
        val size = BoardSize.SIZE_3X3
        // Solved 3x3: [1,2,3, 4,5,6, 7,8,0] -> empty index is 8 (bottom-right)
        val solved = listOf(1, 2, 3, 4, 5, 6, 7, 8, 0)
        assertTrue(PuzzleSolver.isSolved(solved))

        val movables = PuzzleSolver.getMovableTileIndices(solved, size)
        // Neighbors of index 8 in 3x3: left (7) and above (5)
        assertEquals(listOf(5, 7), movables.sorted())
    }

    @Test
    fun testHintFinding() {
        val size = BoardSize.SIZE_3X3
        // Board with 1 swap away from solved: [1,2,3, 4,5,6, 7,0,8]
        val board = listOf(1, 2, 3, 4, 5, 6, 7, 0, 8)
        assertFalse(PuzzleSolver.isSolved(board))

        val bestMove = PuzzleSolver.findBestNextMove(board, size)
        assertNotNull(bestMove)
        // Moving 8 into 0 will solve it (index 8)
        assertEquals(8, bestMove)
    }

    @Test
    fun testCampaignProgression() {
        val level1 = PuzzleGenerator.getCampaignLevel(1)
        assertEquals(BoardSize.SIZE_3X3, level1.boardSize)
        assertEquals(Difficulty.EASY, level1.difficulty)

        val level150 = PuzzleGenerator.getCampaignLevel(150)
        assertEquals(BoardSize.SIZE_4X4, level150.boardSize)

        val level500 = PuzzleGenerator.getCampaignLevel(500)
        assertEquals(BoardSize.SIZE_5X5, level500.boardSize)
    }
}
