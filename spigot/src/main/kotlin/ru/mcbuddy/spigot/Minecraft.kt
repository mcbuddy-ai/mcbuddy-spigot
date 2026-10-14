package ru.mcbuddy.spigot

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*

import org.bukkit.ChatColor.translateAlternateColorCodes

object Minecraft {
  fun formatMessage(message: String) = translateAlternateColorCodes('&', message)

  /** Stands in for a player's own words in a template; see [withText]. */
  const val SLOT = "\u0000"

  /** Colors the template, then puts the player's words in place of [SLOT], so an `&` they typed stays as typed. */
  fun withText(template: String, text: String) = formatMessage(template).replace(SLOT, text)
}
