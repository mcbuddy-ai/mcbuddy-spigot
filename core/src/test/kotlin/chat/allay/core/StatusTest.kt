package chat.allay.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class StatusTest {
  private val lang = Texts(mapOf("ru" to mapOf(
    "status.line" to "&6{spinner} &6{verb}… &7({seconds}с · ↑ {input} · ↓ {output} {tokens|токен|токена|токенов})",
    "status.answering" to "Пишу ответ",
    "status.verbs" to listOf("Думаю"),
  )), "ru").lang("ru")
  private val format = StatusFormat(lang)

  @Test fun `the spinner goes there and back without repeating the ends`() {
    val spinner = Spinner("·✢✳✶✻✽")
    assertEquals(listOf("·", "✢", "✳", "✶", "✻", "✽", "✻", "✶", "✳", "✢", "·"), (0L..10L).map(spinner::frame))
    assertEquals(10, spinner.size)
  }

  @Test fun `the status line reads like Claude Code`() {
    assertEquals("&6✻ &6Обдумываю… &7(12с · ↑ 2.4k · ↓ 340 токенов)", format.line("✻", "Обдумываю", Phase.THINKING, 12, 2_400, 340))
    assertEquals("&6✻ &6Пишу ответ… &7(3с · ↑ 12k · ↓ 1.2k токенов)", format.line("✻", "Обдумываю", Phase.ANSWERING, 3, 12_345, 1_234))
    assertEquals("&6· &6Колдую… &7(0с · ↑ 0 · ↓ 1 токен)", format.line("·", "Колдую", Phase.THINKING, 0, 0, 1))
  }

  @Test fun `output takes the larger of the estimate and the reported count`() {
    val run = Run(FakeAsker, Run.Mode.ASK, lang, 3)
    run.addText("12345678")
    assertEquals(2, run.output)
    run.usage(900, 40)
    assertEquals(900, run.input.get())
    assertEquals(40, run.output)
    run.addText("x".repeat(400))
    assertEquals(102, run.output)
  }

  @Test fun `token counts stay short`() {
    assertEquals("999", StatusFormat.compact(999))
    assertEquals("9.9k", StatusFormat.compact(9_949))
    assertEquals("12k", StatusFormat.compact(12_345))
  }

  @Test fun `plurals follow each language's rule`() {
    val ru = listOf("токен", "токена", "токенов")
    assertEquals(listOf("токен", "токена", "токенов", "токенов", "токен", "токена", "токенов"), listOf(1L, 3, 5, 11, 21, 104, 112).map { Plurals.pick("ru", it, ru) })
    assertEquals(listOf("token", "tokens", "tokens"), listOf(1L, 0, 2).map { Plurals.pick("en", it, listOf("token", "tokens")) })
    // Portuguese takes the singular for zero too.
    assertEquals(listOf("baú", "baú", "baús"), listOf(0L, 1, 2).map { Plurals.pick("pt", it, listOf("baú", "baús")) })
  }

  @Test fun `verbs change over time and cycle through all of them`() {
    val verbs = Verbs(listOf("a", "b", "c"), 3_000, seed = 7)
    assertEquals(verbs.at(0), verbs.at(2_999))
    assertNotEquals(verbs.at(0), verbs.at(3_000))
    assertEquals(setOf("a", "b", "c"), (0 until 3).map { verbs.at(it * 3_000L) }.toSet())
    assertEquals("…", Verbs(emptyList(), 3_000, 1).at(0))
  }
}
