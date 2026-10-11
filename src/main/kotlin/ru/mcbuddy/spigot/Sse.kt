package ru.mcbuddy.spigot

/**
 * Parses a Server-Sent Events stream line by line: `event:` and `data:` fields, a blank line
 * ends an event, lines starting with `:` are comments (the backend's pings).
 */
class SseParser(private val onEvent: (name: String, data: String) -> Unit) {
  private var name = "message"
  private val data = StringBuilder()

  fun line(raw: String) {
    val line = raw.removeSuffix("\r")
    when {
      line.isEmpty() -> dispatch()
      line.startsWith(":") -> Unit
      else -> {
        val field = line.substringBefore(':')
        val value = line.substringAfter(':', "").removePrefix(" ")
        when (field) {
          "event" -> name = value
          "data" -> {
            if (data.isNotEmpty()) data.append('\n')
            data.append(value)
          }
        }
      }
    }
  }

  /** Flushes an event that the stream ended without a blank line after. */
  fun end() = dispatch()

  private fun dispatch() {
    if (data.isNotEmpty()) onEvent(name, data.toString())
    name = "message"
    data.setLength(0)
  }
}
