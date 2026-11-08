package it.pokerodds

import java.util.SplittableRandom

data class EquityResult(val equity: Double, val win: Double, val tie: Double)

object Equity {
    /**
     * Monte Carlo: deals the rest of the board and random hands to [opponents], counts how often
     * the hero wins (ties split). Returns null if [cancelled] turns true mid-run.
     */
    fun run(hero: IntArray, board: IntArray, opponents: Int, iterations: Int, cancelled: () -> Boolean): EquityResult? {
        val used = BooleanArray(52)
        hero.forEach { used[it] = true }
        board.forEach { used[it] = true }
        val deck = (0 until 52).filter { !used[it] }.toIntArray()
        val need = 5 - board.size
        val draw = need + 2 * opponents
        val h = IntArray(7)
        val o = IntArray(7)
        h[0] = hero[0]; h[1] = hero[1]
        for (i in board.indices) { h[i + 2] = board[i]; o[i + 2] = board[i] }
        val ev = Evaluator()
        val rnd = SplittableRandom()
        var win = 0
        var tie = 0
        var eqSum = 0.0
        val len = deck.size
        for (n in 0 until iterations) {
            if (n % 1000 == 0 && cancelled()) return null
            for (i in 0 until draw) {
                val j = i + rnd.nextInt(len - i)
                val t = deck[i]; deck[i] = deck[j]; deck[j] = t
            }
            for (i in 0 until need) { h[board.size + 2 + i] = deck[i]; o[board.size + 2 + i] = deck[i] }
            val hs = ev.evaluate(h, 7)
            var ties = 0
            var lost = false
            for (p in 0 until opponents) {
                o[0] = deck[need + 2 * p]; o[1] = deck[need + 2 * p + 1]
                val s = ev.evaluate(o, 7)
                if (s > hs) { lost = true; break }
                if (s == hs) ties++
            }
            if (!lost) {
                if (ties == 0) { win++; eqSum += 1.0 } else { tie++; eqSum += 1.0 / (ties + 1) }
            }
        }
        return EquityResult(eqSum / iterations, win.toDouble() / iterations, tie.toDouble() / iterations)
    }

    /** Next cards that improve your hand category to two pair or better (not counting board-only improvements). */
    fun outs(hero: IntArray, board: IntArray): Int? {
        if (board.size != 3 && board.size != 4) return null
        val ev = Evaluator()
        val all = hero + board
        val cur = Evaluator.category(ev.evaluate(all, all.size))
        val used = all.toSet()
        var outs = 0
        for (c in 0 until 52) {
            if (c in used) continue
            val nb = board + c
            val mine = hero + nb
            val cat = Evaluator.category(ev.evaluate(mine, mine.size))
            val bcat = Evaluator.category(ev.evaluate(nb, nb.size))
            if (cat > cur && cat > bcat && cat >= 2) outs++
        }
        return outs
    }
}
