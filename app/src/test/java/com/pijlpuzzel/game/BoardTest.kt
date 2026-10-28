package com.pijlpuzzel.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardTest {

    @Test
    fun blockedArrowReportsBlocker() {
        val front = Arrow(2, 0, Dir.LEFT)
        val back = Arrow(0, 0, Dir.RIGHT)
        val board = Board(3, 1, listOf(back, front))
        assertEquals(front, board.blocker(back))
        assertEquals(back, board.blocker(front))
    }

    @Test
    fun freeArrowCanEscapeAndBeRemoved() {
        val a = Arrow(1, 1, Dir.UP)
        val board = Board(3, 3, listOf(a))
        assertNull(board.blocker(a))
        board.remove(a)
        assertTrue(board.isCleared)
    }

    @Test
    fun everyGeneratedLevelIsSolvable() {
        for (level in 1..300) {
            val board = LevelGenerator.generate(level)
            val start = board.remaining
            assertTrue("level $level is leeg", start > 0)
            // Wegtikken maakt alleen ruimte vrij, dus steeds een vrije pijl kiezen lost elk oplosbaar level op.
            while (!board.isCleared) {
                val free = board.hint()
                assertNotNull("level $level vastgelopen met ${board.remaining} van $start pijlen", free)
                board.remove(free!!)
            }
        }
    }

    @Test
    fun levelsGrowAndAreDeterministic() {
        val (c1, r1) = LevelGenerator.sizeFor(1)
        val (c30, r30) = LevelGenerator.sizeFor(30)
        assertTrue(c30 * r30 > c1 * r1)
        assertEquals(LevelGenerator.generate(7).arrows(), LevelGenerator.generate(7).arrows())
    }
}
