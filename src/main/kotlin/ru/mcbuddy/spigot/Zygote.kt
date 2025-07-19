package ru.mcbuddy.spigot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.Minecraft.formatMessage

class Zygote : JavaPlugin() {
  private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())

  private lateinit var client: OkHttpClient
  private lateinit var configuration: Config
  private lateinit var service: Service
  private lateinit var animator: Animator
  private lateinit var executor: Executor
  private lateinit var handler: Handler

  override fun onEnable() {
    saveDefaultConfig()
    initializeComponents()
    listOf("ask", "askx", "reload").map(::getCommand).forEach { it?.setExecutor(this) }
    logger.info("✅ MCBuddy Spigot Plugin initialized")
  }

  private fun initializeComponents() {
    configuration = Config(this)
    client = HttpClient(this, configuration).client
    service = Service(configuration, client)
    animator = Animator(this, configuration)
    executor = Executor(this, configuration, animator)
    handler = Handler(this, configuration, service, animator, executor, scope)
  }

  override fun onDisable() {
    animator.stopAllAnimations()
    scope.cancel()
    client.dispatcher.executorService.shutdown()
    logger.info("✅ MCBuddy Spigot Plugin disabled")
  }

  override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>) = when (command.name.lowercase()) {
    "ask" -> handler.handleAskCommand(sender, args)
    "askx" -> handler.handleAskxCommand(sender, args)
    "reload" -> handleReloadCommand(sender)
    else -> false
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
}
