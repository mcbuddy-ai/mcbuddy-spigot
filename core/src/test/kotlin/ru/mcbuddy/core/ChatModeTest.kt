package ru.mcbuddy.core

import ru.mcbuddy.core.Chat.Route
import ru.mcbuddy.core.tools.Guide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatModeTest {
  @Test fun `prefixes route a line the same with or without spaces`() {
    assertEquals(Route.ASK to "how do I find netherite?", Chat.route("  how do I find netherite? "))
    assertEquals(Route.ASKX to "give me bread", Chat.route("!give me bread"))
    assertEquals(Route.ASKX to "give me bread", Chat.route("!   give me bread"))
    assertEquals(Route.PUBLIC to "where's the nearest village?", Chat.route("? where's the nearest village?"))
    assertEquals(Route.CHAT to "brb", Chat.route("~brb"))
    assertEquals(Route.CHAT to "brb", Chat.route(" ~  brb"))
    // Only the first prefix counts; the rest is the message.
    assertEquals(Route.ASKX to "?what", Chat.route("!?what"))
    assertEquals(Route.PUBLIC to "", Chat.route("?"))
  }

  @Test fun `the guide lists only what the player may use`() {
    val player = setOf(Permissions.ASK, Permissions.ASKALL, Permissions.CHAT)
    val usages = Guide.commands(player::contains).map { it.first }
    assertTrue("/ask <question>" in usages && "/askall <question>" in usages && "/buddy chat [on|off]" in usages)
    assertTrue(usages.none { it.startsWith("/askx") || it == "/buddy reload" })
    assertEquals(setOf("plain text", "?", "~"), Guide.prefixes(player::contains).keys)
    assertEquals(setOf("plain text", "!", "?", "~"), Guide.prefixes((player + Permissions.ASKX)::contains).keys)
    assertEquals(emptyMap(), Guide.prefixes(setOf(Permissions.ASK)::contains))
  }
}
