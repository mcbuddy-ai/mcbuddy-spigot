package ru.mcbuddy.spigot

import ru.mcbuddy.spigot.ChatMode.Route
import ru.mcbuddy.spigot.tools.BuddyTools
import ru.mcbuddy.spigot.tools.WorldTools
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatModeTest {
  @Test fun `prefixes route a line the same with or without spaces`() {
    assertEquals(Route.ASK to "how do I find netherite?", ChatMode.route("  how do I find netherite? "))
    assertEquals(Route.ASKX to "give me bread", ChatMode.route("!give me bread"))
    assertEquals(Route.ASKX to "give me bread", ChatMode.route("!   give me bread"))
    assertEquals(Route.PUBLIC to "where's the nearest village?", ChatMode.route("? where's the nearest village?"))
    assertEquals(Route.CHAT to "brb", ChatMode.route("~brb"))
    assertEquals(Route.CHAT to "brb", ChatMode.route(" ~  brb"))
    // Only the first prefix counts; the rest is the message.
    assertEquals(Route.ASKX to "?what", ChatMode.route("!?what"))
    assertEquals(Route.PUBLIC to "", ChatMode.route("?"))
  }

  @Test fun `the guide lists only what the player may use`() {
    val player = setOf(Permissions.ASK, Permissions.ASKALL, Permissions.CHAT)
    val usages = BuddyTools.commands(player::contains).map { it.first }
    assertTrue("/ask <question>" in usages && "/askall <question>" in usages && "/buddy chat [on|off]" in usages)
    assertTrue(usages.none { it.startsWith("/askx") || it == "/buddy reload" })
    assertEquals(setOf("plain text", "?", "~"), BuddyTools.prefixes(player::contains).keys)
    assertEquals(setOf("plain text", "!", "?", "~"), BuddyTools.prefixes((player + Permissions.ASKX)::contains).keys)
    assertEquals(emptyMap(), BuddyTools.prefixes(setOf(Permissions.ASK)::contains))
  }

  @Test fun `plugin descriptions are one short line`() {
    assertEquals("Graves for your items", WorldTools.shortDescription("  Graves for\n your   items "))
    assertNull(WorldTools.shortDescription("   "))
    val long = WorldTools.shortDescription("word ".repeat(60))!!
    assertEquals(120, long.length)
    assertTrue(long.endsWith("…"))
  }
}
