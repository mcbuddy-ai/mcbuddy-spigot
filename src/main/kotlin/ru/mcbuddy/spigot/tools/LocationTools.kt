package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bukkit.Location
import org.bukkit.entity.Player
import ru.mcbuddy.spigot.Plurals

/** Places that matter to the asker: where they respawn, where the world starts, where they last died. */
object LocationTools {
  /** A place as the model needs it, with distance and direction when the player can walk there. */
  private fun describe(place: Location?, player: Player): JsonElement {
    val world = place?.world ?: return JsonNull
    return buildJsonObject {
      put("x", place.blockX)
      put("y", place.blockY)
      put("z", place.blockZ)
      put("dimension", world.environment.name.lowercase())
      if (world == player.world) {
        val dx = place.x - player.location.x
        val dz = place.z - player.location.z
        put("distance", Math.round(Math.hypot(dx, dz)))
        put("direction", Facts.direction(dx, dz))
      }
    }
  }

  private val locations = object : ClientTool {
    override val name = "get_locations"

    override fun run(context: ToolContext, args: JsonObject): ToolOutput {
      val player = context.requirePlayer()
      val death = player.lastDeathLocation
      val result = buildJsonObject {
        put("respawn_point", describe(player.respawnLocation, player))
        put("world_spawn", describe(player.server.worlds.first().spawnLocation, player))
        put("last_death", describe(death, player))
      }
      val summary = if (death?.world == player.world) {
        val distance = Math.round(death.distance(player.location))
        "смерть в $distance ${Plurals.ru(distance, "блоке", "блоках", "блоках")}"
      } else if (player.respawnLocation != null) "есть точка возрождения" else "точки возрождения нет"
      return ToolOutput(result, summary)
    }
  }

  val all = listOf<ClientTool>(locations)
}
