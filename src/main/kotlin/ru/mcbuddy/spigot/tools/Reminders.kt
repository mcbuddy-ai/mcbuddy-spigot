package ru.mcbuddy.spigot.tools

import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** One player's pending reminders, apart from Bukkit so tests can reach it. The cap is read on every add, so `/ask reload` applies it. */
class ReminderBook(private val max: () -> Int, private val newId: () -> String = ::shortId) {
  class Reminder(val id: String, val text: String, val dueAt: Long, val cancel: () -> Unit)

  private val reminders = mutableListOf<Reminder>()

  @Synchronized fun add(text: String, dueAt: Long, schedule: (String) -> () -> Unit): Reminder {
    val cap = max()
    if (reminders.size >= cap) throw ToolError("this player already has $cap reminders pending; cancel one first")
    val id = generateSequence(newId).first { candidate -> reminders.none { it.id == candidate } }
    return Reminder(id, text, dueAt, schedule(id)).also { reminders += it }
  }

  /** Soonest first. */
  @Synchronized fun list(): List<Reminder> = reminders.sortedBy { it.dueAt }

  /** Drops a reminder that just fired, without cancelling its timer. */
  @Synchronized fun fired(id: String) {
    reminders.removeIf { it.id == id }
  }

  @Synchronized fun cancel(id: String): Int {
    val reminder = reminders.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: return 0
    reminders.remove(reminder)
    reminder.cancel()
    return 1
  }

  @Synchronized fun cancelAll(): Int {
    val count = reminders.size
    reminders.forEach { it.cancel() }
    reminders.clear()
    return count
  }

  companion object {
    private const val ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

    /** Four characters without look-alikes (l, 1, o, 0, i): easy to read back from chat. */
    fun shortId() = (1..4).map { ALPHABET.random() }.joinToString("")
  }
}

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
