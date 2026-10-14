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
import ru.mcbuddy.spigot.tools.Dimensions
import ru.mcbuddy.spigot.tools.Facts
import ru.mcbuddy.spigot.tools.Features
import ru.mcbuddy.spigot.tools.InteractTools
import ru.mcbuddy.spigot.tools.Note
import ru.mcbuddy.spigot.tools.ToolError
import ru.mcbuddy.spigot.tools.Villagers

/**
 * Buttons under answers and the windows behind them. A button runs `/buddy do <id>`; the id leads to an action
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
  private fun lang(player: Player) = features.locales.of(player)

  /** Draws one chat line of `[label]` buttons. */
  fun show(player: Player, buttons: List<Button>) {
    if (buttons.isEmpty()) return
    val lang = lang(player)
    val parts = mutableListOf<BaseComponent>()
    buttons.forEachIndexed { index, button ->
      if (index > 0) parts += TextComponent(" ")
      val id = store.add(player.uniqueId, button)
      val label = button.label.ifBlank { defaultLabel(lang, button) }
      parts += clickable(lang.text("buttons.format", "label" to label), id, hover(lang, player, button))
    }
    player.spigot().sendMessage(*parts.toTypedArray())
  }

  /** The quiet tip under a second `/ask`, like Claude Code's: chat mode exists, with buttons to try it or hide the tip. */
  fun tip(player: Player) {
    val lang = lang(player)
    val on = store.add(player.uniqueId, Button.ChatMode(""))
    val off = store.add(player.uniqueId, Button.HideTips(""))
    player.spigot().sendMessage(
      *TextComponent.fromLegacyText(formatMessage(lang.text("chat_mode.tip"))),
      TextComponent("  "),
      clickable(lang.text("chat_mode.tip_enable"), on, lang.text("chat_mode.tip_enable_hover")),
      TextComponent(" "),
      clickable(lang.text("chat_mode.tip_hide"), off, lang.text("chat_mode.tip_hide_hover")),
    )
  }

  private fun clickable(text: String, id: String, hover: String) = TextComponent(*TextComponent.fromLegacyText(formatMessage(text))).apply {
    clickEvent = ClickEvent(ClickEvent.Action.RUN_COMMAND, "/buddy do $id")
    hoverEvent = HoverEvent(HoverEvent.Action.SHOW_TEXT, Text(TextComponent.fromLegacyText(formatMessage(hover))))
  }

  private fun defaultLabel(lang: Lang, button: Button) = when (button) {
    is Button.ReadBook -> lang.text("buttons.read_book")
    is Button.TakeBook -> lang.text("buttons.take_book")
    else -> "?"
  }

  private fun hover(lang: Lang, player: Player, button: Button): String = when (button) {
    is Button.Ask -> lang.text("buttons.hover.ask", "question" to button.question)
    is Button.Point -> lang.text("buttons.hover.point", "x" to button.x, "y" to button.y, "z" to button.z)
    is Button.Note -> lang.text("buttons.hover.note", "text" to button.text, "x" to button.x, "y" to button.y, "z" to button.z)
    is Button.Recipe -> lang.text("buttons.hover.recipe")
    is Button.Enchants -> lang.text("buttons.hover.enchants")
    is Button.Villagers -> lang.text("buttons.hover.villagers")
    is Button.ReadBook -> lang.text("buttons.hover.read_book", "title" to (features.books.find(player.uniqueId, button.book)?.title ?: ""))
    is Button.TakeBook -> lang.text("buttons.hover.take_book", "title" to (features.books.find(player.uniqueId, button.book)?.title ?: ""))
    is Button.ChatMode -> lang.text("chat_mode.tip_enable_hover")
    is Button.HideTips -> lang.text("chat_mode.tip_hide_hover")
  }

  /** `/buddy do <id>`. */
  fun press(player: Player, id: String) {
    val lang = lang(player)
    when (val button = store.take(player.uniqueId, id)) {
      null -> lang.send(player, "buttons.expired")
      is Button.Ask -> {
        lang.send(player, "buttons.question", "question" to button.question)
        ask(player, button.question)
      }
      is Button.Point -> point(player, button)
      is Button.Note -> note(player, button)
      is Button.Recipe -> recipe(player, button.item)
      is Button.Enchants -> enchants(player)
      is Button.Villagers -> villagers(player, button.item)
      is Button.ReadBook -> features.books.find(player.uniqueId, button.book)?.let { player.openBook(Books.item(it, ui.bookAuthor)) }
        ?: lang.send(player, "buttons.book_gone")
      is Button.TakeBook -> features.books.find(player.uniqueId, button.book)?.let { book ->
        // Free on purpose: a book of the assistant's own text is no gear, so the “no items” rule makes room for it.
        player.inventory.addItem(Books.item(book, ui.bookAuthor)).values.forEach { player.world.dropItemNaturally(player.location, it) }
        lang.send(player, "buttons.book_taken")
      } ?: lang.send(player, "buttons.book_gone")
      is Button.ChatMode -> features.chat.set(player, true)
      is Button.HideTips -> features.chat.hints(player, false)
    }
  }

  private fun point(player: Player, button: Button.Point) {
    if (button.dimension != null && Dimensions.normalize(button.dimension) != Dimensions.id(player.world)) {
      lang(player).send(player, "buttons.other_dimension")
      return
    }
    val target = Location(player.world, button.x + 0.5, button.y.toDouble(), button.z + 0.5)
    player.compassTarget = target
    ActionTools.trail(plugin, player, target, 15)
    val dx = target.x - player.location.x
    val dz = target.z - player.location.z
    val lang = lang(player)
    lang.send(player, "buttons.path_shown", "distance" to Math.round(Math.hypot(dx, dz)), "direction" to lang.text("directions.${Facts.direction(dx, dz)}"))
  }

  private fun note(player: Player, button: Button.Note) {
    val current = Dimensions.id(player.world)
    val dimension = Dimensions.normalize(button.dimension) ?: current
    try {
      features.notes.of(player.uniqueId).add { id ->
        Note(id, button.text, button.x, button.y, button.z, if (dimension == current) player.world.name else null, dimension, System.currentTimeMillis())
      }
      features.notes.save(player.uniqueId)
      lang(player).send(player, "buttons.note_saved", "text" to button.text)
    } catch (error: ToolError) {
      player.sendMessage(formatMessage("&c${error.message}"))
    }
  }

  /** `/buddy recipe <item>` and the recipe button. */
  fun recipe(player: Player, item: String) {
    val material = try {
      InteractTools.material(item)
    } catch (_: ToolError) {
      lang(player).send(player, "messages.unknown_item", "item" to item)
      return
    }
    if (RecipeMenu.recipesFor(material).isEmpty()) {
      lang(player).send(player, "messages.no_recipe")
      return
    }
    RecipeMenu(player, lang(player), material).open()
  }

  /** `/buddy enchants` and the enchanting button: the window for whatever is in hand right now. */
  fun enchants(player: Player) {
    val item = player.inventory.itemInMainHand.takeUnless { it.type.isAir || it.type == Material.BOOK || it.type == Material.ENCHANTED_BOOK }
      ?: return lang(player).send(player, "messages.hold_item")
    if (EnchantMenu.applicable(item).isEmpty()) return lang(player).send(player, "messages.no_enchants")
    EnchantMenu(player, lang(player), item.clone()).open()
  }

  /** `/buddy villagers` and the villagers button. */
  fun villagers(player: Player, item: String?) {
    val material = item?.let { runCatching { InteractTools.material(it) }.getOrNull() }
    val found = Villagers.around(player, features.villagerRadius(), material)
    if (found.isEmpty()) return lang(player).send(player, "messages.no_villagers")
    VillagerMenu(player, plugin, lang(player), found).open()
  }
}
