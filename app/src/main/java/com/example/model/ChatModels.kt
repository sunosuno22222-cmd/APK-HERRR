package com.example.model

import com.google.gson.annotations.SerializedName

data class ChatMessage(
    val role: String,
    val content: String
)

data class OpenRouterChatRequest(
    val model: String,
    val messages: List<ChatMessage>
)

data class OpenRouterChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ChatMessage
)

// Gemini Models as per skill REST API example (simplified but compatible)
data class GeminiRequest(
    val contents: List<GeminiContent>
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

data class GeminiResponse(
    val candidates: List<GeminiCandidate>
)

data class GeminiCandidate(
    val content: GeminiContent
)
