@file:Suppress("SameParameterValue")

package ru.mcbuddy.spigot

import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.plugin.java.JavaPlugin
import ru.mcbuddy.spigot.tools.OperatorTools

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
    val statusFormat = config.getString("ui.action_bar.format", "&6{spinner} &6{verb}… &7({elapsed} · ↑ {input} · ↓ {output})")!!
    val answeringVerb = config.getString("ui.action_bar.answering", "Пишу ответ")!!
    val spinnerFrames = config.getString("ui.action_bar.spinner", "·✢✳✶✻✽")!!
    val frameMillis = config.getInt("ui.action_bar.frame_millis", 120).toLong().coerceAtLeast(50)
    val verbSeconds = config.getInt("ui.action_bar.verb_seconds", 3).toLong()
    val verbs = config.getStringList("ui.action_bar.verbs").ifEmpty { DEFAULT_VERBS }
    val askxVerbs = config.getStringList("ui.action_bar.askx_verbs").ifEmpty { DEFAULT_ASKX_VERBS }

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

    val buttonFormat = config.getString("ui.buttons.format", "&a[{label}]")!!
    val buttonHoverAsk = config.getString("ui.buttons.hover.ask", "&7Спросить: &f{question}")!!
    val buttonHoverPoint = config.getString("ui.buttons.hover.point", "&7Показать путь к &f{x} {y} {z}")!!
    val buttonHoverNote = config.getString("ui.buttons.hover.note", "&7Записать в блокнот: &f{text} &7({x} {y} {z})")!!
    val buttonHoverRecipe = config.getString("ui.buttons.hover.recipe", "&7Открыть рецепт")!!
    val buttonHoverEnchants = config.getString("ui.buttons.hover.enchants", "&7Открыть чары для предмета в руке")!!
    val buttonHoverVillagers = config.getString("ui.buttons.hover.villagers", "&7Открыть жителей рядом")!!
    val buttonHoverReadBook = config.getString("ui.buttons.hover.read_book", "&7Открыть книгу «{title}»")!!
    val buttonHoverTakeBook = config.getString("ui.buttons.hover.take_book", "&7Положить книгу «{title}» в инвентарь")!!
    val buttonReadBook = config.getString("ui.buttons.read_book", "Открыть книгу")!!
    val buttonTakeBook = config.getString("ui.buttons.take_book", "Взять книгу")!!
    val bookAuthor = config.getString("ui.book_author", "MCBuddy")!!

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
    val usageMessage = config.getString("messages.usage", "&7Использование: &f/ask <вопрос>\n&7Пример: &f/ask как скрафтить алмазную кирку?\n&7Остановить: &f/ask cancel&7, начать заново: &f/ask new&7, память: &f/ask memory")!!

    val askxThinkingMessage = config.getString("messages.askx_thinking", "&eАнализирую ваш запрос и генерирую команды...")!!
    val askxErrorMessage = config.getString("messages.askx_error", "&cНе удалось выполнить команды. Попробуйте переформулировать запрос или обратитесь к администратору.")!!
    val askxUsageMessage = config.getString("messages.askx_usage", "&7Использование: &f/askx <описание действия>\n&7Пример: &f/askx дай мне алмазный меч с остротой 5")!!
    val blockedCommandMessage = config.getString("messages.blocked_command", "&cЭта команда заблокирована для выполнения через /askx из соображений безопасности.")!!

    val cancelledMessage = config.getString("messages.cancelled", "&7Остановил.")!!
    val nothingToCancelMessage = config.getString("messages.nothing_to_cancel", "&7Сейчас я ничего не делаю.")!!
    val reminderMessage = config.getString("messages.reminder", "&e⏰ Напоминание: &f{text}")!!

    val sessionClearedMessage = config.getString("messages.session_cleared", "&aНачинаем разговор заново. Что я о тебе помню, осталось: &f/ask memory")!!
    val memoryHeaderMessage = config.getString("messages.memory_header", "&6Что я о тебе помню:")!!
    val memoryLineMessage = config.getString("messages.memory_line", "&e{n}. &f{text}")!!
    val memoryFooterMessage = config.getString("messages.memory_footer", "&7Забыть: &f/ask forget <номер|all>")!!
    val memoryEmptyMessage = config.getString("messages.memory_empty", "&7Я пока ничего о тебе не запомнил. Скажи «запомни, что…», и запомню.")!!
    val forgotMessage = config.getString("messages.forgot", "&aЗабыл: &f{text}")!!
    val forgotAllMessage = config.getString("messages.forgot_all", "&aЗабыл всё, что помнил о тебе ({count}).")!!
    val forgetUsageMessage = config.getString("messages.forget_usage", "&7Использование: &f/ask forget <номер|all>&7. Номера — в &f/ask memory")!!
    val forgetNotFoundMessage = config.getString("messages.forget_not_found", "&cНет записи с номером {n}. Список: &f/ask memory")!!
    val memoryUnsupportedMessage = config.getString("messages.memory_unsupported", "&cСервер MCBuddy пока не умеет хранить память.")!!

    val goalDoneMessage = config.getString("messages.goal_done", "&a✔ Цель выполнена: &f{title}")!!
    val buttonExpiredMessage = config.getString("messages.button_expired", "&7Эта кнопка уже не работает. Спроси ещё раз.")!!
    val buttonQuestionMessage = config.getString("messages.button_question", "&7› {question}")!!
    val pathShownMessage = config.getString("messages.path_shown", "&7Показываю путь: &f{distance} &7блоков на {direction}.")!!
    val pathOtherWorldMessage = config.getString("messages.path_other_world", "&7Эта точка в другом измерении.")!!
    val noteSavedMessage = config.getString("messages.note_saved", "&aЗаписал в блокнот: &f{text}")!!
    val bookTakenMessage = config.getString("messages.book_taken", "&aКнига у тебя в инвентаре.")!!
    val bookGoneMessage = config.getString("messages.book_gone", "&7Этой книги уже нет. Попроси расписать ещё раз.")!!
    val noRecipeMessage = config.getString("messages.no_recipe", "&7У этого предмета нет рецепта: его добывают, находят или выменивают.")!!
    val unknownItemMessage = config.getString("messages.unknown_item", "&cНет такого предмета: {item}")!!
    val recipeUsageMessage = config.getString("messages.recipe_usage", "&7Использование: &f/ask recipe <предмет>")!!
    val holdItemMessage = config.getString("messages.hold_item", "&7Возьми в руку предмет, который хочешь зачаровать.")!!
    val noEnchantsMessage = config.getString("messages.no_enchants", "&7На этот предмет нельзя наложить чары.")!!
    val noVillagersMessage = config.getString("messages.no_villagers", "&7Рядом нет жителей.")!!

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

    init {
      plugin.logger.info("✅ Tool Settings loaded")
    }
  }

  /** Texts of the recipe, enchanting, and villager windows. */
  val menus = Menus() ; inner class Menus(private val config: FileConfiguration = plugin.config) {
    private fun text(key: String, default: String) = config.getString("menus.$key", default)!!

    val recipeTitle = text("recipe.title", "Рецепт")
    val back = text("back", "&eНазад")
    val close = text("close", "&cЗакрыть")
    val previous = text("previous", "&eНазад по списку")
    val next = text("next", "&eДальше по списку")
    val recipePage = text("recipe.page", "&7Рецепт {n} из {total}")
    val recipeOpen = text("recipe.open", "&7Нажми — рецепт этого предмета")
    val recipeNone = text("recipe.none", "&8Не крафтится: добывается, выпадает или выменивается")
    val recipeMakes = text("recipe.makes", "&7Получится: {count} шт.")
    val recipeCooking = text("recipe.cooking", "&7{seconds} с, {xp} опыта")
    val recipeOptions = text("recipe.options", "&7Подойдёт любой из вариантов")

    val enchantTitle = text("enchant.title", "Чары")
    val planTitle = text("enchant.plan_title", "Порядок на наковальне")
    val enchantPick = text("enchant.pick", "&7Нажми, чтобы выбрать")
    val enchantSelected = text("enchant.selected", "&a✔ Выбрано")
    val enchantHas = text("enchant.has", "&aУже есть на предмете")
    val enchantConflict = text("enchant.conflict", "&cНе сочетается с выбранными или с чарами предмета")
    val enchantTreasure = text("enchant.treasure", "&7Сокровище: только из сундуков, у жителей и на рыбалке")
    val enchantCurse = text("enchant.curse", "&cПроклятие")
    val enchantPrice = text("enchant.price", "&7Уровень с книги стоит {cost} ур.")
    val enchantPenalty = text("enchant.penalty", "&7Штраф за прошлые работы: {penalty} ур.")
    val planButton = text("enchant.plan", "&aПоказать порядок")
    val planHint = text("enchant.plan_hint", "&7Выбери чары и нажми")
    val planEmpty = text("enchant.plan_empty", "&7Сначала выбери хотя бы одни чары")
    val planStep = text("enchant.step", "&eШаг {n}: {cost} ур.")
    val planTooExpensive = text("enchant.too_expensive", "&cСлишком дорого: наковальня берёт до 39 уровней")
    val planTotal = text("enchant.total", "&7Всего: {total} ур.")
    val planItem = text("enchant.item", "&7Твой предмет")
    val planPlus = text("enchant.plus", "&7+")
    val planResult = text("enchant.result", "&7=")

    val villagersTitle = text("villagers.title", "Жители рядом")
    val villagerWhere = text("villagers.where", "&7{distance} блоков на {direction}")
    val villagerLevel = text("villagers.level", "&7Уровень {level}")
    val villagerPath = text("villagers.path", "&7Нажми — показать путь")
    val tradePrice = text("villagers.price", "&7Цена: &f{price}")
    val tradeLeft = text("villagers.left", "&7Осталось сделок: {left}")
    val tradeOut = text("villagers.out", "&cРаспродано до пополнения")

    init {
      plugin.logger.info("✅ Menu texts loaded")
    }
  }

  val time = Time() ; inner class Time(config: FileConfiguration = plugin.config) {
    val zone = config.getString("time.zone", "Europe/Moscow")!!.let { zone -> zone.takeIf { ru.mcbuddy.spigot.tools.TimeZones.parse(it) != null } ?: "UTC" }
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
