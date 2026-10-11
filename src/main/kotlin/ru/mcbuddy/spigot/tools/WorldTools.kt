package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.bukkit.Bukkit
import org.bukkit.Registry
import org.bukkit.World
import ru.mcbuddy.spigot.Plurals
import ru.mcbuddy.spigot.tools.Facts.round1

/** Tools about the world around the asker and the server itself. */
object WorldTools {
  /** Rules that change advice; names differ between versions, so both spellings are tried. */
  private val RULES = setOf(
    "keep_inventory", "keepInventory", "mob_griefing", "mobGriefing", "players_sleeping_percentage", "playersSleepingPercentage",
    "do_insomnia", "doInsomnia", "spawn_phantoms", "natural_regeneration", "naturalRegeneration", "do_immediate_respawn", "doImmediateRespawn",
    "advance_time", "do_daylight_cycle", "doDaylightCycle", "advance_weather", "do_weather_cycle", "doWeatherCycle", "fall_damage", "fallDamage",
  )

  private fun worldOf(context: ToolContext): World = context.player?.world ?: Bukkit.getWorlds().first()

  private val world = object : ClientTool {
    override val name = "get_world"

    override fun run(context: ToolContext, args: JsonObject): ToolOutput {
      val world = worldOf(context)
      val day = world.fullTime / 24_000 + 1
      val phase = Facts.dayPhase(world.time)
      val result = buildJsonObject {
        put("dimension", world.environment.name.lowercase())
        put("time", world.time)
        put("phase", phase)
        put("day", day)
        put("moon", Facts.moonPhase(world.fullTime))
        put("weather", if (world.isThundering) "thunder" else if (world.hasStorm()) "rain" else "clear")
        put("difficulty", world.difficulty.name.lowercase())
        // Through the registry on purpose: GameRule is an interface in Spigot and a class in Paper,
        // so a jar built against Spigot breaks on Paper when it touches GameRule's static members.
        putJsonObject("rules") {
          world.gameRules.filter { it in RULES }.forEach { name ->
            val rule = org.bukkit.NamespacedKey.fromString(name.lowercase())?.let { Registry.GAME_RULE.get(it) } ?: return@forEach
            when (val value = world.getGameRuleValue(rule)) {
              is Boolean -> put(name, value)
              is Number -> put(name, value)
              else -> {}
            }
          }
        }
        put("border", round1(world.worldBorder.size))
      }
      return ToolOutput(result, "день $day, сейчас ${PHASES[phase] ?: phase}")
    }
  }

  private val server = object : ClientTool {
    override val name = "get_server"

    override fun run(context: ToolContext, args: JsonObject): ToolOutput {
      val server = Bukkit.getServer()
      val version = version(server.bukkitVersion)
      val online = server.onlinePlayers.size.toLong()
      val result = buildJsonObject {
        put("software", server.name)
        put("version", version)
        put("difficulty", Bukkit.getWorlds().first().difficulty.name.lowercase())
        put("online", online)
        put("max_players", server.maxPlayers)
        put("plugin_version", org.bukkit.plugin.java.JavaPlugin.getProvidingPlugin(WorldTools::class.java).description.version)
      }
      return ToolOutput(result, "${server.name} $version, $online ${Plurals.ru(online, "игрок", "игрока", "игроков")} онлайн")
    }
  }

  private val locate = object : ClientTool {
    override val name = "locate_structure"

    override fun run(context: ToolContext, args: JsonObject): ToolOutput {
      val player = context.requirePlayer()
      val wanted = (args["structure"] as? JsonPrimitive)?.contentOrNull?.lowercase()?.removePrefix("minecraft:")?.trim()
        ?.takeIf { it.isNotEmpty() } ?: throw ToolError("say which structure, for example village or stronghold")
      // Searching runs on the server thread, so only a few candidates and a capped radius.
      val candidates = Registry.STRUCTURE.filter { it.key.key == wanted || it.key.key.startsWith("${wanted}_") }.take(6)
      if (candidates.isEmpty()) throw ToolError("no structure called $wanted")
      val origin = player.location
      val found = candidates.mapNotNull { player.world.locateNearestStructure(origin, it, context.structureRadius, false) }
        .minByOrNull { it.location.distanceSquared(origin) }
        ?: return ToolOutput(buildJsonObject { put("found", false); put("radius_chunks", context.structureRadius) }, "не нашёл поблизости")
      val at = found.location
      val dx = at.x - origin.x
      val dz = at.z - origin.z
      val distance = Math.round(Math.hypot(dx, dz))
      val direction = Facts.direction(dx, dz)
      val result = buildJsonObject {
        put("found", true)
        put("structure", found.structure.key.key)
        put("x", at.blockX)
        put("z", at.blockZ)
        put("distance", distance)
        put("direction", direction)
      }
      return ToolOutput(result, "$distance ${Plurals.ru(distance, "блок", "блока", "блоков")} на ${DIRECTIONS[direction]}")
    }
  }

  /** `26.3-R0.1-SNAPSHOT` on Spigot and `26.3.build.169-beta` on Paper both mean 26.3. */
  fun version(bukkitVersion: String): String = Regex("^\\d+(\\.\\d+)*").find(bukkitVersion)?.value?.let { found ->
    if (".build" in bukkitVersion) bukkitVersion.substringBefore(".build") else found
  } ?: bukkitVersion

  private val PHASES = mapOf("day" to "светло", "sunset" to "закат", "night" to "ночь", "sunrise" to "рассвет")

  private val DIRECTIONS = mapOf(
    "north" to "север", "north-east" to "северо-восток", "east" to "восток", "south-east" to "юго-восток",
    "south" to "юг", "south-west" to "юго-запад", "west" to "запад", "north-west" to "северо-запад",
  )

  val all = listOf(world, server, locate)
}
