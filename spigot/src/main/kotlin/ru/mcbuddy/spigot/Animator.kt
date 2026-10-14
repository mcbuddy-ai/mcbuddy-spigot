package ru.mcbuddy.spigot

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*

import net.md_5.bungee.api.ChatMessageType.ACTION_BAR
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import ru.mcbuddy.spigot.Minecraft.formatMessage
import ru.mcbuddy.core.tools.ToolLabels
import kotlinx.serialization.json.JsonObject

/**
 * The live display, modeled on Claude Code: a spinner with a changing verb, elapsed time, and
 * tokens in the action bar, and one chat line per tool the assistant uses.
 */
class Animator(private val plugin: JavaPlugin, private val config: Config) {
  private val activeAnimations = mutableMapOf<Player, BukkitRunnable>()
  private val ui = config.ui
  private val spinner = Spinner(ui.spinnerFrames)
  private val particle = value("ui.particles.type", ui.particleType, Particle.ENCHANT) { Particle.valueOf(it) }
  private val successSound = sound(ui.successSound, Sound.ENTITY_EXPERIENCE_ORB_PICKUP)
  private val errorSound = sound(ui.errorSound, Sound.AMBIENT_CAVE)
  private val thinkingSound = sound(ui.thinkingSound, Sound.BLOCK_NOTE_BLOCK_CHIME)

  /** A sound from the config as Bukkit's, with its volume and pitch. */
  private class Effect(val sound: Sound, val volume: Float, val pitch: Float)

  private fun sound(setting: Config.Sound, default: Sound) =
    Effect(value(setting.key, setting.name, default) { Sound.valueOf(it) }, setting.volume, setting.pitch)

  /** A name from the config as Bukkit's value; an unknown name falls back to [default] with a warning. */
  private fun <T> value(key: String, name: String?, default: T, parse: (String) -> T): T {
    name ?: return default
    return runCatching { parse(name.uppercase()) }.getOrElse {
      plugin.logger.warning("$key: there is no such value as $name; using the default")
      default
    }
  }

  private fun Player.play(effect: Effect) = playSound(location, effect.sound, effect.volume, effect.pitch)

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
      player.play(thinkingSound)
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
          player.world.spawnParticle(particle, player.location.add(0.0, ui.particleHeightOffset, 0.0), ui.particleCount, 0.3, 0.3, 0.3, 0.01)
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
      player.play(successSound)
    }
  }

  fun playErrorSound(player: Player) {
    if (ui.soundsEnabled) {
      player.play(errorSound)
    }
  }
}
