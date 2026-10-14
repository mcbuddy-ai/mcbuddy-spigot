package ru.mcbuddy.spigot.tools

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Enemy
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import ru.mcbuddy.core.tools.Facts.place
import ru.mcbuddy.core.tools.Facts.round1
import ru.mcbuddy.core.tools.Facts.short

/** Tools about the asking player: state, inventory, surroundings, statistics, the villagers around. */
object PlayerTools {
  private fun tool(name: String, body: (ToolContext, Player, JsonObject) -> ToolOutput) = object : Tool {
    override val name = name
    override fun run(context: ToolContext, args: JsonObject) = body(context, context.requirePlayer(), args)
  }

  private val status = tool("get_player_status") { context, player, _ ->
    val location = player.location
    val block = location.block
    val maxHealth = player.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
    val result = buildJsonObject {
      put("gamemode", player.gameMode.name.lowercase())
      put("health", round1(player.health))
      put("max_health", round1(maxHealth))
      put("food", player.foodLevel)
      put("saturation", round1(player.saturation.toDouble()))
      put("level", player.level)
      put("level_progress", round1(player.exp.toDouble()))
      put("x", location.blockX)
      put("y", location.blockY)
      put("z", location.blockZ)
      put("dimension", Dimensions.id(player.world))
      put("biome", short(block.biome.key.toString()))
      put("light", block.lightLevel.toInt())
      put("facing", player.facing.name.lowercase())
      if (player.isFlying) put("flying", true)
      put("effects", buildJsonArray {
        player.activePotionEffects.forEach {
          add(buildJsonObject {
            put("type", it.type.key.key)
            put("level", it.amplifier + 1)
            put("seconds", if (it.isInfinite) -1 else it.duration / 20)
          })
        }
      })
      player.inventory.itemInMainHand.compact()?.let { put("held_item", it) }
    }
    ToolOutput(result, context.lang.text("summary.status", "health" to round1(player.health), "max" to round1(maxHealth), "food" to player.foodLevel))
  }

  private val inventory = tool("get_inventory") { context, player, args ->
    val inventory = player.inventory
    val main = inventory.storageContents.mapIndexedNotNull { slot, item -> item.compact(slot) }
    val result = buildJsonObject {
      put("main", buildJsonArray { main.forEach(::add) })
      putJsonObject("armor") {
        inventory.helmet.compact()?.let { put("head", it) }
        inventory.chestplate.compact()?.let { put("chest", it) }
        inventory.leggings.compact()?.let { put("legs", it) }
        inventory.boots.compact()?.let { put("feet", it) }
      }
      inventory.itemInOffHand.compact()?.let { put("offhand", it) }
      // Totals so “how many arrows do I have” counts the ones in a bundle too.
      Items.inside((inventory.storageContents.asIterable() + inventory.itemInOffHand).data()).takeIf { it.isNotEmpty() }?.let { totals ->
        putJsonObject("inside_containers") { totals.entries.sortedByDescending { it.value }.forEach { (id, n) -> put(id, n) } }
      }
      if (args.bool("include_enderchest") == true) {
        val chest = player.enderChest.contents
        put("enderchest", buildJsonArray { chest.forEachIndexed { slot, item -> item.compact(slot)?.let(::add) } })
        Items.inside(chest.data()).takeIf { it.isNotEmpty() }?.let { totals ->
          putJsonObject("enderchest_inside_containers") { totals.entries.sortedByDescending { it.value }.forEach { (id, n) -> put(id, n) } }
        }
      }
    }
    ToolOutput(result, context.lang.text("summary.inventory", "stacks" to main.size))
  }

  private val notable = Regex("_ORE$|^ANCIENT_DEBRIS$|CHEST$|^BARREL$|^SPAWNER$|^TRIAL_SPAWNER$|^VAULT$|PORTAL|^END_PORTAL_FRAME$|^BEACON$|SHULKER_BOX$|^BUDDING_AMETHYST$")

  /** Mobs count within this many blocks, whatever the block radius. */
  const val ENTITY_RADIUS = 32.0

  private val surroundings = tool("get_surroundings") { context, player, args ->
    val radius = (args.int("radius") ?: 8).coerceIn(1, 16)
    val origin = player.location
    val world = player.world
    val counts = mutableMapOf<Material, Int>()
    val found = mutableListOf<Pair<org.bukkit.block.Block, Int>>()
    val minY = (origin.blockY - radius).coerceAtLeast(world.minHeight)
    val maxY = (origin.blockY + radius).coerceAtMost(world.maxHeight - 1)
    for (x in -radius..radius) for (y in minY..maxY) for (z in -radius..radius) {
      val block = world.getBlockAt(origin.blockX + x, y, origin.blockZ + z)
      val type = block.type
      if (type.isAir) continue
      counts.merge(type, 1, Int::plus)
      if (notable.containsMatchIn(type.name)) found += block to x * x + (y - origin.blockY) * (y - origin.blockY) + z * z
    }
    val seen = world.getNearbyEntities(origin, ENTITY_RADIUS, ENTITY_RADIUS, ENTITY_RADIUS)
      .filter { it != player && (it is LivingEntity) }
      .map { Facts.Seen(short(it.type.key.toString()), it.location.distance(origin), it is Enemy, it is Player) }
    val entities = Facts.entities(seen)
    val result = buildJsonObject {
      put("radius", radius)
      putJsonObject("blocks") {
        counts.entries.sortedByDescending { it.value }.take(12).forEach { (type, count) -> put(type.key.key, count) }
      }
      put("notable", buildJsonArray {
        found.sortedBy { it.second }.take(15).forEach { (block, _) ->
          add(buildJsonObject {
            put("id", block.type.key.key)
            place(block.x, block.y, block.z, origin)
          })
        }
      })
      entities.forEach { (key, value) -> put(key, value) }
    }
    val creatures = seen.count { !it.player }
    ToolOutput(result, context.lang.text("summary.surroundings", "kinds" to counts.size, "creatures" to creatures))
  }

  private val statistics = tool("get_statistics") { context, player, _ ->
    fun stat(statistic: Statistic) = runCatching { player.getStatistic(statistic) }.getOrDefault(0)
    val hours = round1(stat(Statistic.PLAY_ONE_MINUTE) / 72_000.0)
    val result = buildJsonObject {
      put("play_time_hours", hours)
      put("deaths", stat(Statistic.DEATHS))
      put("mob_kills", stat(Statistic.MOB_KILLS))
      put("player_kills", stat(Statistic.PLAYER_KILLS))
      put("walked_km", round1((stat(Statistic.WALK_ONE_CM) + stat(Statistic.SPRINT_ONE_CM)) / 100_000.0))
      put("flown_km", round1((stat(Statistic.FLY_ONE_CM) + stat(Statistic.AVIATE_ONE_CM)) / 100_000.0))
      put("jumps", stat(Statistic.JUMP))
      put("fish_caught", stat(Statistic.FISH_CAUGHT))
      put("minutes_since_death", stat(Statistic.TIME_SINCE_DEATH) / 1_200)
      // Phantoms come after three in-game days without sleep; the model needs this to explain them.
      put("minutes_since_rest", stat(Statistic.TIME_SINCE_REST) / 1_200)
    }
    ToolOutput(result, context.lang.text("summary.statistics", "hours" to hours))
  }

  private val villagers = tool("find_villagers") { context, player, args ->
    val limit = context.features.villagerRadius()
    val radius = (args.int("radius") ?: limit).coerceIn(4, limit)
    val item = args.string("item")?.let(InteractTools::material)
    val profession = args.string("profession")?.lowercase()
    val found = Villagers.around(player, radius, item).filter { profession == null || Villagers.profession(it.trader) == profession }.take(10)
    val result = buildJsonObject {
      put("radius", radius)
      put("villagers", buildJsonArray {
        found.forEach { villager ->
          val at = villager.trader.location
          add(buildJsonObject {
            put("profession", Villagers.profession(villager.trader))
            (villager.trader as? Villager)?.let { put("level", it.villagerLevel) }
            place(at.blockX, at.blockY, at.blockZ, player.location)
            put("trades", buildJsonArray { villager.trades.take(10).forEach { add(Villagers.trade(it)) } })
          })
        }
      })
    }
    ToolOutput(result, if (found.isEmpty()) context.lang.text("summary.villagers_none") else context.lang.text("summary.villagers", "count" to found.size))
  }

  val all = listOf(status, inventory, surroundings, statistics, villagers)
}
