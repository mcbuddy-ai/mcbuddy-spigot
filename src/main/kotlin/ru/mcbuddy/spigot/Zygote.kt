package ru.mcbuddy.spigot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.Material
import org.bukkit.entity.Player
import ru.mcbuddy.spigot.menus.MenuListener
import ru.mcbuddy.spigot.menus.SettingsMenu
import ru.mcbuddy.spigot.tools.Books
import ru.mcbuddy.spigot.tools.ButtonStore
import ru.mcbuddy.spigot.tools.Deaths
import ru.mcbuddy.spigot.tools.Features
import ru.mcbuddy.spigot.tools.Goals
import ru.mcbuddy.spigot.tools.Notes
import ru.mcbuddy.spigot.tools.Reminders
import ru.mcbuddy.spigot.tools.TimeZones
import ru.mcbuddy.spigot.tools.ToolRegistry

class Zygote : JavaPlugin(), TabCompleter {
  private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

  private lateinit var client: OkHttpClient
  private lateinit var configuration: Config
  private lateinit var service: Service
  private lateinit var animator: Animator
  private lateinit var executor: Executor
  private lateinit var handler: Handler
  private lateinit var memory: MemoryCommands
  private lateinit var reminders: Reminders
  private lateinit var notes: Notes
  private lateinit var goals: Goals
  private lateinit var features: Features
  private lateinit var menus: MenuListener
  private lateinit var clicks: Clicks
  private lateinit var chat: ChatMode
  private val buttons = ButtonStore()
  private val locales = Locales(this)

  override fun onEnable() {
    saveDefaultConfig()
    // Outlives /buddy reload, so reminders already set keep their schedule.
    reminders = Reminders(this, locales) { configuration.tools.maxReminders }
    server.pluginManager.registerEvents(reminders, this)
    notes = Notes(this) { configuration.tools.maxNotes }
    goals = Goals(this, locales) { configuration.tools.maxGoals }
    val deaths = Deaths()
    val settings = PlayerSettings(this)
    chat = ChatMode(this, locales, settings) { handler }
    features = Features(
      plugin = this,
      locales = locales,
      reminders = reminders,
      notes = notes,
      goals = goals,
      books = Books(),
      deaths = deaths,
      zones = TimeZones(this) { configuration.time.zone },
      settings = settings,
      chat = chat,
      serverId = PlayerSettings.serverId(this),
      config = { configuration },
      structureRadius = { configuration.tools.structureRadius },
      biomeRadius = { configuration.tools.biomeRadius },
      villagerRadius = { configuration.tools.villagerRadius },
    )
    menus = MenuListener(this)
    listOf(goals, deaths, menus, locales, chat).forEach { server.pluginManager.registerEvents(it, this) }
    goals.start()
    initializeComponents()
    listOf("ask", "askx", "askall", "buddy").map(::getCommand).forEach {
      it?.setExecutor(this)
      it?.tabCompleter = this
    }
    logger.info("MCBuddy ${description.version} is on")
  }

  private fun initializeComponents() {
    configuration = Config(this)
    locales.load(configuration)
    client = HttpClient(this, configuration).client
    if (::handler.isInitialized) handler.cancelAll()
    if (::animator.isInitialized) animator.stopAllAnimations()
    animator = Animator(this, configuration)
    val tools = ToolRegistry(ToolRegistry.all(), configuration.tools::enabled)
    service = Service(this, configuration, client, tools, features, scope)
    executor = Executor(this, configuration, locales, animator)
    handler = Handler(this, configuration, locales, service, animator, executor, scope) { clicks }
    clicks = Clicks(this, configuration, features, buttons) { player, question -> handler.ask(player, question, Run.Mode.ASK) }
    memory = MemoryCommands(this, locales, service, scope)
  }

  override fun onDisable() {
    if (::handler.isInitialized) handler.cancelAll()
    if (::reminders.isInitialized) reminders.cancelAll()
    if (::goals.isInitialized) goals.stop()
    if (::menus.isInitialized) menus.stop()
    if (::animator.isInitialized) animator.stopAllAnimations()
    locales.stop()
    scope.cancel()
    if (::client.isInitialized) client.dispatcher.executorService.shutdown()
  }

  override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
    val text = args.joinToString(" ")
    when (command.name.lowercase()) {
      "ask" -> handler.ask(sender, text, Run.Mode.ASK, tip = sender is Player && text.isNotBlank() && chat.counts(sender))
      "askall" -> handler.ask(sender, text, Run.Mode.ASK, public = true)
      "askx" -> handler.ask(sender, text, Run.Mode.ASKX)
      "buddy" -> buddy(sender, args)
      else -> return false
    }
    return true
  }

  /** `/buddy`: everything around the questions. */
  private fun buddy(sender: CommandSender, args: Array<out String>) {
    val lang = locales.of(sender)
    val word = args.firstOrNull()?.lowercase()
    val player = sender as? Player
    val asker = sender.hasPermission(Permissions.ASK)
    when {
      word == null && player != null -> SettingsMenu(player, lang, features, clicks, memory, description.version).open()
      // Buttons under answers run this; a typed id works only if it was shown to this player.
      word == "do" && args.size == 2 && player != null -> clicks.press(player, args[1])
      word == "reload" && sender.hasPermission(Permissions.RELOAD) -> handleReloadCommand(sender)
      word == "chat" || word == "hints" -> when {
        player == null -> lang.send(sender, "messages.players_only")
        !sender.hasPermission(Permissions.CHAT) -> lang.send(sender, "messages.no_permission")
        else -> {
          val settings = features.settings.of(player.uniqueId)
          val current = if (word == "chat") settings.chat_mode else settings.hints
          val wanted = when (args.getOrNull(1)?.lowercase()) {
            "on" -> true
            "off" -> false
            null -> !current
            else -> return lang.send(sender, "messages.buddy_usage")
          }
          if (word == "chat") chat.set(player, wanted) else chat.hints(player, wanted)
        }
      }
      !asker -> lang.send(sender, "messages.no_permission")
      word == "cancel" && args.size == 1 -> handler.cancel(sender)
      word == "new" && args.size == 1 -> memory.newSession(sender)
      word == "memory" && args.size == 1 -> memory.list(sender)
      word == "forget" && args.size <= 2 -> args.getOrNull(1)?.let { memory.forget(sender, it) } ?: memory.usage(sender)
      word == "recipe" && player != null ->
        if (args.size < 2) lang.send(sender, "messages.recipe_usage") else clicks.recipe(player, args.drop(1).joinToString("_"))
      word == "enchants" && args.size == 1 && player != null -> clicks.enchants(player)
      word == "villagers" && args.size == 1 && player != null -> clicks.villagers(player, null)
      else -> lang.send(sender, "messages.buddy_usage")
    }
  }

  override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
    if (!command.name.equals("buddy", ignoreCase = true)) return emptyList()
    val options = when {
      args.size == 1 -> buildList {
        if (sender.hasPermission(Permissions.CHAT)) addAll(listOf("chat", "hints"))
        if (sender.hasPermission(Permissions.ASK)) addAll(listOf("cancel", "new", "memory", "forget", "recipe", "enchants", "villagers"))
        if (sender.hasPermission(Permissions.RELOAD)) add("reload")
      }
      args.size == 2 && args[0].lowercase() in setOf("chat", "hints") -> listOf("on", "off")
      args.size == 2 && args[0].equals("forget", ignoreCase = true) -> listOf("all")
      args.size == 2 && args[0].equals("recipe", ignoreCase = true) ->
        return Material.entries.asSequence().filter { it.isItem && !it.isAir && !it.isLegacy }.map { it.key.key }
          .filter { it.startsWith(args[1].lowercase()) }.take(50).toList()
      else -> return emptyList()
    }
    return options.filter { it.startsWith(args.last(), ignoreCase = true) }
  }

  private fun handleReloadCommand(sender: CommandSender) {
    runCatching {
      reloadConfig()
      initializeComponents()
      locales.of(sender).send(sender, "messages.reloaded")
      logger.info("Settings and languages reloaded by ${sender.name}")
    }.onFailure {
      locales.of(sender).send(sender, "messages.reload_failed", "error" to (it.message ?: it.javaClass.simpleName))
      logger.warning("Reload failed: $it")
    }
  }
}
