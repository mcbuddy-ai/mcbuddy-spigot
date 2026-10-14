package chat.allay.core

import kotlin.test.Test
import kotlin.test.assertEquals

class SseParserTest {
  private fun parse(vararg lines: String): List<Pair<String, String>> {
    val events = mutableListOf<Pair<String, String>>()
    val parser = SseParser { name, data -> events += name to data }
    lines.forEach(parser::line)
    parser.end()
    return events
  }

  @Test fun `events split on blank lines`() {
    assertEquals(
      listOf("start" to """{"run_id":"r"}""", "text" to """{"delta":"hi"}"""),
      parse("event: start", """data: {"run_id":"r"}""", "", "event: text", """data: {"delta":"hi"}""", ""),
    )
  }

  @Test fun `comments and pings are skipped`() {
    assertEquals(listOf("done" to "{}"), parse(": ping", "", "event: done", "data: {}", ""))
  }

  @Test fun `multi-line data joins with newlines and CRLF is tolerated`() {
    assertEquals(listOf("message" to "a\nb"), parse("data: a\r", "data: b\r", "\r"))
  }

  @Test fun `an event without a trailing blank line still arrives`() {
    assertEquals(listOf("done" to "{}"), parse("event: done", "data: {}"))
  }

  @Test fun `the space after the colon is optional`() {
    assertEquals(listOf("text" to "x"), parse("event:text", "data:x", ""))
  }
}
