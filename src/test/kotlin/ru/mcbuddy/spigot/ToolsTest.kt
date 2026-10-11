package ru.mcbuddy.spigot

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import ru.mcbuddy.spigot.tools.ClientTool
import ru.mcbuddy.spigot.tools.Facts
import ru.mcbuddy.spigot.tools.OperatorTools
import ru.mcbuddy.spigot.tools.ToolContext
import ru.mcbuddy.spigot.tools.ToolLabels
import ru.mcbuddy.spigot.tools.ToolOutput
import ru.mcbuddy.spigot.tools.ToolRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolsTest {
  private fun fake(name: String, operator: Boolean = false) = object : ClientTool {
    override val name = name
    override val operator = operator
    override fun run(context: ToolContext, args: JsonObject) = ToolOutput(JsonPrimitive(name), name)
  }

  @Test fun `the plugin offers exactly the tools of the protocol`() {
    val names = ToolRegistry.all().map { it.name }.toSet()
    assertEquals(
      setOf("get_player_status", "get_inventory", "get_surroundings", "get_target", "get_world", "get_statistics", "get_advancements", "get_recipes", "locate_structure", "get_server", "get_locations", "get_villager_trades", "point_to", "sort_inventory", "set_reminder", "get_reminders", "cancel_reminder", "add_note", "find_notes", "delete_note", "get_container", "scan_nearby_containers", "get_block_info", "get_map_info", "get_enchant_options", "get_mob_info", "get_light_spawn_check", "get_brewing_items", "scan_farm", "get_item_durability", "get_xp_info", "get_food_options",
        "give_item", "teleport", "set_gamemode", "heal", "apply_effect", "set_time", "set_weather"),
      names,
    )
  }

  @Test fun `switched off tools are neither offered nor runnable`() {
    val registry = ToolRegistry(listOf(fake("get_world"), fake("locate_structure"))) { it != "locate_structure" }
    assertEquals(listOf("get_world"), registry.offered(operator = true))
    assertNull(registry.find("locate_structure"))
    assertNull(registry.find("unknown"))
    assertEquals("get_world", registry.find("get_world")?.name)
  }

  @Test fun `operator tools are offered to operators only`() {
    val registry = ToolRegistry(listOf(fake("get_world"), fake("give_item", operator = true), fake("heal", operator = true))) { it != "heal" }
    assertEquals(listOf("get_world"), registry.offered(operator = false))
    assertEquals(listOf("get_world", "give_item"), registry.offered(operator = true))
  }

  @Test fun `every tool that changes the game is an operator tool`() {
    val operator = ToolRegistry.all().filter { it.operator }.map { it.name }.toSet()
    assertEquals(setOf("give_item", "teleport", "set_gamemode", "heal", "apply_effect", "set_time", "set_weather"), operator)
    assertEquals(OperatorTools.NAMES, operator)
  }

  @Test fun `other players collapse into a count`() {
    val result = Facts.entities(
      listOf(
        Facts.Seen("zombie", 12.34, hostile = true, player = false),
        Facts.Seen("zombie", 5.0, hostile = true, player = false),
        Facts.Seen("cow", 3.0, hostile = false, player = false),
        Facts.Seen("player", 2.0, hostile = false, player = true),
        Facts.Seen("player", 9.0, hostile = false, player = true),
      ),
    )
    assertEquals(2, result["other_players"]!!.jsonPrimitive.int)
    val entities = result["entities"] as JsonArray
    assertEquals(listOf("cow", "zombie"), entities.map { it.jsonObject["type"]!!.jsonPrimitive.content })
    assertEquals(2, entities[1].jsonObject["count"]!!.jsonPrimitive.int)
    assertEquals("5.0", entities[1].jsonObject["nearest"]!!.jsonPrimitive.content)
    assertEquals(setOf("entities", "other_players"), result.keys, "nothing about other players may appear besides the count")
    assertFalse(entities.any { "player" in it.toString() }, "players must not show up as an entity group: $result")
    assertTrue(entities[1].jsonObject.containsKey("hostile"))
  }

  @Test fun `compass directions follow Minecraft axes`() {
    assertEquals("north", Facts.direction(0.0, -10.0))
    assertEquals("east", Facts.direction(10.0, 0.0))
    assertEquals("south-west", Facts.direction(-5.0, 5.0))
  }

  @Test fun `day and moon phases`() {
    assertEquals("day", Facts.dayPhase(1_000))
    assertEquals("sunset", Facts.dayPhase(12_500))
    assertEquals("night", Facts.dayPhase(18_000))
    assertEquals("sunrise", Facts.dayPhase(23_000))
    assertEquals("full", Facts.moonPhase(0))
    assertEquals("new", Facts.moonPhase(4 * 24_000L + 100))
  }

  @Test fun `labels fill in arguments and fall back to defaults`() {
    val labels = ToolLabels(mapOf("get_inventory" to "Роюсь в карманах"))
    assertEquals("Роюсь в карманах", labels.label("get_inventory", null))
    assertEquals("Ищу в интернете: «рецепт маяка»", labels.label("search_web", buildJsonObject { put("query", "рецепт маяка") }))
    assertEquals("Ищу рецепт: ", labels.label("get_recipes", null))
    assertEquals("Вызываю new_tool", labels.label("new_tool", null))
    // Server tools may come with arrays; a placeholder that isn't a plain value just stays empty.
    assertEquals("Считаю точно", labels.label("exact_calculations", buildJsonObject { put("expressions", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("2+2")))) }))
    assertEquals("Ищу рецепт: ", ToolLabels(mapOf("get_recipes" to "Ищу рецепт: {item}")).label("get_recipes", buildJsonObject { put("item", kotlinx.serialization.json.JsonArray(emptyList())) }))
  }

  @Test fun `server versions read the same on Spigot and Paper`() {
    assertEquals("26.3", ru.mcbuddy.spigot.tools.WorldTools.version("26.3-R0.1-SNAPSHOT"))
    assertEquals("26.3", ru.mcbuddy.spigot.tools.WorldTools.version("26.3.build.169-beta"))
    assertEquals("26.1.2", ru.mcbuddy.spigot.tools.WorldTools.version("26.1.2-R0.1-SNAPSHOT"))
  }
}
