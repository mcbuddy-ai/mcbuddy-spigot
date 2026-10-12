package ru.mcbuddy.spigot.tools

import org.bukkit.Material
import org.bukkit.entity.AbstractVillager
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.entity.WanderingTrader
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import ru.mcbuddy.spigot.Plurals

/** Villagers and wandering traders around a player, nearest first, with what they trade. */
object Villagers {
  class Found(val trader: AbstractVillager, val distance: Double, val trades: List<MerchantRecipe>)

  /** [item] keeps only trades that sell or ask for it, and only traders who have one. */
  fun around(player: Player, radius: Int, item: Material? = null): List<Found> =
    player.getNearbyEntities(radius.toDouble(), radius.toDouble(), radius.toDouble())
      .filterIsInstance<AbstractVillager>()
      .map { trader ->
        val trades = trader.recipes.filter { offer -> item == null || offer.result.type == item || offer.ingredients.any { it.type == item } || storesBook(offer.result, item) }
        Found(trader, trader.location.distance(player.location), trades)
      }
      .filter { item == null || it.trades.isNotEmpty() }
      .sortedBy { it.distance }

  /** An enchanted book counts as “the item” when the model asks for enchanted_book. */
  private fun storesBook(result: ItemStack, item: Material) = item == Material.ENCHANTED_BOOK && result.type == Material.ENCHANTED_BOOK

  fun profession(trader: AbstractVillager): String = when (trader) {
    is Villager -> trader.profession.key.key
    is WanderingTrader -> "wandering_trader"
    else -> "trader"
  }

  /** The workstation that gives a profession, as the icon of a villager in the window. */
  fun icon(profession: String): Material = when (profession) {
    "armorer" -> Material.BLAST_FURNACE
    "butcher" -> Material.SMOKER
    "cartographer" -> Material.CARTOGRAPHY_TABLE
    "cleric" -> Material.BREWING_STAND
    "farmer" -> Material.COMPOSTER
    "fisherman" -> Material.BARREL
    "fletcher" -> Material.FLETCHING_TABLE
    "leatherworker" -> Material.CAULDRON
    "librarian" -> Material.LECTERN
    "mason" -> Material.STONECUTTER
    "shepherd" -> Material.LOOM
    "toolsmith" -> Material.SMITHING_TABLE
    "weaponsmith" -> Material.GRINDSTONE
    "wandering_trader" -> Material.WANDERING_TRADER_SPAWN_EGG
    else -> Material.VILLAGER_SPAWN_EGG
  }

  /** Russian names as the game shows them; the window's lore can't ask the client to translate. */
  val PROFESSIONS_RU = mapOf(
    "armorer" to "Бронник", "butcher" to "Мясник", "cartographer" to "Картограф", "cleric" to "Священник", "farmer" to "Фермер",
    "fisherman" to "Рыбак", "fletcher" to "Лучник", "leatherworker" to "Кожевник", "librarian" to "Библиотекарь", "mason" to "Каменщик",
    "shepherd" to "Пастух", "toolsmith" to "Инструментальщик", "weaponsmith" to "Оружейник", "nitwit" to "Нищий", "none" to "Безработный",
    "wandering_trader" to "Странствующий торговец",
  )

  /** What villagers usually ask for, in Russian; anything else shows its id. */
  private val PRICES_RU = mapOf(
    "emerald" to "изумруд", "book" to "книга", "paper" to "бумага", "wheat" to "пшеница", "potato" to "картофель", "carrot" to "морковь",
    "beetroot" to "свёкла", "pumpkin" to "тыква", "melon" to "арбуз", "coal" to "уголь", "iron_ingot" to "железный слиток",
    "diamond" to "алмаз", "stick" to "палка", "string" to "нить", "feather" to "перо", "flint" to "кремень", "leather" to "кожа",
    "rabbit_hide" to "кроличья шкурка", "rotten_flesh" to "гнилая плоть", "gold_ingot" to "золотой слиток", "glass_pane" to "стеклянная панель",
    "compass" to "компас", "ink_sac" to "чернильный мешок", "clay_ball" to "комок глины", "stone" to "камень", "chicken" to "сырая курица",
    "porkchop" to "сырая свинина", "rabbit" to "сырая крольчатина", "mutton" to "сырая баранина", "beef" to "сырая говядина",
    "dried_kelp_block" to "блок сушёной ламинарии", "sweet_berries" to "сладкие ягоды", "cod" to "сырая треска", "salmon" to "сырой лосось",
    "tropical_fish" to "тропическая рыба", "pufferfish" to "иглобрюх", "bucket" to "ведро", "lapis_lazuli" to "лазурит",
    "redstone" to "красная пыль", "glass_bottle" to "пузырёк", "nether_wart" to "адский нарост", "scute" to "щиток", "tripwire_hook" to "крюк",
    "white_wool" to "белая шерсть", "writable_book" to "книга с пером",
  )

  fun priceRu(item: ItemStack): String {
    val name = PRICES_RU[item.type.key.key] ?: item.type.key.key.replace('_', ' ')
    return "${item.amount} × $name"
  }

  /** The price this player pays now, with demand and their discounts. */
  fun prices(offer: MerchantRecipe): List<ItemStack> = listOfNotNull(offer.getAdjustedIngredient1() ?: offer.ingredients.firstOrNull()) + offer.ingredients.drop(1)

  fun blocks(distance: Long) = "$distance ${Plurals.ru(distance, "блок", "блока", "блоков")}"
}
