package com.safecruise.app.data.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    // Physical USB device with adb reverse tcp:8000 tcp:8000: 127.0.0.1:8000
    // Android emulator: 10.0.2.2:8000
    var baseUrl: String = "http://127.0.0.1:8000/"
        set(value) {
            field = if (value.endsWith("/")) value else "$value/"
            rebuildService()
        }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(3, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .writeTimeout(3, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var _apiService: SafeCruiseApiService? = null

    val apiService: SafeCruiseApiService
        get() = _apiService ?: rebuildService()

    @Synchronized
    private fun rebuildService(): SafeCruiseApiService {
        val service = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SafeCruiseApiService::class.java)
        _apiService = service
        return service
    }
}
