package com.example.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.ChatMessage
import com.example.repository.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FloatingUiState(
    val isChatExpanded: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class FloatingViewModel : ViewModel() {
    private val repository = AiRepository()
    
    private val _uiState = MutableStateFlow(FloatingUiState())
    val uiState = _uiState.asStateFlow()

    fun toggleChat() {
        _uiState.value = _uiState.value.copy(isChatExpanded = !_uiState.value.isChatExpanded)
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val currentMessages = _uiState.value.messages
        val newMessages = currentMessages + ChatMessage("user", text)
        
        _uiState.value = _uiState.value.copy(
            messages = newMessages,
            isLoading = true,
            error = null
        )

        viewModelScope.launch {
            val response = repository.getChatResponse(text, currentMessages)
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + ChatMessage("assistant", response),
                isLoading = false
            )
        }
    }

    fun analyzeScreen(bitmap: Bitmap) {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            val analysis = repository.analyzeScreen(bitmap, "Analise esta tela e me diga o que você vê nela de forma resumida.")
            _uiState.value = _uiState.value.copy(
                messages = _uiState.value.messages + ChatMessage("assistant", analysis),
                isLoading = false
            )
        }
    }

    fun clearHistory() {
        _uiState.value = _uiState.value.copy(messages = emptyList())
    }

    fun summarizeCurrentChat() {
        if (_uiState.value.messages.isEmpty()) return
        sendMessage("Resuma o que conversamos até agora.")
    }

    fun explainBetter() {
        val lastMessage = _uiState.value.messages.lastOrNull { it.role == "assistant" }
        if (lastMessage != null) {
            sendMessage("Pode explicar melhor o que você disse acima?")
        }
    }

    fun generateAnother() {
        val lastUserMessage = _uiState.value.messages.lastOrNull { it.role == "user" }
        if (lastUserMessage != null) {
            sendMessage("Gere uma outra resposta diferente para minha pergunta anterior: ${lastUserMessage.content}")
        }
    }
}
