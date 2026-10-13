package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.bukkit.Material
import org.bukkit.Nameable
import org.bukkit.Tag
import org.bukkit.attribute.Attribute
import org.bukkit.block.Block
import org.bukkit.block.BrewingStand
import org.bukkit.block.Container
import org.bukkit.block.CreatureSpawner
import org.bukkit.block.ShulkerBox
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.Levelled
import org.bukkit.block.data.Waterlogged
import org.bukkit.block.data.type.Farmland
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Animals
import org.bukkit.entity.Enemy
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.MapMeta
import ru.mcbuddy.spigot.Plurals
import ru.mcbuddy.spigot.tools.Facts.round1
import ru.mcbuddy.spigot.tools.Facts.short

/** Tools that look closely at what is around the player: containers, blocks, maps, tables, mobs, farms. */
object InspectTools {
  private fun tool(name: String, body: (Player, JsonObject) -> ToolOutput) = object : ClientTool {
    override val name = name
    override fun run(context: ToolContext, args: JsonObject) = body(context.requirePlayer(), args)
  }

  /** Items grouped by id with counts, biggest first; shulker boxes add their own contents under `inside`. */
  private fun contents(items: Iterable<ItemStack?>, limit: Int = 27): JsonObject {
    val counts = mutableMapOf<String, Int>()
    val inside = mutableMapOf<String, Int>()
    items.filterNotNull().filterNot { it.type.isAir }.forEach { item ->
      counts.merge(item.type.key.key, item.amount, Int::plus)
      val box = (item.itemMeta as? BlockStateMeta)?.takeIf { it.hasBlockState() }?.blockState as? ShulkerBox
      box?.inventory?.contents?.filterNotNull()?.filterNot { it.type.isAir }?.forEach { inside.merge(it.type.key.key, it.amount, Int::plus) }
    }
    return buildJsonObject {
      putJsonObject("items") { counts.entries.sortedByDescending { it.value }.take(limit).forEach { (id, n) -> put(id, n) } }
      if (counts.size > limit) put("more_kinds", counts.size - limit)
      if (inside.isNotEmpty()) putJsonObject("inside_shulker_boxes") { inside.entries.sortedByDescending { it.value }.take(limit).forEach { (id, n) -> put(id, n) } }
    }
  }

  /** Block entities within the radius, reading only the chunks the radius touches. */
  private fun blockEntitiesNear(origin: org.bukkit.Location, radius: Int): List<org.bukkit.block.BlockState> {
    val world = origin.world ?: return emptyList()
    val result = mutableListOf<org.bukkit.block.BlockState>()
    for (cx in ((origin.blockX - radius) shr 4)..((origin.blockX + radius) shr 4)) for (cz in ((origin.blockZ - radius) shr 4)..((origin.blockZ + radius) shr 4)) {
      if (!world.isChunkLoaded(cx, cz)) continue
      world.getChunkAt(cx, cz).tileEntities.filterTo(result) { it.location.distanceSquared(origin) <= radius * radius }
    }
    return result
  }

  private fun target(player: Player, range: Int = 6): Block =
    player.getTargetBlockExact(range) ?: throw ToolError("the player isn't looking at a block within $range blocks")

  /** The container under the crosshair, by the same reach and rules for every tool that opens one. */
  private fun targetContainer(player: Player): Pair<Block, Container> {
    val block = target(player)
    val state = block.state as? Container ?: throw ToolError("${block.type.key.key} under the crosshair is not a container")
    return block to state
  }

  /** Stacks `detail` lists at most: a full double chest, every one of its slots. */
  private const val MAX_DETAIL = 54

  private val container = tool("get_container") { player, args ->
    val (block, state) = targetContainer(player)
    val inventory = state.inventory
    val result = buildJsonObject {
      put("type", block.type.key.key)
      (state as? Nameable)?.customName?.let { put("name", it) }
      put("slots", inventory.size)
      put("used_slots", inventory.contents.count { it != null && !it.type.isAir })
      if (args.bool("detail") == true) {
        val (shown, more) = Mechanics.capped(inventory.contents.mapIndexedNotNull { slot, item -> item.compact(slot) }, MAX_DETAIL)
        put("stacks", buildJsonArray { shown.forEach(::add) })
        if (more > 0) put("more_stacks", more)
      } else {
        contents(inventory.contents.asIterable()).forEach { (key, value) -> put(key, value) }
      }
    }
    val used = inventory.contents.count { it != null && !it.type.isAir }.toLong()
    ToolOutput(result, "$used ${Plurals.ru(used, "слот занят", "слота заняты", "слотов занято")} из ${inventory.size}")
  }

  private val scanContainers = tool("scan_nearby_containers") { player, args ->
    val radius = (args.int("radius") ?: 8).coerceIn(1, 12)
    val wanted = args.string("item")?.lowercase()?.removePrefix("minecraft:")
    val origin = player.location
    val found = blockEntitiesNear(origin, radius).filterIsInstance<Container>().map { it.block to it }
    var total = 0
    val rows = found.sortedBy { it.first.location.distanceSquared(origin) }.mapNotNull { (block, container) ->
      val items = container.inventory.contents.filterNotNull().filterNot { it.type.isAir }
      val count = wanted?.let { id -> items.filter { it.type.key.key == id }.sumOf { it.amount } }
      if (wanted != null && count == 0) return@mapNotNull null
      total += count ?: 0
      buildJsonObject {
        put("type", block.type.key.key)
        put("dx", block.x - origin.blockX)
        put("dy", block.y - origin.blockY)
        put("dz", block.z - origin.blockZ)
        (container as? Nameable)?.customName?.let { put("name", it) }
        if (wanted != null) put("count", count) else {
          put("used_slots", items.size)
          putJsonObject("top") { contents(items, limit = 4)["items"]!!.let { it as JsonObject }.forEach { (k, v) -> put(k, v) } }
        }
      }
    }.take(20)
    val result = buildJsonObject {
      put("radius", radius)
      put("containers", buildJsonArray { rows.forEach(::add) })
      if (wanted != null) put("total_${wanted}", total)
    }
    val n = rows.size.toLong()
    ToolOutput(result, if (wanted != null) "$total шт. в $n ${Plurals.ru(n, "контейнере", "контейнерах", "контейнерах")}" else "$n ${Plurals.ru(n, "контейнер", "контейнера", "контейнеров")}")
  }

  private fun preferredTool(type: Material): String? = when {
    Tag.MINEABLE_PICKAXE.isTagged(type) -> "pickaxe"
    Tag.MINEABLE_AXE.isTagged(type) -> "axe"
    Tag.MINEABLE_SHOVEL.isTagged(type) -> "shovel"
    Tag.MINEABLE_HOE.isTagged(type) -> "hoe"
    else -> null
  }

  private fun requiredTier(type: Material): String? = when {
    Tag.NEEDS_DIAMOND_TOOL.isTagged(type) -> "diamond"
    Tag.NEEDS_IRON_TOOL.isTagged(type) -> "iron"
    Tag.NEEDS_STONE_TOOL.isTagged(type) -> "stone"
    else -> null
  }

  private val blockInfo = tool("get_block_info") { player, _ ->
    val block = target(player)
    val type = block.type
    val held = player.inventory.itemInMainHand
    val drops = block.getDrops(held, player)
    val speed = block.getBreakSpeed(player)
    val data = block.blockData
    val result = buildJsonObject {
      put("id", type.key.key)
      put("hardness", round1(type.hardness.toDouble()))
      put("blast_resistance", round1(type.blastResistance.toDouble()))
      preferredTool(type)?.let { put("preferred_tool", it) }
      requiredTier(type)?.let { put("needs_tier", it) }
      put("held_tool_is_right", block.isPreferredTool(held))
      // Hardness −1 means unbreakable; otherwise the time with what the player holds right now.
      if (type.hardness >= 0 && speed > 0) put("break_seconds", round1(1.0 / (speed * 20)))
      put("drops_with_held_item", buildJsonArray { drops.forEach { it.compact()?.let(::add) } })
      if (data is Ageable) {
        put("age", data.age)
        put("max_age", data.maximumAge)
      }
      if (data is Waterlogged) put("waterlogged", data.isWaterlogged)
      put("block_light", block.lightFromBlocks.toInt())
      put("sky_light", block.lightFromSky.toInt())
    }
    val summary = if (drops.isEmpty()) "${type.key.key}: ничего не выпадет" else "${type.key.key}: выпадет ${drops.sumOf { it.amount }} шт."
    ToolOutput(result, summary)
  }

  private val mapInfo = tool("get_map_info") { player, _ ->
    val item = listOf(player.inventory.itemInMainHand, player.inventory.itemInOffHand).firstOrNull { it.type == Material.FILLED_MAP }
      ?: throw ToolError("the player isn't holding a filled map")
    val view = (item.itemMeta as? MapMeta)?.mapView ?: throw ToolError("this map has no data yet")
    val level = view.scale.value.toInt()
    val span = 128 shl level
    val world = view.world
    val location = player.location
    val inside = world == player.world &&
      Math.abs(location.blockX - view.centerX) <= span / 2 && Math.abs(location.blockZ - view.centerZ) <= span / 2
    val result = buildJsonObject {
      put("scale_level", level)
      put("blocks_per_side", span)
      put("center_x", view.centerX)
      put("center_z", view.centerZ)
      world?.let { put("dimension", it.environment.name.lowercase()) }
      put("locked", view.isLocked)
      put("player_inside", inside)
    }
    ToolOutput(result, "масштаб $level, ${span}×$span")
  }

  private val enchantOptions = tool("get_enchant_options") { player, _ ->
    val table = target(player)
    if (table.type != Material.ENCHANTING_TABLE) throw ToolError("the player isn't looking at an enchanting table")
    val shelves = Mechanics.bookshelves(
      provider = { dx, dy, dz -> Tag.ENCHANTMENT_POWER_PROVIDER.isTagged(table.getRelative(dx, dy, dz).type) },
      transmitter = { dx, dy, dz -> Tag.ENCHANTMENT_POWER_TRANSMITTER.isTagged(table.getRelative(dx, dy, dz).type) },
    )
    val range = Mechanics.topOfferRange(shelves)
    val lapis = player.inventory.contents.filterNotNull().filter { it.type == Material.LAPIS_LAZULI }.sumOf { it.amount }
    val result = buildJsonObject {
      put("bookshelves", shelves)
      put("counted_max", 15)
      put("top_offer_min_level", range.first)
      put("top_offer_max_level", range.last)
      put("player_level", player.level)
      put("lapis_in_inventory", lapis)
    }
    ToolOutput(result, "$shelves ${Plurals.ru(shelves.toLong(), "полка", "полки", "полок")}, до ${range.last} ур.")
  }

  private val mobInfo = tool("get_mob_info") { player, _ ->
    val eye = player.eyeLocation
    val mob = player.world.rayTraceEntities(eye, eye.direction, 16.0) { it != player && it is LivingEntity }?.hitEntity as? LivingEntity
      ?: throw ToolError("there is no mob under the crosshair within 16 blocks")
    // Other players stay anonymous, here as everywhere.
    if (mob is Player) throw ToolError("that's another player; tools only describe the asker and mobs")
    val held = player.inventory.itemInMainHand
    val base = player.getAttribute(Attribute.ATTACK_DAMAGE)?.value ?: 1.0
    val bonus = Mechanics.enchantmentBonus(
      sharpness = held.getEnchantmentLevel(Enchantment.SHARPNESS),
      smite = held.getEnchantmentLevel(Enchantment.SMITE),
      bane = held.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS),
      undead = Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(mob.type),
      arthropod = Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(mob.type),
    )
    val armor = mob.getAttribute(Attribute.ARMOR)?.value ?: 0.0
    val toughness = mob.getAttribute(Attribute.ARMOR_TOUGHNESS)?.value ?: 0.0
    val perHit = Mechanics.afterArmor(base + bonus, armor, toughness)
    val hits = Mechanics.hitsToKill(mob.health, perHit)
    val result = buildJsonObject {
      put("type", short(mob.type.key.toString()))
      mob.customName?.let { put("name", it) }
      put("health", round1(mob.health))
      put("max_health", round1(mob.getAttribute(Attribute.MAX_HEALTH)?.value ?: mob.health))
      put("armor", round1(armor))
      mob.getAttribute(Attribute.ATTACK_DAMAGE)?.value?.let { put("attack_damage", round1(it)) }
      put("hostile", mob is Enemy)
      put("distance", round1(mob.location.distance(eye)))
      putJsonObject("your_hit") {
        held.compact()?.let { put("weapon", it) }
        put("damage_per_hit", round1(perHit))
        hits?.let { put("hits_to_kill", it) }
        put("estimate", "fully charged hits, no crits, sweeping, potions, or difficulty scaling")
      }
    }
    ToolOutput(result, "${short(mob.type.key.toString())}: ${hits?.let { "≈$it ${Plurals.ru(it.toLong(), "удар", "удара", "ударов")}" } ?: "урона нет"}")
  }

  private val spawnCheck = tool("get_light_spawn_check") { player, args ->
    val radius = (args.int("radius") ?: 8).coerceIn(1, 16)
    val height = minOf(radius, 6)
    val origin = player.location
    val world = player.world
    val spots = mutableListOf<IntArray>()
    for (dx in -radius..radius) for (dy in -height..height) for (dz in -radius..radius) {
      val y = origin.blockY + dy
      if (y - 1 < world.minHeight || y + 1 >= world.maxHeight) continue
      val feet = world.getBlockAt(origin.blockX + dx, y, origin.blockZ + dz)
      val below = feet.getRelative(0, -1, 0).type
      val head = feet.getRelative(0, 1, 0)
      if (!below.isSolid || !below.isOccluding) continue
      if (!feet.isPassable || feet.isLiquid || !head.isPassable || head.isLiquid) continue
      if (feet.lightFromBlocks.toInt() != 0) continue
      spots += intArrayOf(dx, dy, dz)
    }
    val nearest = spots.sortedBy { it[0] * it[0] + it[1] * it[1] + it[2] * it[2] }.take(10)
    val result = buildJsonObject {
      put("radius", radius)
      put("dark_spots", spots.size)
      put("nearest", buildJsonArray { nearest.forEach { add(buildJsonObject { put("dx", it[0]); put("dy", it[1]); put("dz", it[2]) }) } })
      put("rule", "hostile mobs spawn at block light 0; on the surface sky light also has to be dark, so these spots matter at night and underground")
    }
    val n = spots.size.toLong()
    ToolOutput(result, if (n == 0L) "тёмных мест нет" else "$n ${Plurals.ru(n, "тёмное место", "тёмных места", "тёмных мест")}")
  }

  private val brewingIds = setOf(
    "nether_wart", "blaze_powder", "blaze_rod", "glass_bottle", "potion", "splash_potion", "lingering_potion", "dragon_breath",
    "redstone", "glowstone_dust", "gunpowder", "fermented_spider_eye", "spider_eye", "sugar", "rabbit_foot",
    "glistering_melon_slice", "golden_carrot", "magma_cream", "ghast_tear", "pufferfish", "phantom_membrane", "turtle_helmet",
    "breeze_rod", "cobweb", "slime_block", "stone", "brown_mushroom", "brewing_stand", "cauldron", "water_bucket",
  )

  private val brewing = tool("get_brewing_items") { player, _ ->
    val items = player.inventory.contents.filterNotNull().filter { it.type.key.key in brewingIds }
    val origin = player.location
    val stands = blockEntitiesNear(origin, 8).filterIsInstance<BrewingStand>()
      .sortedBy { it.location.distanceSquared(origin) }.take(4)
    val result = buildJsonObject {
      put("inventory", buildJsonArray { items.forEach { it.compact()?.let(::add) } })
      put("brewing_stands", buildJsonArray {
        stands.forEach { stand ->
          val inventory = stand.inventory
          add(buildJsonObject {
            put("dx", stand.x - origin.blockX)
            put("dy", stand.y - origin.blockY)
            put("dz", stand.z - origin.blockZ)
            put("fuel_level", stand.fuelLevel)
            if (stand.brewingTime > 0) put("seconds_left", stand.brewingTime / 20)
            inventory.ingredient.compact()?.let { put("ingredient", it) }
            inventory.fuel.compact()?.let { put("fuel", it) }
            put("bottles", buildJsonArray { (0..2).forEach { slot -> inventory.getItem(slot).compact()?.let(::add) } })
          })
        }
      })
    }
    val n = items.size.toLong()
    ToolOutput(result, "$n ${Plurals.ru(n, "стак", "стака", "стаков")} для зельеварения, ${stands.size} ${Plurals.ru(stands.size.toLong(), "варочная стойка", "варочные стойки", "варочных стоек")}")
  }

  private val farm = tool("scan_farm") { player, args ->
    val radius = (args.int("radius") ?: 8).coerceIn(1, 16)
    val height = minOf(radius, 4)
    val origin = player.location
    val world = player.world
    val crops = mutableMapOf<String, MutableMap<Int, Int>>()
    val maxAge = mutableMapOf<String, Int>()
    var wet = 0
    var dry = 0
    var hoppers = 0
    val composters = mutableListOf<Int>()
    val spawners = mutableMapOf<String, Int>()
    for (dx in -radius..radius) for (dy in -height..height) for (dz in -radius..radius) {
      val y = origin.blockY + dy
      if (y < world.minHeight || y >= world.maxHeight) continue
      val block = world.getBlockAt(origin.blockX + dx, y, origin.blockZ + dz)
      when (val data = block.blockData) {
        is Farmland -> if (data.moisture >= data.maximumMoisture) wet++ else dry++
        is Ageable -> if (block.type != Material.FIRE && block.type != Material.FROSTED_ICE) {
          val id = block.type.key.key
          crops.getOrPut(id) { sortedMapOf() }.merge(data.age, 1, Int::plus)
          maxAge[id] = data.maximumAge
        }
        is Levelled -> if (block.type == Material.COMPOSTER) composters += data.level
        else -> {}
      }
      when (block.type) {
        Material.HOPPER -> hoppers++
        Material.SPAWNER -> (block.state as? CreatureSpawner)?.spawnedType?.let { spawners.merge(short(it.key.toString()), 1, Int::plus) }
        else -> {}
      }
    }
    val animals = world.getNearbyEntities(origin, radius.toDouble(), height.toDouble(), radius.toDouble())
      .filterIsInstance<Animals>().groupingBy { short(it.type.key.toString()) }.eachCount()
    val result = buildJsonObject {
      put("radius", radius)
      putJsonObject("crops") {
        crops.forEach { (id, ages) ->
          putJsonObject(id) {
            put("total", ages.values.sum())
            put("ripe", ages[maxAge[id]] ?: 0)
            put("max_age", maxAge[id] ?: 0)
            putJsonObject("by_age") { ages.forEach { (age, n) -> put(age.toString(), n) } }
          }
        }
      }
      putJsonObject("farmland") { put("hydrated", wet); put("dry", dry) }
      if (composters.isNotEmpty()) put("composter_levels", buildJsonArray { composters.forEach { add(kotlinx.serialization.json.JsonPrimitive(it)) } })
      if (hoppers > 0) put("hoppers", hoppers)
      if (spawners.isNotEmpty()) putJsonObject("spawners") { spawners.forEach { (k, v) -> put(k, v) } }
      putJsonObject("animals") { animals.forEach { (k, v) -> put(k, v) } }
    }
    val planted = crops.values.sumOf { it.values.sum() }.toLong()
    val herd = animals.values.sum().toLong()
    ToolOutput(result, "$planted ${Plurals.ru(planted, "растение", "растения", "растений")}, $herd ${Plurals.ru(herd, "животное", "животных", "животных")}")
  }

  private val armorSlots = mapOf("head" to EquipmentSlot.HEAD, "chest" to EquipmentSlot.CHEST, "legs" to EquipmentSlot.LEGS, "feet" to EquipmentSlot.FEET)
  private val slotted = setOf("inventory", "enderchest", "container")

  /** One item the player can reach: in hand, worn, in the inventory or ender chest, or in the container under the crosshair. */
  private val inspectItem = tool("inspect_item") { player, args ->
    val from = args.string("from")?.lowercase() ?: "hand"
    val slot = args.int("slot")
    fun slotIn(size: Int): Int = slot?.takeIf { it in 0 until size } ?: throw ToolError("$from needs a slot from 0 to ${size - 1}")
    val inventory = player.inventory
    val item = when (from) {
      "hand" -> inventory.itemInMainHand
      "offhand" -> inventory.itemInOffHand
      in armorSlots -> inventory.getItem(armorSlots.getValue(from))
      "inventory" -> inventory.storageContents.let { it[slotIn(it.size)] }
      "enderchest" -> player.enderChest.let { it.getItem(slotIn(it.size)) }
      "container" -> targetContainer(player).second.inventory.let { it.getItem(slotIn(it.size)) }
      else -> throw ToolError("from must be hand, offhand, head, chest, legs, feet, inventory, enderchest, or container")
    }
    if (item == null || item.type.isAir) throw ToolError(if (slot != null && from in slotted) "slot $slot of $from is empty" else "nothing in $from")
    val result = buildJsonObject {
      put("from", from)
      slot?.takeIf { from in slotted }?.let { put("slot", it) }
      Items.detail(item).forEach { (key, value) -> put(key, value) }
    }
    ToolOutput(result, item.type.key.key)
  }

  val all = listOf(container, scanContainers, blockInfo, mapInfo, enchantOptions, mobInfo, spawnCheck, brewing, farm, inspectItem)
}
