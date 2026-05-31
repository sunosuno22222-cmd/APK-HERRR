package com.example.repository

import android.graphics.Bitmap
import android.util.Base64
import com.example.BuildConfig
import com.example.api.ApiClient
import com.example.model.*
import java.io.ByteArrayOutputStream

class AiRepository {

    suspend fun getChatResponse(prompt: String, history: List<ChatMessage>): String {
        val messages = history + ChatMessage("user", prompt)
        val request = OpenRouterChatRequest(
            model = "openrouter/owl-alpha",
            messages = messages
        )
        return try {
            val apiKey = BuildConfig.OPENROUTER_API_KEY
            val response = ApiClient.openRouterApi.getChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )
            response.choices.firstOrNull()?.message?.content ?: "Sem resposta da IA."
        } catch (e: Exception) {
            "Erro: ${e.message}"
        }
    }

    suspend fun analyzeScreen(bitmap: Bitmap, prompt: String): String {
        val base64Image = bitmap.toBase64()
        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = GeminiInlineData("image/jpeg", base64Image))
                    )
                )
            )
        )
        return try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val response = ApiClient.geminiApi.generateContent(apiKey, request)
            response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Sem análise da tela."
        } catch (e: Exception) {
            "Erro na análise: ${e.message}"
        }
    }

    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }
}
