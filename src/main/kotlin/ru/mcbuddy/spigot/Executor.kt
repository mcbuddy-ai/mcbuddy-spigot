package ru.mcbuddy.spigot

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

/** Runs `/askx` commands as the player who asked, minus `blocked_commands`. */
class Executor(private val plugin: JavaPlugin, private val config: Config, private val locales: Locales, private val animator: Animator) {
  fun executeCommandsSequentially(commands: List<String>, sender: CommandSender) {
    if (hasBlockedCommands(commands)) {
      if (sender is Player) {
        animator.playErrorSound(sender)
      }
      locales.of(sender).send(sender, "messages.blocked_command")
      return
    }

    var delay = 0L

    for (command in commands) {
      plugin.server.scheduler.runTaskLater(plugin, Runnable { execute(command, sender) }, delay)
      delay += 5L // A quarter of a second between commands, so each sees what the last one did.
    }
  }

  fun executeCommandsParallel(commands: List<String>, sender: CommandSender) {
    if (hasBlockedCommands(commands)) {
      if (sender is Player) {
        animator.playErrorSound(sender)
      }
      locales.of(sender).send(sender, "messages.blocked_command")
      return
    }

    for (command in commands) {
      plugin.server.scheduler.runTask(plugin, Runnable { execute(command, sender) })
    }
  }

  private fun hasBlockedCommands(commands: List<String>) = commands.any { command ->
    val cleanCommand = command.split(" ")[0].lowercase()
    cleanCommand in config.blockedCommands
  }

  private fun execute(command: String, sender: CommandSender) {
    try {
      val result = plugin.server.dispatchCommand(sender, command)
      locales.of(sender).send(sender, if (result) "messages.command_done" else "messages.command_failed", "command" to command)
    } catch (e: Exception) {
      locales.of(sender).send(sender, "messages.command_error", "command" to command)
      plugin.logger.warning("❌ Error executing command '$command': ${e.message}")
    }
  }
}