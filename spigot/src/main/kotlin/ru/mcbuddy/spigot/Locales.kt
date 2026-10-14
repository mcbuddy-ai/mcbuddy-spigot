package ru.mcbuddy.spigot

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerPreLoginEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Every player reads the plugin in their own language: `lang/<code>.yml`, picked by the country of their IP
 * address, then by their game's language, then `locales.default`. The console gets the default.
 */
class Locales(private val plugin: JavaPlugin) : Listener {
  private val countries = ConcurrentHashMap<UUID, String>()
  val geoIp = GeoIp(File(plugin.dataFolder, "geoip"), plugin.logger)
  private var geoIpTask: BukkitTask? = null

  @Volatile lateinit var texts: Texts; private set
  @Volatile private lateinit var resolver: LocaleResolver

  /** Reads the language files and starts the GeoIP refresh; `/buddy reload` runs it again. */
  fun load(config: Config) {
    val folder = File(plugin.dataFolder, "lang")
    Texts.BUNDLED.forEach { code -> if (!File(folder, "$code.yml").exists()) plugin.saveResource("lang/$code.yml", false) }
    val languages = buildMap {
      folder.listFiles { file -> file.extension == "yml" }.orEmpty().sortedBy { it.name }.forEach { file ->
        val code = LocaleResolver.normalize(file.nameWithoutExtension)
        val texts = runCatching { Texts.read(file) }.onFailure { plugin.logger.warning("Can't read lang/${file.name}: ${it.message}") }.getOrNull() ?: return@forEach
        // Keys a release added come from the bundled file until the server's copy has them.
        put(code, bundled(code) + texts)
      }
      Texts.BUNDLED.forEach { code -> if (code !in this) put(code, bundled(code)) }
    }
    val default = LocaleResolver.normalize(config.locales.default)
    if (default !in languages) plugin.logger.warning("locales.default: there is no lang/$default.yml; using ${Texts.FALLBACK}")
    texts = Texts(languages, default)
    resolver = LocaleResolver(config.locales, languages.keys)
    plugin.logger.info("Languages: ${languages.keys.sorted().joinToString()}, default ${texts.default}")

    geoIpTask?.cancel()
    if (!config.geoIp.enabled || "ip" !in config.locales.sources) {
      geoIp.close()
      return
    }
    geoIp.open()
    // Players online during a reload have no lookup from their login.
    plugin.server.onlinePlayers.forEach { player -> geoIp.country(player.address?.address)?.let { countries[player.uniqueId] = it } }
    geoIpTask = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable {
      if (geoIp.isStale(config.geoIp)) {
        geoIp.refresh(config.geoIp)
        plugin.server.onlinePlayers.forEach { player -> geoIp.country(player.address?.address)?.let { countries[player.uniqueId] = it } }
      }
    }, 0L, 20L * 60 * 60 * 12)
  }

  private fun bundled(code: String): Map<String, Any> =
    plugin.getResource("lang/$code.yml")?.let { stream -> InputStreamReader(stream, Charsets.UTF_8).use(Texts::read) }.orEmpty()

  /** The language of whoever reads a message; the console and command blocks read the default. */
  fun of(sender: CommandSender?): Lang = texts.lang((sender as? Player)?.let { code(it) })

  fun code(player: Player): String = resolver.resolve(countries[player.uniqueId], player.locale) ?: texts.default

  fun stop() {
    geoIpTask?.cancel()
    geoIp.close()
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  fun onPreLogin(event: AsyncPlayerPreLoginEvent) {
    if (event.loginResult != AsyncPlayerPreLoginEvent.Result.ALLOWED) return
    geoIp.country(event.address)?.let { countries[event.uniqueId] = it }
  }

  @EventHandler
  fun onQuit(event: PlayerQuitEvent) {
    countries.remove(event.player.uniqueId)
  }
}
