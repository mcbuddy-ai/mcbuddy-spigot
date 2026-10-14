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
class Handler(
  private val plugin: JavaPlugin,
  private val configuration: Config,
  private val locales: Locales,
  private val service: Service,
  private val animator: Animator,
  private val executor: Executor,
  private val scope: CoroutineScope,
  private val clicks: () -> Clicks,
) {
  private val runs = ConcurrentHashMap<String, Run>()

  fun handleAskCommand(sender: CommandSender, args: Array<out String>) = handle(sender, args, Run.Mode.ASK)

  fun handleAskxCommand(sender: CommandSender, args: Array<out String>) = handle(sender, args, Run.Mode.ASKX)

  /** `/ask cancel`: stops the sender's run, like Esc in Claude Code. */
  fun cancel(sender: CommandSender): Boolean {
    val run = runs.remove(key(sender))
    if (run == null) {
      locales.of(sender).send(sender, "messages.nothing_to_cancel")
      return true
    }
    run.cancel()
    (sender as? Player)?.let(animator::stopPlayerAnimation)
    locales.of(sender).send(sender, "messages.cancelled")
    return true
  }

  fun cancelAll() {
    runs.values.forEach(Run::cancel)
    runs.clear()
  }

  private fun handle(sender: CommandSender, args: Array<out String>, mode: Run.Mode): Boolean {
    val lang = locales.of(sender)
    if (args.isEmpty()) {
      lang.send(sender, if (mode == Run.Mode.ASK) "messages.usage" else "messages.askx_usage")
      return true
    }
    plugin.logger.info("🔄 Handling ${mode.wire} command")

    val message = args.joinToString(" ")
    if (configuration.ui.chatStatusEnabled) {
      lang.send(sender, if (mode == Run.Mode.ASK) "messages.thinking" else "messages.askx_thinking")
    }

    // A new question replaces the one still running.
    runs.remove(key(sender))?.cancel()
    val run = Run(sender, mode, lang, configuration.ui.verbSeconds)
    runs[key(sender)] = run
    animator.start(run)

    val listener = object : Service.Listener {
      override fun toolStarted(callId: String, name: String, args: JsonObject?) = onMain { animator.toolStarted(run, name, args) }
      override fun toolFinished(callId: String, summary: String?, failed: Boolean) = onMain { animator.toolFinished(run, summary, failed) }
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
          sender.sendMessage(formatMessage(ErrorHandler.message(lang, e)))
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
      player?.let { clicks().show(it, run.buttons.toList()) }
      plugin.logger.info("✅ Ask command handled")
      return
    }

    val commands = outcome.commands?.commands.orEmpty().map { it.trim().removePrefix("/") }.filter { it.isNotEmpty() }
    if (commands.isEmpty()) {
      player?.let(animator::playErrorSound)
      if (outcome.answer.isNotBlank()) sender.sendMessage(formatMessage(outcome.answer)) else run.lang.send(sender, "messages.no_commands")
      plugin.logger.info("❌ No commands to execute")
      return
    }
    player?.let(animator::playSuccessSound)
    if (outcome.answer.isNotBlank()) sender.sendMessage(formatMessage(outcome.answer))
    val sequential = outcome.commands?.is_sequence == true
    run.lang.send(sender, if (sequential) "messages.commands_in_order" else "messages.commands_at_once", "count" to commands.size)
    if (sequential) executor.executeCommandsSequentially(commands, sender) else executor.executeCommandsParallel(commands, sender)
    plugin.logger.info("✅ Askx command handled")
  }

  private fun onMain(block: () -> Unit) {
    if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable(block))
  }

  private fun key(sender: CommandSender) = (sender as? Player)?.uniqueId?.toString() ?: "console:${sender.name}"
}
