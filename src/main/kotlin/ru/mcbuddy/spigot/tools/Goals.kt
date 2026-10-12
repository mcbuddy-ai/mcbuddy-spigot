package ru.mcbuddy.spigot.tools

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Registry
import org.bukkit.Sound
import org.bukkit.Statistic
import org.bukkit.boss.BarColor
import org.bukkit.boss.BarStyle
import org.bukkit.boss.BossBar
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import ru.mcbuddy.spigot.Minecraft.formatMessage
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot
import kotlin.math.max

/**
 * A goal the player set through the assistant, shown as a boss bar. Progress counts only what happens after it was set:
 * statistics count from [baseline], pickups from events, places and levels from where the player is now.
 */
@Serializable
data class Goal(
  val id: String,
  val kind: String,
  val title: String,
  val target: Long,
  val color: String = "green",
  val style: String = "solid",
  val statistic: String? = null,
  val material: String? = null,
  val entity: String? = null,
  val advancement: String? = null,
  val x: Int? = null,
  val z: Int? = null,
  val radius: Int? = null,
  val biome: String? = null,
  val dimension: String? = null,
  /** The statistic's value, or the distance to the place, when the goal was set. */
  val baseline: Long = 0,
  /** Statistics in centimeters show in blocks: 100 raw units each. */
  val scale: Int = 1,
  val progress: Long = 0,
  val createdAt: Long,
) {
  val done get() = progress >= target

  /** The bar's fill, 0–1. */
  val fraction get() = if (target <= 0) 1.0 else (progress.toDouble() / target).coerceIn(0.0, 1.0)

  companion object {
    val KINDS = setOf("stat", "pickup", "level", "advancement", "reach", "biome", "dimension")
  }
}

/**
 * One player's goals, apart from Bukkit so tests can reach it. Items from the player's own death don't count
 * as picked up again: [deathDrops] holds what they dropped, and pickups use it up first.
 */
class GoalBook(initial: List<Goal>, private val max: () -> Int, private val newId: () -> String = ReminderBook::shortId) {
  private val goals = initial.toMutableList()
  private val deathDrops = HashMap<String, Long>()

  @Synchronized fun add(make: (String) -> Goal): Goal {
    val cap = max()
    if (goals.size >= cap) throw ToolError("this player already has $cap goals; cancel one first")
    val id = generateSequence(newId).first { candidate -> goals.none { it.id == candidate } }
    return make(id).also { goals += it }
  }

  @Synchronized fun all(): List<Goal> = goals.toList()

  @Synchronized fun remove(id: String): Goal? = goals.firstOrNull { it.id.equals(id, ignoreCase = true) }?.also { goals.remove(it) }

  @Synchronized fun clear(): Int = goals.size.also { goals.clear() }

  /** Sets a goal's progress; returns the goal as it is now, or null when it's gone. */
  @Synchronized fun progress(id: String, value: Long): Goal? {
    val index = goals.indexOfFirst { it.id == id }
    if (index < 0) return null
    if (goals[index].progress != value) goals[index] = goals[index].copy(progress = value.coerceAtLeast(0))
    return goals[index]
  }

  @Synchronized fun died(drops: Map<String, Int>) {
    drops.forEach { (item, count) -> deathDrops.merge(item, count.toLong(), Long::plus) }
  }

  /** Counts a pickup toward every pickup goal for this item; returns the goals that moved. */
  @Synchronized fun pickedUp(item: String, count: Int): List<Goal> {
    val owed = deathDrops[item] ?: 0
    val repaid = minOf(owed, count.toLong())
    if (repaid > 0) deathDrops[item] = owed - repaid
    val fresh = count - repaid
    if (fresh <= 0) return emptyList()
    return goals.withIndex().filter { (_, goal) -> goal.kind == "pickup" && goal.material == item }.map { (index, goal) ->
      goal.copy(progress = goal.progress + fresh).also { goals[index] = it }
    }
  }
}

/** Goals on disk in `goals/<uuid>.json`, their boss bars, and the events and ticks that move them. */
class Goals(private val plugin: Plugin, private val max: () -> Int, private val doneMessage: () -> String) : Listener {
  private val folder = File(plugin.dataFolder, "goals")
  private val books = ConcurrentHashMap<UUID, GoalBook>()
  private val bars = ConcurrentHashMap<String, BossBar>()
  private val json = Json { ignoreUnknownKeys = true }
  private var ticker: BukkitTask? = null

  fun of(uuid: UUID): GoalBook = books.computeIfAbsent(uuid) { GoalBook(load(it), max) }

  fun start() {
    ticker = plugin.server.scheduler.runTaskTimer(plugin, Runnable { plugin.server.onlinePlayers.forEach(::poll) }, 20L, 20L)
    plugin.server.onlinePlayers.forEach(::show)
  }

  fun stop() {
    ticker?.cancel()
    bars.values.forEach(BossBar::removeAll)
    bars.clear()
  }

  /** Validates a goal from the assistant's arguments and starts tracking it for the player. */
  fun add(player: Player, spec: Goal): Goal {
    val book = of(player.uniqueId)
    val start = baseline(player, spec)
    // A place's bar fills with the distance covered, so its target is the distance at the start.
    val target = if (spec.kind == "reach") max(1, start) else spec.target
    val goal = book.add { id -> spec.copy(id = id, baseline = start, target = target, createdAt = System.currentTimeMillis()) }
    save(player.uniqueId)
    // The player may already be there, or at that level.
    poll(player)
    show(player)
    return book.all().firstOrNull { it.id == goal.id } ?: goal
  }

  fun remove(player: Player, id: String?): Int {
    val book = of(player.uniqueId)
    val removed = if (id == null) book.all().map { it.id }.also { book.clear() } else listOfNotNull(book.remove(id)?.id)
    removed.forEach { bars.remove(key(player.uniqueId, it))?.removeAll() }
    if (removed.isNotEmpty()) save(player.uniqueId)
    return removed.size
  }

  /** What the bar says: the title, then where the player stands. */
  fun label(goal: Goal): String = when (goal.kind) {
    "reach" -> "${goal.title} · ${max(0, goal.target - goal.progress)} ${ru.mcbuddy.spigot.Plurals.ru(max(0, goal.target - goal.progress), "блок", "блока", "блоков")}"
    "advancement", "biome", "dimension" -> goal.title
    else -> "${goal.title} · ${goal.progress / goal.scale}/${goal.target / goal.scale}"
  }

  private fun baseline(player: Player, goal: Goal): Long = when (goal.kind) {
    "stat" -> statistic(player, goal)
    "reach" -> distance(player, goal)?.toLong() ?: 0
    else -> 0
  }

  private fun statistic(player: Player, goal: Goal): Long {
    val statistic = runCatching { Statistic.valueOf(goal.statistic!!.uppercase()) }.getOrNull() ?: return 0
    return runCatching {
      when (statistic.type) {
        Statistic.Type.UNTYPED -> player.getStatistic(statistic)
        Statistic.Type.ENTITY -> player.getStatistic(statistic, entityType(goal.entity)!!)
        else -> player.getStatistic(statistic, Material.matchMaterial(goal.material!!)!!)
      }.toLong()
    }.getOrDefault(0)
  }

  private fun distance(player: Player, goal: Goal): Double? {
    if (goal.dimension != null && player.world.environment.name.lowercase() != goal.dimension) return null
    return hypot(player.location.x - goal.x!!, player.location.z - goal.z!!)
  }

  /** Reads every goal that follows the player's state rather than events. */
  private fun poll(player: Player) {
    val book = books[player.uniqueId] ?: return
    for (goal in book.all()) {
      val value = when (goal.kind) {
        "stat" -> statistic(player, goal) - goal.baseline
        "level" -> player.level.toLong()
        "advancement" -> {
          val advancement = goal.advancement?.let(NamespacedKey::fromString)?.let(Bukkit::getAdvancement)
          if (advancement != null && player.getAdvancementProgress(advancement).isDone) 1 else 0
        }
        "reach" -> distance(player, goal)?.let { d -> if (d <= (goal.radius ?: 10)) goal.target else (goal.baseline - d.toLong()).coerceIn(0, goal.target - 1) } ?: goal.progress
        "biome" -> if (player.location.block.biome.key.key == goal.biome) 1 else 0
        "dimension" -> if (player.world.environment.name.lowercase() == goal.dimension) 1 else 0
        else -> continue
      }
      book.progress(goal.id, value)?.let { update(player, it) }
    }
  }

  private fun update(player: Player, goal: Goal) {
    if (goal.done) return complete(player, goal)
    val bar = bars[key(player.uniqueId, goal.id)] ?: return show(player)
    bar.setTitle(formatMessage(label(goal)))
    bar.progress = goal.fraction
  }

  private fun complete(player: Player, goal: Goal) {
    of(player.uniqueId).remove(goal.id)
    save(player.uniqueId)
    bars.remove(key(player.uniqueId, goal.id))?.removeAll()
    player.playSound(player.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1f)
    player.sendMessage(formatMessage(doneMessage().replace("{title}", goal.title)))
  }

  private fun show(player: Player) {
    val book = books[player.uniqueId] ?: of(player.uniqueId)
    for (goal in book.all()) {
      bars.computeIfAbsent(key(player.uniqueId, goal.id)) {
        val color = runCatching { BarColor.valueOf(goal.color.uppercase()) }.getOrDefault(BarColor.GREEN)
        val style = runCatching { BarStyle.valueOf(goal.style.uppercase()) }.getOrDefault(BarStyle.SOLID)
        Bukkit.createBossBar(formatMessage(label(goal)), color, style)
      }.apply {
        progress = goal.fraction
        if (player !in players) addPlayer(player)
      }
    }
  }

  @EventHandler fun onJoin(event: PlayerJoinEvent) = show(event.player)

  @EventHandler fun onQuit(event: PlayerQuitEvent) {
    val uuid = event.player.uniqueId
    bars.keys.filter { it.startsWith("$uuid:") }.forEach { bars.remove(it)?.removeAll() }
    books.remove(uuid)
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  fun onPickup(event: EntityPickupItemEvent) {
    val player = event.entity as? Player ?: return
    val stack = event.item.itemStack
    val count = stack.amount - event.remaining
    // A player's own throws come back without counting; so do items from their death, through the book.
    if (count <= 0 || event.item.thrower == player.uniqueId) return
    val book = books[player.uniqueId] ?: return
    val moved = book.pickedUp(stack.type.key.key, count)
    if (moved.isEmpty()) return
    save(player.uniqueId)
    moved.forEach { update(player, it) }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  fun onDeath(event: PlayerDeathEvent) {
    val book = books[event.entity.uniqueId] ?: return
    book.died(event.drops.groupBy { it.type.key.key }.mapValues { (_, stacks) -> stacks.sumOf { it.amount } })
  }

  @EventHandler fun onAdvancement(event: PlayerAdvancementDoneEvent) = poll(event.player)

  fun save(uuid: UUID) {
    val book = books[uuid] ?: return
    folder.mkdirs()
    val target = File(folder, "$uuid.json")
    val goals = book.all()
    if (goals.isEmpty()) {
      target.delete()
      return
    }
    val temp = File(folder, "$uuid.json.tmp")
    temp.writeText(json.encodeToString(goals))
    Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
  }

  private fun load(uuid: UUID): List<Goal> {
    val file = File(folder, "$uuid.json")
    if (!file.exists()) return emptyList()
    return try {
      json.decodeFromString<List<Goal>>(file.readText())
    } catch (error: Exception) {
      plugin.logger.warning("Couldn't read goals of $uuid: ${error.message}; keeping it as $uuid.json.broken")
      file.renameTo(File(folder, "$uuid.json.broken"))
      emptyList()
    }
  }

  private fun key(uuid: UUID, id: String) = "$uuid:$id"

  companion object {
    fun entityType(name: String?): EntityType? = name?.lowercase()?.removePrefix("minecraft:")?.let { Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(it)) }
  }
}
