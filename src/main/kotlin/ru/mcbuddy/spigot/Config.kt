package ru.mcbuddy.spigot

import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.tools.OperatorTools

/** Settings from `config.yml`. Every key has a default; texts live in `lang/`, so the message keys of 2.3 configs are ignored. */
class Config(private val plugin: JavaPlugin) {
  private val config: FileConfiguration = plugin.config

  val server = Server() ; inner class Server {
    val url = config.getString("server.url", "https://mcbuddy.ru")!!
  }

  val http = Http() ; inner class Http {
    val callTimeout = config.getInt("http.call_timeout", 300).toLong()
    val connectTimeout = config.getInt("http.connect_timeout", 10).toLong()
    val readTimeout = config.getInt("http.read_timeout", 300).toLong()
    val writeTimeout = config.getInt("http.write_timeout", 10).toLong()
    val maxIdleConnections = config.getInt("http.max_idle_connections", 5)
    val keepAliveDuration = config.getInt("http.keep_alive_duration", 300).toLong()
    val maxRequestsPerHost = config.getInt("http.max_requests_per_host", 5)
    val followRedirects = config.getBoolean("http.follow_redirects", true)
    val retryOnConnectionFailure = config.getBoolean("http.retry_on_connection_failure", true)
    val compressionEnabled = config.getBoolean("http.compression_enabled", true)
    val logging = config.getBoolean("http.logging", false)
    // Configs before 2.4 pinned an old version here; the plugin's own version wins now.
    val userAgent = "MCBuddy-Spigot/${plugin.description.version} (Minecraft Plugin)"
    val toolTimeout = config.getInt("http.tool_timeout", 10).toLong()
    val customHeaders = config.getConfigurationSection("http.custom_headers")
      .let { it?.getKeys(false)?.associateWith(it::getString) }.orEmpty()
      .filterKeys { !it.equals("X-Plugin-Version", ignoreCase = true) }
  }

  val ui = UI() ; inner class UI {
    val actionBarEnabled = config.getBoolean("ui.action_bar.enabled", true)
    val spinnerFrames = config.getString("ui.action_bar.spinner", "·✢✳✶✻✽")!!
    val frameMillis = config.getInt("ui.action_bar.frame_millis", 120).toLong().coerceAtLeast(50)
    val verbSeconds = config.getInt("ui.action_bar.verb_seconds", 3).toLong()
    val toolLines = config.getBoolean("ui.tool_lines.enabled", true)

    val particlesEnabled = config.getBoolean("ui.particles.enabled", true)
    val particleType = value("ui.particles.type", Particle.ENCHANT) { Particle.valueOf(it) }
    val particleCount = config.getInt("ui.particles.count", 3)
    val particleHeightOffset = config.getDouble("ui.particles.height_offset", 2.0)
    val particleAnimationInterval = config.getInt("ui.particles.animation_interval", 250).toLong()

    val chatStatusEnabled = config.getBoolean("ui.chat_status.enabled", false)
    val bookAuthor = config.getString("ui.book_author", "MCBuddy")!!

    val soundsEnabled = config.getBoolean("ui.sounds.enabled", true)
    val successSound = value("ui.sounds.success_sound", Sound.ENTITY_EXPERIENCE_ORB_PICKUP) { Sound.valueOf(it) }
    val successVolume = config.getDouble("ui.sounds.success_volume", 0.5).toFloat()
    val successPitch = config.getDouble("ui.sounds.success_pitch", 1.2).toFloat()
    val errorSound = value("ui.sounds.error_sound", Sound.AMBIENT_CAVE) { Sound.valueOf(it) }
    val errorVolume = config.getDouble("ui.sounds.error_volume", 0.6).toFloat()
    val errorPitch = config.getDouble("ui.sounds.error_pitch", 0.7).toFloat()
    val thinkingSound = value("ui.sounds.thinking_sound", Sound.BLOCK_NOTE_BLOCK_CHIME) { Sound.valueOf(it) }
    val thinkingVolume = config.getDouble("ui.sounds.thinking_volume", 0.3).toFloat()
    val thinkingPitch = config.getDouble("ui.sounds.thinking_pitch", 1.0).toFloat()

    private fun <T> value(key: String, default: T, parse: (String) -> T): T {
      val name = config.getString(key) ?: return default
      return runCatching { parse(name.uppercase()) }.getOrElse {
        plugin.logger.warning("$key: there is no such value as $name; using the default")
        default
      }
    }
  }

  val tools = Tools() ; inner class Tools {
    private val switches = config.getConfigurationSection("tools.enabled")
    private val operator = config.getConfigurationSection("tools.operator")
    val structureRadius = config.getInt("tools.locate_structure_radius", 64).coerceIn(1, 128)
    val maxReminders = config.getInt("tools.reminders.max_pending", 10).coerceIn(1, 100)
    val maxNotes = config.getInt("tools.notes.max_per_player", 200).coerceIn(1, 1000)
    val maxGoals = config.getInt("tools.goals.max_per_player", 5).coerceIn(1, 20)
    val biomeRadius = config.getInt("tools.locate_biome_radius", 2048).coerceIn(64, 6400)
    val villagerRadius = config.getInt("tools.villager_radius", 32).coerceIn(8, 64)

    /** Missing keys mean on: an old config gets every tool. Operator tools also need `tools.operator.enabled`. */
    fun enabled(name: String) = if (name in OperatorTools.NAMES) {
      (operator?.getBoolean("enabled", true) ?: true) && (operator?.getBoolean(name, true) ?: true)
    } else {
      switches?.getBoolean(name, true) ?: true
    }
  }

  val time = Time() ; inner class Time {
    val zone = config.getString("time.zone", "Europe/Moscow")!!.let { zone -> zone.takeIf { ru.mcbuddy.spigot.tools.TimeZones.parse(it) != null } ?: "UTC" }
  }

  val blockedCommands: Set<String> = config.getStringList("blocked_commands").map(String::lowercase).toSet()

  class Locales(val default: String, val sources: List<String>, val countries: Map<String, List<String>>)

  val locales = Locales(
    default = config.getString("locales.default", "en")!!,
    sources = config.getStringList("locales.sources").map(String::lowercase).ifEmpty { listOf("ip", "client") },
    countries = config.getConfigurationSection("locales.countries")?.let { section -> section.getKeys(false).associateWith(section::getStringList) }
      ?: DEFAULT_COUNTRIES,
  )

  class GeoIp(val enabled: Boolean, val url: String, val refreshDays: Int)

  val geoIp = GeoIp(
    enabled = config.getBoolean("geoip.enabled", true),
    url = config.getString("geoip.url", "https://download.db-ip.com/free/dbip-country-lite-{year}-{month}.mmdb.gz")!!,
    refreshDays = config.getInt("geoip.refresh_days", 30),
  )

  private companion object {
    val DEFAULT_COUNTRIES = mapOf(
      "ru" to listOf("RU", "BY", "KZ", "KG", "TJ", "UZ", "AM", "AZ", "MD", "GE", "TM"),
      "es" to listOf("ES", "MX", "AR", "CO", "CL", "PE", "VE", "EC", "GT", "CU", "BO", "DO", "HN", "PY", "SV", "NI", "CR", "PA", "UY", "PR", "GQ"),
      "pt" to listOf("BR", "PT", "AO", "MZ", "CV", "GW", "ST", "TL"),
      "de" to listOf("DE", "AT", "CH", "LI", "LU"),
    )
  }
}
