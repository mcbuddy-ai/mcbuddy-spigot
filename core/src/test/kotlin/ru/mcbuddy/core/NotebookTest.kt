package ru.mcbuddy.core

import ru.mcbuddy.core.tools.Note
import ru.mcbuddy.core.tools.NoteBook
import ru.mcbuddy.core.tools.ToolError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotebookTest {
  private var counter = 0
  private fun book(max: Int = 200) = NoteBook(emptyList(), { max }) { "n${counter++}" }

  private fun NoteBook.note(text: String, x: Int? = null, z: Int? = null, at: Long = 0) =
    add { id -> Note(id, text, x, if (x != null) 64 else null, z, if (x != null) "world" else null, if (x != null) "normal" else null, at) }

  private val fromOrigin = { note: Note -> note.x?.let { Math.hypot(it.toDouble(), note.z!!.toDouble()) } }

  @Test fun `the cap stops new notes`() {
    val book = book(max = 2)
    book.note("one")
    book.note("two")
    assertFailsWith<ToolError> { book.note("three") }
  }

  @Test fun `a query matches text in any case, nearest first`() {
    val book = book()
    book.note("Житель продаёт СТРЕЛЫ", x = 100, z = 0)
    book.note("ферма тыкв", x = 5, z = 5)
    book.note("ещё стрелы у реки", x = 10, z = 0)
    val found = book.find("стрелы", fromOrigin, limit = 20)
    assertTrue(found.matched)
    assertEquals(listOf("ещё стрелы у реки", "Житель продаёт СТРЕЛЫ"), found.notes.map { it.text })
  }

  @Test fun `no match returns everything so the model can match by meaning`() {
    val book = book()
    book.note("fletcher with arrows", x = 50, z = 0)
    book.note("base", x = 1, z = 1)
    book.note("a thought without a place", at = 5)
    val found = book.find("лучник", fromOrigin, limit = 20)
    assertFalse(found.matched)
    assertEquals(3, found.total)
    assertEquals(listOf("base", "fletcher with arrows", "a thought without a place"), found.notes.map { it.text })
    assertEquals(2, book.find(null, fromOrigin, limit = 2).notes.size)
  }

  @Test fun `delete ignores case and reports misses`() {
    val book = book()
    val note = book.note("x")
    assertFalse(book.delete("nope"))
    assertTrue(book.delete(note.id.uppercase()))
    assertEquals(0, book.all().size)
  }
}
