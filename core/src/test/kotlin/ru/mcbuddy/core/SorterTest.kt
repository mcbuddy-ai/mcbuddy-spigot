package ru.mcbuddy.core

import ru.mcbuddy.core.tools.Sorter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SorterTest {
  /** An item as the sorter sees it: what it is, extras that make it unique, how many, and its stack size. */
  data class Item(val id: String, val meta: String = "", val amount: Int, val max: Int = 64)

  private fun sort(items: List<Item?>) = Sorter.sort(
    items = items,
    amount = Item::amount,
    maxStack = Item::max,
    similar = { a, b -> a.id == b.id && a.meta == b.meta },
    order = compareBy<Item> { it.id }.thenByDescending { it.amount },
    withAmount = { item, amount -> item.copy(amount = amount) },
  )

  @Test fun `equal items merge into full stacks and empty slots go last`() {
    val sorted = sort(listOf(Item("stone", amount = 40), null, Item("dirt", amount = 10), Item("stone", amount = 40), null))!!
    assertEquals(listOf(Item("dirt", amount = 10), Item("stone", amount = 64), Item("stone", amount = 16), null, null), sorted.slots)
    assertEquals(0, sorted.merged)
  }

  @Test fun `merging frees slots and counts them`() {
    val sorted = sort(listOf(Item("stone", amount = 10), Item("stone", amount = 10), Item("stone", amount = 10)))!!
    assertEquals(listOf(Item("stone", amount = 30), null, null), sorted.slots)
    assertEquals(2, sorted.merged)
  }

  @Test fun `items that differ in meta or stack size stay apart`() {
    val sorted = sort(
      listOf(
        Item("diamond_sword", "sharpness 5", 1, max = 1),
        Item("diamond_sword", "", 1, max = 1),
        Item("ender_pearl", amount = 10, max = 16),
        Item("ender_pearl", amount = 10, max = 16),
      ),
    )!!
    assertEquals(
      listOf(Item("diamond_sword", "sharpness 5", 1, 1), Item("diamond_sword", "", 1, 1), Item("ender_pearl", amount = 16, max = 16), Item("ender_pearl", amount = 4, max = 16)),
      sorted.slots,
    )
  }

  @Test fun `nothing moves when overstacked items wouldn't fit back`() {
    assertNull(sort(listOf(Item("stone", amount = 200), null)))
  }
}
