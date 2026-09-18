// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (c) 2026 jagones84

package com.example.daysurpopt.data

import com.example.daysurpopt.BuildConfig
import com.example.daysurpopt.domain.OpenRouterRequest
import com.example.daysurpopt.domain.OpenRouterResponse
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

/**
 * Retrofit interface for OpenRouter API.
 */
interface OpenRouterApi {
    @Headers("Content-Type: application/json")
    @POST("api/v1/chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = BuildConfig.OPENROUTER_HTTP_REFERER,
        @Header("X-Title") title: String = BuildConfig.OPENROUTER_TITLE,
        @Body request: OpenRouterRequest
    ): Response<OpenRouterResponse>
}

/**
 * Singleton factory for creating the OpenRouterApi client.
 * Configures OkHttp with logging and timeouts.
 */
object OpenRouterClient {
    fun create(): OpenRouterApi {
        val logging = HttpLoggingInterceptor().apply {
            redactHeader("Authorization")
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl(BuildConfig.OPENROUTER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenRouterApi::class.java)
    }
}

internal suspend fun OpenRouterApi.chatCompletionText(
    authorization: String,
    request: OpenRouterRequest
): String {
    return try {
        val response = chatCompletion(
            authorization = authorization,
            request = request
        )

        if (response.isSuccessful) {
            val body = response.body()
            body?.choices?.firstOrNull()?.message?.content
                ?: body?.error?.message
                ?: "No response from AI"
        } else {
            val status = response.code()
            val raw = response.errorBody()?.string().orEmpty()
            val parsed = runCatching { Gson().fromJson(raw, OpenRouterResponse::class.java) }.getOrNull()
            val apiMessage = parsed?.error?.message?.takeIf { it.isNotBlank() }

            when (status) {
                401 -> "Invalid API key."
                402 -> apiMessage ?: "Insufficient credits or payment required."
                408 -> "Request timeout. Please retry."
                429 -> "Rate limit reached. Please wait and retry."
                in 500..599 -> "Service unavailable. Please retry later."
                else -> apiMessage ?: "Request failed (HTTP $status)."
            }
        }
    } catch (_: java.net.SocketTimeoutException) {
        "Request timeout. Please retry."
    } catch (_: java.io.IOException) {
        "Network error. Please check your connection."
    } catch (e: Exception) {
        e.message?.takeIf { it.isNotBlank() } ?: "Request failed."
    }
}
