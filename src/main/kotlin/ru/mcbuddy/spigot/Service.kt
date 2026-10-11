package ru.mcbuddy.spigot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Protocol.Event
import ru.mcbuddy.spigot.tools.ToolContext
import ru.mcbuddy.spigot.tools.ToolError
import ru.mcbuddy.spigot.tools.ToolRegistry
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Talks to the MCBuddy backend: the v2 stream with tools, or v1 when the backend is older. */
class Service(
  private val plugin: JavaPlugin,
  private val config: Config,
  private val client: OkHttpClient,
  private val tools: ToolRegistry,
  private val scope: CoroutineScope,
) {
  @Serializable data class AskRequest(val question: String, val platform: String = "minecraft", val user_id: String? = null)
  @Serializable data class AskResponse(val answer: String, val status: String? = null, val timestamp: String? = null)
  @Serializable data class AskXRequest(val action: String, val platform: String = "minecraft", val user_id: String? = null)
  @Serializable data class AskXResponse(val isSequence: Boolean, val commands: List<String>, val status: String? = null, val timestamp: String? = null, val error: String? = null)

  /** How a run ended. */
  data class Outcome(val answer: String, val commands: Protocol.Commands?)

  /** What the display needs to hear about while the stream runs. Called off the server thread. */
  interface Listener {
    fun toolStarted(callId: String, name: String, args: kotlinx.serialization.json.JsonObject?)
    fun toolFinished(callId: String, summary: String?, failed: Boolean)
  }

  private class V2Unsupported : Exception()

  // encodeDefaults sends platform: without it the backend files v1 requests under "unknown".
  private val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
  }
  private val errorParser = ErrorParser()
  private val jsonType = "application/json".toMediaType()

  suspend fun chat(run: Run, message: String, listener: Listener): Outcome = withContext(IO) {
    try {
      stream(run, message, listener)
    } catch (_: V2Unsupported) {
      plugin.logger.info("ℹ️ The backend has no /api/v2/chat; using v1")
      legacy(run, message)
    }
  }

  private fun stream(run: Run, message: String, listener: Listener): Outcome {
    val body = Protocol.json.encodeToString(Protocol.ChatRequest.serializer(), request(run, message))
    val call = client.newCall(
      Request.Builder()
        .url("${config.server.url}/api/v2/chat")
        .header("Accept", "text/event-stream")
        .header("X-Plugin-Version", plugin.description.version)
        .post(body.toRequestBody(jsonType))
        .build(),
    )
    run.call = call
    call.execute().use { response ->
      if (response.code == 404 || response.code == 405) throw V2Unsupported()
      if (!response.isSuccessful) throw ApiErrorException(errorParser.parseErrorFromResponse(response))

      var outcome: Outcome? = null
      var commands: Protocol.Commands? = null
      var failure: Protocol.ErrorEvent? = null
      val parser = SseParser { name, data ->
        when (val event = Protocol.decode(name, data)) {
          is Event.Started -> run.runId = event.data.run_id
          is Event.Reasoning -> run.addReasoning(event.delta)
          is Event.Text -> run.addText(event.delta)
          is Event.Status -> status(run, event.data, listener)
          is Event.Call -> {
            if (run.announced.add(event.data.call_id)) listener.toolStarted(event.data.call_id, event.data.name, event.data.args)
            scope.launch { answerTool(run, event.data, listener) }
          }
          is Event.Command -> commands = event.data
          is Event.Spent -> run.tokens.set(event.data.output_tokens + event.data.reasoning_tokens)
          is Event.Finished -> outcome = Outcome(event.data.answer.ifBlank { run.answer() }, commands)
          is Event.Failed -> failure = event.data
          null -> {}
        }
      }
      val source = response.body.source()
      while (!run.cancelled && outcome == null && failure == null) {
        val line = source.readUtf8Line() ?: break
        parser.line(line)
      }
      parser.end()
      failure?.let { throw ApiErrorException(ApiError(it.code, it.message)) }
      return outcome ?: Outcome(run.answer(), commands).takeIf { it.answer.isNotBlank() || commands != null }
        ?: throw ApiErrorException(ApiError("NO_AI_RESPONSE", "The stream ended without an answer"))
    }
  }

  private fun status(run: Run, status: Protocol.ToolStatus, listener: Listener) {
    when (status.state) {
      "started" -> if (run.announced.add(status.call_id)) listener.toolStarted(status.call_id, status.name, status.args)
      // The plugin reports its own tools when it posts their results; only server tools finish here.
      "finished", "failed" -> if (status.kind == "server") listener.toolFinished(status.call_id, status.summary, status.state == "failed")
    }
  }

  private fun request(run: Run, message: String): Protocol.ChatRequest {
    val player = run.sender as? Player
    val server = plugin.server
    return Protocol.ChatRequest(
      mode = run.mode.wire,
      message = message,
      user_id = player?.uniqueId?.toString() ?: "console",
      locale = player?.locale,
      player = player?.let { Protocol.PlayerInfo(it.name, it.uniqueId.toString(), it.isOp) },
      server = Protocol.ServerInfo(server.name, ru.mcbuddy.spigot.tools.WorldTools.version(server.bukkitVersion), plugin.description.version),
      tools = tools.offered(),
    )
  }

  /** Runs a client tool on the server thread and posts what came out; failures go back as errors, never as exceptions. */
  private fun answerTool(run: Run, call: Protocol.ToolCall, listener: Listener) {
    val tool = tools.find(call.name)
    val context = ToolContext(run.sender, config.tools.structureRadius)
    val result = if (tool == null) {
      Protocol.ToolResult(ok = false, error = "the tool ${call.name} is switched off on this server")
    } else {
      try {
        val output = plugin.server.scheduler.callSyncMethod(plugin) { tool.run(context, call.args) }.get(config.http.toolTimeout, TimeUnit.SECONDS)
        listener.toolFinished(call.call_id, output.summary, failed = false)
        Protocol.ToolResult(ok = true, result = output.result)
      } catch (error: Exception) {
        val cause = generateSequence<Throwable>(error) { it.cause }.firstOrNull { it is ToolError } ?: generateSequence<Throwable>(error) { it.cause }.last()
        val reason = when (cause) {
          is ToolError -> cause.message ?: "failed"
          is TimeoutException -> "the server took too long"
          else -> "failed: ${cause.javaClass.simpleName}".also { plugin.logger.warning("❌ Tool ${call.name} failed: $cause") }
        }
        listener.toolFinished(call.call_id, reason, failed = true)
        Protocol.ToolResult(ok = false, error = reason)
      }
    }
    val runId = run.runId ?: return
    if (run.cancelled) return
    runCatching {
      client.newCall(
        Request.Builder()
          .url("${config.server.url}/api/v2/chat/$runId/tools/${call.call_id}")
          .post(Protocol.json.encodeToString(Protocol.ToolResult.serializer(), result).toRequestBody(jsonType))
          .build(),
      ).execute().close()
    }.onFailure { plugin.logger.warning("❌ Couldn't send the result of ${call.name}: $it") }
  }

  /** The v1 API: one request, one answer, no tools. Kept for backends that predate v2. */
  private fun legacy(run: Run, message: String): Outcome {
    val userId = (run.sender as? Player)?.uniqueId?.toString() ?: "console"
    return when (run.mode) {
      Run.Mode.ASK -> {
        val body = json.encodeToString(AskRequest.serializer(), AskRequest(message, user_id = userId))
        val answer = execute(run, "/api/ask", body) { json.decodeFromString<AskResponse>(it).answer }
        Outcome(answer, null)
      }
      Run.Mode.ASKX -> {
        val body = json.encodeToString(AskXRequest.serializer(), AskXRequest(message, user_id = userId))
        val response = execute(run, "/api/askx", body) { json.decodeFromString<AskXResponse>(it) }
        response.error?.let { throw ApiErrorException(ApiError("AI_ERROR", it)) }
        Outcome("", Protocol.Commands(response.isSequence, response.commands))
      }
    }
  }

  private fun <T> execute(run: Run, path: String, body: String, read: (String) -> T): T {
    val call = client.newCall(Request.Builder().url("${config.server.url}$path").post(body.toRequestBody(jsonType)).build())
    run.call = call
    call.execute().use { response ->
      if (!response.isSuccessful) throw ApiErrorException(errorParser.parseErrorFromResponse(response))
      return read(response.body.string())
    }
  }

}
