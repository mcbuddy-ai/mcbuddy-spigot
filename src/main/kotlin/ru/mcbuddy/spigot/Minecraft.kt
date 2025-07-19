package ru.mcbuddy.spigot

import org.bukkit.ChatColor.translateAlternateColorCodes

object Minecraft {
  fun formatMessage(message: String) = translateAlternateColorCodes('&', message)
}