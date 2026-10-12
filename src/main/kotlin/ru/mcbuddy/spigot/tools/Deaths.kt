package ru.mcbuddy.spigot.tools

import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** What a player lost when they last died, kept in memory: a restart leaves only the game's own death point. */
class Deaths : Listener {
  class Death(val location: Location, val at: Long, val drops: Map<String, Int>, val keptInventory: Boolean, val experience: Int)

  private val last = ConcurrentHashMap<UUID, Death>()

  fun of(player: UUID): Death? = last[player]

  @EventHandler(priority = EventPriority.MONITOR)
  fun onDeath(event: PlayerDeathEvent) {
    val drops = event.drops.groupBy { it.type.key.key }.mapValues { (_, stacks) -> stacks.sumOf { it.amount } }
    last[event.entity.uniqueId] = Death(event.entity.location.clone(), System.currentTimeMillis(), drops, event.keepInventory, event.droppedExp)
  }

  companion object {
    /** Items on the ground vanish after 5 minutes, counted only while their chunk is loaded. */
    const val DESPAWN_TICKS = 6_000
  }
}
