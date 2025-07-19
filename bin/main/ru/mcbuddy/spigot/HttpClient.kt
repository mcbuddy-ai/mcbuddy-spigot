package ru.mcbuddy.spigot

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.TimeUnit.SECONDS

class HttpClient(private val plugin: JavaPlugin, private val config: Config) {
  val client = run {
    val http = config.http

    val builder = OkHttpClient.Builder()
      .callTimeout(if (http.callTimeout == 0L) 0 else http.callTimeout, SECONDS)
      .connectTimeout(http.connectTimeout, SECONDS)
      .readTimeout(http.readTimeout, SECONDS)
      .writeTimeout(http.writeTimeout, SECONDS)
      .followRedirects(http.followRedirects)
      .followSslRedirects(http.followRedirects)
      .retryOnConnectionFailure(http.retryOnConnectionFailure)
      .connectionPool(ConnectionPool(http.maxIdleConnections, http.keepAliveDuration, SECONDS))

    if (http.compressionEnabled) {
      builder.addInterceptor { chain ->
        val originalRequest = chain.request()
        val compressedRequest = originalRequest.newBuilder().header("Accept-Encoding", "gzip, deflate").build()
        chain.proceed(compressedRequest)
      }
    }

    builder.addInterceptor { chain ->
      val originalRequest = chain.request()
      val requestBuilder = originalRequest.newBuilder().header("User-Agent", http.userAgent)
      http.customHeaders.forEach { (key, value) -> if (value?.isNotBlank() == true) requestBuilder.header(key, value) }
      chain.proceed(requestBuilder.build())
    }

    if (http.logging) {
      builder.addInterceptor { chain ->
        val request = chain.request()
        plugin.logger.info("🔄 Requesting HTTP/${request.method} ${request.url}")
        val response = chain.proceed(request)
        plugin.logger.info("✅ Response from HTTP/${response.code} ${request.url}")
        response
      }
    }

    val dispatcher = Dispatcher()
    dispatcher.maxRequestsPerHost = http.maxRequestsPerHost
    builder.dispatcher(dispatcher)
    builder.build()
  }

  init {
    plugin.logger.info("✅ HttpClient initialized")
  }
}