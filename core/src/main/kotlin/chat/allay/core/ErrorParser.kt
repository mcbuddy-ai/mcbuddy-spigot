package chat.allay.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Response

/** Reads a failed response: v2's `{ "error": { "code", "message", … } }`, v1's `{ "error", "code", … }`, or the bare status. */
class ErrorParser {
  private val json = Json { ignoreUnknownKeys = true }

  fun parseErrorFromResponse(response: Response): ApiError {
    val body = runCatching { response.body.string() }.getOrNull()
    val error = body?.takeIf { it.isNotBlank() }?.let { parse(it, response.code) } ?: createHttpError(response)
    // RATE_LIMITED without retry_after: the header may still say when.
    return if (error.retryAfter == null) error.copy(retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()) else error
  }

  /** A JSON error body; null when it's something else. */
  fun parse(body: String, statusCode: Int?): ApiError? = runCatching {
    val root = json.parseToJsonElement(body).jsonObject
    when (val error = root["error"]) {
      is JsonObject -> ApiError(
        code = error.text("code") ?: return null,
        message = error.text("message").orEmpty(),
        statusCode = statusCode,
        resetsAt = error.text("resets_at"),
        retryAfter = (error["retry_after"] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toDoubleOrNull()?.toLong() },
      )
      is JsonPrimitive -> ApiError(code = root.text("code") ?: return null, message = error.content, statusCode = statusCode)
      else -> null
    }
  }.getOrNull()

  private fun JsonObject.text(name: String) = (this[name] as? JsonPrimitive)?.contentOrNull

  private fun createHttpError(response: Response): ApiError {
    return ApiError(
      code = "HTTP_ERROR",
      message = "HTTP ${response.code}: ${response.message}",
      statusCode = response.code
    )
  }
}
