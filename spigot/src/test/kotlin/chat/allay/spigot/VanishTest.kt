package chat.allay.spigot

import org.bukkit.entity.Player
import org.bukkit.metadata.MetadataValue
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VanishTest {
  /** A player with only what [Vanish] asks: metadata, operator status, and identity. */
  private fun player(name: String, op: Boolean = false, vanished: List<Boolean> = emptyList()): Player {
    val values = vanished.map(::value)
    lateinit var self: Player
    self = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(Player::class.java)) { _, method, args ->
      when (method.name) {
        "getMetadata" -> if (args[0] == "vanished") values else emptyList<MetadataValue>()
        "isOp" -> op
        "getName" -> name
        "equals" -> args[0] === self
        "hashCode" -> System.identityHashCode(self)
        "toString" -> name
        else -> throw UnsupportedOperationException(method.name)
      }
    } as Player
    return self
  }

  private fun value(flag: Boolean): MetadataValue = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(MetadataValue::class.java)) { _, method, _ ->
    when (method.name) {
      "asBoolean" -> flag
      "value" -> flag
      else -> throw UnsupportedOperationException(method.name)
    }
  } as MetadataValue

  @Test fun `any true vanished value hides the player`() {
    assertFalse(Vanish.vanished(player("a")))
    assertFalse(Vanish.vanished(player("a", vanished = listOf(false))))
    // Two vanish plugins may both set it; one true is enough.
    assertTrue(Vanish.vanished(player("a", vanished = listOf(false, true))))
  }

  @Test fun `only the player and operators see a vanished player`() {
    val hidden = player("hidden", vanished = listOf(true))
    val shown = player("shown")
    val viewer = player("viewer")
    val operator = player("op", op = true)
    assertFalse(Vanish.visible(viewer, hidden))
    assertTrue(Vanish.visible(hidden, hidden))
    assertTrue(Vanish.visible(operator, hidden))
    assertTrue(Vanish.visible(viewer, shown))
    assertFalse(Vanish.visible(null, hidden))
    assertEquals(listOf(shown, viewer), Vanish.visibleTo(viewer, listOf(hidden, shown, viewer)))
  }
}
