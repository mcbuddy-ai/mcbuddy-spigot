package ru.mcbuddy.spigot

import net.md_5.bungee.api.ChatMessageType.ACTION_BAR
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import ru.mcbuddy.spigot.Minecraft.formatMessage
import ru.mcbuddy.spigot.tools.ToolLabels
import kotlinx.serialization.json.JsonObject

/**
 * The live display, modeled on Claude Code: a spinner with a changing verb, elapsed time, and
 * tokens in the action bar, and one chat line per tool the assistant uses.
 */
class Animator(private val plugin: JavaPlugin, private val config: Config) {
  private val activeAnimations = mutableMapOf<Player, BukkitRunnable>()
  private val ui = config.ui
  private val spinner = Spinner(ui.spinnerFrames)

  /** The current action bar text for a run. */
  fun statusLine(run: Run): String {
    val elapsed = run.elapsedMillis
    return StatusFormat(run.lang).line(
      spinner = spinner.frame(elapsed / ui.frameMillis),
      verb = run.verbs.at(elapsed),
      phase = run.phase,
      elapsedSeconds = elapsed / 1000,
      input = run.input.get(),
      output = run.output,
    )
  }

  fun start(run: Run) {
    val player = run.sender as? Player ?: return
    stopPlayerAnimation(player)

    if (ui.soundsEnabled) {
      player.playSound(player.location, ui.thinkingSound, ui.thinkingVolume, ui.thinkingPitch)
    }

    val animation = object : BukkitRunnable() {
      var lastParticles = 0L

      override fun run() {
        if (!player.isOnline || run.cancelled) {
          cancel()
          return
        }
        if (ui.actionBarEnabled) {
          player.spigot().sendMessage(ACTION_BAR, *TextComponent.fromLegacyText(formatMessage(statusLine(run))))
        }
        val now = System.currentTimeMillis()
        if (ui.particlesEnabled && now - lastParticles >= ui.particleAnimationInterval) {
          lastParticles = now
          player.world.spawnParticle(ui.particleType, player.location.add(0.0, ui.particleHeightOffset, 0.0), ui.particleCount, 0.3, 0.3, 0.3, 0.01)
        }
      }
    }

    animation.runTaskTimer(plugin, 0L, 2L)
    activeAnimations[player] = animation
  }

  fun toolStarted(run: Run, name: String, args: JsonObject?) {
    if (ui.toolLines) run.lang.send(run.sender, "tool_lines.started", "label" to ToolLabels.label(run.lang, name, args))
  }

  /** A failed tool shows its reason only where the language's `tool_lines.failed` asks for `{reason}`: the reasons are written for the model. */
  fun toolFinished(run: Run, summary: String?, failed: Boolean) {
    if (!ui.toolLines || summary.isNullOrBlank()) return
    if (failed) run.lang.send(run.sender, "tool_lines.failed", "reason" to summary) else run.lang.send(run.sender, "tool_lines.finished", "summary" to summary)
  }

  fun stopPlayerAnimation(player: Player) {
    activeAnimations.remove(player)?.let { animation ->
      animation.cancel()
      if (ui.actionBarEnabled) {
        player.spigot().sendMessage(ACTION_BAR, TextComponent(""))
      }
    }
  }

  fun stopAllAnimations() {
    activeAnimations.values.forEach(BukkitRunnable::cancel)
    activeAnimations.clear()
  }

  fun playSuccessSound(player: Player) {
    if (ui.soundsEnabled) {
      player.playSound(player.location, ui.successSound, ui.successVolume, ui.successPitch)
    }
  }

  fun playErrorSound(player: Player) {
    if (ui.soundsEnabled) {
      player.playSound(player.location, ui.errorSound, ui.errorVolume, ui.errorPitch)
    }
  }
}
