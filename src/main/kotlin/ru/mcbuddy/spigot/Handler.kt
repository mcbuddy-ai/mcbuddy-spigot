@file:Suppress("SameReturnValue", "DuplicatedCode")

package ru.mcbuddy.spigot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Minecraft.formatMessage

class Handler(private val plugin: JavaPlugin, private val configuration: Config, private val service: Service, private val animator: Animator, private val executor: Executor, private val scope: CoroutineScope) {
  private val errorHandler = ErrorHandler(configuration)

  fun handleAskCommand(sender: CommandSender, args: Array<out String>): Boolean {
    plugin.logger.info("🔄 Handling ask command")

    if (args.isEmpty()) {
      sender.sendMessage(formatMessage(configuration.messages.usageMessage))
      return true
    }

    val question = args.joinToString(" ")
    val userId = if (sender is Player) sender.uniqueId.toString() else "console"

    if (configuration.ui.chatStatusEnabled) {
      sender.sendMessage(formatMessage(configuration.messages.thinkingMessage))
    }

    if (sender is Player) {
      animator.startThinkingAnimationActionBar(sender, false)
    }

    scope.launch {
      try {
        val response = service.ask(question, userId)

        plugin.server.scheduler.runTask(plugin) { ->
          if (sender is Player) {
            animator.stopPlayerAnimation(sender)
            animator.playSuccessSound(sender)
          }

          sender.sendMessage(formatMessage(response))
          plugin.logger.info("✅ Ask command handled")
        }
      } catch (e: Exception) {
        plugin.logger.warning("❌ Error when requesting server: $e")

        plugin.server.scheduler.runTask(plugin) { ->
          if (sender is Player) {
            animator.stopPlayerAnimation(sender)
            animator.playErrorSound(sender)
          }

          val errorMessage = when (e) {
            is ApiErrorException -> errorHandler.getUserErrorMessage(e.apiError)
            else -> errorHandler.getUserErrorMessage(e)
          }
          
          sender.sendMessage(formatMessage(errorMessage))
        }
      }
    }

    return true
  }

  fun handleAskxCommand(sender: CommandSender, args: Array<out String>): Boolean {
    plugin.logger.info("🔄 Handling askx command")

    if (args.isEmpty()) {
      sender.sendMessage(formatMessage(configuration.messages.askxUsageMessage))
      return true
    }

    val action = args.joinToString(" ")
    val userId = if (sender is Player) sender.uniqueId.toString() else "console"

    if (configuration.ui.chatStatusEnabled) {
      sender.sendMessage(formatMessage(configuration.messages.askxThinkingMessage))
    }

    if (sender is Player) {
      animator.startThinkingAnimationActionBar(sender, true)
    }

    scope.launch {
      try {
        val response = service.askx(action, userId)

        plugin.server.scheduler.runTask(plugin) { ->
          if (sender is Player) {
            animator.stopPlayerAnimation(sender)
          }

          if (response.error != null) {
            if (sender is Player) {
              animator.playErrorSound(sender)
            }
            sender.sendMessage(formatMessage(response.error))
            plugin.logger.info("❌ Error when requesting askx server: ${response.error}")
            return@runTask
          }

          if (response.commands.isEmpty()) {
            if (sender is Player) {
              animator.playErrorSound(sender)
            }
            sender.sendMessage(
              formatMessage(configuration.messages.noCommandsToExecuteMessage)
            )
            plugin.logger.info("❌ No commands to execute")
            return@runTask
          }

          if (sender is Player) {
            animator.playSuccessSound(sender)
          }

          val sequenceText = if (response.isSequence) " последовательно" else " параллельно"
          val message = configuration.messages.commandsExecutingMessage.replace("{count}", response.commands.size.toString()).replace("{sequence}", sequenceText)
          sender.sendMessage(formatMessage(message))

          if (response.isSequence) {
            executor.executeCommandsSequentially(response.commands, sender)
          } else {
            executor.executeCommandsParallel(response.commands, sender)
          }

          plugin.logger.info("✅ Askx command handled")
        }
      } catch (e: Exception) {
        plugin.logger.warning("❌ Error when requesting askx server: $e")

        plugin.server.scheduler.runTask(plugin) { ->
          if (sender is Player) {
            animator.stopPlayerAnimation(sender)
            animator.playErrorSound(sender)
          }

          val errorMessage = when (e) {
            is ApiErrorException -> errorHandler.getUserErrorMessage(e.apiError)
            else -> errorHandler.getUserErrorMessage(e)
          }
          
          sender.sendMessage(formatMessage(errorMessage))
        }
      }
    }
    return true
  }
}