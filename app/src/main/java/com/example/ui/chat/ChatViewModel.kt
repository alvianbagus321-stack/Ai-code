package com.example.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import com.example.data.model.LlmStatus
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {
    private val TAG = "ChatViewModel"

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _currentUserInput = MutableStateFlow("")
    val currentUserInput: StateFlow<String> = _currentUserInput.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isOnlineMode = MutableStateFlow(false)
    val isOnlineMode: StateFlow<Boolean> = _isOnlineMode.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(true)
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

    private val _apiKey = MutableStateFlow("AQ.Ab8RN6LHT06ZRJotBp767pzblyQ0nPgHFIRaL_hbF6kIg433Fg")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _systemPrompt = MutableStateFlow("You are a helpful AI assistant. Answer user queries accurately and directly.")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    fun updateSystemPrompt(newPrompt: String) {
        _systemPrompt.value = newPrompt
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _activeSessionId
        .flatMapLatest { sessionId ->
            if (sessionId == null) {
                flowOf(emptyList())
            } else {
                repository.getMessagesForSession(sessionId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sessions: StateFlow<List<ChatSession>> = repository.allSessions
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val llmStatus: StateFlow<LlmStatus> = repository.llmStatus

    val availableModels: StateFlow<List<String>> = repository.availableModels
    val selectedModelName: StateFlow<String?> = repository.selectedModelName
    val downloadProgress: StateFlow<Float?> = repository.downloadProgress
    val downloadingModelName: StateFlow<String?> = repository.downloadingModelName

    fun selectModel(modelName: String) {
        repository.selectModel(modelName)
    }

    fun downloadModel(url: String, filename: String) {
        repository.downloadModel(url, filename)
    }

    fun cancelDownload() {
        repository.cancelDownload()
    }

    init {
        // Automatically open the first session or create one if empty
        viewModelScope.launch {
            repository.allSessions.firstOrNull()?.firstOrNull()?.let { session ->
                _activeSessionId.value = session.id
            }
        }
    }

    fun startNewSession(title: String = "Offline Chat") {
        viewModelScope.launch {
            val id = repository.createNewSession(title)
            _activeSessionId.value = id
        }
    }

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                val rem = repository.allSessions.firstOrNull()?.firstOrNull()
                _activeSessionId.value = rem?.id
            }
        }
    }

    fun onUserInputChange(newVal: String) {
        _currentUserInput.value = newVal
    }

    fun toggleOnlineMode() {
        _isOnlineMode.value = !_isOnlineMode.value
    }

    fun toggleWebSearch() {
        _webSearchEnabled.value = !_webSearchEnabled.value
    }

    fun updateApiKey(newKey: String) {
        _apiKey.value = newKey
    }

    fun sendMessage() {
        val prompt = _currentUserInput.value.trim()
        if (prompt.isEmpty() || _isGenerating.value) return
        
        _currentUserInput.value = ""
        _isGenerating.value = true
        
        viewModelScope.launch {
            try {
                var currentId = _activeSessionId.value
                if (currentId == null) {
                    currentId = repository.createNewSession("Prompting...")
                    _activeSessionId.value = currentId
                }
                repository.sendMessageAndAwaitResponse(
                    sessionId = currentId,
                    promptText = prompt,
                    isOnlineMode = _isOnlineMode.value,
                    webSearchEnabled = _webSearchEnabled.value,
                    apiKey = _apiKey.value,
                    systemPrompt = _systemPrompt.value
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error sending message: ${e.message}", e)
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun importLocalModel(uri: Uri, context: Context) {
        viewModelScope.launch {
            val displayName = queryFileName(uri, context) ?: "local_model_${System.currentTimeMillis()}.bin"
            try {
                repository.importModelFromUri(uri, displayName)
            } catch (e: Exception) {
                Log.e(TAG, "Failed importing: ${e.message}")
            }
        }
    }

    fun purgeCache() {
        repository.purgeCustomModels()
    }

    private fun queryFileName(uri: Uri, context: Context): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    class Factory(private val repository: ChatRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
                return ChatViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
