package pl.mleczki.partyqueue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReorderMathTest {

    private val order = listOf("a", "b", "c", "d")
    // four rows of height 100 starting at 0
    private val rows = order.mapIndexed { i, k -> RowInfo(k, i, i * 100, 100) }

    @Test
    fun `a small drag inside the own slot swaps nothing`() {
        assertNull(nextSwap(rows, order, "b", 30f))
        assertNull(nextSwap(rows, order, "b", -30f))
    }

    @Test
    fun `carrying a row past the middle of the next one swaps it down`() {
        val swap = nextSwap(rows, order, "b", 60f)!! // centre 150 + 60 = 210 -> inside "c"
        assertEquals(1, swap.delta)
        assertEquals(-100f, swap.shift, 0.001f)
    }

    @Test
    fun `carrying a row up over the previous one swaps it up`() {
        val swap = nextSwap(rows, order, "c", -60f)!! // centre 250 - 60 = 190 -> inside "b"
        assertEquals(-1, swap.delta)
        assertEquals(100f, swap.shift, 0.001f)
    }

    @Test
    fun `the row stays under the finger after the swap`() {
        // before: slot top 100, delta 60 -> drawn at 160. after moving down the slot top is 200, delta becomes -40 -> 160.
        val swap = nextSwap(rows, order, "b", 60f)!!
        assertEquals(160f, 200f + (60f + swap.shift), 0.001f)
    }

    @Test
    fun `no swap while the layout lags behind the new order`() {
        val stale = listOf(order[1], order[0], order[2], order[3]).mapIndexed { i, k -> RowInfo(k, i, i * 100, 100) }
        assertNull(nextSwap(stale, order, "b", 60f))
    }

    @Test
    fun `dragging past the last row or off screen swaps nothing`() {
        assertNull(nextSwap(rows, order, "d", 500f))
        assertNull(nextSwap(rows, order, "a", -500f))
        assertNull(nextSwap(rows, order, "missing", 10f))
    }

    @Test
    fun `rows of different heights use the neighbour's own height`() {
        val uneven = listOf(RowInfo("a", 0, 0, 80), RowInfo("b", 1, 80, 160), RowInfo("c", 2, 240, 100))
        val swap = nextSwap(uneven, listOf("a", "b", "c"), "a", 90f)!! // centre 40 + 90 = 130 -> inside "b"
        assertEquals(1, swap.delta)
        assertEquals(-160f, swap.shift, 0.001f)
    }
}
