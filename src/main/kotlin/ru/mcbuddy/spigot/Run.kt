package ru.mcbuddy.spigot

import okhttp3.Call
import org.bukkit.command.CommandSender
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** One question in flight: what the display shows and what cancelling stops. Touched from several threads. */
class Run(val sender: CommandSender, val mode: Mode, verbs: List<String>, verbSeconds: Long) {
  enum class Mode(val wire: String) { ASK("ask"), ASKX("askx") }

  val startedAt = System.currentTimeMillis()
  val verbs = Verbs(verbs, verbSeconds * 1000, startedAt.toInt())

  @Volatile var phase = Phase.THINKING
  @Volatile var runId: String? = null
  @Volatile var call: Call? = null
  @Volatile var cancelled = false

  /** Tokens streamed so far; replaced by the exact count when the `usage` event arrives. */
  val tokens = AtomicLong()
  private val reasoning = StringBuilder()
  private val answer = StringBuilder()

  /** Calls whose `⏺` line is already in chat, so a `tool_status` and its `tool_call` don't print twice. */
  val announced: MutableSet<String> = ConcurrentHashMap.newKeySet()

  val elapsedMillis get() = System.currentTimeMillis() - startedAt

  fun addReasoning(delta: String) = synchronized(reasoning) {
    reasoning.append(delta)
    if (reasoning.length > 600) reasoning.delete(0, reasoning.length - 600)
    tokens.addAndGet(estimate(delta))
  }

  fun reasoningTail(): String = synchronized(reasoning) { reasoning.toString() }

  fun addText(delta: String) = synchronized(answer) {
    answer.append(delta)
    phase = Phase.ANSWERING
    tokens.addAndGet(estimate(delta))
  }

  fun answer(): String = synchronized(answer) { answer.toString() }

  fun cancel() {
    cancelled = true
    call?.cancel()
  }

  private companion object {
    /** About four characters a token: enough for a live counter until the real usage arrives. */
    fun estimate(text: String) = ((text.length + 3) / 4).toLong()
  }
}
