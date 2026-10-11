@file:Suppress("SameParameterValue")

package ru.mcbuddy.spigot

import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin

class Config(private val plugin: JavaPlugin) {
  init {
    plugin.logger.info("🔄 Initializing configuration...")
  }

  val server = Server() ; inner class Server(config: FileConfiguration = plugin.config) {
    val url = config.getString("server.url", "https://mcbuddy.ru")!!

    init {
      plugin.logger.info("✅ Server Settings loaded")
    }
  }

  val http = Http() ; inner class Http(config: FileConfiguration = plugin.config) {
    val callTimeout = config.getInt("http.call_timeout", 60).toLong()
    val connectTimeout = config.getInt("http.connect_timeout", 10).toLong()
    val readTimeout = config.getInt("http.read_timeout", 30).toLong()
    val writeTimeout = config.getInt("http.write_timeout", 10).toLong()
    val maxIdleConnections = config.getInt("http.max_idle_connections", 5)
    val keepAliveDuration = config.getInt("http.keep_alive_duration", 300).toLong()
    val maxRequestsPerHost = config.getInt("http.max_requests_per_host", 5)
    val followRedirects = config.getBoolean("http.follow_redirects", true)
    val retryOnConnectionFailure = config.getBoolean("http.retry_on_connection_failure", true)
    val compressionEnabled = config.getBoolean("http.compression_enabled", true)
    val logging = config.getBoolean("http.logging", false)
    val userAgent = config.getString("http.user_agent", "MCBuddy-Spigot/2.0.0 (Minecraft Plugin)")!!
    val toolTimeout = config.getInt("http.tool_timeout", 10).toLong()
    val customHeaders = config.getConfigurationSection("http.custom_headers").let { it?.getKeys(false)?.associateWith(it::getString) } ?: emptyMap()

    init {
      plugin.logger.info("✅ HTTP Settings loaded")
    }
  }

  val ui = UI() ; inner class UI(val config: FileConfiguration = plugin.config) {
    val actionBarEnabled = config.getBoolean("ui.action_bar.enabled", true)
    val statusFormat = config.getString("ui.action_bar.format", "&6{spinner} &6{verb}… &7({elapsed} · ↓ {tokens})")!!
    val answeringVerb = config.getString("ui.action_bar.answering", "Пишу ответ")!!
    val spinnerFrames = config.getString("ui.action_bar.spinner", "·✢✳✶✻✽")!!
    val frameMillis = config.getInt("ui.action_bar.frame_millis", 120).toLong().coerceAtLeast(50)
    val verbSeconds = config.getInt("ui.action_bar.verb_seconds", 3).toLong()
    val verbs = config.getStringList("ui.action_bar.verbs").ifEmpty { DEFAULT_VERBS }
    val askxVerbs = config.getStringList("ui.action_bar.askx_verbs").ifEmpty { DEFAULT_ASKX_VERBS }
    val showReasoning = config.getBoolean("ui.action_bar.show_reasoning", true)
    val reasoningFormat = config.getString("ui.action_bar.reasoning_format", " &8&o{reasoning}")!!
    val reasoningLength = config.getInt("ui.action_bar.reasoning_length", 40)

    val toolLines = config.getBoolean("ui.tool_lines.enabled", true)
    val toolStartFormat = config.getString("ui.tool_lines.started", "&7⏺ {label}")!!
    val toolDoneFormat = config.getString("ui.tool_lines.finished", "&8  ⎿ {summary}")!!
    val toolFailedFormat = config.getString("ui.tool_lines.failed", "&c  ⎿ {summary}")!!
    val toolLabels = config.getConfigurationSection("ui.tool_lines.labels")?.let { section ->
      section.getKeys(false).associateWith { section.getString(it)!! }
    } ?: emptyMap()

    val particlesEnabled = config.getBoolean("ui.particles.enabled", true)
    val particleType = getParticle("ui.particles.type", "ENCHANT", Particle.ENCHANT)
    val particleCount = config.getInt("ui.particles.count", 3)
    val particleHeightOffset = config.getDouble("ui.particles.height_offset", 2.0)
    val particleAnimationInterval = config.getInt("ui.particles.animation_interval", 250).toLong()

    val chatStatusEnabled = config.getBoolean("ui.chat_status.enabled", true)

    val soundsEnabled = config.getBoolean("ui.sounds.enabled", true)
    val successSound = getSound("ui.sounds.success_sound", "ENTITY_EXPERIENCE_ORB_PICKUP", Sound.ENTITY_EXPERIENCE_ORB_PICKUP)
    val successVolume = config.getDouble("ui.sounds.success_volume", 0.5).toFloat()
    val successPitch = config.getDouble("ui.sounds.success_pitch", 1.2).toFloat()
    val errorSound = getSound("ui.sounds.error_sound", "AMBIENT_CAVE", Sound.AMBIENT_CAVE)
    val errorVolume = config.getDouble("ui.sounds.error_volume", 0.6).toFloat()
    val errorPitch = config.getDouble("ui.sounds.error_pitch", 0.7).toFloat()
    val thinkingSound = getSound("ui.sounds.thinking_sound", "BLOCK_NOTE_BLOCK_CHIME", Sound.BLOCK_NOTE_BLOCK_CHIME)
    val thinkingVolume = config.getDouble("ui.sounds.thinking_volume", 0.3).toFloat()
    val thinkingPitch = config.getDouble("ui.sounds.thinking_pitch", 1.0).toFloat()

    init {
      plugin.logger.info("✅ UI Settings loaded")
    }

    private fun getSound(key: String, default: String, defaultSound: Sound) = runCatching {
      Sound.valueOf(config.getString(key, default)!!)
    }.getOrElse {
      plugin.logger.warning("❌ Error while loading sound `$key`")
      defaultSound
    }

    private fun getParticle(key: String, default: String, defaultParticle: Particle) = runCatching {
      Particle.valueOf(config.getString(key, default)!!)
    }.getOrElse {
      plugin.logger.warning("❌ Error while loading particle `$key`")
      defaultParticle
    }
  }

  val messages = Messages() ; inner class Messages(config: FileConfiguration = plugin.config) {
    val thinkingMessage = config.getString("messages.thinking", "&eОбращаюсь к AI-ассистенту, подождите немного...")!!
    val errorMessage = config.getString("messages.error", "&cНе удалось получить ответ от сервера")!!
    val usageMessage = config.getString("messages.usage", "&7Использование: &f/ask <вопрос>\n&7Пример: &f/ask как скрафтить алмазную кирку?")!!

    val askxThinkingMessage = config.getString("messages.askx_thinking", "&eАнализирую ваш запрос и генерирую команды...")!!
    val askxErrorMessage = config.getString("messages.askx_error", "&cНе удалось выполнить команды. Попробуйте переформулировать запрос или обратитесь к администратору.")!!
    val askxUsageMessage = config.getString("messages.askx_usage", "&7Использование: &f/askx <описание действия>\n&7Пример: &f/askx дай мне алмазный меч с остротой 5")!!
    val blockedCommandMessage = config.getString("messages.blocked_command", "&cЭта команда заблокирована для выполнения через /askx из соображений безопасности.")!!

    val cancelledMessage = config.getString("messages.cancelled", "&7Остановил.")!!
    val nothingToCancelMessage = config.getString("messages.nothing_to_cancel", "&7Сейчас я ничего не делаю.")!!

    val reloadSuccessMessage = config.getString("messages.reload_success", "&aКонфигурация MCBuddy успешно перезагружена!")!!
    val reloadErrorMessage = config.getString("messages.reload_error", "&cОшибка при перезагрузке конфигурации: {error}")!!

    val noCommandsToExecuteMessage = config.getString("messages.no_commands_to_execute", "&eНет команд для выполнения")!!
    val commandsExecutingMessage = config.getString("messages.commands_executing", "&aВыполняю {count} команд{sequence}...")!!
    val commandExecutedMessage = config.getString("messages.command_executed", "&aВыполнено: /{command}")!!
    val commandFailedMessage = config.getString("messages.command_failed", "&cОшибка: /{command}")!!
    val commandExecutionErrorMessage = config.getString("messages.command_execution_error", "&cОшибка выполнения: /{command}")!!

    init {
      plugin.logger.info("✅ Messages Settings loaded")
    }
  }

  val tools = Tools() ; inner class Tools(config: FileConfiguration = plugin.config) {
    private val switches = config.getConfigurationSection("tools.enabled")
    val structureRadius = config.getInt("tools.locate_structure_radius", 64).coerceIn(1, 128)

    /** Missing keys mean on: an old config gets every tool. */
    fun enabled(name: String) = switches?.getBoolean(name, true) ?: true

    init {
      plugin.logger.info("✅ Tool Settings loaded")
    }
  }

  val blockedCommands = BlockedCommands() ; inner class BlockedCommands(config: FileConfiguration = plugin.config) {
    val commands = config.getStringList("blocked_commands").map(String::lowercase).toSet()

    init {
      plugin.logger.info("✅ Blocked Commands loaded")
    }
  }

  val errorMessages = ErrorMessages() ; inner class ErrorMessages(config: FileConfiguration = plugin.config) {
    // API ошибки
    val apiErrors = config.getConfigurationSection("error_messages.api_errors")?.let { section ->
      section.getKeys(false).associateWith { key -> section.getString(key, "&cОшибка: $key")!! }
    } ?: emptyMap()
    
    // HTTP ошибки
    val httpErrors = config.getConfigurationSection("error_messages.http_errors")?.let { section ->
      section.getKeys(false).mapNotNull { key -> 
        val statusCode = key.toIntOrNull()
        if (statusCode != null) {
          statusCode to section.getString(key, "&cHTTP $statusCode ошибка")!!
        } else null
      }.toMap()
    } ?: emptyMap()
    
    // Системные ошибки
    val systemErrors = config.getConfigurationSection("error_messages.system_errors")?.let { section ->
      mapOf(
        "timeout" to section.getString("timeout", "&cТаймаут")!!,
        "network" to section.getString("network", "&cСетевая ошибка")!!,
        "default" to section.getString("default", "&cОшибка")!!
      )
    } ?: mapOf(
      "timeout" to "&cТаймаут",
      "network" to "&cСетевая ошибка", 
      "default" to "&cОшибка"
    )

    init {
      plugin.logger.info("✅ Error Messages loaded (API: ${apiErrors.size}, HTTP: ${httpErrors.size}, System: ${systemErrors.size})")
    }
  }

  init {
    plugin.logger.info("✅ Configuration successfully loaded")
  }

  private companion object {
    val DEFAULT_VERBS = listOf("Обдумываю", "Прикидываю", "Вспоминаю рецепты", "Сверяюсь с вики", "Копаю глубже", "Колдую", "Размышляю", "Соображаю")
    val DEFAULT_ASKX_VERBS = listOf("Подбираю команды", "Сверяю синтаксис", "Собираю команду", "Прикидываю")
  }
}
