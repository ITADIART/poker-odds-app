package it.pokerodds

/**
 * Smooths detections over the last frames so a card only counts once it has been seen steadily,
 * then splits them into your cards (below the line) and the board (above the line, left to right).
 */
class Tracker(private val window: Int = 8, private val need: Int = 3) {
    private data class Seen(val cx: Float, val cy: Float, val conf: Float)

    private val frames = ArrayDeque<Map<Int, Seen>>()

    fun reset() = frames.clear()

    fun update(dets: List<Detection>, split: Float): Pair<List<Int>, List<Int>> {
        // A card is usually found twice (both corner indices): average its position.
        val grouped = dets.groupBy { it.card }.mapValues { (_, ds) ->
            Seen(
                ds.map { it.box.centerX() }.average().toFloat(),
                ds.map { it.box.centerY() }.average().toFloat(),
                ds.maxOf { it.conf },
            )
        }
        frames.addLast(grouped)
        while (frames.size > window) frames.removeFirst()

        val counts = HashMap<Int, Int>()
        val last = HashMap<Int, Seen>()
        for (f in frames) for ((c, s) in f) {
            counts[c] = (counts[c] ?: 0) + 1
            last[c] = s
        }
        val stable = counts.filter { it.value >= need }.keys
        val hero = stable.filter { last[it]!!.cy > split }
            .sortedByDescending { counts[it]!! * 10 + last[it]!!.conf }
            .take(2)
        val board = stable.filter { last[it]!!.cy <= split }
            .sortedByDescending { counts[it]!! * 10 + last[it]!!.conf }
            .take(5)
            .sortedBy { last[it]!!.cx }
        return hero to board
    }
}
