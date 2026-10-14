package ru.mcbuddy.spigot.tools

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*
import ru.mcbuddy.spigot.send

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Reminders the assistant set for players. They live in memory: a restart or the player leaving drops them. */
class Reminders(private val plugin: Plugin, private val locales: ru.mcbuddy.spigot.Locales, private val maxPending: () -> Int) : Listener {
  private val books = ConcurrentHashMap<UUID, ReminderBook>()

  fun of(player: Player): ReminderBook = books.computeIfAbsent(player.uniqueId) { ReminderBook(maxPending) }

  fun add(player: Player, minutes: Int, text: String): ReminderBook.Reminder {
    val book = of(player)
    return book.add(text, System.currentTimeMillis() + minutes * 60_000L) { id ->
      val task = plugin.server.scheduler.runTaskLater(plugin, Runnable {
        book.fired(id)
        if (!player.isOnline) return@Runnable
        locales.of(player).send(player, "messages.reminder", "text" to text)
        player.playSound(player.location, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.2f)
      }, minutes * 1_200L)
      task::cancel
    }
  }

  fun cancelAll() {
    books.values.forEach(ReminderBook::cancelAll)
    books.clear()
  }

  @EventHandler
  fun onQuit(event: PlayerQuitEvent) {
    books.remove(event.player.uniqueId)?.cancelAll()
  }
}
