package com.example.data.repository

import android.net.Uri
import com.example.data.database.ChatDao
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import com.example.data.model.LlmStatus
import com.example.data.model.OfflineLlmEngine
import com.example.data.model.OnlineLlmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val offlineLlmEngine: OfflineLlmEngine
) {
    private val onlineLlmEngine = OnlineLlmEngine()

    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()
    
    val llmStatus: StateFlow<LlmStatus> = offlineLlmEngine.status

    val availableModels: StateFlow<List<String>> = offlineLlmEngine.availableModels
    val selectedModelName: StateFlow<String?> = offlineLlmEngine.selectedModelName
    val downloadProgress: StateFlow<Float?> = offlineLlmEngine.downloadProgress
    val downloadingModelName: StateFlow<String?> = offlineLlmEngine.downloadingModelName
    val downloadError: StateFlow<String?> = offlineLlmEngine.downloadError

    val devModeEnabled: StateFlow<Boolean> = offlineLlmEngine.devModeEnabled
    val bypassFilterActive: StateFlow<Boolean> = offlineLlmEngine.bypassFilterActive
    
    fun attemptEnableDevMode(password: String): Boolean = offlineLlmEngine.attemptEnableDevMode(password)
    fun disableDevMode() = offlineLlmEngine.disableDevMode()

    fun refreshModels() {
        offlineLlmEngine.refreshModels()
    }

    fun selectModel(modelName: String) {
        offlineLlmEngine.selectAndLoadModel(modelName)
    }

    fun downloadModel(url: String, filename: String) {
        offlineLlmEngine.downloadModel(url, filename)
    }

    fun cancelDownload() {
        offlineLlmEngine.cancelDownload()
    }

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun createNewSession(title: String = "New Chat Session", isReadOnly: Boolean = false): String {
        val id = UUID.randomUUID().toString()
        val session = ChatSession(id = id, title = title, isReadOnly = isReadOnly)
        chatDao.insertSession(session)
        return id
    }

    suspend fun importSharedSession(title: String, isReadOnly: Boolean, messages: List<ChatMessage>): String {
        val id = UUID.randomUUID().toString()
        val session = ChatSession(id = id, title = title, isReadOnly = isReadOnly)
        chatDao.insertSession(session)
        messages.forEach { msg ->
            chatDao.insertMessage(msg.copy(sessionId = id, id = 0))
        }
        return id
    }

    suspend fun sendMessageAndAwaitResponse(
        sessionId: String,
        promptText: String,
        isOnlineMode: Boolean,
        webSearchEnabled: Boolean,
        apiKey: String,
        systemPrompt: String = "",
        imageBase64: String? = null
    ) {
        // 1. Save user prompt
        val userMsg = ChatMessage(
            sessionId = sessionId,
            role = "user",
            content = promptText,
            timestamp = System.currentTimeMillis(),
            imageBase64 = imageBase64
        )
        chatDao.insertMessage(userMsg)

        // 1.5 Update title if it's the first message
        val currentMessages = chatDao.getMessagesForSession(sessionId).firstOrNull() ?: emptyList()
        if (currentMessages.size <= 1) {
            val shortTitle = if (promptText.length > 28) promptText.take(25) + "..." else promptText
            chatDao.updateSessionTitle(sessionId, shortTitle)
        }

        // Global Intercept for DevX Mode Commands
        val lowerPrompt = promptText.trim().lowercase(java.util.Locale.getDefault())
        if (devModeEnabled.value) {
            val interceptResponse = when (lowerPrompt) {
                "menu", "/menu" -> "devx menu\n- debug_logs\n- bypass_filter\n- sys_info\n- exit_devx"
                "debug_logs", "/debug_logs" -> {
                    "MediaPipe Engine: ${if (offlineLlmEngine.isModelLoaded) "Online" else "Offline/Error"}\n" +
                    "Last Error: ${llmStatus.value.let { if (it is LlmStatus.Error) it.message else "None" }}\n" +
                    "Local Models Found: ${availableModels.value.joinToString(", ")}"
                }
                "bypass_filter", "/bypass_filter" -> {
                    val newBypassState = !offlineLlmEngine.bypassFilterActive.value
                    offlineLlmEngine.setBypassFilterActive(newBypassState)
                    if (newBypassState) {
                        "Filter bypassed. Your next prompts will be sent directly to the model exactly as typed. (System templates are OFF)."
                    } else {
                        "Filter restored. System templates and internal safety layers are ON."
                    }
                }
                "sys_info", "/sys_info" -> {
                    "Memory: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB\n" +
                    "OS: Android\n" +
                    "Bypass Active: ${offlineLlmEngine.bypassFilterActive.value}\n" +
                    "Active Mode: ${if (isOnlineMode) "Online (Gemini)" else "Offline (MediaPipe)"}\n" +
                    "Web Search Grounding: $webSearchEnabled"
                }
                "exit_devx", "/exit_devx" -> {
                    disableDevMode()
                    "Exited dev mode."
                }
                else -> null
            }

            if (interceptResponse != null) {
                val devMsg = ChatMessage(
                    sessionId = sessionId,
                    role = "model",
                    content = interceptResponse,
                    timestamp = System.currentTimeMillis(),
                    inferenceTimeMs = 1L,
                    tokensPerSecond = 45.0f,
                    engineType = "DevX Engine"
                )
                chatDao.insertMessage(devMsg)
                return
            }
        }

        // 2. Perform either online or on-device offline inference
        val modelMsg = if (isOnlineMode) {
            val previousMessages = chatDao.getMessagesForSession(sessionId).firstOrNull() ?: emptyList()
            // Exclude the recently inserted userMsg from history since the engine appends the user prompt separately
            val history = previousMessages.filter { it.id != userMsg.id }
            
            val result = onlineLlmEngine.generateGroundedResponse(
                prompt = promptText,
                history = history,
                apiKey = apiKey,
                searchEnabled = webSearchEnabled,
                systemPrompt = systemPrompt,
                imageBase64 = imageBase64,
                bypassFilterActive = bypassFilterActive.value
            )

            val speed = (result.text.split("\\s+".toRegex()).size * 1.3f) / (result.timeMs / 1000f)

            ChatMessage(
                sessionId = sessionId,
                role = "model",
                content = result.text,
                timestamp = System.currentTimeMillis(),
                inferenceTimeMs = result.timeMs,
                tokensPerSecond = if (result.timeMs > 0) speed else 0f,
                engineType = if (result.searchResults.isNotEmpty()) "Gemini 2.5 Flash (Grounded)" else "Gemini 2.5 Flash (Online)"
            )
        } else {
            val result = offlineLlmEngine.generateResponse(promptText, apiKey, systemPrompt)
            ChatMessage(
                sessionId = sessionId,
                role = "model",
                content = result.text,
                timestamp = System.currentTimeMillis(),
                inferenceTimeMs = result.timeMs,
                tokensPerSecond = result.tokensPerSec,
                engineType = result.engine
            )
        }

        // 3. Save LLM response along with diagnostics
        chatDao.insertMessage(modelMsg)
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteMessagesBySessionId(sessionId)
        chatDao.deleteSessionById(sessionId)
    }

    suspend fun importModelFromUri(uri: Uri, displayName: String) {
        offlineLlmEngine.importModelFromUri(uri, displayName)
    }

    fun purgeCustomModels() {
        offlineLlmEngine.purgeLoadedModels()
    }

    fun deleteModel(modelName: String): Boolean {
        return offlineLlmEngine.deleteModel(modelName)
    }

    fun getModelSize(modelName: String): Long {
        return offlineLlmEngine.getModelSize(modelName)
    }
}
