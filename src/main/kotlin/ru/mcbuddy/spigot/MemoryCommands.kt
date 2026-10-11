package ru.mcbuddy.spigot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Minecraft.formatMessage

/** `/ask new`, `/ask memory`, and `/ask forget`: what the backend remembers about the asker, under their control. */
class MemoryCommands(private val plugin: JavaPlugin, private val config: Config, private val service: Service, private val scope: CoroutineScope) {
  private val messages get() = config.messages
  private val errorHandler = ErrorHandler(config)

  fun newSession(sender: CommandSender): Boolean = request(sender) {
    service.resetSession(userId(sender))
    listOf(messages.sessionClearedMessage)
  }

  fun list(sender: CommandSender): Boolean = request(sender) {
    val memories = service.memories(userId(sender))
    if (memories.isEmpty()) return@request listOf(messages.memoryEmptyMessage)
    listOf(messages.memoryHeaderMessage) +
      memories.mapIndexed { index, memory -> messages.memoryLineMessage.replace("{n}", "${index + 1}").replace("{text}", memory.text) } +
      messages.memoryFooterMessage
  }

  /** Numbers come from a fresh list, so they match what `/ask memory` shows right now. */
  fun forget(sender: CommandSender, which: String): Boolean = request(sender) {
    val userId = userId(sender)
    if (which.equals("all", ignoreCase = true)) {
      val count = service.forget(userId, null)
      return@request listOf(messages.forgotAllMessage.replace("{count}", "$count"))
    }
    val number = which.toIntOrNull() ?: return@request listOf(messages.forgetUsageMessage)
    val memory = service.memories(userId).getOrNull(number - 1)
      ?: return@request listOf(messages.forgetNotFoundMessage.replace("{n}", which))
    service.forget(userId, memory.id)
    listOf(messages.forgotMessage.replace("{text}", memory.text))
  }

  private fun request(sender: CommandSender, block: suspend () -> List<String>): Boolean {
    scope.launch {
      val lines = try {
        block()
      } catch (error: ApiErrorException) {
        // A backend before 2.1 has no such routes.
        listOf(if (error.apiError.statusCode == 404) messages.memoryUnsupportedMessage else errorHandler.getUserErrorMessage(error.apiError))
      } catch (error: Exception) {
        plugin.logger.warning("❌ Memory request failed: $error")
        listOf(errorHandler.getUserErrorMessage(error))
      }
      if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable { lines.forEach { sender.sendMessage(formatMessage(it)) } })
    }
    return true
  }

  private fun userId(sender: CommandSender) = (sender as? Player)?.uniqueId?.toString() ?: "console"
}
