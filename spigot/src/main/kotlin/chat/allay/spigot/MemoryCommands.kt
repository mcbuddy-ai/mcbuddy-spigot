package chat.allay.spigot

import chat.allay.core.*
import chat.allay.core.tools.*
import chat.allay.spigot.tools.ToolContext

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import chat.allay.spigot.Minecraft.formatMessage

/** `/allay new`, `/allay memory`, and `/allay forget`: what the backend remembers about the asker, under their control. */
class MemoryCommands(private val plugin: JavaPlugin, private val locales: Locales, private val service: Service<ToolContext>, private val scope: CoroutineScope) {

  fun newSession(sender: CommandSender): Boolean = request(sender) { lang ->
    service.resetSession(userId(sender))
    listOf(lang.text("memory.session_cleared"))
  }

  fun list(sender: CommandSender): Boolean = request(sender) { lang ->
    val memories = service.memories(userId(sender))
    if (memories.isEmpty()) return@request listOf(lang.text("memory.empty"))
    listOf(lang.text("memory.header")) +
      memories.mapIndexed { index, memory -> lang.text("memory.line", "n" to index + 1, "text" to memory.text) } +
      lang.text("memory.footer")
  }

  /** Numbers come from a fresh list, so they match what `/allay memory` shows right now. */
  fun forget(sender: CommandSender, which: String): Boolean = request(sender) { lang ->
    val userId = userId(sender)
    if (which.equals("all", ignoreCase = true)) {
      val count = service.forget(userId, null)
      return@request listOf(lang.text("memory.forgot_all", "count" to count))
    }
    val number = which.toIntOrNull() ?: return@request listOf(lang.text("memory.forget_usage"))
    val memory = service.memories(userId).getOrNull(number - 1)
      ?: return@request listOf(lang.text("memory.not_found", "n" to which))
    service.forget(userId, memory.id)
    listOf(lang.text("memory.forgot", "text" to memory.text))
  }

  fun usage(sender: CommandSender) = locales.of(sender).send(sender, "memory.forget_usage")

  private fun request(sender: CommandSender, block: suspend (Lang) -> List<String>): Boolean {
    val lang = locales.of(sender)
    scope.launch {
      val lines = try {
        block(lang)
      } catch (error: ApiErrorException) {
        // A backend before 2.1 has no such routes.
        listOf(if (error.apiError.statusCode == 404) lang.text("memory.unsupported") else ErrorHandler.message(lang, error.apiError))
      } catch (error: Exception) {
        plugin.logger.warning("Memory request failed: $error")
        listOf(ErrorHandler.message(lang, error))
      }
      if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable { lines.filter { it.isNotEmpty() }.forEach { sender.sendMessage(formatMessage(it)) } })
    }
    return true
  }

  private fun userId(sender: CommandSender) = (sender as? Player)?.uniqueId?.toString() ?: "console"
}
