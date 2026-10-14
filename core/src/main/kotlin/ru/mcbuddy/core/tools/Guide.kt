package ru.mcbuddy.core.tools

import ru.mcbuddy.core.Permissions

/** What MCBuddy tells the model about itself: the commands and chat mode's prefixes a player may use. Texts are English, for the model. */
object Guide {
  /** Every command with the permission it needs and a line for the model. */
  private val COMMANDS = listOf(
    Triple("/ask <question>", Permissions.ASK, "a private question; only the player sees it and the answer"),
    Triple("/askall <question>", Permissions.ASKALL, "a question every player online sees, with the answer"),
    Triple("/askx <request>", Permissions.ASKX, "turns a request into commands and runs them as the player"),
    Triple("/buddy", Permissions.ASK, "opens a settings window: chat mode and its tip, time zone, memory, starting over, and the windows below"),
    Triple("/buddy chat [on|off]", Permissions.CHAT, "chat mode: everything the player writes in chat goes to MCBuddy"),
    Triple("/buddy hints [on|off]", Permissions.CHAT, "the tip about chat mode under answers"),
    Triple("/buddy cancel", Permissions.ASK, "stops the answer being written"),
    Triple("/buddy new", Permissions.ASK, "starts the conversation over; memories stay"),
    Triple("/buddy memory", Permissions.ASK, "lists what MCBuddy remembers about the player"),
    Triple("/buddy forget <number|all>", Permissions.ASK, "forgets one remembered fact, or all of them"),
    Triple("/buddy recipe <item>", Permissions.ASK, "opens the recipe window for an item"),
    Triple("/buddy enchants", Permissions.ASK, "opens the enchanting window for the item in hand"),
    Triple("/buddy villagers", Permissions.ASK, "opens the window of villagers around and their trades"),
    Triple("/buddy reload", Permissions.RELOAD, "reloads MCBuddy's settings and languages"),
  )

  /** The commands a player with these permissions may use, as usage to what it does. */
  fun commands(can: (String) -> Boolean): List<Pair<String, String>> = COMMANDS.filter { can(it.second) }.map { it.first to it.third }

  /** Chat mode's prefixes this player may use; none without chat mode. */
  fun prefixes(can: (String) -> Boolean): Map<String, String> = if (!can(Permissions.CHAT)) emptyMap() else buildMap {
    put("plain text", "a private question, like /ask")
    if (can(Permissions.ASKX)) put("!", "commands, like /askx")
    if (can(Permissions.ASKALL)) put("?", "a question everyone sees, like /askall")
    put("~", "an ordinary chat message to the other players")
  }
}
