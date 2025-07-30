package ru.mcbuddy.spigot

class ErrorHandler(private val config: Config) {
  
  fun getUserErrorMessage(error: Any?): String {
    return when (error) {
      is ApiError -> handleApiError(error)
      is ErrorResponse -> handleErrorResponse(error)
      is Exception -> handleException(error)
      else -> getDefaultErrorMessage()
    }
  }
  
  private fun handleApiError(error: ApiError): String {
    return config.errorMessages.apiErrors[error.code] 
      ?: error.statusCode?.let { config.errorMessages.httpErrors[it] }
      ?: "&cПроизошла ошибка: ${error.message}"
  }
  
  private fun handleErrorResponse(error: ErrorResponse): String {
    return config.errorMessages.apiErrors[error.code] ?: "&cПроизошла ошибка: ${error.error}"
  }
  
  private fun handleException(error: Exception): String {
    return when {
      isTimeoutError(error) -> config.errorMessages.systemErrors["timeout"]!!
      isNetworkError(error) -> config.errorMessages.systemErrors["network"]!!
      else -> getDefaultErrorMessage()
    }
  }
  
  private fun isTimeoutError(error: Exception): Boolean {
    val message = error.message ?: return false
    return message.contains("timeout", ignoreCase = true) || message.contains("SocketTimeoutException", ignoreCase = true)
  }
  
  private fun isNetworkError(error: Exception): Boolean {
    val message = error.message ?: return false
    return message.contains("ConnectException", ignoreCase = true) || message.contains("UnknownHostException", ignoreCase = true) || message.contains("NetworkException", ignoreCase = true)
  }
  
  private fun getDefaultErrorMessage(): String {
    return config.errorMessages.systemErrors["default"]!!
  }
}