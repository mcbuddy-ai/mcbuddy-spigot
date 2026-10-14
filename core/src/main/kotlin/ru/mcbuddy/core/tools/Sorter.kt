package ru.mcbuddy.core.tools

/**
 * Sorts and merges inventory stacks without touching Bukkit, so tests can reach it. Equal items
 * pool their counts and come back as full stacks in [order]; empty slots end up last.
 */
object Sorter {
  class Sorted<T>(val slots: List<T?>, val merged: Int)

  /** Returns null when the items wouldn't fit back into the same number of slots. */
  fun <T> sort(
    items: List<T?>,
    amount: (T) -> Int,
    maxStack: (T) -> Int,
    similar: (T, T) -> Boolean,
    order: Comparator<T>,
    withAmount: (T, Int) -> T,
  ): Sorted<T>? {
    val pools = mutableListOf<Pair<T, Int>>()
    items.filterNotNull().forEach { item ->
      val index = pools.indexOfFirst { similar(it.first, item) }
      if (index < 0) pools += item to amount(item) else pools[index] = pools[index].first to pools[index].second + amount(item)
    }
    val stacks = pools.sortedWith(compareBy(order) { it.first }).flatMap { (item, total) ->
      val max = maxStack(item).coerceAtLeast(1)
      (0 until (total + max - 1) / max).map { withAmount(item, minOf(max, total - it * max)) }
    }
    if (stacks.size > items.size) return null
    val before = items.count { it != null }
    return Sorted(stacks + List(items.size - stacks.size) { null }, before - stacks.size)
  }
}
