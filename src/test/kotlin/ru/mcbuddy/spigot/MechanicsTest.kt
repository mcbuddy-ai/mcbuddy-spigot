package ru.mcbuddy.spigot

import ru.mcbuddy.spigot.tools.Mechanics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MechanicsTest {
  @Test fun `experience follows the vanilla table`() {
    assertEquals(7, Mechanics.pointsToNext(0))
    assertEquals(37, Mechanics.pointsToNext(15))
    assertEquals(42, Mechanics.pointsToNext(16))
    assertEquals(112, Mechanics.pointsToNext(30))
    assertEquals(121, Mechanics.pointsToNext(31))
    assertEquals(0, Mechanics.pointsAt(0))
    assertEquals(352, Mechanics.pointsAt(16))
    assertEquals(394, Mechanics.pointsAt(17))
    assertEquals(1395, Mechanics.pointsAt(30))
    assertEquals(1628, Mechanics.pointsAt(32))
    // Every level's total is the previous total plus the step between them.
    (0 until 100).forEach { level -> assertEquals(Mechanics.pointsAt(level) + Mechanics.pointsToNext(level), Mechanics.pointsAt(level + 1), "level $level") }
    assertEquals(1395 + 56, Mechanics.points(30, 0.5f))
  }

  @Test fun `bookshelves count only through a free gap`() {
    assertEquals(32, Mechanics.BOOKSHELF_OFFSETS.size)
    assertEquals(32, Mechanics.bookshelves({ _, _, _ -> true }, { _, _, _ -> true }))
    // A torch in the diagonal gap blocks only the corner shelf behind it, on that level.
    val blocked = Mechanics.bookshelves({ _, _, _ -> true }, { dx, dy, dz -> !(dx == 1 && dy == 0 && dz == 1) })
    assertEquals(31, blocked)
    assertEquals(0, Mechanics.bookshelves({ _, _, _ -> false }, { _, _, _ -> true }))
  }

  @Test fun `fifteen shelves make a level thirty offer`() {
    assertEquals(30..30, Mechanics.topOfferRange(15))
    assertEquals(30..30, Mechanics.topOfferRange(32))
    assertEquals(1..8, Mechanics.topOfferRange(0))
    assertEquals(20..23, Mechanics.topOfferRange(10))
  }

  @Test fun `armor and enchantments shape the hits to kill`() {
    val zombieHit = Mechanics.afterArmor(7.0, armor = 2.0, toughness = 0.0)
    assertEquals(6.888, zombieHit, 1e-9)
    assertEquals(3, Mechanics.hitsToKill(20.0, zombieHit))
    assertEquals(3.0, Mechanics.enchantmentBonus(sharpness = 5, smite = 0, bane = 0, undead = true, arthropod = false))
    assertEquals(12.5, Mechanics.enchantmentBonus(sharpness = 0, smite = 5, bane = 0, undead = true, arthropod = false))
    assertEquals(0.0, Mechanics.enchantmentBonus(sharpness = 0, smite = 5, bane = 0, undead = false, arthropod = false))
    assertEquals(2, Mechanics.hitsToKill(20.0, 10.0))
    assertEquals(1, Mechanics.hitsToKill(0.5, 10.0))
    assertNull(Mechanics.hitsToKill(20.0, 0.0))
  }

  @Test fun `unbreaking stretches tools more than armor`() {
    assertEquals(100, Mechanics.expectedUses(100, 0, armor = false))
    assertEquals(400, Mechanics.expectedUses(100, 3, armor = false))
    assertEquals(142, Mechanics.expectedUses(100, 3, armor = true))
    assertEquals(9, Mechanics.durabilityPercent(150, 1561))
  }

  @Test fun `the food table knows the classics`() {
    assertEquals(Mechanics.Food(6, 14.4), Mechanics.FOODS["golden_carrot"])
    assertEquals(8, Mechanics.FOODS["cooked_beef"]!!.nutrition)
    assertEquals(true, Mechanics.FOODS["rotten_flesh"]!!.note?.contains("Hunger"))
    Mechanics.FOODS.values.forEach { assert(it.nutrition in 1..10 && it.saturation >= 0) }
  }
}
