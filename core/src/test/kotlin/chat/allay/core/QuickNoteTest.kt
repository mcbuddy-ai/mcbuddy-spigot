package chat.allay.core

import chat.allay.core.tools.QuickNote
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QuickNoteTest {
  private fun spot(text: String) = QuickNote.spot(text)?.let { listOf(it.x, it.y, it.z, it.dimension) }

  @Test fun `three numbers are x y z, with the dimension the text names`() {
    assertEquals(listOf(-20, 95, 120, "the_nether"), spot("координаты портала из ада в мир -20; 95; 120"))
    assertEquals(listOf(-498, 68, -629, null), spot("бастион −498 68 –629"))
    assertEquals(listOf(10, 64, 10, "the_end"), spot("end city in the End: 10 64 10"))
    assertEquals(listOf(1, 2, 3, "overworld"), spot("дом в обычном мире 1, 2, 3"))
  }

  @Test fun `two numbers are x and z`() {
    assertEquals(listOf(100, null, -200, "the_nether"), spot("крепость в незере 100 -200"))
  }

  @Test fun `no coordinates is a plain fact`() {
    assertNull(spot("хочу собрать маяк"))
    assertNull(spot("нужно 16 алмазов"))
    assertNull(spot("версия 1.21.7 и 4 блока"))
    assertNull(spot("1 2 3 4"))
  }

  @Test fun `two dimensions named leave the choice to where the player is`() {
    assertEquals(null, QuickNote.spot("портал из ада в обычный мир 1 2 3")?.dimension)
  }

  @Test fun `ad inside a word isn't the Nether`() {
    assertEquals(null, QuickNote.spot("задание 1 2 3")?.dimension)
  }
}
