package chat.allay.core.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * An item as plain data, the same on every platform: each platform describes its items this way, and what the tools
 * say about items is built here once. Ids are the game's, without the namespace: `diamond_sword`.
 */
data class ItemData(
  val id: String,
  val count: Int,
  /** The name a player gave it, with its colors; null when it has the game's name. */
  val name: String? = null,
  val enchantments: Map<String, Int> = emptyMap(),
  /** What an enchanted book holds. */
  val storedEnchantments: Map<String, Int> = emptyMap(),
  /** The durability its type has; 0 for items that don't wear out. */
  val maxDamage: Int = 0,
  val damage: Int = 0,
  val potion: Potion? = null,
  val trim: Trim? = null,
  /** What a shulker box or bundle holds, in slot order with empty slots as null; null for any other item. */
  val contents: List<ItemData?>? = null,
  val bundle: Boolean = false,
  /** Hunger and saturation, for an item that has its own food values. */
  val food: Food? = null,
  /** What only [Items.detail] needs; platforms fill it only for a close look at one item. */
  val details: Details? = null,
) {
  /** A potion, tipped arrow, or suspicious stew: the base potion and the extra effects. */
  data class Potion(val base: String?, val baseEffects: List<Effect>, val custom: List<Effect>)

  data class Effect(val type: String, val amplifier: Int, val ticks: Int, val infinite: Boolean, val instant: Boolean)

  data class Trim(val pattern: String, val material: String)

  data class Food(val nutrition: Int, val saturation: Double)

  data class Details(
    val lore: List<String> = emptyList(),
    val unbreakable: Boolean = false,
    /** The item's own attribute modifiers, and its type's defaults from every slot, each once. */
    val modifiers: List<Mechanics.Modifier> = emptyList(),
    val defaultModifiers: List<Mechanics.Modifier> = emptyList(),
    /** Dyed leather's color as RGB, when it isn't the undyed default. */
    val dyeColor: Int? = null,
    val repairCost: Int? = null,
    val book: Book? = null,
    val firework: Firework? = null,
    val map: MapInfo? = null,
  )

  sealed interface Book {
    /** A signed book: never its pages, only how many. */
    data class Written(val title: String?, val author: String?, val generation: String, val pages: Int) : Book

    data class Writable(val pages: Int) : Book
  }

  /** A rocket's flight and explosions, or a firework star's one explosion ([flight] null). */
  data class Firework(val flight: Int?, val explosions: List<Explosion>)

  /** [type] as the platform names it: BALL, BALL_LARGE, STAR, BURST, CREEPER. Colors are RGB. */
  data class Explosion(val type: String, val colors: List<Int>, val fade: List<Int>, val trail: Boolean, val flicker: Boolean)

  /** A filled map; [empty] when it has no map data. */
  data class MapInfo(
    val empty: Boolean,
    val scale: Int = 0,
    val centerX: Int = 0,
    val centerZ: Int = 0,
    val world: String? = null,
    val dimension: String? = null,
    val locked: Boolean = false,
  )

  /**
   * The item in as few tokens as the model needs: id, count, and only the extras it has. Books name what they hold,
   * potions their type, armor its trim, shulker boxes and bundles how many items are inside; the rest waits for
   * [Items.detail].
   */
  fun compact(slot: Int? = null): JsonObject = buildJsonObject {
    slot?.let { put("slot", it) }
    put("id", id)
    if (count > 1) put("count", count)
    name?.let { put("name", it) }
    if (enchantments.isNotEmpty()) putJsonObject("enchantments") { enchantments.forEach { (enchantment, level) -> put(enchantment, level) } }
    if (storedEnchantments.isNotEmpty()) putJsonObject("stored_enchantments") { storedEnchantments.forEach { (enchantment, level) -> put(enchantment, level) } }
    if (maxDamage > 0) {
      put("damage", damage)
      put("max_damage", maxDamage)
    }
    Items.potion(this@ItemData)?.let { put("potion", it) }
    trim?.let { trim ->
      putJsonObject("trim") {
        put("pattern", trim.pattern)
        put("material", trim.material)
      }
    }
    contents?.let { inside -> put("contents_count", inside.sumOf { it?.count ?: 0 }) }
  }
}

/** Where a player stands, for what an item says about them: a map knows whether they're on it. */
data class Spot(val world: String, val x: Int, val z: Int)

/** What [ItemData.compact] leaves out, and totals over many items. */
object Items {
  /** Stacks listed from inside a shulker box or bundle; a shulker box holds 27. */
  const val MAX_CONTENTS = 27

  /** The base potion and custom effects in a few words: `long_swiftness`, `strength + speed 2`, or a stew's `night_vision`. */
  fun potion(item: ItemData): String? {
    val potion = item.potion ?: return null
    val custom = potion.custom.map { effect -> effect.type + if (effect.amplifier > 0) " ${effect.amplifier + 1}" else "" }
    return (listOfNotNull(potion.base) + custom).joinToString(" + ").ifEmpty { null }
  }

  /** Totals by item id of what the shulker boxes and bundles among [items] hold, one level deep. */
  fun inside(items: Iterable<ItemData?>, bundles: Boolean = true, shulkerBoxes: Boolean = true): Map<String, Int> {
    val totals = mutableMapOf<String, Int>()
    items.filterNotNull().forEach { item ->
      if (if (item.bundle) !bundles else !shulkerBoxes) return@forEach
      item.contents?.filterNotNull()?.forEach { totals.merge(it.id, it.count, Int::plus) }
    }
    return totals
  }

  /** Items grouped by id with counts, biggest first; shulker boxes and bundles add what they hold. */
  fun counts(items: Iterable<ItemData?>, limit: Int = 27): JsonObject {
    val counts = mutableMapOf<String, Int>()
    items.filterNotNull().forEach { item -> counts.merge(item.id, item.count, Int::plus) }
    fun top(totals: Map<String, Int>) = buildJsonObject { totals.entries.sortedByDescending { it.value }.take(limit).forEach { (id, n) -> put(id, n) } }
    val boxes = inside(items, bundles = false)
    val bundles = inside(items, shulkerBoxes = false)
    return buildJsonObject {
      put("items", top(counts))
      if (counts.size > limit) put("more_kinds", counts.size - limit)
      if (boxes.isNotEmpty()) put("inside_shulker_boxes", top(boxes))
      if (bundles.isNotEmpty()) put("inside_bundles", top(bundles))
    }
  }

  /** How many of the matching items a stack is, and how many more its shulker box or bundle holds. */
  fun matching(item: ItemData?, match: (String) -> Boolean): Pair<Int, Int> {
    if (item == null) return 0 to 0
    val direct = if (match(item.id)) item.count else 0
    val nested = item.contents?.filterNotNull()?.filter { match(it.id) }?.sumOf { it.count } ?: 0
    return direct to nested
  }

  /** What a filled map shows: its center, scale, and whether the player stands on it. */
  private fun map(map: ItemData.MapInfo, viewer: Spot?): JsonObject {
    if (map.empty) return buildJsonObject { put("empty", true) }
    val span = 128 shl map.scale
    return buildJsonObject {
      put("scale_level", map.scale)
      put("blocks_per_side", span)
      put("center_x", map.centerX)
      put("center_z", map.centerZ)
      map.dimension?.let { put("dimension", it) }
      put("locked", map.locked)
      if (viewer != null) {
        put("player_inside", map.world == viewer.world && Math.abs(viewer.x - map.centerX) <= span / 2 && Math.abs(viewer.z - map.centerZ) <= span / 2)
      }
    }
  }

  /** Every effect the item gives, with the seconds its tooltip shows. */
  fun effects(item: ItemData): List<JsonObject> {
    val potion = item.potion ?: return emptyList()
    val scale = Mechanics.potionDurationScale(item.id)
    return (potion.baseEffects + potion.custom).map { effect ->
      buildJsonObject {
        put("type", effect.type)
        put("level", effect.amplifier + 1)
        if (effect.instant) put("instant", true) else put("seconds", Mechanics.effectSeconds(effect.ticks, scale, effect.infinite))
      }
    }
  }

  private fun rounded(modifiers: List<Mechanics.Modifier>) = modifiers
    // The game keeps some defaults as floats: −2.4000000953674316 goes back to −2.4.
    .map { it.copy(amount = Math.round(it.amount * 1e6) / 1e6) }
    // Modifiers that change nothing, like leather armor's zero toughness, only cost tokens.
    .filter { it.amount != 0.0 }

  /** The modifiers that work on this item: its own, or else its type's defaults. */
  fun attributes(details: ItemData.Details): List<Mechanics.Modifier> = Mechanics.effectiveModifiers(rounded(details.modifiers), rounded(details.defaultModifiers))

  private fun hex(rgb: Int) = "#%06x".format(rgb)

  private fun explosion(effect: ItemData.Explosion) = buildJsonObject {
    put("shape", when (effect.type) {
      "BALL" -> "small_ball"
      "BALL_LARGE" -> "large_ball"
      else -> effect.type.lowercase()
    })
    putJsonArray("colors") { effect.colors.forEach { add(JsonPrimitive(hex(it))) } }
    if (effect.fade.isNotEmpty()) putJsonArray("fade_colors") { effect.fade.forEach { add(JsonPrimitive(hex(it))) } }
    if (effect.trail) put("trail", true)
    if (effect.flicker) put("twinkle", true)
  }

  /** Color codes as the game writes them: `§` and a character. */
  private val COLOR = Regex("(?i)§[0-9A-FK-ORX]")

  /**
   * Everything about one item: [ItemData.compact] plus lore, attributes and the tooltip's attack numbers, potion
   * effects, dye color, repair cost, a written book's title and author (never its pages), fireworks, and what a
   * shulker box or bundle holds, one level deep. Names, lore, and book titles are written by players.
   */
  fun detail(item: ItemData, viewer: Spot? = null): JsonObject {
    val details = item.details ?: ItemData.Details()
    return buildJsonObject {
      item.compact().forEach { (key, value) -> put(key, value) }
      details.lore.takeIf { it.isNotEmpty() }?.let { lore ->
        putJsonArray("lore") { lore.take(10).forEach { add(JsonPrimitive(COLOR.replace(it, "").take(120))) } }
      }
      if (details.unbreakable) put("unbreakable", true)
      val attributes = attributes(details)
      if (attributes.isNotEmpty()) {
        putJsonArray("attributes") {
          attributes.forEach { modifier ->
            add(buildJsonObject {
              put("attribute", modifier.attribute)
              put("amount", modifier.amount)
              put("operation", modifier.operation)
              put("slot", modifier.slot)
            })
          }
        }
        Mechanics.tooltip(attributes).takeIf { it.isNotEmpty() }?.let { numbers -> putJsonObject("tooltip") { numbers.forEach { (key, value) -> put(key, value) } } }
      }
      effects(item).takeIf { it.isNotEmpty() }?.let { effects -> putJsonArray("effects") { effects.forEach(::add) } }
      details.dyeColor?.let { put("dye_color", hex(it)) }
      details.repairCost?.let { put("repair_cost", it) }
      when (val book = details.book) {
        is ItemData.Book.Written -> putJsonObject("book") {
          book.title?.let { put("title", it) }
          book.author?.let { put("author", it) }
          put("generation", book.generation)
          put("pages", book.pages)
        }
        is ItemData.Book.Writable -> putJsonObject("book") { put("pages", book.pages) }
        null -> {}
      }
      details.firework?.let { firework ->
        putJsonObject("firework") {
          firework.flight?.let { put("flight", it) }
          putJsonArray("explosions") { firework.explosions.forEach { add(explosion(it)) } }
        }
      }
      details.map?.let { put("map", map(it, viewer)) }
      item.contents?.let { inside ->
        val (shown, more) = Mechanics.capped(inside.mapIndexedNotNull { slot, stack -> stack?.compact(slot) }, MAX_CONTENTS)
        putJsonArray("contents") { shown.forEach(::add) }
        if (more > 0) put("more_stacks", more)
      }
    }
  }
}
