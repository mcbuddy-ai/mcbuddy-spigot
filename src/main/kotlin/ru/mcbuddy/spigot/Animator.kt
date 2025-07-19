package ru.mcbuddy.spigot

import net.md_5.bungee.api.ChatMessageType.ACTION_BAR
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitRunnable
import ru.mcbuddy.spigot.Minecraft.formatMessage

class Animator(private val plugin: JavaPlugin, private val config: Config) {
  private val activeAnimations = mutableMapOf<Player, BukkitRunnable>()

  fun startThinkingAnimationActionBar(player: Player, isAskx: Boolean = false) {
    stopPlayerAnimation(player)

    val baseText = if (isAskx) config.ui.actionBarAskxText else config.ui.actionBarThinkingText

    if (config.ui.soundsEnabled) {
      player.playSound(player.location, config.ui.thinkingSound, config.ui.thinkingVolume, config.ui.thinkingPitch)
    }

    val animation = object : BukkitRunnable() {
      var dots = 0
      var particleTicks = 0

      override fun run() {
        if (!player.isOnline) {
          cancel()
          return
        }

        if (config.ui.actionBarEnabled) {
          val dotsText = ".".repeat((dots % 4) + 1)
          val message = formatMessage("$baseText$dotsText")
          player.spigot().sendMessage(ACTION_BAR, TextComponent(message))
          dots++
        }

        if (config.ui.particlesEnabled && particleTicks * 50 >= config.ui.particleAnimationInterval) {
          startThinkingAnimationParticles(player)
          particleTicks = 0
        } else {
          particleTicks++
        }
      }
    }

    animation.runTaskTimer(plugin, 0L, config.ui.actionBarAnimationSpeed / 50L)
    activeAnimations[player] = animation
  }

  private fun startThinkingAnimationParticles(player: Player) {
    val location = player.location.add(0.0, config.ui.particleHeightOffset, 0.0)
    player.world.spawnParticle(
      config.ui.particleType,
      location,
      config.ui.particleCount,
      0.3, 0.3, 0.3, 0.01
    )
  }

  fun stopPlayerAnimation(player: Player) {
    activeAnimations[player]?.let { animation ->
      animation.cancel()
      activeAnimations.remove(player)

      if (config.ui.actionBarEnabled) {
        player.spigot().sendMessage(ACTION_BAR, TextComponent(""))
      }
    }
  }

  fun stopAllAnimations() {
    activeAnimations.values.forEach(BukkitRunnable::cancel)
    activeAnimations.clear()
  }

  fun playSuccessSound(player: Player) {
    if (config.ui.soundsEnabled) {
      player.playSound(player.location, config.ui.successSound, config.ui.successVolume, config.ui.successPitch)
    }
  }

  fun playErrorSound(player: Player) {
    if (config.ui.soundsEnabled) {
      player.playSound(player.location, config.ui.errorSound, config.ui.errorVolume, config.ui.errorPitch)
    }
  }
}