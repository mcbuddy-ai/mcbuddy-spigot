package ru.mcbuddy.core.tools

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** A long answer MCBuddy wrote as a book, when the player asked for detail. */
class WrittenBook(val id: String, val title: String, val pages: List<String>)

/** Books per player, the last few only; they live in memory, like reminders. */
class Books(private val keep: Int = 10) {
  private val players = ConcurrentHashMap<UUID, ArrayDeque<WrittenBook>>()

  fun add(player: UUID, title: String, text: String): WrittenBook {
    val shelf = players.computeIfAbsent(player) { ArrayDeque() }
    synchronized(shelf) {
      val id = generateSequence(ReminderBook::shortId).first { candidate -> shelf.none { it.id == candidate } }
      val book = WrittenBook(id, title, paginate(text))
      shelf.addLast(book)
      while (shelf.size > keep) shelf.removeFirst()
      return book
    }
  }

  fun find(player: UUID, id: String): WrittenBook? = players[player]?.let { shelf -> synchronized(shelf) { shelf.firstOrNull { it.id == id } } }

  fun forget(player: UUID) {
    players.remove(player)
  }

  companion object {
    /** A page holds about 14 lines of 19 characters; staying under both keeps words from spilling off the page. */
    const val PAGE_CHARS = 230
    const val PAGE_LINES = 13
    const val MAX_PAGES = 50

    /** Splits text into pages at line and word breaks; a paragraph never starts at the bottom of a page if it can move. */
    fun paginate(text: String): List<String> {
      val pages = mutableListOf<String>()
      val page = StringBuilder()
      var lines = 0
      fun flush() {
        if (page.isNotBlank()) pages += page.toString().trimEnd()
        page.clear()
        lines = 0
      }
      for (paragraph in text.replace("\r", "").trim().split("\n")) {
        val words = paragraph.split(" ").filter { it.isNotEmpty() }
        // A visual line is about 19 characters wide.
        val needed = maxOf(1, (paragraph.length + 18) / 19)
        if (lines > 0 && (lines + needed > PAGE_LINES || page.length + paragraph.length + 1 > PAGE_CHARS) && needed <= PAGE_LINES) flush()
        for (word in words) {
          if (page.length + word.length + 1 > PAGE_CHARS) flush()
          if (page.isNotEmpty() && !page.endsWith("\n")) page.append(' ')
          page.append(word)
        }
        page.append('\n')
        lines += needed
        if (lines >= PAGE_LINES) flush()
      }
      flush()
      return pages.take(MAX_PAGES).ifEmpty { listOf("") }
    }
  }
}
