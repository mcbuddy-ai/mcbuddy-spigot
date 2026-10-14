package ru.mcbuddy.core

/** What a player reads when a question fails, in their language: `errors.api.<code>`, then `errors.http.<status>`, then the general ones. */
object ErrorHandler {
  fun message(lang: Lang, error: Any?): String = when (error) {
    is ApiErrorException -> api(lang, error.apiError)
    is ApiError -> api(lang, error)
    is ErrorResponse -> lang.has("errors.api.${error.code}").let { if (it) lang.text("errors.api.${error.code}") else lang.text("errors.other", "message" to error.error) }
    is Exception -> when {
      isTimeout(error) -> lang.text("errors.timeout")
      isNetwork(error) -> lang.text("errors.network")
      else -> lang.text("errors.default")
    }
    else -> lang.text("errors.default")
  }

  private fun api(lang: Lang, error: ApiError): String = when {
    lang.has("errors.api.${error.code}") -> lang.text("errors.api.${error.code}")
    error.statusCode != null && lang.has("errors.http.${error.statusCode}") -> lang.text("errors.http.${error.statusCode}")
    else -> lang.text("errors.other", "message" to error.message)
  }

  private fun isTimeout(error: Exception): Boolean {
    val message = error.message ?: return false
    return message.contains("timeout", ignoreCase = true) || message.contains("SocketTimeoutException", ignoreCase = true)
  }

  private fun isNetwork(error: Exception): Boolean {
    val message = error.message ?: return false
    return message.contains("ConnectException", ignoreCase = true) || message.contains("UnknownHostException", ignoreCase = true) || message.contains("NetworkException", ignoreCase = true)
  }
}
