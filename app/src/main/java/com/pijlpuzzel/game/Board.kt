package com.pijlpuzzel.game

import kotlin.random.Random

enum class Dir(val dx: Int, val dy: Int, val degrees: Float) {
    UP(0, -1, 0f), RIGHT(1, 0, 90f), DOWN(0, 1, 180f), LEFT(-1, 0, 270f)
}

data class Arrow(val x: Int, val y: Int, val dir: Dir)

/** Speelveld zonder Android-afhankelijkheden, zodat het los te testen is. */
class Board(val cols: Int, val rows: Int, arrows: List<Arrow>) {
    private val grid = arrayOfNulls<Arrow>(cols * rows)

    init {
        arrows.forEach { grid[index(it.x, it.y)] = it }
    }

    val remaining: Int get() = grid.count { it != null }
    val isCleared: Boolean get() = remaining == 0

    fun arrows(): List<Arrow> = grid.filterNotNull()

    fun arrowAt(x: Int, y: Int): Arrow? = if (inBounds(x, y)) grid[index(x, y)] else null

    /** De eerste pijl die in de weg staat, of null als de pijl vrij kan wegschuiven. */
    fun blocker(a: Arrow): Arrow? {
        var x = a.x + a.dir.dx
        var y = a.y + a.dir.dy
        while (inBounds(x, y)) {
            grid[index(x, y)]?.let { return it }
            x += a.dir.dx
            y += a.dir.dy
        }
        return null
    }

    fun canEscape(a: Arrow) = blocker(a) == null

    /** Aantal vakjes tot de rand, inclusief het vakje van de pijl zelf. */
    fun distanceToEdge(a: Arrow): Int = when (a.dir) {
        Dir.UP -> a.y + 1
        Dir.DOWN -> rows - a.y
        Dir.LEFT -> a.x + 1
        Dir.RIGHT -> cols - a.x
    }

    fun remove(a: Arrow) {
        if (grid[index(a.x, a.y)] == a) grid[index(a.x, a.y)] = null
    }

    fun hint(): Arrow? = arrows().firstOrNull { canEscape(it) }

    private fun inBounds(x: Int, y: Int) = x in 0 until cols && y in 0 until rows
    private fun index(x: Int, y: Int) = y * cols + x
}

object LevelGenerator {

    fun sizeFor(level: Int): Pair<Int, Int> {
        val cols = (4 + (level - 1) / 3).coerceAtMost(9)
        val rows = (5 + (level - 1) / 2).coerceAtMost(13)
        return cols to rows
    }

    fun densityFor(level: Int): Double = (0.55 + level * 0.015).coerceAtMost(0.92)

    /**
     * Bouwt een level dat altijd oplosbaar is. Pijlen worden geplaatst in omgekeerde
     * volgorde van wegtikken: elke nieuwe pijl moet een vrije baan naar de rand hebben
     * langs de pijlen die er al liggen. Die liggen er later nog, dus wie de pijlen in
     * omgekeerde volgorde wegtikt, botst nooit.
     */
    fun generate(level: Int): Board {
        val (cols, rows) = sizeFor(level)
        val random = Random(level * 7919L + 13)
        val target = (cols * rows * densityFor(level)).toInt()
        val placed = mutableListOf<Arrow>()
        val occupied = BooleanArray(cols * rows)

        fun pathClear(x: Int, y: Int, d: Dir): Boolean {
            var cx = x + d.dx
            var cy = y + d.dy
            while (cx in 0 until cols && cy in 0 until rows) {
                if (occupied[cy * cols + cx]) return false
                cx += d.dx
                cy += d.dy
            }
            return true
        }

        var progress = true
        while (placed.size < target && progress) {
            progress = false
            val empty = (0 until cols * rows).filter { !occupied[it] }.shuffled(random)
            for (cell in empty) {
                if (placed.size >= target) break
                val x = cell % cols
                val y = cell / cols
                val dir = Dir.entries.shuffled(random).firstOrNull { pathClear(x, y, it) } ?: continue
                placed += Arrow(x, y, dir)
                occupied[cell] = true
                progress = true
            }
        }
        return Board(cols, rows, placed)
    }
}
