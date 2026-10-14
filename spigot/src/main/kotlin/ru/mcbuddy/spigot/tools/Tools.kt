package ru.mcbuddy.spigot.tools

import ru.mcbuddy.core.*
import ru.mcbuddy.core.tools.*

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.mcbuddy.core.Config
import ru.mcbuddy.core.Lang
import ru.mcbuddy.core.PlayerSettings
import ru.mcbuddy.core.tools.Books
import ru.mcbuddy.core.tools.Button
import ru.mcbuddy.core.tools.ClientTool
import ru.mcbuddy.core.tools.Notes
import ru.mcbuddy.core.tools.TimeZones
import ru.mcbuddy.spigot.ChatMode
import ru.mcbuddy.spigot.Locales

/** What tools keep between calls, built once and shared by every run. Limits are read on use, so `/buddy reload` applies them. */
class Features(
  val plugin: Plugin,
  val locales: Locales,
  val reminders: Reminders,
  val notes: Notes,
  val goals: Goals,
  val books: Books,
  val deaths: Deaths,
  val zones: TimeZones,
  val settings: PlayerSettings,
  val chat: ChatMode,
  /** Names the server's shared thread of public questions on the backend. */
  val serverId: String,
  /** The settings now; `/buddy reload` replaces them. */
  val config: () -> Config,
  val structureRadius: () -> Int,
  val biomeRadius: () -> Int,
  val villagerRadius: () -> Int,
)

/**
 * Who asked. Tools see only this sender: the owner decided the agent may learn about the asker alone.
 * [buttons] collects what goes under the answer; the handler shows them once the answer is out.
 */
class ToolContext(val sender: CommandSender, val features: Features, val buttons: MutableList<Button> = java.util.Collections.synchronizedList(mutableListOf())) {
  /** The asker's language, for the summaries under tool lines. */
  val lang: Lang get() = features.locales.of(sender)

  val plugin: Plugin get() = features.plugin
  val reminders: Reminders get() = features.reminders
  val notes: Notes get() = features.notes
  val structureRadius: Int get() = features.structureRadius()
  val player: Player? get() = sender as? Player

  fun requirePlayer(): Player = player ?: throw ru.mcbuddy.core.tools.ToolError("there is no player: the question came from the console")
}

/** A game tool on Bukkit: it reads the world through the [ToolContext]. */
typealias Tool = ClientTool<ToolContext>

/** Every tool this plugin offers. */
object Tools {
  fun all(): List<Tool> = PlayerTools.all + LookTools.all + LocationTools.all + WorldTools.all + KnowledgeTools.all + InspectTools.all + CareTools.all +
    NotebookTools.all + ActionTools.all + InteractTools.all + GoalTools.all + BuddyTools.all + OperatorTools.all
}
