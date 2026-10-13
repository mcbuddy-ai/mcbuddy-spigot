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

/** What tools keep between calls, built once and shared by every run. Limits are read on use, so `/ask reload` applies them. */
class Features(
  val plugin: Plugin,
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
  val plugin: Plugin get() = features.plugin
  val reminders: Reminders get() = features.reminders
  val notes: Notes get() = features.notes
  val structureRadius: Int get() = features.structureRadius()
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
    fun all(): List<ClientTool> = PlayerTools.all + LocationTools.all + WorldTools.all + KnowledgeTools.all + InspectTools.all + CareTools.all +
      NotebookTools.all + ActionTools.all + InteractTools.all + GoalTools.all + OperatorTools.all
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
      "get_reminders" to "Смотрю напоминания",
      "cancel_reminder" to "Отменяю напоминание",
      "add_note" to "Записываю в блокнот",
      "find_notes" to "Листаю блокнот",
      "delete_note" to "Вычёркиваю из блокнота",
      "get_container" to "Заглядываю в контейнер",
      "scan_nearby_containers" to "Обыскиваю сундуки рядом",
      "get_block_info" to "Изучаю блок",
      "get_item_durability" to "Проверяю износ снаряжения",
      "get_xp_info" to "Считаю опыт",
      "get_food_options" to "Смотрю, что можно съесть",
      "ping_me" to "Подаю сигнал",
      "get_map_info" to "Разглядываю карту",
      "get_enchant_options" to "Осматриваю стол зачарований",
      "get_mob_info" to "Присматриваюсь к мобу",
      "get_light_spawn_check" to "Ищу тёмные места",
      "get_brewing_items" to "Проверяю запасы для зелий",
      "scan_farm" to "Осматриваю ферму",
      "locate_biome" to "Ищу ближайший биом: {biome}",
      "get_death_info" to "Вспоминаю, где ты погиб",
      "find_villagers" to "Обхожу жителей рядом",
      "get_anvil_plan" to "Прикидываю порядок на наковальне",
      "inspect_item" to "Разглядываю предмет",
      "add_buttons" to "Готовлю кнопки",
      "write_book" to "Пишу книгу: «{title}»",
      "set_goal" to "Ставлю цель: {title}",
      "get_goals" to "Смотрю цели",
      "cancel_goal" to "Снимаю цель",
      "set_timezone" to "Запоминаю часовой пояс: {zone}",
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
