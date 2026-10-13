package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Color
import org.bukkit.FireworkEffect
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.ShulkerBox
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BookMeta
import org.bukkit.inventory.meta.BundleMeta
import org.bukkit.inventory.meta.Damageable
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.inventory.meta.FireworkEffectMeta
import org.bukkit.inventory.meta.FireworkMeta
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.inventory.meta.LeatherArmorMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.inventory.meta.Repairable
import org.bukkit.inventory.meta.SuspiciousStewMeta
import org.bukkit.inventory.meta.WritableBookMeta
import org.bukkit.potion.PotionEffect

/**
 * An item in as few tokens as the model needs: id, count, and only the extras it has. Books name what
 * they hold, potions their type, armor its trim, shulker boxes and bundles how many items are inside;
 * the rest waits for [Items.detail].
 */
fun ItemStack?.compact(slot: Int? = null): JsonObject? {
  if (this == null || type.isAir || amount <= 0) return null
  val meta = itemMeta
  return buildJsonObject {
    slot?.let { put("slot", it) }
    put("id", type.key.key)
    if (amount > 1) put("count", amount)
    if (meta?.hasDisplayName() == true) put("name", meta.displayName)
    if (enchantments.isNotEmpty()) putJsonObject("enchantments") { enchantments.forEach { (enchantment, level) -> put(enchantment.key.key, level) } }
    val stored = (meta as? EnchantmentStorageMeta)?.storedEnchants.orEmpty()
    if (stored.isNotEmpty()) putJsonObject("stored_enchantments") { stored.forEach { (enchantment, level) -> put(enchantment.key.key, level) } }
    val max = type.maxDurability.toInt()
    if (max > 0) {
      put("damage", (meta as? Damageable)?.damage ?: 0)
      put("max_damage", max)
    }
    Items.potion(meta)?.let { put("potion", it) }
    Items.trim(meta)?.let { put("trim", it) }
    Items.contents(this@compact, meta)?.let { inside -> put("contents_count", inside.sumOf { if (it == null || it.type.isAir) 0 else it.amount }) }
  }
}

/** What [compact] leaves out, for a close look at one item. Must run on the server thread. */
object Items {
  /** Stacks listed from inside a shulker box or bundle; a shulker box holds 27. */
  const val MAX_CONTENTS = 27

  /** The base potion and custom effects in a few words: `long_swiftness`, `strength + speed 2`, or a stew's `night_vision`. */
  fun potion(meta: ItemMeta?): String? {
    val base = (meta as? PotionMeta)?.basePotionType?.key?.key
    val custom = when (meta) {
      is PotionMeta -> meta.customEffects
      is SuspiciousStewMeta -> meta.customEffects
      else -> return null
    }.map { effect -> effect.type.key.key + if (effect.amplifier > 0) " ${effect.amplifier + 1}" else "" }
    return (listOfNotNull(base) + custom).joinToString(" + ").ifEmpty { null }
  }

  fun trim(meta: ItemMeta?): JsonObject? {
    val trim = (meta as? ArmorMeta)?.takeIf { it.hasTrim() }?.trim ?: return null
    return buildJsonObject {
      put("pattern", trim.pattern.key.key)
      put("material", trim.material.key.key)
    }
  }

  /** What a shulker box or bundle holds, in slot order with empty slots as null; null for any other item. */
  fun contents(item: ItemStack, meta: ItemMeta?): List<ItemStack?>? = when {
    meta is BundleMeta -> meta.items
    Tag.SHULKER_BOXES.isTagged(item.type) && meta is BlockStateMeta ->
      if (!meta.hasBlockState()) emptyList() else (meta.blockState as? ShulkerBox)?.inventory?.contents?.toList().orEmpty()
    else -> null
  }

  /** Totals by item id of what the shulker boxes and bundles among [items] hold, one level deep. */
  fun inside(items: Iterable<ItemStack?>, bundles: Boolean = true, shulkerBoxes: Boolean = true): Map<String, Int> {
    val totals = mutableMapOf<String, Int>()
    items.filterNotNull().filterNot { it.type.isAir }.forEach { item ->
      val meta = item.itemMeta
      if (if (meta is BundleMeta) !bundles else !shulkerBoxes) return@forEach
      contents(item, meta)?.filterNotNull()?.filterNot { it.type.isAir }?.forEach { totals.merge(it.type.key.key, it.amount, Int::plus) }
    }
    return totals
  }

  /** Every effect the item gives, with the seconds its tooltip shows. */
  fun effects(item: ItemStack, meta: ItemMeta?): List<JsonObject> {
    val all: List<PotionEffect> = when (meta) {
      is PotionMeta -> meta.basePotionType?.potionEffects.orEmpty() + meta.customEffects
      is SuspiciousStewMeta -> meta.customEffects
      else -> return emptyList()
    }
    val scale = Mechanics.potionDurationScale(item.type.key.key)
    return all.map { effect ->
      buildJsonObject {
        put("type", effect.type.key.key)
        put("level", effect.amplifier + 1)
        if (effect.type.isInstant) put("instant", true) else put("seconds", Mechanics.effectSeconds(effect.duration, scale, effect.isInfinite))
      }
    }
  }

  private fun operation(operation: AttributeModifier.Operation) = when (operation) {
    AttributeModifier.Operation.ADD_NUMBER -> "add_value"
    AttributeModifier.Operation.ADD_SCALAR -> "add_multiplied_base"
    AttributeModifier.Operation.MULTIPLY_SCALAR_1 -> "add_multiplied_total"
  }

  private fun modifiers(entries: Collection<Map.Entry<Attribute, AttributeModifier>>) = entries
    // The game keeps some defaults as floats: −2.4000000953674316 goes back to −2.4.
    .map { (attribute, modifier) -> Mechanics.Modifier(attribute.key.key, Math.round(modifier.amount * 1e6) / 1e6, operation(modifier.operation), modifier.slotGroup.toString()) }
    // Modifiers that change nothing, like leather armor's zero toughness, only cost tokens.
    .filter { it.amount != 0.0 }

  /** The modifiers that work on this item: its own, or else its type's defaults from every slot, each once. */
  fun attributes(item: ItemStack, meta: ItemMeta?): List<Mechanics.Modifier> {
    val own = meta?.takeIf { it.hasAttributeModifiers() }?.attributeModifiers?.entries().orEmpty()
    val defaults = EquipmentSlot.entries
      .flatMap { slot -> runCatching { item.type.getDefaultAttributeModifiers(slot).entries() }.getOrDefault(emptyList()) }
      .distinctBy { (attribute, modifier) -> attribute.key to modifier.key }
    return Mechanics.effectiveModifiers(modifiers(own), modifiers(defaults))
  }

  private fun Color.hex() = "#%06x".format(asRGB())

  private fun explosion(effect: FireworkEffect) = buildJsonObject {
    put("shape", when (effect.type) {
      FireworkEffect.Type.BALL -> "small_ball"
      FireworkEffect.Type.BALL_LARGE -> "large_ball"
      else -> effect.type.name.lowercase()
    })
    putJsonArray("colors") { effect.colors.forEach { add(JsonPrimitive(it.hex())) } }
    if (effect.fadeColors.isNotEmpty()) putJsonArray("fade_colors") { effect.fadeColors.forEach { add(JsonPrimitive(it.hex())) } }
    if (effect.hasTrail()) put("trail", true)
    if (effect.hasFlicker()) put("twinkle", true)
  }

  /**
   * Everything about one item: [compact] plus lore, attributes and the tooltip's attack numbers, potion effects,
   * dye color, repair cost, a written book's title and author (never its pages), fireworks, and what a shulker box or
   * bundle holds, one level deep. Names, lore, and book titles are written by players.
   */
  fun detail(item: ItemStack): JsonObject {
    val meta = item.itemMeta
    val base = item.compact() ?: return buildJsonObject {}
    return buildJsonObject {
      base.forEach { (key, value) -> put(key, value) }
      meta?.lore?.takeIf { it.isNotEmpty() }?.let { lore ->
        putJsonArray("lore") { lore.take(10).forEach { add(JsonPrimitive(ChatColor.stripColor(it).orEmpty().take(120))) } }
      }
      if (meta?.isUnbreakable == true) put("unbreakable", true)
      val attributes = attributes(item, meta)
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
      effects(item, meta).takeIf { it.isNotEmpty() }?.let { effects -> putJsonArray("effects") { effects.forEach(::add) } }
      (meta as? LeatherArmorMeta)?.color?.takeIf { it != Bukkit.getItemFactory().defaultLeatherColor }?.let { put("dye_color", it.hex()) }
      (meta as? Repairable)?.takeIf { it.hasRepairCost() && it.repairCost > 0 }?.let { put("repair_cost", it.repairCost) }
      // Paper gives a book and quill BookMeta too; only a signed book has a title, author, and generation.
      when {
        meta is BookMeta && item.type == Material.WRITTEN_BOOK -> putJsonObject("book") {
          if (meta.hasTitle()) put("title", meta.title)
          if (meta.hasAuthor()) put("author", meta.author)
          put("generation", (meta.generation ?: BookMeta.Generation.ORIGINAL).name.lowercase())
          put("pages", meta.pageCount)
        }
        meta is WritableBookMeta -> putJsonObject("book") { put("pages", meta.pageCount) }
      }
      when (meta) {
        is FireworkMeta -> putJsonObject("firework") {
          put("flight", meta.power)
          putJsonArray("explosions") { meta.effects.forEach { add(explosion(it)) } }
        }
        is FireworkEffectMeta -> meta.effect?.let { effect -> putJsonObject("firework") { putJsonArray("explosions") { add(explosion(effect)) } } }
        else -> {}
      }
      contents(item, meta)?.let { inside ->
        val (shown, more) = Mechanics.capped(inside.mapIndexedNotNull { slot, stack -> stack.compact(slot) }, MAX_CONTENTS)
        putJsonArray("contents") { shown.forEach(::add) }
        if (more > 0) put("more_stacks", more)
      }
    }
  }
}
