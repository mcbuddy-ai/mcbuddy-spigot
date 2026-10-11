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

  /** Tokens streamed so far, estimated from the deltas. */
  private val estimated = AtomicLong()

  /** Totals from the latest `usage` event; the backend sends one after every model step. */
  val input = AtomicLong()
  private val reported = AtomicLong()
  private val answer = StringBuilder()

  /** Calls whose `⏺` line is already in chat, so a `tool_status` and its `tool_call` don't print twice. */
  val announced: MutableSet<String> = ConcurrentHashMap.newKeySet()

  val elapsedMillis get() = System.currentTimeMillis() - startedAt

  fun addReasoning(delta: String) {
    estimated.addAndGet(estimate(delta))
  }

  fun addText(delta: String) = synchronized(answer) {
    answer.append(delta)
    phase = Phase.ANSWERING
    estimated.addAndGet(estimate(delta))
  }

  fun usage(inputTokens: Long, outputTokens: Long) {
    input.set(inputTokens)
    reported.set(outputTokens)
  }

  /** Tokens written so far: the live estimate until the backend's count catches up or passes it. */
  val output get() = maxOf(estimated.get(), reported.get())

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
