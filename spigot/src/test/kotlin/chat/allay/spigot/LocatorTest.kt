package chat.allay.spigot

import chat.allay.spigot.tools.Locator
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [Locator.shows] against the game's rules in 26.3 (`WaypointTransmitter.doesSourceIgnoreReceiver`). */
class LocatorTest {
  private val range = 6.0E7

  @Test fun `players on the bar by default, however far`() {
    assertTrue(Locator.shows(viewerSpectator = false, targetSpectator = false, transmit = range, receive = range, distance = 5_000.0))
  }

  @Test fun `crouching, a pumpkin, or invisibility take a player off the bar`() {
    // Each multiplies the transmit range by 0, so a spectator doesn't see them either.
    assertFalse(Locator.shows(viewerSpectator = false, targetSpectator = false, transmit = 0.0, receive = range, distance = 3.0))
    assertFalse(Locator.shows(viewerSpectator = true, targetSpectator = false, transmit = 0.0, receive = range, distance = 3.0))
  }

  @Test fun `the shorter of both ranges limits the bar`() {
    assertTrue(Locator.shows(viewerSpectator = false, targetSpectator = false, transmit = 100.0, receive = range, distance = 100.0))
    assertFalse(Locator.shows(viewerSpectator = false, targetSpectator = false, transmit = 100.0, receive = range, distance = 100.5))
    assertFalse(Locator.shows(viewerSpectator = false, targetSpectator = false, transmit = range, receive = 50.0, distance = 60.0))
  }

  @Test fun `spectators see everyone who transmits, nobody sees spectators`() {
    assertTrue(Locator.shows(viewerSpectator = true, targetSpectator = false, transmit = 10.0, receive = 10.0, distance = 900.0))
    assertTrue(Locator.shows(viewerSpectator = true, targetSpectator = true, transmit = range, receive = range, distance = 9.0))
    assertFalse(Locator.shows(viewerSpectator = false, targetSpectator = true, transmit = range, receive = range, distance = 9.0))
  }
}
