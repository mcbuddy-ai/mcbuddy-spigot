package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Who asked. Tools see only this sender: the owner decided the agent may learn about the asker alone. */
class ToolContext(val sender: CommandSender, val structureRadius: Int) {
  val player: Player? get() = sender as? Player

  fun requirePlayer(): Player = player ?: throw ToolError("there is no player: the question came from the console")
}

/** A tool result for the model, plus a short Russian line for the chat (`⎿ 36 предметов`). */
class ToolOutput(val result: JsonElement, val summary: String)

class ToolError(message: String) : Exception(message)

/** A tool the backend can ask the plugin to run. Runs on the server thread. */
interface ClientTool {
  val name: String

  fun run(context: ToolContext, args: JsonObject): ToolOutput
}

/** The tools this plugin offers, minus the ones the admin switched off. */
class ToolRegistry(tools: List<ClientTool>, private val enabled: (String) -> Boolean) {
  private val byName = tools.associateBy { it.name }

  fun offered(): List<String> = byName.keys.filter(enabled)

  fun find(name: String): ClientTool? = byName[name]?.takeIf { enabled(it.name) }

  companion object {
    fun all(): List<ClientTool> = PlayerTools.all + WorldTools.all + KnowledgeTools.all
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
      "search_web" to "Ищу в интернете: «{query}»",
    )
  }
}
