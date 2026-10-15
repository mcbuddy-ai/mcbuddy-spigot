package chat.allay.spigot

import org.bukkit.command.CommandSender
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.metadata.Metadatable

/**
 * Vanished players, as vanish plugins mark them: SuperVanish, PremiumVanish, Essentials, and others set the
 * Bukkit metadata `vanished`. Allay never names, counts, or shows a vanished player to anyone but that player
 * and operators. Metadata belongs to the server thread; tools run there.
 */
object Vanish {
  fun vanished(target: Metadatable): Boolean = target.getMetadata("vanished").any { it.asBoolean() }

  /** Whether [viewer] may learn about [other] through Allay: themself, an operator, or anyone while [other] isn't vanished. */
  fun visible(viewer: CommandSender?, other: Entity): Boolean =
    other !is Player || other == viewer || viewer?.isOp == true || !vanished(other)

  fun <T : Entity> visibleTo(viewer: CommandSender?, others: Iterable<T>): List<T> = others.filter { visible(viewer, it) }
}
