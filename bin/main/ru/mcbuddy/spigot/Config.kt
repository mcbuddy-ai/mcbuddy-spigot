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
    val userAgent = config.getString("http.user_agent", "MCBuddy-Spigot/1.1.0 (Minecraft Plugin)")!!
    val customHeaders = config.getConfigurationSection("http.custom_headers").let { it?.getKeys(false)?.associateWith(it::getString) } ?: emptyMap()

    init {
      plugin.logger.info("✅ HTTP Settings loaded")
    }
  }

  val ui = UI() ; inner class UI(val config: FileConfiguration = plugin.config) {
    val actionBarEnabled = config.getBoolean("ui.action_bar.enabled", true)
    val actionBarThinkingText = config.getString("ui.action_bar.thinking_text", "&eAI-ассистент думает")!!
    val actionBarAskxText = config.getString("ui.action_bar.askx_text", "&eГенерирую команды")!!
    val actionBarAnimationSpeed = config.getInt("ui.action_bar.animation_speed", 500).toLong()

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
      Registry.SOUNDS.get(NamespacedKey.fromString(config.getString(key, default)!!)!!)!!
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

  val blockedCommands = BlockedCommands() ; inner class BlockedCommands(config: FileConfiguration = plugin.config) {
    val commands = config.getStringList("blocked_commands").map(String::lowercase).toSet()

    init {
      plugin.logger.info("✅ Blocked Commands loaded")
    }
  }

  init {
    plugin.logger.info("✅ Configuration successfully loaded")
  }
}