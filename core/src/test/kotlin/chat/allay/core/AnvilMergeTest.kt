package chat.allay.core

import chat.allay.core.tools.Mechanics
import chat.allay.core.tools.Mechanics.AnvilPiece
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnvilMergeTest {
  /** A bow as the game has it: its enchantments, their maximum levels, and Infinity against Mending. */
  private val bow = object : Mechanics.Enchanting {
    private val max = mapOf("power" to 5, "punch" to 2, "flame" to 1, "infinity" to 1, "unbreaking" to 3, "mending" to 1)
    override fun maxLevel(key: String) = max[key] ?: 1
    override fun fits(key: String) = key in max
    override fun compatible(a: String, b: String) = setOf(a, b) != setOf("infinity", "mending")
  }

  private fun item(vararg enchantments: Pair<String, Int>, repairCost: Int = 0) = AnvilPiece(false, mapOf(*enchantments), repairCost)
  private fun book(vararg enchantments: Pair<String, Int>, repairCost: Int = 0) = AnvilPiece(true, mapOf(*enchantments), repairCost)

  @Test fun `equal levels go one up and are paid at the new level`() {
    val made = assertNotNull(Mechanics.combine(item("power" to 3), item("power" to 3), bow))
    assertEquals(mapOf("power" to 4), made.result.enchantments)
    assertEquals(4, made.cost)
    assertEquals(1, made.result.repairCost)
  }

  @Test fun `a book pays half, and both repair costs count`() {
    val made = assertNotNull(Mechanics.combine(item("unbreaking" to 1, repairCost = 3), book("unbreaking" to 3, repairCost = 1), bow))
    assertEquals(3 + 1 + 1 * 3, made.cost)
    assertEquals(7, made.result.repairCost)
  }

  @Test fun `a conflict costs a level and gives nothing`() {
    assertNull(Mechanics.combine(item("infinity" to 1), book("mending" to 1), bow))
    // With something that fits, the conflict still adds its level.
    val made = assertNotNull(Mechanics.combine(item("infinity" to 1), book("mending" to 1, "power" to 1), bow))
    assertEquals(mapOf("infinity" to 1, "power" to 1), made.result.enchantments)
    assertEquals(1 + 1, made.cost)
  }

  @Test fun `a book never takes an item`() {
    assertNull(Mechanics.combine(book("power" to 1), item("power" to 1), bow))
  }

  @Test fun `of two bows the weaker goes on the left, since only the right side is paid for`() {
    val strong = item("power" to 3, "unbreaking" to 3)
    val weak = item("power" to 2, "unbreaking" to 2, "punch" to 1)
    val plan = assertNotNull(Mechanics.mergePlan(listOf(strong, weak), bow))
    assertEquals(listOf(1), plan.steps.single().left)
    assertEquals(mapOf("power" to 3, "unbreaking" to 3, "punch" to 1), plan.result.enchantments)
    assertEquals(9, plan.total)
  }

  @Test fun `order decides whether two and two and three reach four`() {
    val plan = assertNotNull(Mechanics.mergePlan(listOf(item("power" to 2), book("power" to 2), book("power" to 3)), bow))
    assertEquals(4, plan.result.enchantments["power"])
    assertTrue(!plan.tooExpensive)
  }

  @Test fun `nothing to gain means no plan`() {
    assertNull(Mechanics.mergePlan(listOf(item("power" to 5), book("power" to 3)), bow))
  }

  @Test fun `a useless piece is left out`() {
    val plan = assertNotNull(Mechanics.mergePlan(listOf(item("power" to 5), book("power" to 3), book("flame" to 1)), bow))
    assertEquals(listOf(0, 2), plan.used)
    assertEquals(mapOf("power" to 5, "flame" to 1), plan.result.enchantments)
  }
}
