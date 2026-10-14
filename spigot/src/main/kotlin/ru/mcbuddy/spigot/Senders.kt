package ru.mcbuddy.spigot

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import ru.mcbuddy.core.Asker
import ru.mcbuddy.core.Lang
import ru.mcbuddy.core.Run
import java.util.UUID

/** A Bukkit sender as the core sees whoever asked. */
class SenderAsker(val sender: CommandSender) : Asker {
  override val name: String get() = sender.name
  override val uuid: UUID? get() = (sender as? Player)?.uniqueId
  override val operator: Boolean get() = sender.isOp
  override val locale: String? get() = (sender as? Player)?.locale
  override fun can(permission: String) = sender.hasPermission(permission)
}

/** Who asked this run, as Bukkit knows them. */
val Run.sender: CommandSender get() = (asker as SenderAsker).sender

/** Sends the message unless the server turned it off with an empty text. */
fun Lang.send(to: CommandSender, key: String, vararg values: Pair<String, Any?>) {
  val text = text(key, *values)
  if (text.isNotEmpty()) to.sendMessage(Minecraft.formatMessage(text))
}
