package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import ru.mcbuddy.spigot.Plurals
import ru.mcbuddy.spigot.tools.Facts.round1

/** Tools about keeping the player going: gear wear, experience, and what to eat. */
object CareTools {
  private fun tool(name: String, body: (Player, JsonObject) -> ToolOutput) = object : ClientTool {
    override val name = name
    override fun run(context: ToolContext, args: JsonObject) = body(context.requirePlayer(), args)
  }

  private val armorSuffixes = listOf("_helmet", "_chestplate", "_leggings", "_boots")

  private val durability = tool("get_item_durability") { player, _ ->
    val inventory = player.inventory
    val slots: List<Pair<String, ItemStack?>> = (0..8).map { "hotbar_$it" to inventory.getItem(it) } + listOf(
      "head" to inventory.helmet, "chest" to inventory.chestplate, "legs" to inventory.leggings, "feet" to inventory.boots,
      "offhand" to inventory.itemInOffHand,
    )
    var low = 0
    val items = slots.mapNotNull { (slot, item) ->
      if (item == null || item.type.isAir) return@mapNotNull null
      val max = item.type.maxDurability.toInt()
      if (max <= 0) return@mapNotNull null
      val damage = (item.itemMeta as? Damageable)?.damage ?: 0
      val remaining = (max - damage).coerceAtLeast(0)
      val unbreaking = item.getEnchantmentLevel(Enchantment.UNBREAKING)
      val armor = armorSuffixes.any { item.type.key.key.endsWith(it) }
      val percent = Mechanics.durabilityPercent(remaining, max)
      if (percent < 10) low++
      buildJsonObject {
        put("slot", slot)
        put("id", item.type.key.key)
        put("remaining", remaining)
        put("max", max)
        put("percent", percent)
        if (unbreaking > 0) {
          put("unbreaking", unbreaking)
          put("expected_uses", Mechanics.expectedUses(remaining, unbreaking, armor))
        }
        if (item.getEnchantmentLevel(Enchantment.MENDING) > 0) put("mending", true)
        if (percent < 10) put("almost_broken", true)
      }
    }
    val n = items.size.toLong()
    ToolOutput(buildJsonObject { put("items", buildJsonArray { items.forEach(::add) }) }, if (low > 0) "$low на исходе из $n" else "$n ${Plurals.ru(n, "предмет", "предмета", "предметов")}, всё в порядке")
  }

  private val xp = tool("get_xp_info") { player, args ->
    val target = args.int("target_level")?.also { if (it !in 0..100) throw ToolError("target_level must be 0–100") }
    val level = player.level
    val toNext = Mechanics.pointsToNext(level)
    val points = Mechanics.points(level, player.exp)
    val result = buildJsonObject {
      put("level", level)
      put("progress", round1(player.exp.toDouble()))
      put("total_points", points)
      put("points_to_next_level", toNext - (points - Mechanics.pointsAt(level)))
      target?.let {
        put("target_level", it)
        put("points_to_target", (Mechanics.pointsAt(it) - points).coerceAtLeast(0))
      }
    }
    ToolOutput(result, "уровень $level, $points очков")
  }

  private val food = tool("get_food_options") { player, _ ->
    val counts = mutableMapOf<String, Int>()
    val custom = mutableMapOf<String, Pair<Int, Double>>()
    player.inventory.contents.filterNotNull().filterNot { it.type.isAir }.forEach { item ->
      val id = item.type.key.key
      val meta = item.itemMeta
      if (meta?.hasFood() == true) custom[id] = meta.food.nutrition to meta.food.saturation.toDouble()
      if (id in Mechanics.FOODS || id in custom) counts.merge(id, item.amount, Int::plus)
    }
    val foods = counts.entries.map { (id, count) ->
      val known = Mechanics.FOODS[id]
      val nutrition = custom[id]?.first ?: known!!.nutrition
      val saturation = custom[id]?.second ?: known!!.saturation
      buildJsonObject {
        put("id", id)
        put("count", count)
        put("hunger", nutrition)
        put("saturation", saturation)
        known?.note?.let { put("note", it) }
      }
    }.sortedByDescending { (it["saturation"] as kotlinx.serialization.json.JsonPrimitive).content.toDouble() }
    val result = buildJsonObject {
      put("food", player.foodLevel)
      put("saturation", round1(player.saturation.toDouble()))
      put("hunger_missing", 20 - player.foodLevel)
      put("foods", buildJsonArray { foods.forEach(::add) })
    }
    val n = foods.size.toLong()
    ToolOutput(result, if (n == 0L) "еды нет" else "$n ${Plurals.ru(n, "вид", "вида", "видов")} еды")
  }

  val all = listOf(durability, xp, food)
}
