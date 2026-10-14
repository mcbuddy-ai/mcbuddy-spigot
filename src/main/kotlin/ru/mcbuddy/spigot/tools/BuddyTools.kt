package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Permissions
import ru.mcbuddy.spigot.Settings

/** MCBuddy about itself: what this player can use here, and their settings. Texts are English, for the model. */
object BuddyTools {
  /** Every command with the permission it needs and a line for the model. */
  private val COMMANDS = listOf(
    Triple("/ask <question>", Permissions.ASK, "a private question; only the player sees it and the answer"),
    Triple("/askall <question>", Permissions.ASKALL, "a question every player online sees, with the answer"),
    Triple("/askx <request>", Permissions.ASKX, "turns a request into commands and runs them as the player"),
    Triple("/buddy chat [on|off]", Permissions.CHAT, "chat mode: everything the player writes in chat goes to MCBuddy"),
    Triple("/buddy hints [on|off]", Permissions.CHAT, "the tip about chat mode under answers"),
    Triple("/buddy cancel", Permissions.ASK, "stops the answer being written"),
    Triple("/buddy new", Permissions.ASK, "starts the conversation over; memories stay"),
    Triple("/buddy memory", Permissions.ASK, "lists what MCBuddy remembers about the player"),
    Triple("/buddy forget <number|all>", Permissions.ASK, "forgets one remembered fact, or all of them"),
    Triple("/buddy recipe <item>", Permissions.ASK, "opens the recipe window for an item"),
    Triple("/buddy enchants", Permissions.ASK, "opens the enchanting window for the item in hand"),
    Triple("/buddy villagers", Permissions.ASK, "opens the window of villagers around and their trades"),
    Triple("/buddy reload", Permissions.RELOAD, "reloads MCBuddy's settings and languages"),
  )

  /** The commands a player with these permissions may use, as usage to what it does. */
  fun commands(can: (String) -> Boolean): List<Pair<String, String>> = COMMANDS.filter { can(it.second) }.map { it.first to it.third }

  /** Chat mode's prefixes this player may use; none without chat mode. */
  fun prefixes(can: (String) -> Boolean): Map<String, String> = if (!can(Permissions.CHAT)) emptyMap() else buildMap {
    put("plain text", "a private question, like /ask")
    if (can(Permissions.ASKX)) put("!", "commands, like /askx")
    if (can(Permissions.ASKALL)) put("?", "a question everyone sees, like /askall")
    put("~", "an ordinary chat message to the other players")
  }

  /** What the server has switched on, by the tool behind each feature. */
  private val FEATURES = mapOf(
    "notes" to "add_note", "goals" to "goals", "reminders" to "reminders", "books" to "write_book", "buttons" to "add_buttons",
    "compass" to "point_to", "sorting" to "sort_inventory", "ping" to "ping_me", "timezone" to "set_timezone",
  )

  private val guide = object : ClientTool {
    override val name = "get_buddy_guide"

    override fun run(context: ToolContext, args: JsonObject): ToolOutput {
      val sender = context.sender
      val config = context.features.config()
      val commands = commands(sender::hasPermission)
      val result = buildJsonObject {
        put("plugin_version", JavaPlugin.getProvidingPlugin(BuddyTools::class.java).description.version)
        put("commands", buildJsonArray {
          commands.forEach { (usage, does) -> add(buildJsonObject { put("usage", usage); put("does", does) }) }
        })
        val prefixes = prefixes(sender::hasPermission)
        if (prefixes.isNotEmpty()) putJsonObject("chat_mode") {
          putJsonObject("prefixes") { prefixes.forEach { (prefix, does) -> put(prefix, does) } }
          put("note", "spaces after a prefix don't matter; commands starting with / work as usual")
        }
        context.player?.let { player ->
          val settings = context.features.settings.of(player.uniqueId)
          putJsonObject("settings") {
            put("chat_mode", settings.chat_mode)
            put("hints", settings.hints)
            put("timezone", context.features.zones.of(player.uniqueId))
          }
        }
        put("features", buildJsonArray {
          FEATURES.filterValues(config.tools::enabled).keys.forEach { add(it) }
          add("windows")
          if (sender.isOp && OperatorTools.NAMES.any(config.tools::enabled)) add("operator_tools")
        })
        putJsonObject("limits") {
          put("notes", config.tools.maxNotes)
          put("reminders", config.tools.maxReminders)
          put("goals", config.tools.maxGoals)
          put("askall_cooldown_seconds", config.askall.cooldownSeconds)
        }
      }
      return ToolOutput(result, context.lang.text("summary.guide", "count" to commands.size))
    }
  }

  private val settings = object : ClientTool {
    override val name = "buddy_settings"

    override fun run(context: ToolContext, args: JsonObject): ToolOutput {
      val player = context.requirePlayer()
      val chatMode = args.bool("chat_mode")
      val hints = args.bool("hints")
      if (chatMode == null && hints == null) throw ToolError("pass chat_mode or hints")
      if (!player.hasPermission(Permissions.CHAT)) throw ToolError("chat mode is not allowed for this player on this server")
      // The answer confirms the change, so the plugin only speaks up to explain chat mode's prefixes.
      var now: Settings = context.features.settings.of(player.uniqueId)
      chatMode?.let { now = context.features.chat.set(player, it, announce = false) }
      hints?.let { now = context.features.chat.hints(player, it, announce = false) }
      val result = buildJsonObject {
        put("chat_mode", now.chat_mode)
        put("hints", now.hints)
      }
      val lang = context.lang
      val word = { on: Boolean -> lang.text(if (on) "summary.switched_on" else "summary.switched_off") }
      return ToolOutput(result, lang.text("summary.settings", "chat" to word(now.chat_mode), "hints" to word(now.hints)))
    }
  }

  val all = listOf(guide, settings)
}
