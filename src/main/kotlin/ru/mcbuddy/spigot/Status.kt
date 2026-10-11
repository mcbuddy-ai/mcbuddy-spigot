package ru.mcbuddy.spigot

import java.util.Locale

/** What a run is doing right now, as the action bar shows it. */
enum class Phase { THINKING, ANSWERING }

/** Claude Code's spinner: the glyphs grow, then shrink back, so the motion never jumps. */
class Spinner(frames: String) {
  private val sequence: List<String> = frames.codePoints().toArray().map { String(Character.toChars(it)) }.let { forward ->
    if (forward.size < 3) forward else forward + forward.drop(1).dropLast(1).reversed()
  }.ifEmpty { listOf("*") }

  fun frame(tick: Long): String = sequence[(tick % sequence.size).toInt()]

  val size get() = sequence.size
}

/** Builds the status line from a template; every piece of text stays configurable. */
class StatusFormat(
  private val template: String,
  private val answeringVerb: String,
  private val reasoningTemplate: String,
  private val reasoningLength: Int,
) {
  fun line(spinner: String, verb: String, phase: Phase, elapsedSeconds: Long, tokens: Long, reasoning: String?): String {
    val base = template
      .replace("{spinner}", spinner)
      .replace("{verb}", if (phase == Phase.ANSWERING) answeringVerb else verb)
      .replace("{elapsed}", "${elapsedSeconds}с")
      // "1.2k" reads as many, whatever the exact number ends with.
      .replace("{tokens}", "${compact(tokens)} ${if (tokens >= 1_000) "токенов" else Plurals.tokens(tokens)}")
    val tail = reasoning?.let(::lastWords)?.takeIf { it.isNotBlank() && phase == Phase.THINKING } ?: return base
    return base + reasoningTemplate.replace("{reasoning}", tail)
  }

  /** The end of the reasoning text, cut at a word boundary, so it reads like a live thought. */
  fun lastWords(text: String): String {
    val flat = text.replace(Regex("\\s+"), " ").trim()
    if (flat.length <= reasoningLength) return flat
    val cut = flat.takeLast(reasoningLength)
    return "…" + cut.substringAfter(' ', cut)
  }

  companion object {
    fun compact(n: Long): String = when {
      n < 1_000 -> n.toString()
      n < 10_000 -> String.format(Locale.ROOT, "%.1fk", n / 1_000.0)
      else -> "${n / 1_000}k"
    }
  }
}

/** Picks the verb for a moment in time; it changes every few seconds, never twice in a row the same. */
class Verbs(private val verbs: List<String>, private val intervalMillis: Long, seed: Int) {
  private val order = verbs.indices.shuffled(kotlin.random.Random(seed))

  fun at(elapsedMillis: Long): String {
    if (verbs.isEmpty()) return "Думаю"
    val step = (elapsedMillis / intervalMillis.coerceAtLeast(500)).toInt()
    return verbs[order[step % order.size]]
  }
}

object Plurals {
  /** Russian plural: 1 предмет, 2 предмета, 5 предметов. */
  fun ru(n: Long, one: String, few: String, many: String): String {
    val mod100 = n % 100
    val mod10 = n % 10
    return when {
      mod100 in 11..14 -> many
      mod10 == 1L -> one
      mod10 in 2..4 -> few
      else -> many
    }
  }

  fun tokens(n: Long) = ru(n, "токен", "токена", "токенов")
}
