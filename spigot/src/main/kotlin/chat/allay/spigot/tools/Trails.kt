package chat.allay.spigot.tools

import chat.allay.core.tools.ToolError

import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import java.util.UUID

/**
 * Particle trails toward a spot that only the player sees. One per player: a new trail replaces the old one, and a
 * trail ends once the player arrives, so a ten-minute trail doesn't hang over them after they're there.
 */
object Trails {
  /** The looks the assistant picks from; the names are what `point_to` takes as `style`. */
  enum class Style(val particle: Particle) {
    SPARKLE(Particle.END_ROD), FLAME(Particle.FLAME), SOUL(Particle.SOUL_FIRE_FLAME), GLOW(Particle.GLOW), MAGIC(Particle.WITCH),
    SPARK(Particle.ELECTRIC_SPARK), CHERRY(Particle.CHERRY_LEAVES), NOTES(Particle.NOTE), HEARTS(Particle.HEART),
    SNOW(Particle.SNOWFLAKE), TOTEM(Particle.TOTEM_OF_UNDYING), HAPPY(Particle.HAPPY_VILLAGER);

    companion object {
      /** The style the model named; none means the classic sparkle. */
      fun parse(name: String?): Style = name?.trim()?.ifEmpty { null }?.let { wanted ->
        entries.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
          ?: throw ToolError("style must be one of ${entries.joinToString { it.name.lowercase() }}")
      } ?: SPARKLE
    }
  }

  /** Closer than this, horizontally, the player is there. */
  private const val ARRIVED = 3.0
  /** Closer than this, a column of particles marks the spot itself. */
  private const val MARKER = 48.0

  // Touched on the server thread only.
  private val running = HashMap<UUID, BukkitTask>()

  /** Shows the way to [target] for [seconds]; [arrived] runs once if the player gets there before it ends. */
  fun show(plugin: Plugin, player: Player, target: Location, seconds: Int, style: Style = Style.SPARKLE, arrived: () -> Unit = {}) {
    stop(player)
    // A particle that needs data (a color, a block) would throw; the styles above need none, but a game update may change that.
    val particle = style.particle.takeIf { it.dataType == Void::class.java } ?: Particle.END_ROD
    val pulses = seconds * 2
    var pulse = 0
    lateinit var task: BukkitTask
    // Two pulses a second, so the line follows the player as they turn and walk.
    task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
      if (!player.isOnline || player.world != target.world || pulse++ >= pulses) return@Runnable end(player, task)
      val dx = target.x - player.location.x
      val dz = target.z - player.location.z
      val distance = Math.hypot(dx, dz)
      if (distance <= ARRIVED) {
        player.spawnParticle(particle, target.clone().add(0.0, 1.0, 0.0), 30, 0.6, 0.8, 0.6, 0.02)
        end(player, task)
        arrived()
        return@Runnable
      }
      line(player, target, particle)
      if (distance <= MARKER) column(player, target, particle)
    }, 0L, 10L)
    running[player.uniqueId] = task
  }

  /** Ends the player's trail; false when none was running. */
  fun stop(player: Player): Boolean = running.remove(player.uniqueId)?.also(BukkitTask::cancel) != null

  private fun end(player: Player, task: BukkitTask) {
    task.cancel()
    running.remove(player.uniqueId, task)
  }

  /** A short line from the player's chest toward the target. */
  private fun line(player: Player, target: Location, particle: Particle) {
    val from = player.eyeLocation.subtract(0.0, 0.4, 0.0)
    val way = target.toVector().subtract(from.toVector())
    if (way.lengthSquared() < 1) return
    val step = way.normalize().multiply(0.5)
    val point = from.clone()
    repeat(24) {
      point.add(step)
      player.spawnParticle(particle, point, 1, 0.0, 0.0, 0.0, 0.0)
    }
  }

  /** A pillar over the spot, so the player sees where exactly it is once it's near. */
  private fun column(player: Player, target: Location, particle: Particle) {
    val point = target.clone()
    repeat(12) {
      player.spawnParticle(particle, point, 1, 0.05, 0.0, 0.05, 0.0)
      point.add(0.0, 0.5, 0.0)
    }
  }
}
