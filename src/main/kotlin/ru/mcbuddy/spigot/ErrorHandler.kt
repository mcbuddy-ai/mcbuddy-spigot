package ru.mcbuddy.spigot

class ErrorHandler {
  
  private val errorMessages = mapOf(
    ErrorCodes.EMPTY_QUESTION to "&cВопрос не может быть пустым",
    ErrorCodes.QUESTION_TOO_LONG to "&cВопрос слишком длинный (максимум 1000 символов)",
    ErrorCodes.EMPTY_ACTION to "&cОписание действия не может быть пустым",
    ErrorCodes.ACTION_TOO_LONG to "&cОписание действия слишком длинное (максимум 500 символов)",
    ErrorCodes.TOO_MANY_COMMANDS to "&cСлишком много команд для выполнения (максимум 12)",
    ErrorCodes.AI_ERROR to "&cAI не смог обработать ваш запрос. Попробуйте переформулировать.",
    ErrorCodes.BAD_REQUEST to "&cНекорректный запрос. Попробуйте еще раз.",
    ErrorCodes.METHOD_NOT_SUPPORTED to "&cМетод не поддерживается",
    
    ErrorCodes.AI_REQUEST_FAILED to "&cAI-сервис временно недоступен. Попробуйте позже.",
    ErrorCodes.NO_AI_RESPONSE to "&cAI не ответил на запрос. Попробуйте позже.",
    ErrorCodes.NETWORK_ERROR to "&cПроблемы с сетью. Проверьте подключение к интернету.",
    ErrorCodes.REDIS_ERROR to "&cПроблемы с базой данных. Попробуйте позже.",
    ErrorCodes.QUEUE_ERROR to "&cСервер перегружен. Попробуйте через несколько минут.",
    ErrorCodes.PROCESSING_ERROR to "&cОшибка обработки запроса. Попробуйте позже.",
    ErrorCodes.PARSE_ERROR to "&cОшибка обработки ответа AI. Попробуйте позже.",
    ErrorCodes.INVALID_FORMAT to "&cНекорректный формат ответа AI. Попробуйте позже."
  )
  
  private val httpErrorMessages = mapOf(
    400 to "&cНекорректный запрос. Попробуйте переформулировать.",
    401 to "&cОшибка авторизации. Обратитесь к администратору.",
    403 to "&cДоступ запрещен.",
    404 to "&cСервис не найден. Обратитесь к администратору.",
    429 to "&cПревышен лимит запросов. Подождите немного и попробуйте снова.",
    500 to "&cВнутренняя ошибка сервера. Попробуйте позже.",
    502 to "&cСервер временно недоступен. Попробуйте позже.",
    503 to "&cСервис временно недоступен. Попробуйте позже.",
    504 to "&cТаймаут запроса. Сервер перегружен, попробуйте позже."
  )
  
  fun getUserErrorMessage(error: Any?): String {
    return when (error) {
      is ApiError -> handleApiError(error)
      is ErrorResponse -> handleErrorResponse(error)
      is Exception -> handleException(error)
      else -> getDefaultErrorMessage()
    }
  }
  
  private fun handleApiError(error: ApiError): String {
    return errorMessages[error.code] 
      ?: error.statusCode?.let { httpErrorMessages[it] }
      ?: "&cПроизошла ошибка: ${error.message}"
  }
  
  private fun handleErrorResponse(error: ErrorResponse): String {
    return errorMessages[error.code] ?: "&cПроизошла ошибка: ${error.error}"
  }
  
  private fun handleException(error: Exception): String {
    return when {
      isTimeoutError(error) -> "&cЗапрос занял слишком много времени. AI-сервер перегружен, попробуйте позже."
      isNetworkError(error) -> "&cПроблемы с подключением к серверу. Проверьте интернет-соединение."
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
    return "&cПроизошла неожиданная ошибка. Попробуйте позже."
  }
}