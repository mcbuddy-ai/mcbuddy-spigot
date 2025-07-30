package ru.mcbuddy.spigot

import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class Service(private val config: Config, private val client: OkHttpClient) {
  @Serializable data class AskRequest(val question: String, val platform: String = "minecraft", val user_id: String? = null)
  @Serializable data class AskResponse(val answer: String, val status: String? = null, val timestamp: String? = null)
  @Serializable data class AskXRequest(val action: String, val platform: String = "minecraft", val user_id: String? = null)
  @Serializable data class AskXResponse(val isSequence: Boolean, val commands: List<String>, val status: String? = null, val timestamp: String? = null, val error: String? = null)

  private val json = Json { ignoreUnknownKeys = true }
  private val errorParser = ErrorParser()

  suspend fun ask(question: String, userId: String) = withContext(IO) {
    val request = AskRequest(question = question, platform = "minecraft", user_id = userId)
    val jsonBody = json.encodeToString(AskRequest.serializer(), request)
    val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

    val httpRequest = Request.Builder()
      .url("${config.server.url}/api/ask")
      .post(requestBody)
      .build()

    execute(httpRequest) { json.decodeFromString<AskResponse>(it).answer }
  }

  suspend fun askx(action: String, userId: String) = withContext(IO) {
    val request = AskXRequest(action = action, platform = "minecraft", user_id = userId)
    val jsonBody = json.encodeToString(AskXRequest.serializer(), request)
    val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

    val httpRequest = Request.Builder()
      .url("${config.server.url}/api/askx")
      .post(requestBody)
      .build()

    execute(httpRequest) { json.decodeFromString<AskXResponse>(it) }
  }

  private fun <T> execute(request: Request, responseProcessor: (String) -> T): T {
    val response = client.newCall(request).execute()
    
    if (!response.isSuccessful) {
      val apiError = errorParser.parseErrorFromResponse(response)
      throw ApiErrorException(apiError)
    }
    
    val responseBody = response.body?.string() ?: throw Exception("Сервер вернул пустой ответ")
    return responseProcessor(responseBody)
  }
}