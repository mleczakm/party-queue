package pl.mleczki.partyqueue

/** Where one visible list row sits on screen. */
data class RowInfo(val key: Any, val index: Int, val offset: Int, val size: Int)

/** Swap the dragged row one slot ([delta] = +1 down, -1 up) and add [shift] to its drag offset to keep it under the finger. */
data class Swap(val delta: Int, val shift: Float)

/**
 * Decides whether a row that is being carried by [dragDelta] pixels has moved over a neighbour.
 * Returns null while the layout still shows an older order than [order], so one drag cannot swap twice.
 */
fun nextSwap(rows: List<RowInfo>, order: List<String>, dragged: String, dragDelta: Float): Swap? {
    if (rows.any { order.getOrNull(it.index) != it.key }) return null
    val cur = rows.firstOrNull { it.key == dragged } ?: return null
    val centre = cur.offset + cur.size / 2f + dragDelta
    val target = rows.firstOrNull { it.key != dragged && centre >= it.offset && centre < it.offset + it.size } ?: return null
    val down = target.index > cur.index
    return Swap(if (down) 1 else -1, if (down) -target.size.toFloat() else target.size.toFloat())
}
