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

    private val _systemLogs = MutableStateFlow<List<String>>(listOf("System Log Initialized"))
    val systemLogs: StateFlow<List<String>> = _systemLogs.asStateFlow()

    data class BackupProgress(
        val isActive: Boolean = false,
        val isExport: Boolean = true,
        val currentStep: String = "",
        val detail: String = ""
    )

    private val _backupProgress = MutableStateFlow(BackupProgress())
    val backupProgress: StateFlow<BackupProgress> = _backupProgress.asStateFlow()

    fun logEvent(message: String) {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date())
        _systemLogs.update { list -> list + "[$timestamp] $message" }
        Log.i("APP_LOG", "[$timestamp] $message")
    }

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _currentUserInput = MutableStateFlow("")
    val currentUserInput: StateFlow<String> = _currentUserInput.asStateFlow()

    private val _currentImageBase64 = MutableStateFlow<String?>(null)
    val currentImageBase64: StateFlow<String?> = _currentImageBase64.asStateFlow()

    fun setImageBase64(base64: String?) {
        _currentImageBase64.value = base64
    }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isOnlineMode = MutableStateFlow(false)
    val isOnlineMode: StateFlow<Boolean> = _isOnlineMode.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(true)
    val webSearchEnabled: StateFlow<Boolean> = _webSearchEnabled.asStateFlow()

    val apiKey: StateFlow<String> = repository.apiKey
    val imageGenMode: StateFlow<String> = repository.imageGenMode

    fun setImageGenMode(mode: String) {
        repository.setImageGenMode(mode)
    }

    private val _systemPrompt = MutableStateFlow("You are a helpful AI assistant. Answer user queries accurately and directly.")
    val systemPrompt: StateFlow<String> = _systemPrompt.asStateFlow()

    private val _isImagenModeActive = MutableStateFlow(false)
    val isImagenModeActive: StateFlow<Boolean> = _isImagenModeActive.asStateFlow()

    fun toggleImagenMode() {
        _isImagenModeActive.value = !_isImagenModeActive.value
        logEvent("Toggled Imagen 3 Mode. Active: ${_isImagenModeActive.value}")
    }

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

    val isActiveSessionReadOnly: StateFlow<Boolean> = combine(
        _activeSessionId,
        sessions
    ) { activeId, sessionList ->
        sessionList.find { it.id == activeId }?.isReadOnly ?: false
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    val llmStatus: StateFlow<LlmStatus> = repository.llmStatus

    val availableModels: StateFlow<List<String>> = repository.availableModels
    val selectedModelName: StateFlow<String?> = repository.selectedModelName
    val downloadProgress: StateFlow<Float?> = repository.downloadProgress
    val downloadingModelName: StateFlow<String?> = repository.downloadingModelName
    val downloadError: StateFlow<String?> = repository.downloadError

    // GGUF fields
    val ggufDownloadProgress: StateFlow<Float?> = repository.ggufDownloadProgress
    val ggufDownloadingModelName: StateFlow<String?> = repository.ggufDownloadingModelName
    val ggufDownloadError: StateFlow<String?> = repository.ggufDownloadError

    val devModeEnabled: StateFlow<Boolean> = repository.devModeEnabled
    val bypassFilterActive: StateFlow<Boolean> = repository.bypassFilterActive

    
    val themeColor: StateFlow<String?> = repository.themeColor
    val backgroundImageUri: StateFlow<String?> = repository.backgroundImageUri
    val bgOpacity: StateFlow<Float> = repository.bgOpacity

    val vaultThemeColor: StateFlow<String?> = repository.vaultThemeColor
    val vaultBackgroundImageUri: StateFlow<String?> = repository.vaultBackgroundImageUri
    val vaultBgOpacity: StateFlow<Float> = repository.vaultBgOpacity
    val inputBarOpacity: StateFlow<Float> = repository.inputBarOpacity

    fun setThemeColor(colorHex: String?) {
        repository.setThemeColor(colorHex)
    }

    fun setBackgroundImageUri(uri: String?) {
        repository.setBackgroundImageUri(uri)
    }

    fun setBgOpacity(opacity: Float) {
        repository.setBgOpacity(opacity)
    }

    fun setVaultThemeColor(colorHex: String?) {
        repository.setVaultThemeColor(colorHex)
    }

    fun setVaultBackgroundImageUri(uri: String?) {
        repository.setVaultBackgroundImageUri(uri)
    }

    fun setVaultBgOpacity(opacity: Float) {
        repository.setVaultBgOpacity(opacity)
    }

    fun setInputBarOpacity(opacity: Float) {
        repository.setInputBarOpacity(opacity)
    }

    fun attemptEnableDevMode(password: String): Boolean = repository.attemptEnableDevMode(password)
    fun disableDevMode() = repository.disableDevMode()
    fun setBypassFilterActive(active: Boolean) = repository.setBypassFilterActive(active)


    fun refreshModels() {
        repository.refreshModels()
    }

    fun selectModel(modelName: String) {
        repository.selectModel(modelName)
    }

    fun downloadModel(url: String, filename: String) {
        repository.downloadModel(url, filename)
    }

    fun cancelDownload() {
        repository.cancelDownload()
    }

    fun downloadGgufModel(url: String, filename: String) {
        repository.downloadGgufModel(url, filename)
    }

    fun cancelGgufDownload() {
        repository.cancelGgufDownload()
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

    fun clearCurrentSession() {
        val sessionId = _activeSessionId.value
        if (sessionId != null) {
            viewModelScope.launch {
                repository.clearSessionMessages(sessionId)
            }
        }
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

    fun updateMessage(message: ChatMessage) {
        viewModelScope.launch {
            repository.updateMessage(message)
        }
    }

    fun onUserInputChange(newVal: String) {
        _currentUserInput.value = newVal
    }

    fun toggleOnlineMode() {
        _isOnlineMode.value = !_isOnlineMode.value
        logEvent("Toggled online mode. Active: ${_isOnlineMode.value}")
    }

    fun toggleWebSearch() {
        _webSearchEnabled.value = !_webSearchEnabled.value
        logEvent("Toggled web search. Active: ${_webSearchEnabled.value}")
    }

    fun updateApiKey(newKey: String) {
        repository.setApiKey(newKey)
        logEvent("API Key updated (Length: ${newKey.length})")
    }

    fun sendMessage() {
        val prompt = _currentUserInput.value.trim()
        val imgBase64 = _currentImageBase64.value
        if ((prompt.isEmpty() && imgBase64 == null) || _isGenerating.value) return
        
        _currentUserInput.value = ""
        _currentImageBase64.value = null
        _isGenerating.value = true
        
        logEvent("Sending message. Prompt length: ${prompt.length} (Has Image: ${imgBase64 != null})")
        viewModelScope.launch {
            try {
                var currentId = _activeSessionId.value
                if (currentId == null) {
                    currentId = repository.createNewSession("Prompting...")
                    _activeSessionId.value = currentId
                }
                logEvent("Routing request to ${if (_isOnlineMode.value) "Online Gemini" else "Offline LLM"} within session $currentId")
                repository.sendMessageAndAwaitResponse(
                    sessionId = currentId,
                    promptText = prompt,
                    isOnlineMode = _isOnlineMode.value,
                    webSearchEnabled = _webSearchEnabled.value,
                    apiKey = repository.apiKey.value,
                    systemPrompt = _systemPrompt.value,
                    imageBase64 = imgBase64,
                    isImagenActive = _isImagenModeActive.value
                )
                if (_isImagenModeActive.value) {
                    _isImagenModeActive.value = false
                }
                logEvent("Message response received and saved successfully.")
            } catch (e: Exception) {
                logEvent("Error sending message: ${e.message}")
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
        logEvent("Purged all custom models from local storage.")
    }

    fun deleteModel(modelName: String) {
        val deleted = repository.deleteModel(modelName)
        if (deleted) {
            logEvent("Deleted model $modelName successfully.")
        } else {
            logEvent("Failed to delete model $modelName.")
        }
    }

    fun getModelSizeFormatted(modelName: String): String {
        val bytes = repository.getModelSize(modelName)
        if (bytes <= 0) return "0 B"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format(java.util.Locale.US, "%.2f GB", gb)
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
            else -> String.format(java.util.Locale.US, "%.1f KB", kb)
        }
    }

    suspend fun exportAppDatabase(): String {
        return repository.exportDatabaseToJson()
    }

    suspend fun importAppDatabase(jsonStr: String): Boolean {
        val success = repository.importDatabaseFromJson(jsonStr)
        if (success) {
            logEvent("Successfully imported and restored all application data.")
            val firstSession = repository.allSessions.firstOrNull()?.firstOrNull()
            _activeSessionId.value = firstSession?.id
        } else {
            logEvent("Failed to import or parse backup file.")
        }
        return success
    }

    suspend fun exportFullBackup(context: android.content.Context, outputStream: java.io.OutputStream) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            _backupProgress.value = BackupProgress(isActive = true, isExport = true, currentStep = "Mempersiapkan backup...", detail = "Silakan tunggu...")
            var zipOut: java.util.zip.ZipOutputStream? = null
            try {
                zipOut = java.util.zip.ZipOutputStream(outputStream)
                
                // 1. Export database as JSON and add to ZIP
                _backupProgress.value = BackupProgress(isActive = true, isExport = true, currentStep = "Mengekspor Chat History...", detail = "Menyusun file database...")
                val dbJson = repository.exportDatabaseToJson()
                val dbEntry = java.util.zip.ZipEntry("database_backup.json")
                zipOut.putNextEntry(dbEntry)
                zipOut.write(dbJson.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()
                logEvent("Zipped database backup JSON successfully.")
                
                // 2. Add downloaded models from context.filesDir/local_llm_models
                val modelsDir = java.io.File(context.filesDir, "local_llm_models")
                if (modelsDir.exists() && modelsDir.isDirectory) {
                    val modelFiles = modelsDir.listFiles()
                    if (modelFiles != null) {
                        for ((index, file) in modelFiles.withIndex()) {
                            if (file.isFile) {
                                val sizeInMb = file.length() / (1024 * 1024)
                                _backupProgress.value = BackupProgress(
                                    isActive = true,
                                    isExport = true,
                                    currentStep = "Mengompres Model Offline (${index + 1}/${modelFiles.size})...",
                                    detail = "${file.name} ($sizeInMb MB)"
                                )
                                val fileEntry = java.util.zip.ZipEntry("models/${file.name}")
                                zipOut.putNextEntry(fileEntry)
                                file.inputStream().use { input ->
                                    input.copyTo(zipOut)
                                }
                                zipOut.closeEntry()
                                logEvent("Packed offline model into ZIP: ${file.name}")
                            }
                        }
                    }
                }
                
                _backupProgress.value = BackupProgress(isActive = true, isExport = true, currentStep = "Finalisasi backup ZIP...", detail = "Menyimpan ke lokasi tujuan...")
                zipOut.finish()
                logEvent("Full app backup ZIP generated successfully.")
            } catch (e: Exception) {
                e.printStackTrace()
                logEvent("Failed to write app backup ZIP: ${e.message}")
                throw e
            } finally {
                try {
                    zipOut?.close()
                } catch (e: Exception) {}
                _backupProgress.value = BackupProgress(isActive = false)
            }
        }
    }

    suspend fun importFullBackup(context: android.content.Context, inputStream: java.io.InputStream): Boolean {
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            _backupProgress.value = BackupProgress(isActive = true, isExport = false, currentStep = "Menganalisis file backup...", detail = "Membuka file ZIP...")
            var dbRestored = false
            var zipIn: java.util.zip.ZipInputStream? = null
            try {
                zipIn = java.util.zip.ZipInputStream(inputStream)
                var entry = zipIn.nextEntry
                val modelsDir = java.io.File(context.filesDir, "local_llm_models").apply {
                    if (!exists()) mkdirs()
                }
                
                while (entry != null) {
                    if (entry.name == "database_backup.json") {
                        _backupProgress.value = BackupProgress(isActive = true, isExport = false, currentStep = "Memulihkan Chat History...", detail = "Membaca pesan & sesi...")
                        val jsonBytes = zipIn.readBytes()
                        val jsonStr = String(jsonBytes, Charsets.UTF_8)
                        val dbSuccess = repository.importDatabaseFromJson(jsonStr)
                        if (dbSuccess) {
                            dbRestored = true
                            logEvent("Restored internal chat database from backup ZIP.")
                        }
                    } else if (entry.name.startsWith("models/")) {
                        val fileName = entry.name.substringAfter("models/")
                        if (fileName.isNotEmpty() && !entry.isDirectory) {
                            _backupProgress.value = BackupProgress(
                                isActive = true, 
                                isExport = false, 
                                currentStep = "Memulihkan Model Offline...", 
                                detail = "Mengekstrak $fileName..."
                            )
                            val destFile = java.io.File(modelsDir, fileName)
                            if (destFile.exists()) {
                                destFile.delete()
                            }
                            destFile.outputStream().use { output ->
                                zipIn.copyTo(output)
                            }
                            logEvent("Unpacked offline model from backup ZIP: $fileName")
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
                
                if (dbRestored) {
                    _backupProgress.value = BackupProgress(isActive = true, isExport = false, currentStep = "Memperbarui daftar model...", detail = "Menyelesaikan proses pemulihan...")
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val firstSession = repository.allSessions.firstOrNull()?.firstOrNull()
                        _activeSessionId.value = firstSession?.id
                        repository.refreshModels()
                    }
                    logEvent("Successfully imported and restored all application data and offline models from ZIP.")
                    true
                } else {
                    logEvent("Failed to restore ZIP backup: database file not found inside ZIP archive.")
                    false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                logEvent("Failed to import full backup ZIP: ${e.message}")
                false
            } finally {
                try {
                    zipIn?.close()
                } catch (e: Exception) {}
                _backupProgress.value = BackupProgress(isActive = false)
            }
        }
    }

    fun getExportText(): String {
        val sb = java.lang.StringBuilder()
        sb.append("=========================================\n")
        sb.append("         OFFLINE AI CHAT EXPORT          \n")
        sb.append("=========================================\n")
        sb.append("Session ID: ${activeSessionId.value ?: "None"}\n")
        sb.append("Current Time: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}\n")
        sb.append("Online Mode Active: ${isOnlineMode.value}\n")
        sb.append("Web Search Active: ${webSearchEnabled.value}\n")
        sb.append("Developer Override Active: ${devModeEnabled.value}\n")
        sb.append("Bypass Filter Active: ${repository.bypassFilterActive.value}\n")
        sb.append("LLM Status: ${llmStatus.value}\n")
        sb.append("System Prompt: ${systemPrompt.value}\n")
        sb.append("Selected Model: ${selectedModelName.value ?: "Default/None"}\n")
        sb.append("\n")
        sb.append("--- CHAT MESSAGES ---\n")
        val currentMsgs = messages.value
        if (currentMsgs.isEmpty()) {
            sb.append("(No messages in this session)\n")
        } else {
            currentMsgs.forEach { msg ->
                val timeStr = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(msg.timestamp))
                sb.append("[$timeStr] ${msg.role.uppercase()}: ${msg.content}\n")
                if (msg.engineType.isNotEmpty()) {
                    sb.append("  [Engine: ${msg.engineType} | Speed: ${msg.tokensPerSecond} t/s | Inference Time: ${msg.inferenceTimeMs}ms]\n")
                }
                if (msg.imageBase64 != null) {
                    sb.append("  [Attached Image Base64 Length: ${msg.imageBase64.length}]\n")
                }
                sb.append("\n")
            }
        }
        sb.append("--- SYSTEM LOGS ---\n")
        systemLogs.value.forEach { log ->
            sb.append(log).append("\n")
        }
        sb.append("=========================================\n")
        return sb.toString()
    }

    fun importSharedSessionFromJson(jsonStr: String) {
        viewModelScope.launch {
            try {
                val json = org.json.JSONObject(jsonStr)
                val title = json.optString("title", "Imported Shared Chat")
                val isReadOnly = json.optBoolean("isReadOnly", false)
                val messagesArray = json.optJSONArray("messages")
                
                val chatMessagesList = mutableListOf<ChatMessage>()
                if (messagesArray != null) {
                    for (i in 0 until messagesArray.length()) {
                        val mObj = messagesArray.getJSONObject(i)
                        val role = mObj.optString("role", "user")
                        val content = mObj.optString("content", "")
                        val timestamp = mObj.optLong("timestamp", System.currentTimeMillis())
                        val engineType = mObj.optString("engineType", "")
                        val tokensPerSecond = mObj.optDouble("tokensPerSecond", 0.0).toFloat()
                        val inferenceTimeMs = mObj.optLong("inferenceTimeMs", 0L)
                        val imageBase64 = mObj.optString("imageBase64", null).let { if (it == "null" || it.isEmpty()) null else it }
                        
                        chatMessagesList.add(
                            ChatMessage(
                                sessionId = "",
                                role = role,
                                content = content,
                                timestamp = timestamp,
                                engineType = engineType,
                                tokensPerSecond = tokensPerSecond,
                                inferenceTimeMs = inferenceTimeMs,
                                imageBase64 = imageBase64
                            )
                        )
                    }
                }
                
                val newSessionId = repository.importSharedSession(title, isReadOnly, chatMessagesList)
                _activeSessionId.value = newSessionId
                logEvent("Successfully imported shared session: $title (ReadOnly: $isReadOnly)")
            } catch (e: Exception) {
                logEvent("Failed to import shared session: ${e.message}")
            }
        }
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
