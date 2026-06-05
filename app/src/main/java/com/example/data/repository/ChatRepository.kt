package com.example.data.repository

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.database.ChatDao
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import com.example.data.model.LlmStatus
import com.example.data.model.OfflineLlmEngine
import com.example.data.model.OnlineLlmEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

class ChatRepository(
    private val chatDao: ChatDao,
    private val offlineLlmEngine: OfflineLlmEngine,
    private val context: android.content.Context
) {
    val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
    private val sharedPrefs = context.getSharedPreferences("app_theme_prefs", android.content.Context.MODE_PRIVATE)

    private val _themeColor = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("theme_color", null))
    val themeColor: StateFlow<String?> = _themeColor.asStateFlow()

    private val _backgroundImageUri = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("background_uri", null))
    val backgroundImageUri: StateFlow<String?> = _backgroundImageUri.asStateFlow()

    private val _bgOpacity = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getFloat("bg_opacity", 0.5f))
    val bgOpacity: StateFlow<Float> = _bgOpacity.asStateFlow()

    private val _vaultThemeColor = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("vault_theme_color", null))
    val vaultThemeColor: StateFlow<String?> = _vaultThemeColor.asStateFlow()

    private val _vaultBackgroundImageUri = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("vault_background_uri", null))
    val vaultBackgroundImageUri: StateFlow<String?> = _vaultBackgroundImageUri.asStateFlow()

    private val _vaultBgOpacity = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getFloat("vault_bg_opacity", 0.5f))
    val vaultBgOpacity: StateFlow<Float> = _vaultBgOpacity.asStateFlow()

    private val _multiAgentThemeColor = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("multi_agent_theme_color", null))
    val multiAgentThemeColor: StateFlow<String?> = _multiAgentThemeColor.asStateFlow()

    private val _multiAgentBackgroundImageUri = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("multi_agent_background_uri", null))
    val multiAgentBackgroundImageUri: StateFlow<String?> = _multiAgentBackgroundImageUri.asStateFlow()

    private val _multiAgentBgOpacity = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getFloat("multi_agent_bg_opacity", 0.5f))
    val multiAgentBgOpacity: StateFlow<Float> = _multiAgentBgOpacity.asStateFlow()

    private val _inputBarOpacity = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getFloat("input_bar_opacity", 0.9f))
    val inputBarOpacity: StateFlow<Float> = _inputBarOpacity.asStateFlow()

    private val _storageType = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("storage_type", "drive") ?: "drive")
    val storageType: StateFlow<String> = _storageType.asStateFlow()

    private val _localDirectoryUri = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("local_directory_uri", null))
    val localDirectoryUri: StateFlow<String?> = _localDirectoryUri.asStateFlow()

    private val _userName = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getString("user_name", "User") ?: "User")
    val userName: StateFlow<String> = _userName.asStateFlow()

    private val _apiKey = kotlinx.coroutines.flow.MutableStateFlow<String>(sharedPrefs.getString("api_key", "") ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    private val _imageGenMode = kotlinx.coroutines.flow.MutableStateFlow<String>(sharedPrefs.getString("image_gen_mode", "alternative") ?: "alternative")
    val imageGenMode: StateFlow<String> = _imageGenMode.asStateFlow()

    fun setApiKey(key: String) {
        sharedPrefs.edit().putString("api_key", key).apply()
        _apiKey.value = key
    }

    fun setImageGenMode(mode: String) {
        sharedPrefs.edit().putString("image_gen_mode", mode).apply()
        _imageGenMode.value = mode
    }

    fun setThemeColor(colorHex: String?) {
        sharedPrefs.edit().putString("theme_color", colorHex).apply()
        _themeColor.value = colorHex
    }

    fun setBackgroundImageUri(uri: String?) {
        sharedPrefs.edit().putString("background_uri", uri).apply()
        _backgroundImageUri.value = uri
    }

    fun setBgOpacity(opacity: Float) {
        sharedPrefs.edit().putFloat("bg_opacity", opacity).apply()
        _bgOpacity.value = opacity
    }

    fun setVaultThemeColor(colorHex: String?) {
        sharedPrefs.edit().putString("vault_theme_color", colorHex).apply()
        _vaultThemeColor.value = colorHex
    }

    fun setVaultBackgroundImageUri(uri: String?) {
        sharedPrefs.edit().putString("vault_background_uri", uri).apply()
        _vaultBackgroundImageUri.value = uri
    }

    fun setVaultBgOpacity(opacity: Float) {
        sharedPrefs.edit().putFloat("vault_bg_opacity", opacity).apply()
        _vaultBgOpacity.value = opacity
    }

    fun setMultiAgentThemeColor(hex: String?) {
        sharedPrefs.edit().putString("multi_agent_theme_color", hex).apply()
        _multiAgentThemeColor.value = hex
    }

    fun setMultiAgentBackgroundImageUri(uri: String?) {
        sharedPrefs.edit().putString("multi_agent_background_uri", uri).apply()
        _multiAgentBackgroundImageUri.value = uri
    }

    fun setMultiAgentBgOpacity(opacity: Float) {
        sharedPrefs.edit().putFloat("multi_agent_bg_opacity", opacity).apply()
        _multiAgentBgOpacity.value = opacity
    }

    fun setInputBarOpacity(opacity: Float) {
        sharedPrefs.edit().putFloat("input_bar_opacity", opacity).apply()
        _inputBarOpacity.value = opacity
    }

    fun setUserName(name: String) {
        sharedPrefs.edit().putString("user_name", name).apply()
        _userName.value = name
    }

    fun setStorageType(type: String) {
        sharedPrefs.edit().putString("storage_type", type).apply()
        _storageType.value = type
    }
    
    fun setLocalDirectoryUri(uri: String?) {
        sharedPrefs.edit().putString("local_directory_uri", uri).apply()
        _localDirectoryUri.value = uri
    }

    private fun saveFileLocal(filename: String, content: String): Boolean {
        return try {
            val directoryUri = Uri.parse(_localDirectoryUri.value ?: return false)
            val docFile = DocumentFile.fromTreeUri(context, directoryUri)
            val file = docFile?.findFile(filename) ?: docFile?.createFile("text/plain", filename)
            file?.uri?.let { uri ->
                context.contentResolver.openOutputStream(uri)?.bufferedWriter().use { it?.write(content) }
                true
            } ?: false
        } catch (e: Exception) { e.printStackTrace(); false }
    }

    private fun readFileLocal(filename: String): String? {
        return try {
            val directoryUri = Uri.parse(_localDirectoryUri.value ?: return null)
            val docFile = DocumentFile.fromTreeUri(context, directoryUri)
            val file = docFile?.findFile(filename)
            file?.uri?.let { uri ->
                context.contentResolver.openInputStream(uri)?.bufferedReader().use { it?.readText() }
            }
        } catch (e: Exception) { e.printStackTrace(); null }
    }

    private val onlineLlmEngine = OnlineLlmEngine()

    // Google Drive Integration
    val googleDriveHelper = GoogleDriveHelper(context)
    val notificationHelper = NotificationHelper(context)
    private val _isDriveLinked = kotlinx.coroutines.flow.MutableStateFlow(googleDriveHelper.isLinked())
    val isDriveLinked: StateFlow<Boolean> = _isDriveLinked.asStateFlow()

    fun updateDriveLinkStatus() {
        _isDriveLinked.value = googleDriveHelper.isLinked()
    }

    suspend fun processGoogleDriveDocumentOpps(sessionId: String, originalText: String, role: String): String {
        val isDrive = _storageType.value == "drive"
        
        // Check link status only if drive is selected
        if (isDrive && !googleDriveHelper.isLinked()) {
            return originalText
        }

        var replacedText = originalText

        // 1. Parse .create <filename>\n<content>\n.endfile blocks
        val createRegex = Regex("""\.create\s+(\S+)[\r\n]+([\s\S]*?)[\r\n]+\.endfile""")
        val matches = createRegex.findAll(originalText)
        
        var modified = false
        var appendix = ""

        for (match in matches) {
            val fullMatch = match.value
            val filename = match.groupValues[1]
            val content = match.groupValues[2]

            val success = if (isDrive) {
                googleDriveHelper.syncFileToDrive(filename, content)
            } else {
                saveFileLocal(filename, content)
            }
            modified = true
            
            val storageDisplay = if (isDrive) "Google Drive" else "Lokal"
            val storageMsg = if (success) "Tersinkronisasi dengan $storageDisplay" else "Gagal sinkron/simpan ($storageDisplay)"
            
            val replacement = "```file:$filename:$storageMsg\n$content\n```"
            replacedText = replacedText.replace(fullMatch, replacement)
        }

        // 2. Parse .read <filename> commands
        val readRegex = Regex("""\.read\s+(\S+)""")
        val readMatches = readRegex.findAll(replacedText)

        for (match in readMatches) {
            val fullMatch = match.value
            val filename = match.groupValues[1]
            val content = if (isDrive) googleDriveHelper.fetchFileFromDrive(filename) else readFileLocal(filename)
            modified = true
            
            val storageDisplay = if (isDrive) "Google Drive" else "Lokal"
            
            if (content != null) {
                val feedMsg = ChatMessage(
                    sessionId = sessionId,
                    role = "user",
                    content = "[Konten File '$filename' dari $storageDisplay]:\n\n$content",
                    timestamp = System.currentTimeMillis() + 10L,
                    engineType = "$storageDisplay Sync"
                )
                chatDao.insertMessage(feedMsg)
                replacedText = replacedText.replace(fullMatch, "*(📁 $storageDisplay: Berhasil memuat file '$filename' untuk dianalisis oleh AI!)*")
            } else {
                replacedText = replacedText.replace(fullMatch, "*(📁 $storageDisplay: File '$filename' tidak ditemui atau gagal diunduh!)*")
            }
        }

        return if (modified) replacedText else originalText
    }

    // Multi-Agent Configuration Variables
    val apiKeys: List<kotlinx.coroutines.flow.MutableStateFlow<String>> = (1..5).map { index ->
        val defaultVal = if (index == 1) sharedPrefs.getString("api_key", "") ?: "" else ""
        val savedVal = sharedPrefs.getString("api_key_slot_$index", null) ?: defaultVal
        kotlinx.coroutines.flow.MutableStateFlow(savedVal)
    }
    
    val apiKeyStatuses: List<kotlinx.coroutines.flow.MutableStateFlow<String>> = (1..5).map { index ->
        val key = apiKeys[index - 1].value
        val defaultStatus = if (key.isNotEmpty()) "Aktif" else "Belum Diisi"
        val savedStatus = sharedPrefs.getString("api_key_status_$index", defaultStatus) ?: defaultStatus
        kotlinx.coroutines.flow.MutableStateFlow(savedStatus)
    }

    val apiKeyUsages: List<kotlinx.coroutines.flow.MutableStateFlow<Float>> = (1..5).map { index ->
        val savedUsage = sharedPrefs.getFloat("api_key_usage_$index", 1.0f)
        kotlinx.coroutines.flow.MutableStateFlow(savedUsage)
    }

    private val _activeKeyIndex = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getInt("active_api_key_slot", 1))
    val activeKeyIndex: StateFlow<Int> = _activeKeyIndex.asStateFlow()

    fun setApiKeySlot(index: Int, key: String) {
        sharedPrefs.edit().putString("api_key_slot_$index", key).apply()
        apiKeys[index - 1].value = key
        val newStatus = if (key.isNotEmpty()) "Aktif" else "Belum Diisi"
        setApiKeySlotStatus(index, newStatus)
        if (index == 1) {
            setApiKey(key)
        }
    }

    fun setApiKeySlotStatus(index: Int, status: String) {
        sharedPrefs.edit().putString("api_key_status_$index", status).apply()
        apiKeyStatuses[index - 1].value = status
    }

    private fun consumeApiKeyUsage(index: Int, tokensApproximation: Float) {
        val currentUsage = apiKeyUsages[index - 1].value
        if (currentUsage > 0f) {
            val discount = tokensApproximation * 0.00001f // e.g. 500 tokens -> 0.005 decrease
            var newUsage = currentUsage - discount
            if (newUsage <= 0f) {
                newUsage = 0f
                setApiKeySlotStatus(index, "Kuota Habis")
                // Rotate to next available
                for (i in 1..5) {
                    if (apiKeyUsages[i - 1].value > 0f && apiKeys[i - 1].value.isNotEmpty()) {
                        setActiveKeyIndex(i)
                        break
                    }
                }
            }
            sharedPrefs.edit().putFloat("api_key_usage_$index", newUsage).apply()
            apiKeyUsages[index - 1].value = newUsage
        }
    }

    fun setActiveKeyIndex(index: Int) {
        sharedPrefs.edit().putInt("active_api_key_slot", index).apply()
        _activeKeyIndex.value = index
    }

    private val _numAgents = kotlinx.coroutines.flow.MutableStateFlow(sharedPrefs.getInt("num_agents", 3))
    val numAgents: StateFlow<Int> = _numAgents.asStateFlow()

    fun setNumAgents(num: Int) {
        val bounded = num.coerceIn(1, 7)
        sharedPrefs.edit().putInt("num_agents", bounded).apply()
        _numAgents.value = bounded
    }

    val agentNames: List<kotlinx.coroutines.flow.MutableStateFlow<String>> = (1..7).map { index ->
        val defaultName = when (index) {
            1 -> "Pencipta Kode"
            2 -> "Saber Lint"
            3 -> "Pakar UI/UX"
            4 -> "Asisten Lokal"
            5 -> "Dokumentator"
            6 -> "Arsitek Sistem"
            else -> "Manajer QA"
        }
        val savedVal = sharedPrefs.getString("agent_name_$index", defaultName) ?: defaultName
        kotlinx.coroutines.flow.MutableStateFlow(savedVal)
    }

    val agentPrompts: List<kotlinx.coroutines.flow.MutableStateFlow<String>> = (1..7).map { index ->
        val defaultPrompt = when (index) {
            1 -> "Anda adalah programmer berbakat. Tugas Anda menulis kode berkualitas tinggi, modular, dan efisien."
            2 -> "Anda adalah penguji kode yang teliti dan analitis. Cari kesalahan logika, bug, kerentanan keamanan, atau masalah formatting dalam kode."
            3 -> "Anda adalah praktisi produk UI/UX berpengalaman. Evaluasi kode interface, layout, margin, padding, kombinasi warna, dan interaksi pengguna agar sangat ramah pengguna dan elegan."
            4 -> "Anda adalah asisten AI on-device privat yang hemat energi. Fokus pada solusi cepat, modular, dan penjelasan konseptual sederhana."
            5 -> "Anda adalah analis teknis dan dokumentor handal. Tulis panduan penggunaan, komentar kode, dan ringkasan arsitektur yang mudah dipahami."
            6 -> "Anda adalah arsitek sistem senior. Berikan masukan tentang performa, skalabilitas, dan integrasi antar komponen."
            else -> "Anda adalah manajer tim QA. Ringkas keputusan diskusi tim dan buat daftar aksi (to-do list) tindakan berikutnya."
        }
        val savedVal = sharedPrefs.getString("agent_prompt_$index", defaultPrompt) ?: defaultPrompt
        kotlinx.coroutines.flow.MutableStateFlow(savedVal)
    }

    val agentModels: List<kotlinx.coroutines.flow.MutableStateFlow<String>> = (1..7).map { index ->
        val defaultModel = if (index == 4) "local" else "gemini"
        val savedVal = sharedPrefs.getString("agent_model_$index", defaultModel) ?: defaultModel
        kotlinx.coroutines.flow.MutableStateFlow(savedVal)
    }

    fun setAgentConfig(index: Int, name: String, prompt: String, model: String) {
        sharedPrefs.edit()
            .putString("agent_name_$index", name)
            .putString("agent_prompt_$index", prompt)
            .putString("agent_model_$index", model)
            .apply()
        agentNames[index - 1].value = name
        agentPrompts[index - 1].value = prompt
        agentModels[index - 1].value = model
    }

    private val _multiAgentSessionIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(
        sharedPrefs.getStringSet("multi_agent_session_ids", emptySet()) ?: emptySet()
    )
    val multiAgentSessionIds: StateFlow<Set<String>> = _multiAgentSessionIds.asStateFlow()

    private val _isAutonomousMode = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isAutonomousMode: StateFlow<Boolean> = _isAutonomousMode.asStateFlow()

    fun setAutonomousMode(enabled: Boolean) {
        _isAutonomousMode.value = enabled
    }

    fun markSessionAsMultiAgent(sessionId: String) {
        val updated = _multiAgentSessionIds.value.toMutableSet().apply { add(sessionId) }
        sharedPrefs.edit().putStringSet("multi_agent_session_ids", updated).apply()
        _multiAgentSessionIds.value = updated
    }

    private val _currentActiveAgentRunning = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val currentActiveAgentRunning: StateFlow<String?> = _currentActiveAgentRunning.asStateFlow()

    fun setCurrentActiveAgentRunning(name: String?) {
        _currentActiveAgentRunning.value = name
    }

    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()
    
    val llmStatus: StateFlow<LlmStatus> = offlineLlmEngine.status

    val availableModels: StateFlow<List<String>> = offlineLlmEngine.availableModels
    val selectedModelName: StateFlow<String?> = offlineLlmEngine.selectedModelName
    val downloadProgress: StateFlow<Float?> = offlineLlmEngine.downloadProgress
    val downloadingModelName: StateFlow<String?> = offlineLlmEngine.downloadingModelName
    val downloadError: StateFlow<String?> = offlineLlmEngine.downloadError

    // GGUF Download states
    val ggufDownloadProgress: StateFlow<Float?> = offlineLlmEngine.ggufDownloadProgress
    val ggufDownloadingModelName: StateFlow<String?> = offlineLlmEngine.ggufDownloadingModelName
    val ggufDownloadError: StateFlow<String?> = offlineLlmEngine.ggufDownloadError

    val devModeEnabled: StateFlow<Boolean> = offlineLlmEngine.devModeEnabled
    val bypassFilterActive: StateFlow<Boolean> = offlineLlmEngine.bypassFilterActive
    
    fun setBypassFilterActive(active: Boolean) {
        offlineLlmEngine.setBypassFilterActive(active)
    }
    
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

    fun downloadGgufModel(url: String, filename: String) {
        offlineLlmEngine.downloadGgufModel(url, filename)
    }

    fun cancelGgufDownload() {
        offlineLlmEngine.cancelGgufDownload()
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
        imageBase64: String? = null,
        isImagenActive: Boolean = false
    ) {
        val activeEngineStr = if (isOnlineMode) "Online Gemini" else "Asisten AI Lokal"
        notificationHelper.showNotification(
            "Asisten AI Berjalan di Latar Belakang",
            "Sedang memproses tanggapan menggunakan $activeEngineStr..."
        )
        try {
            // 1. Save user prompt
            val processedPrompt = processGoogleDriveDocumentOpps(sessionId, promptText, "user")
            val userMsg = ChatMessage(
                sessionId = sessionId,
                role = "user",
                content = processedPrompt,
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

            // 2. Perform either online (optionally with Imagen mode) or on-device offline inference
            val modelMsg = if (isOnlineMode) {
                if (isImagenActive) {
                    val result = onlineLlmEngine.generateImagenResponse(
                        prompt = promptText,
                        apiKey = apiKey,
                        context = context,
                        mode = _imageGenMode.value
                    )
                    ChatMessage(
                        sessionId = sessionId,
                        role = "model",
                        content = result.text,
                        timestamp = System.currentTimeMillis(),
                        inferenceTimeMs = result.timeMs,
                        tokensPerSecond = 0f,
                        engineType = "Imagen 3 (Online)"
                    )
                } else {
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
                        engineType = if (result.searchResults.isNotEmpty()) "Gemini 3.5 Flash (Grounded)" else "Gemini 3.5 Flash (Online)"
                    )
                }
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

            // Estimate tokens from result and consume usage
            val tokensApproximation = modelMsg.content.length.toFloat() / 4f + promptText.length.toFloat() / 4f
            consumeApiKeyUsage(activeKeyIndex.value, tokensApproximation)

            // 3. Save LLM response along with diagnostics
            val processedModelContent = processGoogleDriveDocumentOpps(sessionId, modelMsg.content, "model")
            chatDao.insertMessage(modelMsg.copy(content = processedModelContent))
        } finally {
            notificationHelper.dismissNotification()
            notificationHelper.showCompletionNotification(
                "AI Selesai Memproses",
                "Tanggapan baru telah tersedia di layar obrolan."
            )
        }
    }

    suspend fun sendMultiAgentMessageAndAwaitResponse(
        sessionId: String,
        promptText: String
    ) {
        notificationHelper.showNotification(
            "Diskusi Multi-Agen Dimulai",
            "Mempersiapkan rute diskusi kolaboratif..."
        )
        try {
            // 1. Save user prompt
            val processedPrompt = processGoogleDriveDocumentOpps(sessionId, promptText, "user")
            val userMsg = ChatMessage(
                sessionId = sessionId,
                role = "user",
                content = processedPrompt,
                timestamp = System.currentTimeMillis(),
                engineType = "User (Manajer)"
            )
            chatDao.insertMessage(userMsg)

            // Update Session Title if it's the first message
            val currentMessages = chatDao.getMessagesForSession(sessionId).firstOrNull() ?: emptyList()
            if (currentMessages.size <= 1) {
                val shortTitle = "👥 " + (if (promptText.length > 20) promptText.take(18) + "..." else promptText)
                chatDao.updateSessionTitle(sessionId, shortTitle)
            }

            // 2. Loop through active agents
            val activeCount = _numAgents.value
            val agentsList = (1..activeCount).map { i ->
                Triple(agentNames[i - 1].value, agentPrompts[i - 1].value, agentModels[i - 1].value)
            }

            // Check for private message or tagging
            val msgRegex = Regex("""\.msg\s+(\S+)\s+(.*)""")
            val msgMatch = msgRegex.find(promptText)
            val privateTarget = msgMatch?.groupValues?.get(1)
            val privateContent = msgMatch?.groupValues?.get(2)
            
            val isTagged = promptText.contains("@")

            var conversationContext = promptText
            
            for ((agentName, agentPrompt, agentModel) in agentsList) {
                // If private message, skip if not target
                if (privateTarget != null && !agentName.equals(privateTarget, ignoreCase = true)) {
                    continue
                }
                
                // Update active status
                _currentActiveAgentRunning.value = agentName
                notificationHelper.showNotification(
                    "Diskusi Multi-Agen Aktif",
                    "Agen [$agentName] sedang memproses ${if (privateTarget != null) "pesan pribadi" else "pesan"}..."
                )

                // Read the message history up to this point
                val history = chatDao.getMessagesForSession(sessionId).firstOrNull() ?: emptyList()
                
                // Use accumulated conversation context
                val currentPrompt = if (privateContent != null) privateContent else conversationContext

                // Construct System Instruction for this specific agent
                val otherAgentsInfo = agentsList.filter { it.first != agentName }
                        .joinToString(", ") { "${it.first} (${it.third})" }
                
                var customizedSystemPrompt = "Anda adalah $agentName.\n" +
                        "Model Anda: $agentModel.\n" +
                        "Peran & Instruksi Anda: $agentPrompt\n\n" +
                        "Anda adalah bagian dari tim diskusi AI multi-agen. Manajer Anda adalah 'User/Manajer Tim'.\n" +
                        "Rekan tim lainnya di ruangan ini: $otherAgentsInfo.\n" +
                        "Bahaslah masalah yang diajukan oleh Manajer Tim secara kolaboratif. Dahulukan instruksi dari Manajer Tim.\n"
                
                if (privateTarget != null) {
                    customizedSystemPrompt += "\nPESAN PRIBADI (HANYA UNTUK ANDA): $privateContent\n"
                }
                
                if (isTagged && promptText.contains("@$agentName", ignoreCase = true)) {
                    customizedSystemPrompt += "\nPERHATIAN: Manajer secara spesifik mengajak Anda bicara (@$agentName).\n"
                }

                customizedSystemPrompt += "\nPENTING: Sadari nama Anda sendiri '$agentName' dan jangan berbicara atas nama agen lain. Mulailah respons Anda langsung dengan pendapat Anda. Gunakan bahasa yang sama dengan input Manajer!"

                var responseText: String
                var duration: Long = 0L
                var tokSpeed = 0f
                var actualEngineUsed = ""

                val startTime = System.currentTimeMillis()

                if (agentModel == "local") {
                    val result = offlineLlmEngine.generateResponse(currentPrompt, systemPrompt = customizedSystemPrompt)
                    responseText = result.text
                    duration = result.timeMs
                    tokSpeed = result.tokensPerSec
                    actualEngineUsed = "$agentName (AI Lokal)"
                } else {
                    var success = false
                    var errorMsg = ""
                    var currentResponse = ""
                    
                    val startingIndex = _activeKeyIndex.value
                    var triedSlot = startingIndex
                    var attempts = 0

                    while (attempts < 5 && !success) {
                        val keySlotVal = apiKeys[triedSlot - 1].value.trim()
                        if (keySlotVal.length > 5) {
                            try {
                                val apiHistory = history.filter { it.id != userMsg.id }
                                val result = onlineLlmEngine.generateGroundedResponse(
                                    prompt = currentPrompt,
                                    history = apiHistory,
                                    apiKey = keySlotVal,
                                    searchEnabled = false,
                                    systemPrompt = customizedSystemPrompt,
                                    bypassFilterActive = false
                                )
                                currentResponse = result.text
                                duration = result.timeMs
                                success = true
                                setApiKeySlotStatus(triedSlot, "Aktif")
                            } catch (e: Exception) {
                                val msg = e.localizedMessage ?: "Unknown API exception"
                                errorMsg = msg
                                android.util.Log.e("ChatRepository", "Agent call failed for key slot $triedSlot: $msg")
                                setApiKeySlotStatus(triedSlot, "Tidak Aktif/Error")
                                triedSlot = if (triedSlot >= 5) 1 else triedSlot + 1
                                setActiveKeyIndex(triedSlot)
                            }
                        } else {
                            errorMsg = "API Key slot kosong."
                            triedSlot = if (triedSlot >= 5) 1 else triedSlot + 1
                        }
                        attempts++
                    }

                    if (success) {
                        responseText = currentResponse
                        tokSpeed = (responseText.split("\\s+".toRegex()).size * 1.3f) / (duration / 1000f)
                        actualEngineUsed = "$agentName (Gemini 3.5)"
                        val tokensApprox = responseText.length.toFloat() / 4f + promptText.length.toFloat() / 4f
                        consumeApiKeyUsage(triedSlot, tokensApprox)
                    } else {
                        responseText = "Maaf, $agentName gagal merespons karena semua slot API Key habis limit atau tidak valid: $errorMsg"
                        duration = System.currentTimeMillis() - startTime
                        tokSpeed = 0f
                        actualEngineUsed = "$agentName (Gagal)"
                    }
                }

                // Process document operations (if any) generated by the agent
                val processedAgentContent = processGoogleDriveDocumentOpps(sessionId, responseText, "model")
                
                // Update context for next agent
                conversationContext += "\n\n$agentName: $processedAgentContent"

                // Save agent's reply inside database
                val agentMsg = ChatMessage(
                    sessionId = sessionId,
                    role = "model",
                    content = processedAgentContent,
                    timestamp = System.currentTimeMillis(),
                    inferenceTimeMs = duration,
                    tokensPerSecond = if (duration > 0) tokSpeed else 0f,
                    engineType = actualEngineUsed
                )
                chatDao.insertMessage(agentMsg)
            }
        } finally {
            _currentActiveAgentRunning.value = null
            notificationHelper.dismissNotification()
            notificationHelper.showCompletionNotification(
                "Diskusi Multi-Agen Selesai",
                "Semua agen selesai memberikan opini mereka di ruangan diskusi."
            )
        }
    }

    suspend fun clearSessionMessages(sessionId: String) {
        chatDao.deleteMessagesBySessionId(sessionId)
    }

    suspend fun updateMessage(message: ChatMessage) {
        chatDao.updateMessage(message)
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

    suspend fun exportDatabaseToJson(): String {
        val root = org.json.JSONObject()
        root.put("version", 1)

        val sessionsList = chatDao.getAllSessionsList()
        val sessionsArray = org.json.JSONArray()
        for (session in sessionsList) {
            val sObj = org.json.JSONObject().apply {
                put("id", session.id)
                put("title", session.title)
                put("timestamp", session.timestamp)
                put("isReadOnly", session.isReadOnly)
            }
            sessionsArray.put(sObj)
        }
        root.put("sessions", sessionsArray)

        val messagesList = chatDao.getAllMessagesList()
        val messagesArray = org.json.JSONArray()
        for (msg in messagesList) {
            val mObj = org.json.JSONObject().apply {
                put("sessionId", msg.sessionId)
                put("role", msg.role)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
                put("inferenceTimeMs", msg.inferenceTimeMs)
                put("tokensPerSecond", msg.tokensPerSecond.toDouble())
                put("engineType", msg.engineType)
                if (msg.imageBase64 != null) {
                    put("imageBase64", msg.imageBase64)
                }
            }
            messagesArray.put(mObj)
        }
        root.put("messages", messagesArray)

        return root.toString(2)
    }

    suspend fun importDatabaseFromJson(jsonStr: String): Boolean {
        try {
            val root = org.json.JSONObject(jsonStr)
            val sessionsArray = root.optJSONArray("sessions") ?: return false
            val messagesArray = root.optJSONArray("messages") ?: return false

            val sessionsToInsert = mutableListOf<ChatSession>()
            for (i in 0 until sessionsArray.length()) {
                val sObj = sessionsArray.getJSONObject(i)
                sessionsToInsert.add(
                    ChatSession(
                        id = sObj.getString("id"),
                        title = sObj.getString("title"),
                        timestamp = sObj.optLong("timestamp", System.currentTimeMillis()),
                        isReadOnly = sObj.optBoolean("isReadOnly", false)
                    )
                )
            }

            val messagesToInsert = mutableListOf<ChatMessage>()
            for (i in 0 until messagesArray.length()) {
                val mObj = messagesArray.getJSONObject(i)
                messagesToInsert.add(
                    ChatMessage(
                        id = 0, // Auto-generate database ID
                        sessionId = mObj.getString("sessionId"),
                        role = mObj.getString("role"),
                        content = mObj.getString("content"),
                        timestamp = mObj.optLong("timestamp", System.currentTimeMillis()),
                        inferenceTimeMs = mObj.optLong("inferenceTimeMs", 0L),
                        tokensPerSecond = mObj.optDouble("tokensPerSecond", 0.0).toFloat(),
                        engineType = mObj.optString("engineType", ""),
                        imageBase64 = if (mObj.has("imageBase64")) mObj.getString("imageBase64") else null
                    )
                )
            }

            // Clear first to ensure full restore (overwrite of older data, as requested)
            chatDao.clearAllMessages()
            chatDao.clearAllSessions()

            // Insert new lists
            chatDao.insertSessionsList(sessionsToInsert)
            chatDao.insertMessagesList(messagesToInsert)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
