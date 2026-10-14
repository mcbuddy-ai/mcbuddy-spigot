package ru.mcbuddy.spigot.tools

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import org.bukkit.Bukkit
import org.bukkit.FireworkEffect
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Tag
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.ShulkerBox
import org.bukkit.entity.Player
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
import org.bukkit.inventory.meta.MapMeta
import org.bukkit.inventory.meta.PotionMeta
import org.bukkit.inventory.meta.Repairable
import org.bukkit.inventory.meta.SuspiciousStewMeta
import org.bukkit.inventory.meta.WritableBookMeta
import org.bukkit.potion.PotionEffect
import ru.mcbuddy.core.tools.Dimensions
import ru.mcbuddy.core.tools.Facts
import ru.mcbuddy.core.tools.ItemData
import ru.mcbuddy.core.tools.Items
import ru.mcbuddy.core.tools.Mechanics
import ru.mcbuddy.core.tools.Spot

/**
 * A Bukkit item as the core's [ItemData]; null for nothing, air, or an empty stack. [full] adds what only a close
 * look at one item needs ([Items.detail]); the rest of the time it would only cost work. Must run on the server thread.
 */
fun ItemStack?.data(full: Boolean = false): ItemData? {
  if (this == null || type.isAir || amount <= 0) return null
  val meta = itemMeta
  return ItemData(
    id = type.key.key,
    count = amount,
    name = meta?.takeIf { it.hasDisplayName() }?.displayName,
    enchantments = enchantments.entries.associate { (enchantment, level) -> enchantment.key.key to level },
    storedEnchantments = (meta as? EnchantmentStorageMeta)?.storedEnchants.orEmpty().entries.associate { (enchantment, level) -> enchantment.key.key to level },
    maxDamage = type.maxDurability.toInt(),
    damage = (meta as? Damageable)?.damage ?: 0,
    potion = potion(meta),
    trim = (meta as? ArmorMeta)?.takeIf { it.hasTrim() }?.trim?.let { ItemData.Trim(it.pattern.key.key, it.material.key.key) },
    contents = contents(this, meta)?.map { it.data() },
    bundle = meta is BundleMeta,
    food = meta?.takeIf { it.hasFood() }?.food?.let { ItemData.Food(it.nutrition, it.saturation.toDouble()) },
    details = if (full) details(this, meta) else null,
  )
}

/** The item as tools list it, or null for an empty slot. */
fun ItemStack?.compact(slot: Int? = null): JsonObject? = data()?.compact(slot)

fun Iterable<ItemStack?>.data(): List<ItemData?> = map { it.data() }

fun Array<out ItemStack?>.data(): List<ItemData?> = map { it.data() }

/** Everything about one item, for `inspect_item`; the map in it knows whether [player] stands on it. */
fun detail(item: ItemStack, player: Player?): JsonObject =
  Items.detail(item.data(full = true)!!, player?.location?.let { Spot(player.world.name, it.blockX, it.blockZ) })

/** What a shulker box or bundle holds, in slot order with empty slots as null; null for any other item. */
fun contents(item: ItemStack, meta: ItemMeta?): List<ItemStack?>? = when {
  meta is BundleMeta -> meta.items
  Tag.SHULKER_BOXES.isTagged(item.type) && meta is BlockStateMeta ->
    if (!meta.hasBlockState()) emptyList() else (meta.blockState as? ShulkerBox)?.inventory?.contents?.toList().orEmpty()
  else -> null
}

private fun effect(effect: PotionEffect) = ItemData.Effect(effect.type.key.key, effect.amplifier, effect.duration, effect.isInfinite, effect.type.isInstant)

private fun potion(meta: ItemMeta?): ItemData.Potion? = when (meta) {
  is PotionMeta -> ItemData.Potion(meta.basePotionType?.key?.key, meta.basePotionType?.potionEffects.orEmpty().map(::effect), meta.customEffects.map(::effect))
  is SuspiciousStewMeta -> ItemData.Potion(null, emptyList(), meta.customEffects.map(::effect))
  else -> null
}

private fun operation(operation: AttributeModifier.Operation) = when (operation) {
  AttributeModifier.Operation.ADD_NUMBER -> "add_value"
  AttributeModifier.Operation.ADD_SCALAR -> "add_multiplied_base"
  AttributeModifier.Operation.MULTIPLY_SCALAR_1 -> "add_multiplied_total"
}

private fun modifiers(entries: Collection<Map.Entry<Attribute, AttributeModifier>>) =
  entries.map { (attribute, modifier) -> Mechanics.Modifier(attribute.key.key, modifier.amount, operation(modifier.operation), modifier.slotGroup.toString()) }

private fun explosion(effect: FireworkEffect) = ItemData.Explosion(
  type = effect.type.name,
  colors = effect.colors.map { it.asRGB() },
  fade = effect.fadeColors.map { it.asRGB() },
  trail = effect.hasTrail(),
  flicker = effect.hasFlicker(),
)

private fun details(item: ItemStack, meta: ItemMeta?): ItemData.Details {
  val own = meta?.takeIf { it.hasAttributeModifiers() }?.attributeModifiers?.entries().orEmpty()
  val defaults = EquipmentSlot.entries
    .flatMap { slot -> runCatching { item.type.getDefaultAttributeModifiers(slot).entries() }.getOrDefault(emptyList()) }
    .distinctBy { (attribute, modifier) -> attribute.key to modifier.key }
  return ItemData.Details(
    lore = meta?.lore.orEmpty(),
    unbreakable = meta?.isUnbreakable == true,
    modifiers = modifiers(own),
    defaultModifiers = modifiers(defaults),
    dyeColor = (meta as? LeatherArmorMeta)?.color?.takeIf { it != Bukkit.getItemFactory().defaultLeatherColor }?.asRGB(),
    repairCost = (meta as? Repairable)?.takeIf { it.hasRepairCost() && it.repairCost > 0 }?.repairCost,
    // Paper gives a book and quill BookMeta too; only a signed book has a title, author, and generation.
    book = when {
      meta is BookMeta && item.type == Material.WRITTEN_BOOK -> ItemData.Book.Written(
        title = meta.title.takeIf { meta.hasTitle() },
        author = meta.author.takeIf { meta.hasAuthor() },
        generation = (meta.generation ?: BookMeta.Generation.ORIGINAL).name.lowercase(),
        pages = meta.pageCount,
      )
      meta is WritableBookMeta -> ItemData.Book.Writable(meta.pageCount)
      else -> null
    },
    firework = when (meta) {
      is FireworkMeta -> ItemData.Firework(meta.power, meta.effects.map(::explosion))
      is FireworkEffectMeta -> meta.effect?.let { ItemData.Firework(null, listOf(explosion(it))) }
      else -> null
    },
    map = if (item.type != Material.FILLED_MAP) null else (meta as? MapMeta)?.mapView?.let { view ->
      ItemData.MapInfo(
        empty = false,
        scale = view.scale.value.toInt(),
        centerX = view.centerX,
        centerZ = view.centerZ,
        world = view.world?.name,
        dimension = view.world?.let { Dimensions.id(it) },
        locked = view.isLocked,
      )
    } ?: ItemData.MapInfo(empty = true),
  )
}

/** A world's dimension as the game's id: `overworld`, `the_nether`, `the_end`. */
fun Dimensions.id(world: World): String = id(world.environment)

fun Dimensions.id(environment: World.Environment): String = when (environment) {
  World.Environment.NORMAL -> "overworld"
  World.Environment.NETHER -> "the_nether"
  World.Environment.THE_END -> "the_end"
  else -> environment.name.lowercase()
}

/** [Facts.place] from where the player stands. */
fun JsonObjectBuilder.place(x: Int, y: Int?, z: Int, from: Location) = with(Facts) { place(x, y, z, from.x, from.z) }
