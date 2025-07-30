package ru.mcbuddy.spigot

import kotlinx.serialization.Serializable

@Serializable data class ApiError(val code: String, val message: String, val statusCode: Int? = null)
@Serializable data class ErrorResponse(val error: String, val code: String, val status: String, val timestamp: String)

object ErrorCodes {
  const val EMPTY_ACTION = "EMPTY_ACTION"
  const val ACTION_TOO_LONG = "ACTION_TOO_LONG"
  const val AI_ERROR = "AI_ERROR"
  const val TOO_MANY_COMMANDS = "TOO_MANY_COMMANDS"
  const val EMPTY_QUESTION = "EMPTY_QUESTION"
  const val QUESTION_TOO_LONG = "QUESTION_TOO_LONG"
  const val BAD_REQUEST = "BAD_REQUEST"
  const val METHOD_NOT_SUPPORTED = "METHOD_NOT_SUPPORTED"
  
  const val PARSE_ERROR = "PARSE_ERROR"
  const val INVALID_FORMAT = "INVALID_FORMAT"
  const val AI_REQUEST_FAILED = "AI_REQUEST_FAILED"
  const val REDIS_ERROR = "REDIS_ERROR"
  const val NETWORK_ERROR = "NETWORK_ERROR"
  const val NO_AI_RESPONSE = "NO_AI_RESPONSE"
  const val QUEUE_ERROR = "QUEUE_ERROR"
  const val PROCESSING_ERROR = "PROCESSING_ERROR"
}

class ApiErrorException(val apiError: ApiError) : Exception(apiError.message)