package ru.mcbuddy.spigot

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*
import ru.mcbuddy.spigot.tools.ToolContext

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Minecraft.formatMessage
import java.util.concurrent.ConcurrentHashMap

/**
 * Runs `/ask`, `/askall`, `/askx`, and chat mode's questions: one stream per sender, a live display while it runs,
 * then the answer or the commands. A public question and its answer go to every player online; the rest of the
 * display stays with the asker.
 */
class Handler(
  private val plugin: JavaPlugin,
  private val configuration: Config,
  private val locales: Locales,
  private val service: Service<ToolContext>,
  private val animator: Animator,
  private val executor: Executor,
  private val scope: CoroutineScope,
  private val clicks: () -> Clicks,
) {
  private val runs = ConcurrentHashMap<String, Run>()
  /** When each player last asked everyone, for `askall.cooldown_seconds`. */
  private val lastPublic = ConcurrentHashMap<String, Long>()

  /** `/buddy cancel`: stops the sender's run, like Esc in Claude Code. */
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

  /**
   * Asks MCBuddy. [public] shows the question and the answer to everyone online; [tip] puts the chat mode tip
   * under the answer.
   */
  fun ask(sender: CommandSender, message: String, mode: Run.Mode, public: Boolean = false, tip: Boolean = false): Boolean {
    val lang = locales.of(sender)
    if (message.isBlank()) {
      lang.send(sender, when {
        mode == Run.Mode.ASKX -> "messages.askx_usage"
        public -> "messages.askall_usage"
        else -> "messages.usage"
      })
      return true
    }
    if (public && !sender.hasPermission(Permissions.ASKALL_NO_COOLDOWN)) {
      val now = System.currentTimeMillis()
      val wait = configuration.askall.cooldownSeconds * 1000 - (now - (lastPublic[key(sender)] ?: 0))
      if (wait > 0) {
        lang.send(sender, "public.cooldown", "seconds" to (wait + 999) / 1000)
        return true
      }
      lastPublic[key(sender)] = now
    }
    plugin.logger.info("🔄 Handling ${if (public) "public " else ""}${mode.wire} question")

    if (public) broadcast("public.question", sender.name, message)
    if (configuration.ui.chatStatusEnabled) {
      lang.send(sender, if (mode == Run.Mode.ASK) "messages.thinking" else "messages.askx_thinking")
    }

    // A new question replaces the one still running.
    runs.remove(key(sender))?.cancel()
    val run = Run(SenderAsker(sender), mode, lang, configuration.ui.verbSeconds, public)
    run.tip = tip
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
          if (public) everyoneBut(sender).forEach { locales.of(it).send(it, "public.failed") }
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
      if (run.public) {
        broadcast("public.answer", sender.name, outcome.answer, colored = true)
      } else {
        sender.sendMessage(formatMessage(outcome.answer))
      }
      // Buttons belong to the asker, even under an answer everyone reads.
      player?.let { clicks().show(it, run.buttons.toList()) }
      if (run.tip) player?.let { clicks().tip(it) }
      plugin.logger.info("✅ Question answered")
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

  /**
   * A line every player online reads in their own language, and the console too. The question is the player's
   * own text, so its `&` stays as typed; the answer is MCBuddy's and may use colors.
   */
  private fun broadcast(key: String, asker: String, text: String, colored: Boolean = false) {
    val readers = plugin.server.onlinePlayers + plugin.server.consoleSender
    readers.forEach { reader ->
      val template = locales.of(reader).text(key, "player" to asker, "text" to Minecraft.SLOT)
      reader.sendMessage(if (colored) formatMessage(template.replace(Minecraft.SLOT, text)) else Minecraft.withText(template, text))
    }
  }

  private fun everyoneBut(sender: CommandSender) = plugin.server.onlinePlayers.filter { it != sender }

  private fun onMain(block: () -> Unit) {
    if (plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable(block))
  }

  private fun key(sender: CommandSender) = (sender as? Player)?.uniqueId?.toString() ?: "console:${sender.name}"
}
