package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.mcbuddy.spigot.Lang

/** What tools keep between calls, built once and shared by every run. Limits are read on use, so `/ask reload` applies them. */
class Features(
  val plugin: Plugin,
  val locales: ru.mcbuddy.spigot.Locales,
  val reminders: Reminders,
  val notes: Notes,
  val goals: Goals,
  val books: Books,
  val deaths: Deaths,
  val zones: TimeZones,
  val structureRadius: () -> Int,
  val biomeRadius: () -> Int,
  val villagerRadius: () -> Int,
)

/**
 * Who asked. Tools see only this sender: the owner decided the agent may learn about the asker alone.
 * [buttons] collects what goes under the answer; the handler shows them once the answer is out.
 */
class ToolContext(val sender: CommandSender, val features: Features, val buttons: MutableList<Button> = java.util.Collections.synchronizedList(mutableListOf())) {
  /** The asker's language, for the summaries under tool lines. */
  val lang: Lang get() = features.locales.of(sender)

  val plugin: Plugin get() = features.plugin
  val reminders: Reminders get() = features.reminders
  val notes: Notes get() = features.notes
  val structureRadius: Int get() = features.structureRadius()
  val player: Player? get() = sender as? Player

  fun requirePlayer(): Player = player ?: throw ToolError("there is no player: the question came from the console")
}

/** A tool result for the model, plus a short line for the chat in the asker's language (`⎿ 36 stacks`). */
class ToolOutput(val result: JsonElement, val summary: String)

class ToolError(message: String) : Exception(message)

internal fun JsonObject.int(key: String) = (get(key) as? JsonPrimitive)?.let { it.intOrNull ?: it.doubleOrNull?.toInt() }
internal fun JsonObject.double(key: String) = (get(key) as? JsonPrimitive)?.doubleOrNull
internal fun JsonObject.bool(key: String) = (get(key) as? JsonPrimitive)?.booleanOrNull
internal fun JsonObject.string(key: String) = (get(key) as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.ifEmpty { null }

/** A tool the backend can ask the plugin to run. Runs on the server thread. */
interface ClientTool {
  val name: String

  /** Changes the game for the asker; offered to operators only. */
  val operator: Boolean get() = false

  fun run(context: ToolContext, args: JsonObject): ToolOutput
}

/** The tools this plugin offers, minus the ones the admin switched off. */
class ToolRegistry(tools: List<ClientTool>, private val enabled: (String) -> Boolean) {
  private val byName = tools.associateBy { it.name }

  /** What the asker may use: operator tools only for operators. */
  fun offered(operator: Boolean): List<String> = byName.values.filter { enabled(it.name) && (operator || !it.operator) }.map { it.name }

  fun find(name: String): ClientTool? = byName[name]?.takeIf { enabled(it.name) }

  companion object {
    fun all(): List<ClientTool> = PlayerTools.all + LookTools.all + LocationTools.all + WorldTools.all + KnowledgeTools.all + InspectTools.all + CareTools.all +
      NotebookTools.all + ActionTools.all + InteractTools.all + GoalTools.all + OperatorTools.all
  }
}

/** Chat labels for tools from `tools.<name>` in the reader's language, with `{arg}` placeholders filled from the call's arguments. */
object ToolLabels {
  fun label(lang: Lang, name: String, args: JsonObject?): String {
    val values = args.orEmpty().mapNotNull { (key, value) -> (value as? JsonPrimitive)?.contentOrNull?.take(60)?.let { key to it } }
    // Tools with an action (reminders, goals) take a label per action, list when the call names none: tools.reminders.add.
    val action = values.toMap()["action"]?.lowercase() ?: "list"
    val key = listOf("tools.$name.$action", "tools.$name").firstOrNull(lang::has)
    val text = if (key != null) lang.text(key, *values.toTypedArray()) else lang.text("tool_lines.unknown", "name" to name)
    // An argument the call didn't pass, or one that isn't a plain value, leaves no trace.
    return Regex("\\{\\w+}").replace(text, "")
  }
}
