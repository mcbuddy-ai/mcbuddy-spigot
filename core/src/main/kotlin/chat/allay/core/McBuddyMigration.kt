package chat.allay.core

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger

/**
 * Moves MCBuddy's data into Allay's folder: `plugins/mcbuddy-spigot/` into `plugins/Allay/`. It runs before anything
 * reads the data, moves rather than copies, logs each file, and writes a marker so it never runs twice. When a move
 * fails halfway, what already moved goes back and the old folder stays as it was. When both folders hold players'
 * data, it changes nothing and warns: an admin merges them by hand. Settings already in Allay's folder (a server that
 * ships its `config.yml` with the image puts it there before the plugin starts) win, and MCBuddy's stay behind.
 *
 * After the move, texts that still say what MCBuddy shipped become Allay's: the backend's address, the default book
 * author, the default sounds, the language files' untouched lines. What the admin wrote stays, with the old name and command replaced.
 */
class McBuddyMigration(
  private val old: File,
  private val new: File,
  private val log: Logger,
  private val move: (Path, Path) -> Unit = { from, to -> Files.move(from, to) },
) {
  enum class Result { NOTHING, MOVED, BOTH, FAILED, ALREADY }

  fun run(): Result {
    if (File(new, MARKER).exists()) return Result.ALREADY
    val present = ITEMS.filter { File(old, it).exists() }
    if (present.isEmpty()) return Result.NOTHING
    val own = DATA.filter { File(new, it).exists() }
    if (own.isNotEmpty()) {
      log.warning("Both ${old.name}/ and ${new.name}/ hold data (${own.joinToString()}); moving nothing. Merge them by hand and delete ${old.name}/.")
      return Result.BOTH
    }
    new.mkdirs()
    val moved = mutableListOf<String>()
    try {
      for (name in present) {
        if (File(new, name).exists()) {
          log.info("Kept ${new.name}/$name that was already there; MCBuddy's stays in ${old.name}/")
          continue
        }
        move(File(old, name).toPath(), File(new, name).toPath())
        moved += name
        log.info("Moved ${old.name}/$name to ${new.name}/$name")
      }
    } catch (error: Exception) {
      for (name in moved.reversed()) {
        runCatching { move(File(new, name).toPath(), File(old, name).toPath()) }
          .onFailure { log.severe("Couldn't put $name back into ${old.name}/: $it; it's in ${new.name}/") }
      }
      log.severe("Couldn't move ${old.name}/ to ${new.name}/: $error. The old folder is as it was; MCBuddy's data waits there.")
      return Result.FAILED
    }
    runCatching { renameTexts(moved) }.onFailure { log.warning("Moved the data, but couldn't update its texts to Allay: $it") }
    File(new, MARKER).writeText("Allay moved this server's data here from ${old.name}/. Delete this file to have it check again.\n")
    return Result.MOVED
  }

  /** Only what came over from MCBuddy: settings that were already here are the admin's. */
  private fun renameTexts(moved: List<String>) {
    if ("config.yml" in moved) File(new, "config.yml").takeIf { it.isFile }?.let { file -> file.writeText(config(file.readText())) }
    if ("lang" in moved) File(new, "lang").listFiles { file -> file.extension == "yml" }.orEmpty().forEach(::language)
  }

  /** A language file: keys still as 3.1.0 shipped them go, so Allay's own texts show; what the admin changed stays. */
  private fun language(file: File) {
    val shipped = javaClass.getResourceAsStream("/mcbuddy-3.1.0/lang/${file.name}")?.reader(Charsets.UTF_8)?.use { YamlConfig(YamlConfig.parse(it)).flatten() }
    if (shipped == null) {
      // A language MCBuddy didn't ship is all the admin's own.
      file.writeText(brand(file.readText()))
      log.info("Renamed MCBuddy to Allay in ${new.name}/lang/${file.name}")
      return
    }
    val changed = YamlConfig.read(file).flatten().filter { (key, value) -> shipped[key] != value }
      .map { (key, value) -> (RENAMED_KEYS[key] ?: key) to if (value is List<*>) value.map { brand(it.toString()) } else brand(value.toString()) }
    if (changed.isEmpty()) {
      file.delete()
      log.info("${new.name}/lang/${file.name} had no changes of its own; Allay writes its own")
      return
    }
    val tree = LinkedHashMap<String, Any?>()
    for ((key, value) in changed) {
      val path = key.split('.')
      @Suppress("UNCHECKED_CAST")
      val section = path.dropLast(1).fold(tree) { map, part -> map.getOrPut(part) { LinkedHashMap<String, Any?>() } as LinkedHashMap<String, Any?> }
      section[path.last()] = value
    }
    val yaml = Yaml(DumperOptions().apply {
      defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
      isAllowUnicode = true
      width = Int.MAX_VALUE
    })
    file.writeText("# Your own texts, kept from MCBuddy. Every other line comes from Allay's ${file.name}.\n" + yaml.dump(tree))
    log.info("Kept ${changed.size} of your own texts in ${new.name}/lang/${file.name}; the rest comes from Allay")
  }

  /** `config.yml`: values still at MCBuddy's defaults take Allay's; comments name Allay; renamed tools keep their switch. */
  private fun config(text: String): String {
    var result = text
    fun replace(pattern: String, replacement: String) {
      result = Regex(pattern, RegexOption.MULTILINE).replace(result, replacement)
    }
    replace("""^(\s*book_author:\s*)"MCBuddy"""", "$1\"Allay\"")
    // mcbuddy.ru is gone; Allay's own backend answers at allay.chat.
    replace("""^(\s*url:\s*)"?https?://(www\.)?mcbuddy\.ru/?"?\s*$""", "$1\"https://allay.chat\"")
    for ((name, sound) in SOUNDS) {
      if (!Regex("""^\s*${name}_sound:\s*"${sound.oldSound}"""", RegexOption.MULTILINE).containsMatchIn(result)) continue
      replace("""^(\s*${name}_sound:\s*)"${sound.oldSound}"""", "$1\"${sound.newSound}\"")
      replace("""^(\s*${name}_volume:\s*)${Regex.escape(sound.oldVolume)}\b""", "$1${sound.newVolume}")
      replace("""^(\s*${name}_pitch:\s*)${Regex.escape(sound.oldPitch)}\b""", "$1${sound.newPitch}")
    }
    replace("""^(\s*)get_buddy_guide:""", "$1get_allay_guide:")
    replace("""^(\s*)buddy_settings:""", "$1allay_settings:")
    // Comments and the lines MCBuddy wrote about itself.
    return result.lines().joinToString("\n") { line -> if (line.trimStart().startsWith("#")) brand(line) else line }
  }

  private class Sound(val oldSound: String, val oldVolume: String, val oldPitch: String, val newSound: String, val newVolume: String, val newPitch: String)

  companion object {
    /** Everything MCBuddy kept in its folder. */
    val ITEMS = listOf("config.yml", "lang", "notes", "goals", "timezones.json", "players.json", "server-id", "geoip")

    /** What players and the server built up; settings, languages, and the GeoIP database aren't. */
    val DATA = listOf("notes", "goals", "timezones.json", "players.json", "server-id")

    const val MARKER = "moved-from-mcbuddy.txt"

    private val SOUNDS = mapOf(
      "success" to Sound("ENTITY_EXPERIENCE_ORB_PICKUP", "0.5", "1.2", "ENTITY_ALLAY_ITEM_GIVEN", "1.0", "1.0"),
      "error" to Sound("AMBIENT_CAVE", "0.6", "0.7", "ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM", "1.0", "0.9"),
      "thinking" to Sound("BLOCK_NOTE_BLOCK_CHIME", "0.3", "1.0", "ENTITY_ALLAY_AMBIENT_WITH_ITEM", "1.0", "1.0"),
    )

    private val RENAMED_KEYS = mapOf(
      "messages.buddy_usage" to "messages.allay_usage",
      "tools.get_buddy_guide" to "tools.get_allay_guide",
      "tools.buddy_settings" to "tools.allay_settings",
    )

    /** The old name, command, permissions, and folder as Allay's. */
    fun brand(text: String) = text
      .replace("plugins/mcbuddy-spigot", "plugins/Allay")
      .replace(Regex("""\bmcbuddy\.(ask|askx|askall|chat|reload)\b"""), "allay.$1")
      .replace("/buddy", "/allay")
      .replace("MCBuddy", "Allay")
  }
}
