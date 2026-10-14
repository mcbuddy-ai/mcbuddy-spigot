package ru.mcbuddy.core

import java.util.UUID

/** An asker for code that only holds on to it. */
val FakeAsker: Asker = object : Asker {
  override val name = "Tester"
  override val uuid: UUID? = null
  override val operator = false
  override val locale: String? = null
  override fun can(permission: String) = false
}
