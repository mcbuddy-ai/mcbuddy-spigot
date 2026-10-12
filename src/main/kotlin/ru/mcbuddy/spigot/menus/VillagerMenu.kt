package ru.mcbuddy.spigot.menus

import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.entity.Villager
import org.bukkit.plugin.Plugin
import ru.mcbuddy.spigot.Config
import ru.mcbuddy.spigot.menus.Menu.Companion.lore
import ru.mcbuddy.spigot.tools.ActionTools
import ru.mcbuddy.spigot.tools.Facts
import ru.mcbuddy.spigot.tools.Villagers

/** The villagers around, one row each: who they are and how far, then what they sell. A click shows the way. */
class VillagerMenu(
  player: Player,
  private val plugin: Plugin,
  private val texts: Config.Menus,
  private val found: List<Villagers.Found>,
) : Menu(player, 54, texts.villagersTitle) {
  private var page = 0

  override fun draw() {
    clear()
    found.drop(page * ROWS).take(ROWS).forEachIndexed { row, villager ->
      val start = row * 9
      val profession = Villagers.profession(villager.trader)
      val name = Villagers.PROFESSIONS_RU[profession] ?: profession
      val dx = villager.trader.location.x - player.location.x
      val dz = villager.trader.location.z - player.location.z
      val lore = listOfNotNull(
        (villager.trader as? Villager)?.let { texts.villagerLevel.replace("{level}", it.villagerLevel.toString()) },
        texts.villagerWhere.replace("{distance}", Math.round(villager.distance).toString()).replace("{direction}", Facts.DIRECTIONS_RU[Facts.direction(dx, dz)] ?: ""),
        texts.villagerPath,
      )
      set(start, icon(Villagers.icon(profession), "&e$name", lore))
      villager.trades.take(8).forEachIndexed { index, offer ->
        val left = offer.maxUses - offer.uses
        val prices = Villagers.prices(offer).joinToString(" + ", transform = Villagers::priceRu)
        val tradeLore = listOf(
          texts.tradePrice.replace("{price}", prices),
          if (left > 0) texts.tradeLeft.replace("{left}", left.toString()) else texts.tradeOut,
        )
        set(start + 1 + index, offer.result.clone().lore(null, tradeLore))
      }
    }
    if (found.size > ROWS) {
      set(PREVIOUS, icon(Material.SPECTRAL_ARROW, texts.previous))
      set(NEXT, icon(Material.SPECTRAL_ARROW, texts.next))
    }
    set(CLOSE, icon(Material.BARRIER, texts.close))
  }

  override fun click(slot: Int) {
    when (slot) {
      CLOSE -> player.closeInventory()
      PREVIOUS -> if (page > 0) { page--; draw() }
      NEXT -> if ((page + 1) * ROWS < found.size) { page++; draw() }
      else -> {
        val villager = found.getOrNull(page * ROWS + slot / 9)?.takeIf { slot < ROWS * 9 } ?: return
        if (!villager.trader.isValid) return
        player.closeInventory()
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, 1.4f)
        ActionTools.trail(plugin, player, villager.trader.location.add(0.0, 1.0, 0.0), 15)
      }
    }
  }

  private companion object {
    const val ROWS = 5
    const val PREVIOUS = 48
    const val NEXT = 50
    const val CLOSE = 53
  }
}
