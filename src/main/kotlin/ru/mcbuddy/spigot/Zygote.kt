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
import ru.mcbuddy.spigot.Minecraft.formatMessage
import ru.mcbuddy.spigot.menus.MenuListener
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
  private val buttons = ButtonStore()

  override fun onEnable() {
    saveDefaultConfig()
    // Outlives /ask reload, so reminders already set keep their schedule.
    reminders = Reminders(this, { configuration.tools.maxReminders }) { configuration.messages.reminderMessage }
    server.pluginManager.registerEvents(reminders, this)
    notes = Notes(this) { configuration.tools.maxNotes }
    goals = Goals(this, { configuration.tools.maxGoals }) { configuration.messages.goalDoneMessage }
    val deaths = Deaths()
    features = Features(
      plugin = this,
      reminders = reminders,
      notes = notes,
      goals = goals,
      books = Books(),
      deaths = deaths,
      zones = TimeZones(this) { configuration.time.zone },
      structureRadius = { configuration.tools.structureRadius },
      biomeRadius = { configuration.tools.biomeRadius },
      villagerRadius = { configuration.tools.villagerRadius },
    )
    menus = MenuListener(this)
    listOf(goals, deaths, menus).forEach { server.pluginManager.registerEvents(it, this) }
    goals.start()
    initializeComponents()
    listOf("ask", "askx").map(::getCommand).forEach {
      it?.setExecutor(this)
      it?.tabCompleter = this
    }
    logger.info("✅ MCBuddy Spigot Plugin initialized")
  }

  private fun initializeComponents() {
    configuration = Config(this)
    client = HttpClient(this, configuration).client
    if (::handler.isInitialized) handler.cancelAll()
    if (::animator.isInitialized) animator.stopAllAnimations()
    animator = Animator(this, configuration)
    val tools = ToolRegistry(ToolRegistry.all(), configuration.tools::enabled)
    service = Service(this, configuration, client, tools, features, scope)
    executor = Executor(this, configuration, animator)
    handler = Handler(this, configuration, service, animator, executor, scope) { clicks }
    clicks = Clicks(this, configuration, features, buttons) { player, question -> handler.handleAskCommand(player, question.split(" ").toTypedArray()) }
    memory = MemoryCommands(this, configuration, service, scope)
  }

  override fun onDisable() {
    if (::handler.isInitialized) handler.cancelAll()
    if (::reminders.isInitialized) reminders.cancelAll()
    if (::goals.isInitialized) goals.stop()
    if (::menus.isInitialized) menus.stop()
    animator.stopAllAnimations()
    scope.cancel()
    client.dispatcher.executorService.shutdown()
    logger.info("✅ MCBuddy Spigot Plugin disabled")
  }

  override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
    val ask = command.name.equals("ask", ignoreCase = true)
    val word = args.firstOrNull()?.lowercase()
    return when (command.name.lowercase()) {
      "ask", "askx" -> when {
        args.size == 1 && word == "reload" && sender.hasPermission(RELOAD_PERMISSION) -> handleReloadCommand(sender)
        args.size == 1 && word == "cancel" -> handler.cancel(sender)
        ask && args.size == 1 && word == "new" -> memory.newSession(sender)
        ask && args.size == 1 && word == "memory" -> memory.list(sender)
        ask && word == "forget" && args.size <= 2 -> args.getOrNull(1)?.let { memory.forget(sender, it) }
          ?: true.also { sender.sendMessage(formatMessage(configuration.messages.forgetUsageMessage)) }
        // Buttons under answers run this; a typed id works only if it was shown to this player.
        ask && word == "do" && args.size == 2 && sender is Player -> true.also { clicks.press(sender, args[1]) }
        ask && word == "recipe" && sender is Player -> true.also {
          if (args.size < 2) sender.sendMessage(formatMessage(configuration.messages.recipeUsageMessage)) else clicks.recipe(sender, args.drop(1).joinToString("_"))
        }
        ask && args.size == 1 && word == "enchants" && sender is Player -> true.also { clicks.enchants(sender) }
        ask && args.size == 1 && word == "villagers" && sender is Player -> true.also { clicks.villagers(sender, null) }
        ask -> handler.handleAskCommand(sender, args)
        else -> handler.handleAskxCommand(sender, args)
      }
      else -> false
    }
  }

  override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
    val ask = command.name.equals("ask", ignoreCase = true)
    val options = when {
      args.size == 1 -> buildList {
        add("cancel")
        if (ask) addAll(listOf("new", "memory", "forget", "recipe", "enchants", "villagers"))
        if (sender.hasPermission(RELOAD_PERMISSION)) add("reload")
      }
      ask && args.size == 2 && args[0].equals("forget", ignoreCase = true) -> listOf("all")
      ask && args.size == 2 && args[0].equals("recipe", ignoreCase = true) ->
        return Material.entries.asSequence().filter { it.isItem && !it.isAir && !it.isLegacy }.map { it.key.key }
          .filter { it.startsWith(args[1].lowercase()) }.take(50).toList()
      else -> return emptyList()
    }
    return options.filter { it.startsWith(args.last(), ignoreCase = true) }
  }

  private fun handleReloadCommand(sender: CommandSender): Boolean {
    logger.info("🔄 Handling reload command")

    runCatching {
      reloadConfig()
      initializeComponents()
      sender.sendMessage(formatMessage(configuration.messages.reloadSuccessMessage))
      logger.info("✅ Configuration reloaded by ${sender.name}")
    }.onFailure {
      val errorMessage = configuration.messages.reloadErrorMessage.replace("{error}", it.message ?: "неизвестная ошибка")
      sender.sendMessage(formatMessage(errorMessage))
      logger.warning("❌ Error while reloading configuration: ${it.message}")
    }

    return true
  }

  private companion object {
    const val RELOAD_PERMISSION = "mcbuddy.reload"
  }
}
