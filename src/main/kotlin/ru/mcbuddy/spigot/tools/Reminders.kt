package ru.mcbuddy.spigot.tools

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import ru.mcbuddy.spigot.Minecraft.formatMessage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Reminders the assistant set for players. They live in memory: a restart or the player leaving drops them. */
class Reminders(private val plugin: Plugin, private val template: () -> String) : Listener {
  private val pending = ConcurrentHashMap<UUID, MutableList<BukkitTask>>()

  fun add(player: Player, minutes: Int, text: String) {
    val tasks = pending.computeIfAbsent(player.uniqueId) { mutableListOf() }
    synchronized(tasks) {
      tasks.removeIf { it.isCancelled || !plugin.server.scheduler.isQueued(it.taskId) }
      if (tasks.size >= MAX_PER_PLAYER) throw ToolError("this player already has $MAX_PER_PLAYER reminders pending")
      lateinit var task: BukkitTask
      task = plugin.server.scheduler.runTaskLater(plugin, Runnable {
        synchronized(tasks) { tasks.remove(task) }
        if (!player.isOnline) return@Runnable
        player.sendMessage(formatMessage(template().replace("{text}", text)))
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f)
      }, minutes * 1_200L)
      tasks += task
    }
  }

  fun cancelAll() {
    pending.values.forEach { tasks -> synchronized(tasks) { tasks.forEach(BukkitTask::cancel) } }
    pending.clear()
  }

  @EventHandler
  fun onQuit(event: PlayerQuitEvent) {
    pending.remove(event.player.uniqueId)?.let { tasks -> synchronized(tasks) { tasks.forEach(BukkitTask::cancel) } }
  }

  companion object {
    const val MAX_PER_PLAYER = 3
  }
}
