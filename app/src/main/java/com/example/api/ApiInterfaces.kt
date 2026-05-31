package com.example.api

import com.example.model.OpenRouterChatRequest
import com.example.model.OpenRouterChatResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApi {
    @POST("api/v1/chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String,
        @Header("HTTP-Referer") referer: String = "https://ai.studio",
        @Header("X-Title") title: String = "AI Float Assistant",
        @Body request: OpenRouterChatRequest
    ): OpenRouterChatResponse
}

interface GeminiApi {
    @POST("v1beta/models/gemini-1.5-flash:generateContent")
    suspend fun generateContent(
        @retrofit2.http.Query("key") apiKey: String,
        @Body request: com.example.model.GeminiRequest
    ): com.example.model.GeminiResponse
}
