package ru.mcbuddy.spigot

import net.md_5.bungee.api.ChatMessageType.ACTION_BAR
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.command.CommandSender
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
  private val format = StatusFormat(ui.statusFormat, ui.answeringVerb, ui.reasoningFormat, ui.reasoningLength)
  private val labels = ToolLabels(ui.toolLabels)

  /** The current action bar text for a run. */
  fun statusLine(run: Run): String {
    val elapsed = run.elapsedMillis
    return format.line(
      spinner = spinner.frame(elapsed / ui.frameMillis),
      verb = run.verbs.at(elapsed),
      phase = run.phase,
      elapsedSeconds = elapsed / 1000,
      tokens = run.tokens.get(),
      reasoning = if (ui.showReasoning) run.reasoningTail() else null,
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

  fun toolStarted(sender: CommandSender, name: String, args: JsonObject?) {
    if (ui.toolLines) sender.sendMessage(formatMessage(ui.toolStartFormat.replace("{label}", labels.label(name, args))))
  }

  fun toolFinished(sender: CommandSender, summary: String?, failed: Boolean) {
    if (!ui.toolLines || summary.isNullOrBlank()) return
    val template = if (failed) ui.toolFailedFormat else ui.toolDoneFormat
    sender.sendMessage(formatMessage(template.replace("{summary}", summary)))
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
