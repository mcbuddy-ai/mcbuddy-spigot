package ru.mcbuddy.spigot

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Minecraft.formatMessage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object Permissions {
  const val ASK = "mcbuddy.ask"
  const val ASKX = "mcbuddy.askx"
  const val ASKALL = "mcbuddy.askall"
  const val ASKALL_NO_COOLDOWN = "mcbuddy.askall.nocooldown"
  const val CHAT = "mcbuddy.chat"
  const val RELOAD = "mcbuddy.reload"
}

/**
 * Chat mode: whatever a player writes in chat goes to MCBuddy instead of the other players, with prefixes for
 * commands, public questions, and the ordinary chat. Also the tip about it under `/ask` answers.
 */
class ChatMode(
  private val plugin: JavaPlugin,
  private val locales: Locales,
  private val settings: PlayerSettings,
  private val handler: () -> Handler,
) : Listener {
  /** Where a chat line goes, by its first character. */
  enum class Route { ASK, ASKX, PUBLIC, CHAT }

  /** `/ask` questions per player since they joined, and who already saw the tip this time. */
  private val asked = ConcurrentHashMap<UUID, Int>()
  private val tipped: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

  fun on(player: Player) = settings.of(player.uniqueId).chat_mode

  // LOWEST, so the line never reaches other chat plugins as an ordinary message.
  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  fun onChat(event: AsyncPlayerChatEvent) {
    val player = event.player
    if (!on(player)) return
    val (route, text) = route(event.message)
    if (route == Route.CHAT) {
      if (text.isEmpty()) event.isCancelled = true else event.message = text
      return
    }
    event.isCancelled = true
    // Chat arrives off the server thread; questions start on it, like commands.
    plugin.server.scheduler.runTask(plugin, Runnable { if (player.isOnline) dispatch(player, route, text) })
  }

  private fun dispatch(player: Player, route: Route, text: String) {
    val lang = locales.of(player)
    when (route) {
      Route.ASK -> when {
        !player.hasPermission(Permissions.ASK) -> lang.send(player, "messages.no_permission")
        text.isEmpty() -> {}
        else -> {
          echo(player, text)
          handler().ask(player, text, Run.Mode.ASK)
        }
      }
      Route.ASKX -> when {
        !player.hasPermission(Permissions.ASKX) -> lang.send(player, "chat_mode.no_askx")
        text.isEmpty() -> lang.send(player, "messages.askx_usage")
        else -> {
          echo(player, "! $text")
          handler().ask(player, text, Run.Mode.ASKX)
        }
      }
      Route.PUBLIC -> when {
        !player.hasPermission(Permissions.ASKALL) -> lang.send(player, "chat_mode.no_askall")
        text.isEmpty() -> lang.send(player, "messages.askall_usage")
        // The question line everyone sees is the echo.
        else -> handler().ask(player, text, Run.Mode.ASK, public = true)
      }
      Route.CHAT -> {}
    }
  }

  /** The player's own line in gray, so a question doesn't just vanish from the chat. */
  private fun echo(player: Player, text: String) {
    player.sendMessage(Minecraft.withText(locales.of(player).text("chat_mode.echo", "text" to Minecraft.SLOT), text))
  }

  @EventHandler
  fun onJoin(event: PlayerJoinEvent) {
    val player = event.player
    if (!on(player)) return
    // After the join messages, so it's the last thing they read.
    plugin.server.scheduler.runTaskLater(plugin, Runnable { if (player.isOnline) locales.of(player).send(player, "chat_mode.reminder") }, 40L)
  }

  @EventHandler
  fun onQuit(event: PlayerQuitEvent) {
    asked.remove(event.player.uniqueId)
    tipped.remove(event.player.uniqueId)
  }

  /** `/buddy chat`, its button, and `buddy_settings`. [announce] explains the change in chat. */
  fun set(player: Player, enabled: Boolean, announce: Boolean = true): Settings {
    val result = settings.update(player.uniqueId) { it.copy(chat_mode = enabled) }
    if (enabled) explain(player) else if (announce) locales.of(player).send(player, "chat_mode.disabled")
    return result
  }

  /** `/buddy hints`, the “don't show” button, and `buddy_settings`. */
  fun hints(player: Player, enabled: Boolean, announce: Boolean = true): Settings {
    val result = settings.update(player.uniqueId) { it.copy(hints = enabled) }
    if (announce) locales.of(player).send(player, if (enabled) "chat_mode.hints_on" else "chat_mode.hints_off")
    return result
  }

  /** What chat mode does, with only the prefixes this player may use. */
  private fun explain(player: Player) {
    val lang = locales.of(player)
    lang.send(player, "chat_mode.enabled")
    if (player.hasPermission(Permissions.ASKX)) lang.send(player, "chat_mode.prefix_askx")
    if (player.hasPermission(Permissions.ASKALL)) lang.send(player, "chat_mode.prefix_public")
    lang.send(player, "chat_mode.prefix_chat")
    lang.send(player, "chat_mode.how_to_off")
  }

  /** Counts a typed `/ask`; true when the tip about chat mode should go under this answer: the second question, once per visit. */
  fun counts(player: Player): Boolean {
    val count = asked.merge(player.uniqueId, 1, Int::plus)!!
    val wants = settings.of(player.uniqueId).let { it.hints && !it.chat_mode } && player.hasPermission(Permissions.CHAT)
    return count >= 2 && wants && tipped.add(player.uniqueId)
  }

  companion object {
    /** `!text` and `! text` alike: the prefix, then the rest without the spaces around it. */
    fun route(message: String): Pair<Route, String> {
      val line = message.trim()
      val route = when (line.firstOrNull()) {
        '!' -> Route.ASKX
        '?' -> Route.PUBLIC
        '~' -> Route.CHAT
        else -> return Route.ASK to line
      }
      return route to line.drop(1).trim()
    }
  }
}
