package ru.mcbuddy.spigot

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Minecraft.formatMessage
import java.util.concurrent.ConcurrentHashMap

/** Runs `/ask` and `/askx`: one stream per sender, a live display while it runs, then the answer or the commands. */
class Handler(private val plugin: JavaPlugin, private val configuration: Config, private val service: Service, private val animator: Animator, private val executor: Executor, private val scope: CoroutineScope) {
  private val errorHandler = ErrorHandler(configuration)
  private val runs = ConcurrentHashMap<String, Run>()

  fun handleAskCommand(sender: CommandSender, args: Array<out String>) = handle(sender, args, Run.Mode.ASK)

  fun handleAskxCommand(sender: CommandSender, args: Array<out String>) = handle(sender, args, Run.Mode.ASKX)

  /** `/ask cancel`: stops the sender's run, like Esc in Claude Code. */
  fun cancel(sender: CommandSender): Boolean {
    val run = runs.remove(key(sender))
    if (run == null) {
      sender.sendMessage(formatMessage(configuration.messages.nothingToCancelMessage))
      return true
    }
    run.cancel()
    (sender as? Player)?.let(animator::stopPlayerAnimation)
    sender.sendMessage(formatMessage(configuration.messages.cancelledMessage))
    return true
  }

  fun cancelAll() {
    runs.values.forEach(Run::cancel)
    runs.clear()
  }

  private fun handle(sender: CommandSender, args: Array<out String>, mode: Run.Mode): Boolean {
    val messages = configuration.messages
    if (args.isEmpty()) {
      sender.sendMessage(formatMessage(if (mode == Run.Mode.ASK) messages.usageMessage else messages.askxUsageMessage))
      return true
    }
    plugin.logger.info("🔄 Handling ${mode.wire} command")

    val message = args.joinToString(" ")
    if (configuration.ui.chatStatusEnabled) {
      sender.sendMessage(formatMessage(if (mode == Run.Mode.ASK) messages.thinkingMessage else messages.askxThinkingMessage))
    }

    // A new question replaces the one still running.
    runs.remove(key(sender))?.cancel()
    val ui = configuration.ui
    val run = Run(sender, mode, if (mode == Run.Mode.ASK) ui.verbs else ui.askxVerbs, ui.verbSeconds)
    runs[key(sender)] = run
    animator.start(run)

    val listener = object : Service.Listener {
      override fun toolStarted(callId: String, name: String, args: JsonObject?) = onMain { animator.toolStarted(sender, name, args) }
      override fun toolFinished(callId: String, summary: String?, failed: Boolean) = onMain { animator.toolFinished(sender, summary, failed) }
    }

    scope.launch {
      try {
        val outcome = service.chat(run, message, listener)
        if (!run.cancelled) onMain { finish(run, outcome) }
      } catch (e: Exception) {
        if (run.cancelled || e is CancellationException) return@launch
        plugin.logger.warning("❌ Error when requesting server: $e")
        onMain {
          (sender as? Player)?.let {
            animator.stopPlayerAnimation(it)
            animator.playErrorSound(it)
          }
          val errorMessage = when (e) {
            is ApiErrorException -> errorHandler.getUserErrorMessage(e.apiError)
            else -> errorHandler.getUserErrorMessage(e)
          }
          sender.sendMessage(formatMessage(errorMessage))
        }
      } finally {
        runs.remove(key(sender), run)
      }
    }
    return true
  }

  private fun finish(run: Run, outcome: Service.Outcome) {
    val sender = run.sender
    val player = sender as? Player
    player?.let(animator::stopPlayerAnimation)

    if (run.mode == Run.Mode.ASK) {
      player?.let(animator::playSuccessSound)
      sender.sendMessage(formatMessage(outcome.answer))
      plugin.logger.info("✅ Ask command handled")
      return
    }

    val commands = outcome.commands?.commands.orEmpty().map { it.trim().removePrefix("/") }.filter { it.isNotEmpty() }
    if (commands.isEmpty()) {
      player?.let(animator::playErrorSound)
      sender.sendMessage(formatMessage(outcome.answer.ifBlank { configuration.messages.noCommandsToExecuteMessage }))
      plugin.logger.info("❌ No commands to execute")
      return
    }
    player?.let(animator::playSuccessSound)
    if (outcome.answer.isNotBlank()) sender.sendMessage(formatMessage(outcome.answer))
    val sequential = outcome.commands?.is_sequence == true
    val message = configuration.messages.commandsExecutingMessage
      .replace("{count}", commands.size.toString())
      .replace("{sequence}", if (sequential) " последовательно" else " параллельно")
    sender.sendMessage(formatMessage(message))
    if (sequential) executor.executeCommandsSequentially(commands, sender) else executor.executeCommandsParallel(commands, sender)
    plugin.logger.info("✅ Askx command handled")
  }

  private fun onMain(block: () -> Unit) {
    if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable(block))
  }

  private fun key(sender: CommandSender) = (sender as? Player)?.uniqueId?.toString() ?: "console:${sender.name}"
}
