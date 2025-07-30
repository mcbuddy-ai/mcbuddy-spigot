package ru.mcbuddy.spigot

import kotlinx.serialization.json.Json
import okhttp3.Response

class ErrorParser {
  private val json = Json { ignoreUnknownKeys = true }
  
  fun parseErrorFromResponse(response: Response): ApiError {
    return try {
      val responseBody = response.body?.string()
      
      if (responseBody.isNullOrEmpty()) {
        createHttpError(response)
      } else {
        parseJsonError(responseBody, response.code)
      }
    } catch (e: Exception) {
      createHttpError(response)
    }
  }
  
  private fun createHttpError(response: Response): ApiError {
    return ApiError(
      code = "HTTP_ERROR",
      message = "HTTP ${response.code}: ${response.message}",
      statusCode = response.code
    )
  }
  
  private fun parseJsonError(responseBody: String, statusCode: Int): ApiError {
    val errorData = json.decodeFromString<ErrorResponse>(responseBody)
    return ApiError(
      code = errorData.code,
      message = errorData.error,
      statusCode = statusCode
    )
  }
}