package ru.mcbuddy.spigot

import net.md_5.bungee.api.chat.BaseComponent
import net.md_5.bungee.api.chat.ClickEvent
import net.md_5.bungee.api.chat.HoverEvent
import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.chat.hover.content.Text
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Minecraft.formatMessage
import ru.mcbuddy.spigot.menus.EnchantMenu
import ru.mcbuddy.spigot.menus.RecipeMenu
import ru.mcbuddy.spigot.menus.VillagerMenu
import ru.mcbuddy.spigot.tools.ActionTools
import ru.mcbuddy.spigot.tools.Books
import ru.mcbuddy.spigot.tools.Button
import ru.mcbuddy.spigot.tools.ButtonStore
import ru.mcbuddy.spigot.tools.Facts
import ru.mcbuddy.spigot.tools.Features
import ru.mcbuddy.spigot.tools.InteractTools
import ru.mcbuddy.spigot.tools.Note
import ru.mcbuddy.spigot.tools.ToolError
import ru.mcbuddy.spigot.tools.Villagers

/**
 * Buttons under answers and the windows behind them. A button runs `/ask do <id>`; the id leads to an action
 * this player was offered, so nobody can press a button they weren't shown.
 */
class Clicks(
  private val plugin: JavaPlugin,
  private val config: Config,
  private val features: Features,
  private val store: ButtonStore,
  private val ask: (Player, String) -> Unit,
) {
  private val ui = config.ui
  private val messages = config.messages

  /** Draws one chat line of `[label]` buttons. */
  fun show(player: Player, buttons: List<Button>) {
    if (buttons.isEmpty()) return
    val parts = mutableListOf<BaseComponent>()
    buttons.forEachIndexed { index, button ->
      if (index > 0) parts += TextComponent(" ")
      val id = store.add(player.uniqueId, button)
      val label = button.label.ifBlank { defaultLabel(button) }
      parts += TextComponent(*TextComponent.fromLegacyText(formatMessage(ui.buttonFormat.replace("{label}", label)))).apply {
        clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ask do $id")
        hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(TextComponent.fromLegacyText(formatMessage(hover(player, button)))))
      }
    }
    player.spigot().sendMessage(*parts.toTypedArray())
  }

  private fun defaultLabel(button: Button) = when (button) {
    is Button.ReadBook -> ui.buttonReadBook
    is Button.TakeBook -> ui.buttonTakeBook
    else -> "?"
  }

  private fun hover(player: Player, button: Button): String = when (button) {
    is Button.Ask -> ui.buttonHoverAsk.replace("{question}", button.question)
    is Button.Point -> ui.buttonHoverPoint.replace("{x}", "${button.x}").replace("{y}", "${button.y}").replace("{z}", "${button.z}")
    is Button.Note -> ui.buttonHoverNote.replace("{text}", button.text).replace("{x}", "${button.x}").replace("{y}", "${button.y}").replace("{z}", "${button.z}")
    is Button.Recipe -> ui.buttonHoverRecipe
    is Button.Enchants -> ui.buttonHoverEnchants
    is Button.Villagers -> ui.buttonHoverVillagers
    is Button.ReadBook -> ui.buttonHoverReadBook.replace("{title}", features.books.find(player.uniqueId, button.book)?.title ?: "")
    is Button.TakeBook -> ui.buttonHoverTakeBook.replace("{title}", features.books.find(player.uniqueId, button.book)?.title ?: "")
  }

  /** `/ask do <id>`. */
  fun press(player: Player, id: String) {
    when (val button = store.take(player.uniqueId, id)) {
      null -> player.sendMessage(formatMessage(messages.buttonExpiredMessage))
      is Button.Ask -> {
        player.sendMessage(formatMessage(messages.buttonQuestionMessage.replace("{question}", button.question)))
        ask(player, button.question)
      }
      is Button.Point -> point(player, button)
      is Button.Note -> note(player, button)
      is Button.Recipe -> recipe(player, button.item)
      is Button.Enchants -> enchants(player)
      is Button.Villagers -> villagers(player, button.item)
      is Button.ReadBook -> features.books.find(player.uniqueId, button.book)?.let { player.openBook(Books.item(it, ui.bookAuthor)) }
        ?: player.sendMessage(formatMessage(messages.bookGoneMessage))
      is Button.TakeBook -> features.books.find(player.uniqueId, button.book)?.let { book ->
        // Free on purpose: a book of the assistant's own text is no gear, so the “no items” rule makes room for it.
        player.inventory.addItem(Books.item(book, ui.bookAuthor)).values.forEach { player.world.dropItemNaturally(player.location, it) }
        player.sendMessage(formatMessage(messages.bookTakenMessage))
      } ?: player.sendMessage(formatMessage(messages.bookGoneMessage))
    }
  }

  private fun point(player: Player, button: Button.Point) {
    if (button.dimension != null && button.dimension != player.world.environment.name.lowercase()) {
      player.sendMessage(formatMessage(messages.pathOtherWorldMessage))
      return
    }
    val target = Location(player.world, button.x + 0.5, button.y.toDouble(), button.z + 0.5)
    player.compassTarget = target
    ActionTools.trail(plugin, player, target, 15)
    val dx = target.x - player.location.x
    val dz = target.z - player.location.z
    player.sendMessage(formatMessage(messages.pathShownMessage
      .replace("{distance}", Math.round(Math.hypot(dx, dz)).toString())
      .replace("{direction}", Facts.DIRECTIONS_RU[Facts.direction(dx, dz)] ?: "")))
  }

  private fun note(player: Player, button: Button.Note) {
    val current = player.world.environment.name.lowercase()
    val dimension = button.dimension ?: current
    try {
      features.notes.of(player.uniqueId).add { id ->
        Note(id, button.text, button.x, button.y, button.z, if (dimension == current) player.world.name else null, dimension, System.currentTimeMillis())
      }
      features.notes.save(player.uniqueId)
      player.sendMessage(formatMessage(messages.noteSavedMessage.replace("{text}", button.text)))
    } catch (error: ToolError) {
      player.sendMessage(formatMessage("&c${error.message}"))
    }
  }

  /** `/ask recipe <item>` and the recipe button. */
  fun recipe(player: Player, item: String) {
    val material = try {
      InteractTools.material(item)
    } catch (_: ToolError) {
      player.sendMessage(formatMessage(messages.unknownItemMessage.replace("{item}", item)))
      return
    }
    if (RecipeMenu.recipesFor(material).isEmpty()) {
      player.sendMessage(formatMessage(messages.noRecipeMessage))
      return
    }
    RecipeMenu(player, config.menus, material).open()
  }

  /** `/ask enchants` and the enchanting button: the window for whatever is in hand right now. */
  fun enchants(player: Player) {
    val item = player.inventory.itemInMainHand.takeUnless { it.type.isAir || it.type == Material.BOOK || it.type == Material.ENCHANTED_BOOK }
      ?: return player.sendMessage(formatMessage(messages.holdItemMessage))
    if (EnchantMenu.applicable(item).isEmpty()) return player.sendMessage(formatMessage(messages.noEnchantsMessage))
    EnchantMenu(player, config.menus, item.clone()).open()
  }

  /** `/ask villagers` and the villagers button. */
  fun villagers(player: Player, item: String?) {
    val material = item?.let { runCatching { InteractTools.material(it) }.getOrNull() }
    val found = Villagers.around(player, features.villagerRadius(), material)
    if (found.isEmpty()) return player.sendMessage(formatMessage(messages.noVillagersMessage))
    VillagerMenu(player, plugin, config.menus, found).open()
  }
}
