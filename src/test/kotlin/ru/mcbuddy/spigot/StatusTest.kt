package ru.mcbuddy.spigot

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StatusTest {
  private val format = StatusFormat("&6{spinner} &6{verb}… &7({elapsed} · ↓ {tokens})", "Пишу ответ", " &8&o{reasoning}", 20)

  @Test fun `the spinner goes there and back without repeating the ends`() {
    val spinner = Spinner("·✢✳✶✻✽")
    assertEquals(listOf("·", "✢", "✳", "✶", "✻", "✽", "✻", "✶", "✳", "✢", "·"), (0L..10L).map(spinner::frame))
    assertEquals(10, spinner.size)
  }

  @Test fun `the status line reads like Claude Code`() {
    assertEquals("&6✻ &6Обдумываю… &7(12с · ↓ 340 токенов)", format.line("✻", "Обдумываю", Phase.THINKING, 12, 340, null))
    assertEquals("&6✻ &6Пишу ответ… &7(3с · ↓ 1.2k токенов)", format.line("✻", "Обдумываю", Phase.ANSWERING, 3, 1_234, "ignored while answering"))
    assertEquals("&6· &6Колдую… &7(0с · ↓ 1 токен)", format.line("·", "Колдую", Phase.THINKING, 0, 1, ""))
  }

  @Test fun `reasoning shows its last words`() {
    val line = format.line("✻", "Думаю", Phase.THINKING, 1, 2, "Игроку нужен незерит, значит сначала древние обломки")
    assertTrue(line.endsWith(" &8&o…древние обломки"), line)
  }

  @Test fun `token counts stay short`() {
    assertEquals("999", StatusFormat.compact(999))
    assertEquals("9.9k", StatusFormat.compact(9_949))
    assertEquals("12k", StatusFormat.compact(12_345))
  }

  @Test fun `russian plurals`() {
    assertEquals(listOf("токен", "токена", "токенов", "токенов", "токен", "токена"), listOf(1L, 3, 5, 11, 21, 104).map(Plurals::tokens))
  }

  @Test fun `verbs change over time and cycle through all of them`() {
    val verbs = Verbs(listOf("a", "b", "c"), 3_000, seed = 7)
    assertEquals(verbs.at(0), verbs.at(2_999))
    assertNotEquals(verbs.at(0), verbs.at(3_000))
    assertEquals(setOf("a", "b", "c"), (0 until 3).map { verbs.at(it * 3_000L) }.toSet())
    assertEquals("Думаю", Verbs(emptyList(), 3_000, 1).at(0))
  }
}
