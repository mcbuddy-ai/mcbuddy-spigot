package ru.mcbuddy.core

import ru.mcbuddy.core.tools.OperatorArgs
import ru.mcbuddy.core.tools.ToolError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OperatorArgsTest {
  @Test fun `item counts stay within nine stacks of 64`() {
    assertEquals(1, OperatorArgs.count(null))
    assertEquals(576, OperatorArgs.count(576))
    assertFailsWith<ToolError> { OperatorArgs.count(577) }
    assertFailsWith<ToolError> { OperatorArgs.count(0) }
  }

  @Test fun `effects have capped length and level`() {
    assertEquals(180, OperatorArgs.effectSeconds(null))
    assertFailsWith<ToolError> { OperatorArgs.effectSeconds(3_601) }
    assertEquals(1, OperatorArgs.effectLevel(null))
    assertFailsWith<ToolError> { OperatorArgs.effectLevel(11) }
    assertFailsWith<ToolError> { OperatorArgs.enchantmentLevel(256) }
    assertFailsWith<ToolError> { OperatorArgs.enchantmentLevel(null) }
  }

  @Test fun `time takes words or ticks`() {
    assertEquals(1_000, OperatorArgs.time("Day"))
    assertEquals(18_000, OperatorArgs.time("midnight"))
    assertEquals(23_999, OperatorArgs.time("23999"))
    assertFailsWith<ToolError> { OperatorArgs.time("24000") }
    assertFailsWith<ToolError> { OperatorArgs.time("dusk") }
  }

  @Test fun `modes and weather come from fixed lists`() {
    assertEquals("creative", OperatorArgs.gamemode("CREATIVE"))
    assertFailsWith<ToolError> { OperatorArgs.gamemode("god") }
    assertEquals("thunder", OperatorArgs.weather("thunder"))
    assertFailsWith<ToolError> { OperatorArgs.weather("snow") }
    assertFailsWith<ToolError> { OperatorArgs.weatherSeconds(86_401) }
  }

  @Test fun `ids lose the namespace and spaces`() {
    assertEquals("diamond_sword", OperatorArgs.id("minecraft:Diamond Sword"))
    assertEquals(null, OperatorArgs.id("  "))
  }
}
