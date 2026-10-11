package ru.mcbuddy.spigot.tools

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Vanilla formulas the tools answer with, so the model gets exact numbers instead of guessing. */
object Mechanics {
  /** Experience points needed to go from this level to the next one. */
  fun pointsToNext(level: Int): Int = when {
    level <= 15 -> 2 * level + 7
    level <= 30 -> 5 * level - 38
    else -> 9 * level - 158
  }

  /** Experience points it takes to reach this level from zero, as whole numbers. */
  fun pointsAt(level: Int): Long {
    val l = level.toLong()
    return when {
      level <= 16 -> l * l + 6 * l
      level <= 31 -> (5 * l * l - 81 * l + 720) / 2
      else -> (9 * l * l - 325 * l + 4440) / 2
    }
  }

  /** The player's points: everything up to the level plus the part of the bar already filled. */
  fun points(level: Int, progress: Float): Long = pointsAt(level) + (progress.coerceIn(0f, 1f) * pointsToNext(level)).toLong()

  /** Where bookshelves count around an enchanting table: a ring two blocks out, on the table's level and one above. */
  val BOOKSHELF_OFFSETS: List<Triple<Int, Int, Int>> = buildList {
    for (dx in -2..2) for (dz in -2..2) for (dy in 0..1) {
      if (Math.abs(dx) == 2 || Math.abs(dz) == 2) add(Triple(dx, dy, dz))
    }
  }

  /** Counts the shelves that power a table: a shelf works only when the block between it and the table lets power through. */
  fun bookshelves(provider: (Int, Int, Int) -> Boolean, transmitter: (Int, Int, Int) -> Boolean): Int =
    BOOKSHELF_OFFSETS.count { (dx, dy, dz) -> provider(dx, dy, dz) && transmitter(dx / 2, dy, dz / 2) }

  /** The bottom and top of the third (best) offer's level for this many shelves; more than 15 count as 15. */
  fun topOfferRange(shelves: Int): IntRange {
    val b = shelves.coerceIn(0, 15)
    return max(1 + b / 2, b * 2)..max(8 + b / 2 + b, b * 2)
  }

  /** Damage after armor, as Java Edition computes it for a melee hit. */
  fun afterArmor(damage: Double, armor: Double, toughness: Double): Double {
    val reduction = (armor - damage / (2 + toughness / 4)).coerceIn(armor * 0.2, 20.0)
    return damage * (1 - reduction / 25)
  }

  /** Extra melee damage from the weapon's enchantments against this kind of mob. */
  fun enchantmentBonus(sharpness: Int, smite: Int, bane: Int, undead: Boolean, arthropod: Boolean): Double =
    (if (sharpness > 0) 0.5 * sharpness + 0.5 else 0.0) +
      (if (undead) 2.5 * smite else 0.0) +
      (if (arthropod) 2.5 * bane else 0.0)

  /** Fully charged hits without crits to bring the health to zero; null when a hit does nothing. */
  fun hitsToKill(health: Double, perHit: Double): Int? = if (perHit <= 0.0) null else max(1, ceil(health / perHit - 1e-9).toInt())

  /**
   * Uses left, counting Unbreaking: tools and weapons skip damage with chance 1 − 1/(L+1),
   * armor with chance 0.4·(1 − 1/(L+1)), so it lasts less long than a tool.
   */
  fun expectedUses(remaining: Int, unbreaking: Int, armor: Boolean): Int {
    if (unbreaking <= 0) return remaining
    val chance = if (armor) 0.6 + 0.4 / (unbreaking + 1) else 1.0 / (unbreaking + 1)
    return (remaining / chance).toInt()
  }

  fun durabilityPercent(remaining: Int, maxDurability: Int): Int = if (maxDurability <= 0) 100 else min(100, remaining * 100 / maxDurability)

  /** Hunger points and saturation each food restores, plus what else eating it does. */
  data class Food(val nutrition: Int, val saturation: Double, val note: String? = null)

  val FOODS: Map<String, Food> = mapOf(
    "apple" to Food(4, 2.4),
    "baked_potato" to Food(5, 6.0),
    "beef" to Food(3, 1.8),
    "beetroot" to Food(1, 1.2),
    "beetroot_soup" to Food(6, 7.2),
    "bread" to Food(5, 6.0),
    "carrot" to Food(3, 3.6),
    "chicken" to Food(2, 1.2, "30% chance of Hunger for 30 s"),
    "chorus_fruit" to Food(4, 2.4, "teleports up to 8 blocks; can be eaten when full"),
    "cod" to Food(2, 0.4),
    "cooked_beef" to Food(8, 12.8),
    "cooked_chicken" to Food(6, 7.2),
    "cooked_cod" to Food(5, 6.0),
    "cooked_mutton" to Food(6, 9.6),
    "cooked_porkchop" to Food(8, 12.8),
    "cooked_rabbit" to Food(5, 6.0),
    "cooked_salmon" to Food(6, 9.6),
    "cookie" to Food(2, 0.4),
    "dried_kelp" to Food(1, 0.6, "eaten fast"),
    "enchanted_golden_apple" to Food(4, 9.6, "Regeneration II 20 s, Absorption IV 2 min, Resistance and Fire Resistance 5 min; can be eaten when full"),
    "glow_berries" to Food(2, 0.4),
    "golden_apple" to Food(4, 9.6, "Regeneration II 5 s, Absorption 2 min; can be eaten when full"),
    "golden_carrot" to Food(6, 14.4),
    "honey_bottle" to Food(6, 1.2, "cures Poison; leaves a glass bottle"),
    "melon_slice" to Food(2, 1.2),
    "mushroom_stew" to Food(6, 7.2),
    "mutton" to Food(2, 1.2),
    "poisonous_potato" to Food(2, 1.2, "60% chance of Poison for 5 s"),
    "porkchop" to Food(3, 1.8),
    "potato" to Food(1, 0.6),
    "pufferfish" to Food(1, 0.2, "Hunger III 15 s, Nausea 15 s, Poison II 60 s"),
    "pumpkin_pie" to Food(8, 4.8),
    "rabbit" to Food(3, 1.8),
    "rabbit_stew" to Food(10, 12.0),
    "rotten_flesh" to Food(4, 0.8, "80% chance of Hunger for 30 s"),
    "salmon" to Food(2, 0.4),
    "spider_eye" to Food(2, 3.2, "Poison for 5 s"),
    "suspicious_stew" to Food(6, 7.2, "gives the effect of the flower it was made with"),
    "sweet_berries" to Food(2, 0.4),
    "tropical_fish" to Food(1, 0.2),
  )
}
