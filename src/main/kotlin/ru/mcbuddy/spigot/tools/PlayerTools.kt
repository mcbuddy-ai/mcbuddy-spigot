package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.bukkit.Material
import org.bukkit.Statistic
import org.bukkit.attribute.Attribute
import org.bukkit.entity.AbstractVillager
import org.bukkit.entity.Enemy
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import ru.mcbuddy.spigot.Plurals
import ru.mcbuddy.spigot.tools.Facts.round1
import ru.mcbuddy.spigot.tools.Facts.short

/** Tools about the asking player: state, inventory, surroundings, crosshair, statistics. */
object PlayerTools {
  private fun tool(name: String, body: (Player, JsonObject) -> ToolOutput) = object : ClientTool {
    override val name = name
    override fun run(context: ToolContext, args: JsonObject) = body(context.requirePlayer(), args)
  }

  private val status = tool("get_player_status") { player, _ ->
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
      put("exp_progress", round1(player.exp.toDouble()))
      putJsonObject("location") {
        put("x", location.blockX)
        put("y", location.blockY)
        put("z", location.blockZ)
      }
      put("dimension", player.world.environment.name.lowercase())
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
    ToolOutput(result, "❤ ${round1(player.health)}/${round1(maxHealth)} · 🍗 ${player.foodLevel}/20")
  }

  private val inventory = tool("get_inventory") { player, args ->
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
      Items.inside(inventory.storageContents.asIterable() + inventory.itemInOffHand).takeIf { it.isNotEmpty() }?.let { totals ->
        putJsonObject("inside_containers") { totals.entries.sortedByDescending { it.value }.forEach { (id, n) -> put(id, n) } }
      }
      if (args.bool("include_enderchest") == true) {
        put("enderchest", buildJsonArray { player.enderChest.contents.forEachIndexed { slot, item -> item.compact(slot)?.let(::add) } })
      }
    }
    val stacks = main.size.toLong()
    ToolOutput(result, "$stacks ${Plurals.ru(stacks, "стак", "стака", "стаков")}")
  }

  private val notable = Regex("_ORE$|^ANCIENT_DEBRIS$|CHEST$|^BARREL$|^SPAWNER$|^TRIAL_SPAWNER$|^VAULT$|PORTAL|^END_PORTAL_FRAME$|^BEACON$|SHULKER_BOX$|^BUDDING_AMETHYST$")

  private val surroundings = tool("get_surroundings") { player, args ->
    val radius = (args.int("radius") ?: 8).coerceIn(1, 16)
    val origin = player.location
    val world = player.world
    val counts = mutableMapOf<Material, Int>()
    val found = mutableListOf<Triple<Material, IntArray, Int>>()
    val minY = (origin.blockY - radius).coerceAtLeast(world.minHeight)
    val maxY = (origin.blockY + radius).coerceAtMost(world.maxHeight - 1)
    for (x in -radius..radius) for (y in minY..maxY) for (z in -radius..radius) {
      val type = world.getBlockAt(origin.blockX + x, y, origin.blockZ + z).type
      if (type.isAir) continue
      counts.merge(type, 1, Int::plus)
      if (notable.containsMatchIn(type.name)) {
        val offset = intArrayOf(x, y - origin.blockY, z)
        found += Triple(type, offset, x * x + offset[1] * offset[1] + z * z)
      }
    }
    val seen = world.getNearbyEntities(origin, 32.0, 32.0, 32.0)
      .filter { it != player && (it is LivingEntity) }
      .map { Facts.Seen(short(it.type.key.toString()), it.location.distance(origin), it is Enemy, it is Player) }
    val entities = Facts.entities(seen)
    val result = buildJsonObject {
      put("radius", radius)
      putJsonObject("blocks") {
        counts.entries.sortedByDescending { it.value }.take(12).forEach { (type, count) -> put(type.key.key, count) }
      }
      put("notable", buildJsonArray {
        found.sortedBy { it.third }.take(15).forEach { (type, offset, _) ->
          add(buildJsonObject {
            put("id", type.key.key)
            put("dx", offset[0])
            put("dy", offset[1])
            put("dz", offset[2])
          })
        }
      })
      entities.forEach { (key, value) -> put(key, value) }
    }
    val kinds = counts.size.toLong()
    val creatures = seen.count { !it.player }.toLong()
    ToolOutput(result, "$kinds ${Plurals.ru(kinds, "вид", "вида", "видов")} блоков, $creatures ${Plurals.ru(creatures, "существо", "существа", "существ")}")
  }

  private val target = tool("get_target") { player, _ ->
    val eye = player.eyeLocation
    val entityHit = player.world.rayTraceEntities(eye, eye.direction, 16.0) { it != player }
    val block = player.getTargetBlockExact(6)
    val entity = entityHit?.hitEntity
    val entityCloser = entity != null && (block == null || entity.location.distance(eye) < block.location.distance(eye))
    when {
      entity != null && entityCloser -> {
        // Another player under the crosshair stays anonymous, like everywhere else.
        val result = buildJsonObject {
          put("kind", "entity")
          put("type", short(entity.type.key.toString()))
          if (entity !is Player) entity.customName?.let { put("name", it) }
          if (entity is LivingEntity && entity !is Player) {
            put("health", round1(entity.health))
            put("max_health", round1(entity.getAttribute(Attribute.MAX_HEALTH)?.value ?: entity.health))
          }
          put("distance", round1(entity.location.distance(eye)))
        }
        ToolOutput(result, short(entity.type.key.toString()))
      }
      block != null -> {
        val result = buildJsonObject {
          put("kind", "block")
          put("id", block.type.key.key)
          put("state", block.blockData.asString.substringAfter('[', "").removeSuffix("]").ifEmpty { null } ?: "")
          put("x", block.x)
          put("y", block.y)
          put("z", block.z)
          put("distance", round1(block.location.add(0.5, 0.5, 0.5).distance(eye)))
        }
        ToolOutput(result, block.type.key.key)
      }
      else -> ToolOutput(buildJsonObject { put("kind", "nothing") }, "ничего")
    }
  }

  private val statistics = tool("get_statistics") { player, _ ->
    fun stat(statistic: Statistic) = runCatching { player.getStatistic(statistic) }.getOrDefault(0)
    val result = buildJsonObject {
      put("play_time_hours", round1(stat(Statistic.PLAY_ONE_MINUTE) / 72_000.0))
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
    ToolOutput(result, "${round1(stat(Statistic.PLAY_ONE_MINUTE) / 72_000.0)} ч в игре")
  }

  private val trades = tool("get_villager_trades") { player, _ ->
    val eye = player.eyeLocation
    val trader = player.world.rayTraceEntities(eye, eye.direction, 8.0) { it is AbstractVillager }?.hitEntity as? AbstractVillager
      ?: throw ToolError("there is no villager or wandering trader under the crosshair within 8 blocks")
    val offers = trader.recipes
    val result = buildJsonObject {
      put("type", short(trader.type.key.toString()))
      if (trader is Villager) {
        put("profession", trader.profession.key.key)
        put("level", trader.villagerLevel)
      }
      put("trades", buildJsonArray {
        offers.forEach { offer ->
          add(buildJsonObject {
            // The first price is what this player pays right now, with demand and discounts applied.
            val prices = listOfNotNull(offer.getAdjustedIngredient1() ?: offer.ingredients.firstOrNull()) + offer.ingredients.drop(1)
            put("buy", buildJsonArray { prices.forEach { item -> item.compact()?.let(::add) } })
            offer.result.compact()?.let { put("sell", it) }
            put("uses", offer.uses)
            put("max_uses", offer.maxUses)
          })
        }
      })
    }
    val count = offers.size.toLong()
    ToolOutput(result, "$count ${Plurals.ru(count, "сделка", "сделки", "сделок")}")
  }

  /** A trade in as few tokens as the model needs; enchanted books name the enchantment they hold. */
  private fun trade(offer: org.bukkit.inventory.MerchantRecipe) = buildJsonObject {
    put("buy", buildJsonArray { Villagers.prices(offer).forEach { item -> item.compact()?.let(::add) } })
    offer.result.compact()?.let { sell ->
      val stored = (offer.result.itemMeta as? org.bukkit.inventory.meta.EnchantmentStorageMeta)?.storedEnchants.orEmpty()
      put("sell", if (stored.isEmpty()) sell else buildJsonObject {
        // `book` predates compact()'s stored_enchantments; one copy is enough.
        sell.forEach { (key, value) -> if (key != "stored_enchantments") put(key, value) }
        putJsonObject("book") { stored.forEach { (enchantment, level) -> put(enchantment.key.key, level) } }
      })
    }
    put("left", offer.maxUses - offer.uses)
  }

  private val villagers = object : ClientTool {
    override val name = "find_villagers"

    override fun run(context: ToolContext, args: JsonObject): ToolOutput {
      val player = context.requirePlayer()
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
              put("x", at.blockX)
              put("y", at.blockY)
              put("z", at.blockZ)
              put("distance", Math.round(villager.distance))
              put("direction", Facts.direction(at.x - player.location.x, at.z - player.location.z))
              put("trades", buildJsonArray { villager.trades.take(10).forEach { add(trade(it)) } })
            })
          }
        })
      }
      val count = found.size.toLong()
      return ToolOutput(result, if (count == 0L) "никого рядом" else "$count ${Plurals.ru(count, "житель", "жителя", "жителей")}")
    }
  }

  val all = listOf(status, inventory, surroundings, target, statistics, trades, villagers)
}
