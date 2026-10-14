package chat.allay.core

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Logger
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class McBuddyMigrationTest {
  private val plugins: File = createTempDirectory("plugins").toFile()
  private val old = File(plugins, "mcbuddy-spigot")
  private val new = File(plugins, "Allay")
  private val log = Logger.getAnonymousLogger().apply { useParentHandlers = false }

  @AfterTest fun clean() {
    plugins.deleteRecursively()
  }

  private fun shipped(path: String, version: String = "3.1.0") = javaClass.getResourceAsStream("/mcbuddy/$version/$path")!!.reader(Charsets.UTF_8).readText()

  private val uuid = "00000000-0000-0000-0000-000000000001"
  private val notes = """[{"id":"ab2c","text":"Fletcher sells arrows","x":1,"y":64,"z":2,"world":"world","dimension":"overworld","createdAt":1}]"""

  /** MCBuddy 3.1.0's folder on a server that has run it for a while. */
  private fun mcbuddy() {
    File(old, "notes").mkdirs()
    File(old, "notes/$uuid.json").writeText(notes)
    File(old, "goals").mkdirs()
    File(old, "goals/$uuid.json").writeText("""[{"id":"x2yz","kind":"level","title":"30","target":30,"createdAt":1}]""")
    File(old, "geoip").mkdirs()
    File(old, "geoip/country.mmdb").writeText("db")
    File(old, "lang").mkdirs()
    // English as 2.4 saved it on a server that has run since then: still nothing of the admin's own.
    File(old, "lang/en.yml").writeText(shipped("lang/en.yml", "2.4.0"))
    File(old, "lang/ru.yml").writeText(shipped("lang/ru.yml").replace("answer: \"&5✦ &dMCBuddy &8»&r {text}\"", "answer: \"&6MCBuddy говорит:&r {text}\""))
    File(old, "lang/it.yml").writeText("public:\n  answer: \"MCBuddy dice: {text}\"\n")
    File(old, "config.yml").writeText(
      """
      # /buddy reload applies changes; MCBuddy reads this file.
      server:
        url: "https://mcbuddy.ru"
      ui:
        book_author: "MCBuddy"
        sounds:
          success_sound: "ENTITY_EXPERIENCE_ORB_PICKUP"   # The answer is in
          success_volume: 0.5
          success_pitch: 1.2
          error_sound: "BLOCK_ANVIL_LAND"
          error_volume: 0.6
          error_pitch: 0.7
          thinking_sound: "BLOCK_NOTE_BLOCK_CHIME"
          thinking_volume: 0.8
          thinking_pitch: 1.0
      tools:
        enabled:
          get_buddy_guide: false
          buddy_settings: true
      """.trimIndent(),
    )
    File(old, "timezones.json").writeText("""{"$uuid":"Asia/Yekaterinburg"}""")
    File(old, "players.json").writeText("""{"$uuid":{"chat_mode":true}}""")
    File(old, "server-id").writeText("7d1e0f9a-0000-4000-8000-000000000042")
  }

  private fun contents(folder: File) = folder.walk().filter { it.isFile }.associate { it.relativeTo(folder).path to it.readText() }

  @Test fun `nothing to move`() {
    assertEquals(McBuddyMigration.Result.NOTHING, McBuddyMigration(old, new, log).run())
    assertFalse(new.exists())
  }

  @Test fun `a clean move keeps every note and the server id, and renames only untouched texts`() {
    mcbuddy()
    assertEquals(McBuddyMigration.Result.MOVED, McBuddyMigration(old, new, log).run())
    assertTrue(contents(old).isEmpty(), "everything moved: ${contents(old).keys}")

    assertEquals(notes, File(new, "notes/$uuid.json").readText())
    assertEquals("7d1e0f9a-0000-4000-8000-000000000042", File(new, "server-id").readText())
    assertEquals("""{"$uuid":"Asia/Yekaterinburg"}""", File(new, "timezones.json").readText())
    assertEquals("""{"$uuid":{"chat_mode":true}}""", File(new, "players.json").readText())
    assertTrue(File(new, "goals/$uuid.json").isFile)
    assertTrue(File(new, "geoip/country.mmdb").isFile)

    val config = File(new, "config.yml").readText()
    assertTrue("book_author: \"Allay\"" in config)
    // Default sounds take Allay's, with their volume and pitch; a sound the admin chose stays, and so does a changed volume.
    assertTrue("success_sound: \"ENTITY_ALLAY_ITEM_GIVEN\"   # The answer is in" in config, config)
    assertTrue("success_volume: 1.0" in config && "success_pitch: 1.0" in config)
    assertTrue("error_sound: \"BLOCK_ANVIL_LAND\"" in config && "error_volume: 0.6" in config)
    assertTrue("thinking_sound: \"ENTITY_ALLAY_AMBIENT_WITH_ITEM\"" in config && "thinking_volume: 0.8" in config)
    assertTrue("get_allay_guide: false" in config && "allay_settings: true" in config)
    assertTrue("# /allay reload applies changes; Allay reads this file." in config)
    // mcbuddy.ru is gone: the default backend moves to allay.chat.
    assertTrue("url: \"https://allay.chat\"" in config && "mcbuddy.ru" !in config, config)

    // English was untouched: it goes, and Allay writes its own. Russian keeps only the admin's line, renamed.
    assertFalse(File(new, "lang/en.yml").exists())
    val russian = YamlConfig.read(File(new, "lang/ru.yml")).flatten()
    assertEquals(mapOf("public.answer" to "&6Allay говорит:&r {text}"), russian)
    assertEquals("public:\n  answer: \"Allay dice: {text}\"\n", File(new, "lang/it.yml").readText())

    assertTrue(File(new, McBuddyMigration.MARKER).isFile)
    assertEquals(McBuddyMigration.Result.ALREADY, McBuddyMigration(old, new, log).run())
  }

  @Test fun `both folders full means no change at all`() {
    mcbuddy()
    new.mkdirs()
    File(new, "players.json").writeText("{}")
    val before = contents(old) to contents(new)
    assertEquals(McBuddyMigration.Result.BOTH, McBuddyMigration(old, new, log).run())
    assertEquals(before, contents(old) to contents(new))
  }

  @Test fun `settings already in Allay's folder stay, and the data still moves`() {
    mcbuddy()
    // A server that ships config.yml with its image puts it here before the plugin starts.
    new.mkdirs()
    File(new, "config.yml").writeText("server:\n  url: \"http://allay:3000\"\n")
    assertEquals(McBuddyMigration.Result.MOVED, McBuddyMigration(old, new, log).run())
    assertEquals("server:\n  url: \"http://allay:3000\"\n", File(new, "config.yml").readText())
    assertTrue(File(old, "config.yml").isFile, "MCBuddy's config stays behind")
    assertEquals(notes, File(new, "notes/$uuid.json").readText())
    assertEquals("7d1e0f9a-0000-4000-8000-000000000042", File(new, "server-id").readText())
    assertFalse(File(new, "lang/en.yml").exists())
  }

  @Test fun `a failure halfway puts everything back`() {
    mcbuddy()
    val before = contents(old)
    var moves = 0
    val failing: (Path, Path) -> Unit = { from, to ->
      // The fourth item fails; the moves back after it work.
      if (++moves == 4) throw java.io.IOException("disk full")
      Files.move(from, to)
    }
    assertEquals(McBuddyMigration.Result.FAILED, McBuddyMigration(old, new, log, failing).run())
    assertEquals(before, contents(old))
    assertTrue(contents(new).isEmpty(), "nothing stays behind: ${contents(new).keys}")
    // It tries again next time.
    assertEquals(McBuddyMigration.Result.MOVED, McBuddyMigration(old, new, log).run())
    assertEquals(notes, File(new, "notes/$uuid.json").readText())
  }
}
