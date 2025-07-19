package ru.mcbuddy.spigot

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Minecraft.formatMessage

class Executor(private val plugin: JavaPlugin, private val config: Config, private val animator: Animator) {
  fun executeCommandsSequentially(commands: List<String>, sender: CommandSender) {
    if (hasBlockedCommands(commands)) {
      if (sender is Player) {
        animator.playErrorSound(sender)
      }
      sender.sendMessage(formatMessage(config.messages.blockedCommandMessage))
      return
    }

    var delay = 0L

    for (command in commands) {
      plugin.server.scheduler.runTaskLater(plugin, Runnable { execute(command, sender) }, delay)
      delay += 5L // Задержка 5 тиков между командами (0.25 секунды)
    }
  }

  fun executeCommandsParallel(commands: List<String>, sender: CommandSender) {
    if (hasBlockedCommands(commands)) {
      if (sender is Player) {
        animator.playErrorSound(sender)
      }
      sender.sendMessage(formatMessage(config.messages.blockedCommandMessage))
      return
    }

    for (command in commands) {
      plugin.server.scheduler.runTask(plugin, Runnable { execute(command, sender) })
    }
  }

  private fun hasBlockedCommands(commands: List<String>) = commands.any { command ->
    val cleanCommand = command.split(" ")[0].lowercase()
    cleanCommand in config.blockedCommands.commands
  }

  private fun execute(command: String, sender: CommandSender) {
    try {
      val result = plugin.server.dispatchCommand(sender, command)
      if (result) {
        val message = config.messages.commandExecutedMessage.replace("{command}", command)
        sender.sendMessage(formatMessage(message))
      } else {
        val message = config.messages.commandFailedMessage.replace("{command}", command)
        sender.sendMessage(formatMessage(message))
      }
    } catch (e: Exception) {
      val message = config.messages.commandExecutionErrorMessage.replace("{command}", command)
      sender.sendMessage(formatMessage(message))
      plugin.logger.warning("❌ Error executing command '$command': ${e.message}")
    }
  }
}