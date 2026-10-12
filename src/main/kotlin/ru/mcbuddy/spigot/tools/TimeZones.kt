package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.Json
import org.bukkit.plugin.Plugin
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Each player's time zone, so MCBuddy knows their real time of day. The game doesn't tell where a player is,
 * so everyone starts with the server's default and can name their own zone. Kept in `timezones.json`.
 */
class TimeZones(private val plugin: Plugin, private val default: () -> String) {
  private val file = File(plugin.dataFolder, "timezones.json")
  private val json = Json { ignoreUnknownKeys = true }
  private val zones = ConcurrentHashMap<String, String>(load())

  fun of(player: UUID): String = zones[player.toString()] ?: default()

  /** Stores a zone such as Asia/Yekaterinburg; returns its canonical id, or throws when Java doesn't know it. */
  fun set(player: UUID, zone: String): ZoneId {
    val id = parse(zone) ?: throw ToolError("unknown time zone $zone; use an IANA id such as Europe/Moscow or Asia/Yekaterinburg")
    zones[player.toString()] = id.id
    save()
    return id
  }

  @Synchronized private fun save() {
    plugin.dataFolder.mkdirs()
    val temp = File(plugin.dataFolder, "timezones.json.tmp")
    temp.writeText(json.encodeToString(zones.toMap()))
    Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }

  private fun load(): Map<String, String> = if (!file.exists()) emptyMap() else runCatching {
    json.decodeFromString<Map<String, String>>(file.readText())
  }.getOrElse {
    plugin.logger.warning("Couldn't read timezones.json: ${it.message}; starting over")
    emptyMap()
  }

  companion object {
    fun parse(zone: String): ZoneId? = runCatching { ZoneId.of(zone.trim()) }.getOrNull()
  }
}
