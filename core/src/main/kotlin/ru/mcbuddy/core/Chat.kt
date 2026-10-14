package ru.mcbuddy.core

/** Permissions as the plugin names them; each platform checks them its own way. */
object Permissions {
  const val ASK = "mcbuddy.ask"
  const val ASKX = "mcbuddy.askx"
  const val ASKALL = "mcbuddy.askall"
  const val ASKALL_NO_COOLDOWN = "mcbuddy.askall.nocooldown"
  const val CHAT = "mcbuddy.chat"
  const val RELOAD = "mcbuddy.reload"
}

/** Chat mode's prefixes: where a line a player writes in chat goes, by its first character. */
object Chat {
  enum class Route { ASK, ASKX, PUBLIC, CHAT }

  /** `!text` and `! text` alike: the prefix, then the rest without the spaces around it. */
  fun route(message: String): Pair<Route, String> {
    val line = message.trim()
    val route = when (line.firstOrNull()) {
      '!' -> Route.ASKX
      '?' -> Route.PUBLIC
      '~' -> Route.CHAT
      else -> return Route.ASK to line
    }
    return route to line.drop(1).trim()
  }
}
