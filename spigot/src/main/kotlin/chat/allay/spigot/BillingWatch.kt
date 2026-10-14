package chat.allay.spigot

import chat.allay.core.*
import chat.allay.core.tools.*

import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.ChatColor
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask
import chat.allay.spigot.Minecraft.formatMessage

/**
 * Polls the subscription every 5 minutes, off the server thread, and tells operators online (and the console) about a
 * payment, a quota past 80% and 100%, and an end three days off. `subscription.json` keeps what they heard across restarts.
 */
class BillingWatch(
  private val plugin: JavaPlugin,
  private val locales: Locales,
  private val billing: Billing,
  private val credentials: Credentials,
  private val announcements: Announcements,
  private val commands: BillingCommands,
) {
  private var task: BukkitTask? = null

  fun start() {
    stop()
    task = plugin.server.scheduler.runTaskTimerAsynchronously(plugin, Runnable(::poll), 20L * 30, 20L * 60 * 5)
  }

  fun stop() {
    task?.cancel()
    task = null
  }

  private fun poll() {
    if (credentials.byok || billing.license.read() == null) return
    val status = runCatching { billing.status() }
      .onFailure { plugin.logger.fine("Couldn't check the subscription: ${credentials.scrub(it.toString())}") }
      .getOrNull() ?: return
    val notices = announcements.check(status)
    if (notices.isNotEmpty() && plugin.isEnabled) plugin.server.scheduler.runTask(plugin, Runnable { notices.forEach(::announce) })
  }

  private fun announce(notice: Notice) {
    val operators = plugin.server.onlinePlayers.filter { it.hasPermission(Permissions.ADMIN) }
    operators.forEach { player ->
      val lang = locales.of(player)
      val text = formatMessage(text(lang, notice, commands.zone(player)))
      if (notice is Notice.Paid) {
        player.spigot().sendMessage(*plain(text))
      } else {
        player.spigot().sendMessage(*plain(text), TextComponent(" "), commands.linkButton(lang, "billing.notice.renew"))
      }
    }
    val console = plugin.server.consoleSender
    plugin.logger.info(ChatColor.stripColor(formatMessage(text(locales.of(console), notice, commands.zone(console)))))
  }

  private fun text(lang: Lang, notice: Notice, zone: java.time.ZoneId): String = when (notice) {
    is Notice.Paid -> lang.text("billing.notice.paid", "date" to (BillingDates.format(lang, notice.until, zone) ?: "?"))
    is Notice.Quota -> {
      val key = if (notice.level >= 100) "billing.notice.quota_100" else "billing.notice.quota_80"
      // A week or a month that's spent refills only with a payment; a year's month says when.
      val date = BillingDates.format(lang, notice.resetsAt, zone)
      if (date == null) lang.text("${key}_no_reset") else lang.text(key, "date" to date)
    }
    is Notice.Expiring -> lang.text("billing.notice.expiring", "date" to (BillingDates.format(lang, notice.until, zone) ?: "?"))
  }
}
