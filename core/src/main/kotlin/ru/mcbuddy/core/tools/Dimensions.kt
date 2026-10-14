package ru.mcbuddy.core.tools

/**
 * Dimension names as the game's ids: `overworld`, `the_nether`, `the_end`. Input is forgiving (`nether`, `end`,
 * and Bukkit's `normal` all work), so notes and goals saved by 2.3 under Bukkit's names still match. Each platform
 * turns its own worlds into these ids.
 */
object Dimensions {
  /** The id for a name the model or an old file used; null stays null, anything unknown passes through lowercased. */
  fun normalize(name: String?): String? = when (val key = name?.trim()?.lowercase()?.removePrefix("minecraft:")) {
    null, "" -> null
    "normal", "overworld", "world" -> "overworld"
    "nether", "the_nether" -> "the_nether"
    "end", "the_end" -> "the_end"
    else -> key
  }

  /** A dimension the model named, checked: one of the three ids, or a [ToolError] that lists them. */
  fun parse(name: String): String = normalize(name)?.takeIf { it in IDS } ?: throw ToolError("dimension must be overworld, the_nether, or the_end")

  val IDS = setOf("overworld", "the_nether", "the_end")
}
