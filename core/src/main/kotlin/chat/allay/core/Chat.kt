package chat.allay.core

/** Permissions as the plugin names them; each platform checks them its own way. */
object Permissions {
  const val ASK = "allay.ask"
  const val ASKX = "allay.askx"
  const val ASKALL = "allay.askall"
  const val ASKALL_NO_COOLDOWN = "allay.askall.nocooldown"
  const val CHAT = "allay.chat"
  const val RELOAD = "allay.reload"
  /** Paying for Allay and seeing why a player got their language: `/allay link`, `/allay status`, `/allay locale`. */
  const val ADMIN = "allay.admin"
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
