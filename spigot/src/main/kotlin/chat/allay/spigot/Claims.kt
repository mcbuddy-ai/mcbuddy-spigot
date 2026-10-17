package chat.allay.spigot

import me.ryanhamshire.GriefPrevention.ClaimPermission
import me.ryanhamshire.GriefPrevention.GriefPrevention
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.block.BlockState
import org.bukkit.entity.Player
import org.bukkit.loot.Lootable
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Level

/**
 * Which containers Allay may tell a player about: the ones they could open themself. A container outside any
 * claim is anyone's to open; one in a claim needs container trust there, as GriefPrevention decides. An unopened
 * structure chest (its loot table still waits) stays hidden from everyone, since Paper's anti-xray hides it too.
 * Without GriefPrevention, every container but those is open. Runs on the server thread, where claims live.
 */
object Claims {
  val available: Boolean get() = Bukkit.getPluginManager().isPluginEnabled("GriefPrevention")

  private var warned = false

  fun mayOpen(player: Player, state: BlockState): Boolean {
    if ((state as? Lootable)?.lootTable != null) return false
    if (!available) return true
    return try {
      GriefPreventionHook.mayOpen(player, state.location)
    } catch (error: Throwable) {
      // A GriefPrevention whose API moved: closed is the safe side, and one line in the log says why.
      if (!warned) {
        warned = true
        JavaPlugin.getProvidingPlugin(Claims::class.java).logger.log(Level.WARNING, "Couldn't ask GriefPrevention about a claim, so Allay keeps claimed containers hidden", error)
      }
      false
    }
  }

  // Only touched when GriefPrevention is enabled; its classes aren't on the classpath otherwise.
  private object GriefPreventionHook {
    fun mayOpen(player: Player, location: Location): Boolean {
      val plugin = GriefPrevention.instance ?: return true
      if (!plugin.claimsEnabledForWorld(location.world)) return true
      val claim = plugin.dataStore.getClaimAt(location, false, null) ?: return true
      return claim.checkPermission(player, ClaimPermission.Inventory, null) == null
    }
  }
}
