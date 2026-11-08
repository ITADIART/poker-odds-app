package it.pokerodds

/**
 * Best-5-of-up-to-7 hand evaluator. Higher score = better hand.
 * score = category shl 20 | tie-break ranks in 4-bit nibbles.
 * Not thread-safe: use one instance per thread.
 */
class Evaluator {
    private val rc = IntArray(13)
    private val sc = IntArray(4)
    private val sm = IntArray(4)

    private fun straightHigh(mask: Int): Int {
        val m = (mask shl 1) or ((mask shr 12) and 1) // bit 0 = ace played low
        for (hi in 13 downTo 4) if (((m shr (hi - 4)) and 31) == 31) return hi - 1
        return -1
    }

    private fun kickers(ex1: Int, ex2: Int, count: Int): Int {
        var out = 0
        var got = 0
        var r = 12
        while (r >= 0 && got < count) {
            if (rc[r] > 0 && r != ex1 && r != ex2) {
                out = (out shl 4) or r
                got++
            }
            r--
        }
        return out
    }

    fun evaluate(cards: IntArray, n: Int): Int {
        rc.fill(0); sc.fill(0); sm.fill(0)
        var rmask = 0
        for (i in 0 until n) {
            val c = cards[i]
            val r = c shr 2
            val s = c and 3
            rc[r]++; sc[s]++
            sm[s] = sm[s] or (1 shl r)
            rmask = rmask or (1 shl r)
        }
        var f = -1
        for (s in 0..3) if (sc[s] >= 5) f = s
        if (f >= 0) {
            val sf = straightHigh(sm[f])
            if (sf >= 0) return (8 shl 20) or (sf shl 16)
        }
        var q = -1; var t1 = -1; var t2 = -1; var p1 = -1; var p2 = -1
        for (r in 12 downTo 0) {
            when (rc[r]) {
                4 -> q = r
                3 -> if (t1 < 0) t1 = r else if (t2 < 0) t2 = r
                2 -> if (p1 < 0) p1 = r else if (p2 < 0) p2 = r
            }
        }
        if (q >= 0) return (7 shl 20) or (q shl 16) or (kickers(q, -1, 1) shl 12)
        if (t1 >= 0 && (t2 >= 0 || p1 >= 0)) return (6 shl 20) or (t1 shl 16) or (maxOf(t2, p1) shl 12)
        if (f >= 0) {
            var out = 0
            var got = 0
            var r = 12
            while (r >= 0 && got < 5) {
                if (sm[f] and (1 shl r) != 0) { out = (out shl 4) or r; got++ }
                r--
            }
            return (5 shl 20) or out
        }
        val st = straightHigh(rmask)
        if (st >= 0) return (4 shl 20) or (st shl 16)
        if (t1 >= 0) return (3 shl 20) or (t1 shl 16) or (kickers(t1, -1, 2) shl 8)
        if (p1 >= 0 && p2 >= 0) return (2 shl 20) or (p1 shl 16) or (p2 shl 12) or (kickers(p1, p2, 1) shl 8)
        if (p1 >= 0) return (1 shl 20) or (p1 shl 16) or (kickers(p1, -1, 3) shl 4)
        return kickers(-1, -1, 5)
    }

    companion object {
        fun category(score: Int) = score shr 20

        fun handName(score: Int): String {
            val a = (score shr 16) and 15
            val b = (score shr 12) and 15
            val r = Cards.RANK_IT
            return when (score shr 20) {
                8 -> if (a == 12) "Scala reale" else "Scala colore al ${r[a]}"
                7 -> "Poker di ${r[a]}"
                6 -> "Full ${r[a]}/${r[b]}"
                5 -> "Colore"
                4 -> "Scala al ${r[a]}"
                3 -> "Tris di ${r[a]}"
                2 -> "Doppia coppia ${r[a]}/${r[b]}"
                1 -> "Coppia di ${r[a]}"
                else -> "Carta alta ${r[a]}"
            }
        }
    }
}
