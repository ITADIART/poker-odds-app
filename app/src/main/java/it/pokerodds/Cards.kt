package it.pokerodds

/** Cards are ints 0..51: rank = c shr 2 (0 = deuce .. 12 = ace), suit = c and 3 (s, h, d, c). */
object Cards {
    const val RANKS = "23456789TJQKA"
    const val SUITS = "shdc"
    private const val SUIT_SYMBOLS = "♠♥♦♣"
    val RANK_IT = arrayOf("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A")

    fun rank(c: Int) = c shr 2
    fun suit(c: Int) = c and 3
    fun of(rank: Int, suit: Int) = rank * 4 + suit

    fun display(c: Int): String = RANK_IT[rank(c)] + SUIT_SYMBOLS[suit(c)]

    fun suitColor(c: Int): Int = when (suit(c)) {
        0 -> 0xFF18181A.toInt()
        1 -> 0xFFC8102E.toInt()
        2 -> 0xFF1F5FBF.toInt()
        else -> 0xFF1E7A3C.toInt()
    }

    private val rankWords = mapOf(
        "ace" to 12, "king" to 11, "queen" to 10, "jack" to 9, "ten" to 8, "nine" to 7,
        "eight" to 6, "seven" to 5, "six" to 4, "five" to 3, "four" to 2, "three" to 1, "two" to 0,
    )
    private val suitWords = mapOf("spade" to 0, "heart" to 1, "diamond" to 2, "club" to 3)

    /**
     * Turns a model class name into a card. Accepts "10C", "10c", "Th", "AH", "ace of hearts",
     * "ace_hearts" and similar. Returns -1 for anything that is not a card (e.g. a joker).
     */
    fun parseLabel(raw: String): Int {
        val s = raw.trim().lowercase()
        if (s.isEmpty()) return -1
        // Word form
        val suitW = suitWords.entries.firstOrNull { s.contains(it.key) }?.value
        if (suitW != null) {
            val rankW = rankWords.entries.firstOrNull { s.contains(it.key) }?.value
                ?: Regex("(10|[2-9]|[akqjt])").find(s.substringBefore(suitWords.keys.first { s.contains(it) }))
                    ?.value?.let { rankFromToken(it) }
            return if (rankW != null && rankW >= 0) of(rankW, suitW) else -1
        }
        // Compact form: rank + suit letter
        val compact = s.replace(Regex("[^0-9a-z]"), "")
        if (compact.length < 2) return -1
        val suitIdx = SUITS.indexOf(compact.last())
        if (suitIdx < 0) return -1
        val r = rankFromToken(compact.dropLast(1))
        return if (r >= 0) of(r, suitIdx) else -1
    }

    private fun rankFromToken(t: String): Int = when (t) {
        "a", "1" -> 12
        "k" -> 11
        "q" -> 10
        "j" -> 9
        "t", "10" -> 8
        else -> t.toIntOrNull()?.takeIf { it in 2..9 }?.minus(2) ?: -1
    }
}
