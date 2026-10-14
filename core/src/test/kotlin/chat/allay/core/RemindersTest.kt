package chat.allay.core

import chat.allay.core.tools.ReminderBook
import chat.allay.core.tools.ToolError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemindersTest {
  private val cancelled = mutableListOf<String>()

  private fun book(max: Int, ids: List<String> = listOf("aaaa", "bbbb", "cccc", "dddd")): ReminderBook {
    val next = ids.iterator()
    return ReminderBook({ max }) { next.next() }
  }

  private fun ReminderBook.add(text: String, dueAt: Long) = add(text, dueAt) { id -> { cancelled += id } }

  @Test fun `reminders list soonest first`() {
    val book = book(10)
    book.add("печь", 3_000)
    book.add("ферма", 1_000)
    book.add("ужин", 2_000)
    assertEquals(listOf("bbbb", "cccc", "aaaa"), book.list().map { it.id })
  }

  @Test fun `the cap stops new reminders and follows the config`() {
    var max = 2
    val book = ReminderBook({ max }, ReminderBook::shortId)
    book.add("a", 1)
    book.add("b", 2)
    assertFailsWith<ToolError> { book.add("c", 3) }
    max = 3
    book.add("c", 3)
    assertEquals(3, book.list().size)
  }

  @Test fun `cancelling stops the timer, by id or all at once`() {
    val book = book(10)
    book.add("a", 1)
    book.add("b", 2)
    book.add("c", 3)
    assertEquals(1, book.cancel("BBBB"))
    assertEquals(0, book.cancel("zzzz"))
    assertEquals(listOf("bbbb"), cancelled)
    assertEquals(2, book.cancelAll())
    assertEquals(listOf("bbbb", "aaaa", "cccc"), cancelled)
    assertTrue(book.list().isEmpty())
  }

  @Test fun `a fired reminder leaves the list without a cancel`() {
    val book = book(10)
    book.add("a", 1)
    book.fired("aaaa")
    assertTrue(book.list().isEmpty())
    assertTrue(cancelled.isEmpty())
  }

  @Test fun `ids are short, readable, and unique within a player`() {
    val id = ReminderBook.shortId()
    assertEquals(4, id.length)
    assertTrue(id.none { it in "lo01i" }, id)
    val book = book(10, listOf("aaaa", "aaaa", "bbbb"))
    book.add("x", 1)
    assertEquals("bbbb", book.add("y", 2).id)
  }
}
