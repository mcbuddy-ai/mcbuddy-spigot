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

/** Who asked. Tools see only this sender: the owner decided the agent may learn about the asker alone. */
class ToolContext(val sender: CommandSender, val structureRadius: Int, val plugin: Plugin, val reminders: Reminders) {
  val player: Player? get() = sender as? Player

  fun requirePlayer(): Player = player ?: throw ToolError("there is no player: the question came from the console")
}

/** A tool result for the model, plus a short Russian line for the chat (`⎿ 36 предметов`). */
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
    fun all(): List<ClientTool> = PlayerTools.all + LocationTools.all + WorldTools.all + KnowledgeTools.all + ActionTools.all + OperatorTools.all
  }
}

/** Chat labels for tools, with `{arg}` placeholders filled from the call's arguments. */
class ToolLabels(private val overrides: Map<String, String>) {
  fun label(name: String, args: JsonObject?): String {
    val template = overrides[name] ?: DEFAULTS[name] ?: "Вызываю $name"
    return Regex("\\{(\\w+)}").replace(template) { match ->
      (args?.get(match.groupValues[1]) as? JsonPrimitive)?.contentOrNull?.take(60) ?: ""
    }
  }

  companion object {
    val DEFAULTS = mapOf(
      "get_player_status" to "Смотрю, как ты",
      "get_inventory" to "Заглядываю в инвентарь",
      "get_surroundings" to "Осматриваюсь вокруг",
      "get_target" to "Смотрю, на что ты смотришь",
      "get_world" to "Смотрю на мир",
      "get_statistics" to "Листаю статистику",
      "get_advancements" to "Проверяю достижения",
      "get_recipes" to "Ищу рецепт: {item}",
      "locate_structure" to "Ищу ближайшую структуру: {structure}",
      "get_server" to "Узнаю о сервере",
      "get_locations" to "Вспоминаю важные места",
      "get_villager_trades" to "Смотрю, чем торгует житель",
      "point_to" to "Показываю дорогу",
      "sort_inventory" to "Навожу порядок в инвентаре",
      "set_reminder" to "Ставлю напоминание",
      "give_item" to "Выдаю {item}",
      "teleport" to "Телепортирую",
      "set_gamemode" to "Меняю режим игры: {mode}",
      "heal" to "Лечу",
      "apply_effect" to "Накладываю эффект {effect}",
      "set_time" to "Меняю время: {time}",
      "set_weather" to "Меняю погоду: {weather}",
      "search_web" to "Ищу в интернете: «{query}»",
      "exact_calculations" to "Считаю точно",
      "remember" to "Запоминаю",
      "forget" to "Забываю",
    )
  }
}
