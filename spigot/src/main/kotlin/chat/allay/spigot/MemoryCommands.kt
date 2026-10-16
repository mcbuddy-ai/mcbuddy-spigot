package chat.allay.spigot

import chat.allay.core.*
import chat.allay.core.tools.*
import chat.allay.spigot.tools.Features
import chat.allay.spigot.tools.ToolContext
import chat.allay.spigot.tools.id

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import chat.allay.spigot.Minecraft.formatMessage

/** `/allay new`, `/allay memory`, and `/allay forget`: what the backend remembers about the asker, under their control. */
class MemoryCommands(
  private val plugin: JavaPlugin,
  private val locales: Locales,
  private val features: Features,
  private val service: Service<ToolContext>,
  private val scope: CoroutineScope,
) {

  fun newSession(sender: CommandSender): Boolean = request(sender) { lang ->
    service.resetSession(userId(sender))
    listOf(formatMessage(lang.text("memory.session_cleared")))
  }

  fun list(sender: CommandSender): Boolean = request(sender) { lang ->
    val memories = service.memories(userId(sender))
    if (memories.isEmpty()) return@request listOf(formatMessage(lang.text("memory.empty")))
    (listOf(lang.text("memory.header")) +
      memories.mapIndexed { index, memory -> lang.text("memory.line", "n" to index + 1, "text" to memory.text) } +
      lang.text("memory.footer")).map(::formatMessage)
  }

  /**
   * `/allay memory <text>`: keeps what the player typed without asking the assistant. Coordinates in it make a note in
   * the notebook, in the dimension the text names or else where the player stands; anything else is a remembered fact.
   */
  fun remember(sender: CommandSender, text: String): Boolean {
    val lang = locales.of(sender)
    val spot = QuickNote.spot(text)
    if (sender is Player && spot != null && features.config().tools.enabled("add_note")) {
      note(sender, lang, text.take(200), spot)
      return true
    }
    if (text.length < 3) {
      lang.send(sender, "memory.remember_usage")
      return true
    }
    return request(sender) { lang ->
      try {
        val memory = service.remember(userId(sender), text.take(200))
        listOf(Minecraft.withText(lang.text("memory.remembered", "text" to Minecraft.SLOT), memory.text))
      } catch (error: ApiErrorException) {
        if (error.apiError.code != "MEMORY_FULL") throw error
        listOf(formatMessage(lang.text("memory.full")))
      }
    }
  }

  private fun note(player: Player, lang: Lang, text: String, spot: QuickNote.Spot) {
    val current = Dimensions.id(player.world)
    val dimension = spot.dimension ?: current
    val y = spot.y ?: player.location.blockY
    try {
      features.notes.of(player.uniqueId).add { id ->
        // Distances need the world; a note from another dimension keeps only the dimension's name, as add_note does.
        Note(id, text, spot.x, y, spot.z, if (dimension == current) player.world.name else null, dimension, System.currentTimeMillis())
      }
      features.notes.save(player.uniqueId)
    } catch (error: ToolError) {
      player.sendMessage(formatMessage("&c${error.message}"))
      return
    }
    val template = lang.text("memory.noted", "text" to Minecraft.SLOT, "x" to spot.x, "y" to y, "z" to spot.z, "dimension" to lang.text("dimensions.$dimension"))
    player.sendMessage(Minecraft.withText(template, text))
  }

  /** Numbers come from a fresh list, so they match what `/allay memory` shows right now. */
  fun forget(sender: CommandSender, which: String): Boolean = request(sender) { lang ->
    val userId = userId(sender)
    if (which.equals("all", ignoreCase = true)) {
      val count = service.forget(userId, null)
      return@request listOf(formatMessage(lang.text("memory.forgot_all", "count" to count)))
    }
    val number = which.toIntOrNull() ?: return@request listOf(formatMessage(lang.text("memory.forget_usage")))
    val memory = service.memories(userId).getOrNull(number - 1)
      ?: return@request listOf(formatMessage(lang.text("memory.not_found", "n" to which)))
    service.forget(userId, memory.id)
    listOf(formatMessage(lang.text("memory.forgot", "text" to memory.text)))
  }

  fun usage(sender: CommandSender) = locales.of(sender).send(sender, "memory.forget_usage")

  /** Runs [block] off the server thread; it returns finished chat lines, colored already. */
  private fun request(sender: CommandSender, block: suspend (Lang) -> List<String>): Boolean {
    val lang = locales.of(sender)
    scope.launch {
      val lines = try {
        block(lang)
      } catch (error: ApiErrorException) {
        // A backend before 2.1 has no such routes, and one before 5.1 can't add a memory.
        listOf(formatMessage(if (error.apiError.statusCode in setOf(404, 405)) lang.text("memory.unsupported") else ErrorHandler.message(lang, error.apiError)))
      } catch (error: Exception) {
        plugin.logger.warning("Memory request failed: $error")
        listOf(formatMessage(ErrorHandler.message(lang, error)))
      }
      if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable { lines.filter { it.isNotEmpty() }.forEach { sender.sendMessage(it) } })
    }
    return true
  }

  private fun userId(sender: CommandSender) = (sender as? Player)?.uniqueId?.toString() ?: "console"
}
