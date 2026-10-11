package ru.mcbuddy.spigot.tools

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ru.mcbuddy.spigot.Plurals
import java.time.Instant

/** Harmless things any player may ask for: nothing here creates items or changes the world. */
object ActionTools {
  private fun tool(name: String, body: (ToolContext, Player, JsonObject) -> ToolOutput) = object : ClientTool {
    override val name = name
    override fun run(context: ToolContext, args: JsonObject) = body(context, context.requirePlayer(), args)
  }

  private val pointTo = tool("point_to") { context, player, args ->
    val x = args.double("x") ?: throw ToolError("x is required")
    val z = args.double("z") ?: throw ToolError("z is required")
    val world = player.world
    val y = (args.double("y") ?: player.location.y).coerceIn(world.minHeight.toDouble(), world.maxHeight - 1.0)
    val limit = world.worldBorder.size / 2 + 1
    val center = world.worldBorder.center
    if (Math.abs(x - center.x) > limit || Math.abs(z - center.z) > limit) throw ToolError("the point is outside the world border")
    val target = Location(world, x, y, z)
    // Compasses point at this only in the overworld; elsewhere the trail still shows the way.
    player.compassTarget = target
    trail(context, player, target)
    val dx = x - player.location.x
    val dz = z - player.location.z
    val distance = Math.round(Math.hypot(dx, dz))
    val direction = Facts.direction(dx, dz)
    val result = buildJsonObject {
      put("distance", distance)
      put("direction", direction)
      put("compass", world.environment == org.bukkit.World.Environment.NORMAL)
    }
    ToolOutput(result, "$distance ${Plurals.ru(distance, "блок", "блока", "блоков")} на ${Facts.DIRECTIONS_RU[direction]}")
  }

  /** A short line of particles from the player toward the target; only this player sees it. */
  private fun trail(context: ToolContext, player: Player, target: Location) {
    repeat(4) { pulse ->
      context.plugin.server.scheduler.runTaskLater(context.plugin, Runnable {
        if (!player.isOnline || player.world != target.world) return@Runnable
        val from = player.eyeLocation.subtract(0.0, 0.4, 0.0)
        val way = target.toVector().subtract(from.toVector())
        if (way.lengthSquared() < 1) return@Runnable
        val step = way.normalize().multiply(0.5)
        val point = from.clone()
        repeat(24) {
          point.add(step)
          player.spawnParticle(Particle.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0)
        }
      }, pulse * 10L)
    }
  }

  private val sortInventory = tool("sort_inventory") { _, player, _ ->
    val inventory = player.inventory
    // Slots 9–35: the hotbar is muscle memory, armor and offhand live elsewhere.
    val main = (9..35).map { inventory.getItem(it)?.takeUnless { item -> item.type.isAir } }
    val sorted = Sorter.sort(
      items = main,
      amount = ItemStack::getAmount,
      maxStack = ItemStack::getMaxStackSize,
      similar = ItemStack::isSimilar,
      order = compareBy<ItemStack> { it.type.key.key }.thenByDescending { it.amount },
      withAmount = { item, amount -> item.clone().apply { this.amount = amount } },
    ) ?: throw ToolError("the inventory has overstacked items and can't be sorted safely")
    sorted.slots.forEachIndexed { index, item -> inventory.setItem(9 + index, item) }
    val merged = sorted.merged.toLong()
    ToolOutput(buildJsonObject { put("merged_stacks", merged) }, if (merged > 0) "объединил $merged ${Plurals.ru(merged, "стак", "стака", "стаков")}" else "разложил по порядку")
  }

  private val reminder = tool("set_reminder") { context, player, args ->
    val minutes = args.int("minutes") ?: throw ToolError("minutes is required")
    if (minutes !in 1..120) throw ToolError("minutes must be 1–120")
    val text = args.string("text")?.take(100) ?: throw ToolError("text is required")
    val reminder = context.reminders.add(player, minutes, text)
    ToolOutput(buildJsonObject { put("id", reminder.id); put("minutes", minutes); put("text", text) }, "через $minutes ${Plurals.ru(minutes.toLong(), "минуту", "минуты", "минут")}")
  }

  private val reminders = tool("get_reminders") { context, player, _ ->
    val list = context.reminders.of(player).list()
    val now = System.currentTimeMillis()
    val result = buildJsonArray {
      list.forEach { reminder ->
        add(buildJsonObject {
          put("id", reminder.id)
          put("text", reminder.text)
          put("minutes_left", ((reminder.dueAt - now).coerceAtLeast(0) + 59_999) / 60_000)
          put("due_at", Instant.ofEpochMilli(reminder.dueAt).toString())
        })
      }
    }
    val count = list.size.toLong()
    ToolOutput(result, if (count == 0L) "напоминаний нет" else "$count ${Plurals.ru(count, "напоминание", "напоминания", "напоминаний")}")
  }

  private val cancelReminder = tool("cancel_reminder") { context, player, args ->
    val book = context.reminders.of(player)
    val cancelled = when {
      args.bool("all") == true -> book.cancelAll()
      else -> book.cancel(args.string("id") ?: throw ToolError("id is required, or all: true"))
    }
    if (cancelled == 0 && args.bool("all") != true) throw ToolError("there is no reminder with this id")
    val count = cancelled.toLong()
    ToolOutput(buildJsonObject { put("cancelled", cancelled) }, "отменил $count ${Plurals.ru(count, "напоминание", "напоминания", "напоминаний")}")
  }

  val all = listOf(pointTo, sortInventory, reminder, reminders, cancelReminder)
}
