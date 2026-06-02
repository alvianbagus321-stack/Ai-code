package com.example.ui.chat

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.database.ChatMessage
import com.example.data.database.ChatSession
import com.example.data.model.LlmStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    val sessions by viewModel.sessions.collectAsState()
    val activeSessionId by viewModel.activeSessionId.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val currentUserInput by viewModel.currentUserInput.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val llmStatus by viewModel.llmStatus.collectAsState()
    val isOnlineMode by viewModel.isOnlineMode.collectAsState()
    val webSearchEnabled by viewModel.webSearchEnabled.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val systemPrompt by viewModel.systemPrompt.collectAsState()

    val availableModels by viewModel.availableModels.collectAsState()
    val selectedModelName by viewModel.selectedModelName.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadingModelName by viewModel.downloadingModelName.collectAsState()

    var showSettingsDialog by remember { mutableStateOf(false) }
    var customModelUrl by remember { mutableStateOf("") }
    var customModelFilename by remember { mutableStateOf("") }
    var selectedDrawerTab by remember { mutableStateOf(0) }

    // Launcher for file picking to load binary model file (.bin) from downloads
    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.importLocalModel(uri, context)
            }
        }
    )

    // Permission launcher for accessing external storage on older platforms
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                modelPickerLauncher.launch(arrayOf("*/*"))
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Access permission required to read model files directly from storage. Starting Document Selector...",
                    android.widget.Toast.LENGTH_LONG
                ).show()
                modelPickerLauncher.launch(arrayOf("*/*"))
            }
        }
    )

    val launchModelPickerWithPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ has native individual media selectors. Document loader works fine default-isolated
            modelPickerLauncher.launch(arrayOf("*/*"))
        } else {
            val check = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            if (check == PackageManager.PERMISSION_GRANTED) {
                modelPickerLauncher.launch(arrayOf("*/*"))
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    // Ultra-premium Color Palette
    val darkSanctuaryBg = Color(0xFF0F172A) // Modern dark slate
    val securityEmerald = Color(0xFF10B981) // Beautiful active green
    val electricBlue = Color(0xFF3B82F6) // AI active indigo
    val cardSurfaceBg = Color(0xFF1E293B) // Balanced container tint
    val activeBubbleBg = Color(0xFF2563EB) // Electric user bubble

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color(0xFF0F172A),
                modifier = Modifier.width(310.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🔐 Local Vault",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        IconButton(
                            onClick = {
                                viewModel.startNewSession("Offline Chat ${sessions.size + 1}")
                                coroutineScope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .background(electricBlue.copy(alpha = 0.2f), CircleShape)
                                .testTag("new_session_drawer_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Session",
                                tint = electricBlue
                            )
                        }
                    }
                    
                    // Elegant capsule switch for separating history and model controller
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedDrawerTab == 0) electricBlue else Color.Transparent)
                                .clickable { selectedDrawerTab = 0 }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "💬 Chat History",
                                color = if (selectedDrawerTab == 0) Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (selectedDrawerTab == 1) electricBlue else Color.Transparent)
                                .clickable { selectedDrawerTab = 1 }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "🧠 Cognitive Engine",
                                color = if (selectedDrawerTab == 1) Color.White else Color(0xFF94A3B8),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Divider(color = Color(0xFF334155), modifier = Modifier.padding(vertical = 4.dp))

                    if (selectedDrawerTab == 0) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                        if (sessions.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No saved vaults yet.\nTap + above to begin.",
                                    textAlign = TextAlign.Center,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp)
                            ) {
                                items(sessions) { session ->
                                    val isActive = session.id == activeSessionId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isActive) electricBlue.copy(alpha = 0.15f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                viewModel.selectSession(session.id)
                                                coroutineScope.launch { drawerState.close() }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Face,
                                            contentDescription = null,
                                            tint = if (isActive) electricBlue else Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = session.title,
                                            color = if (isActive) Color.White else Color(0xFFE2E8F0),
                                            fontSize = 14.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { viewModel.deleteSession(session.id) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Clear,
                                                contentDescription = "Delete",
                                                tint = Color(0xFFEF4444).copy(alpha = 0.7f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    } else if (selectedDrawerTab == 1) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .background(Color(0xFF0F172A))
                                .padding(16.dp)
                        ) {
                        Text(
                            text = "🧠 COGNITIVE MODEL CHANGER",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = electricBlue,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // 1. If actively downloading, show visual feedback and progress
                        if (downloadingModelName != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp)
                                    .border(1.dp, electricBlue.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Downloading Model...",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = electricBlue
                                        )
                                        IconButton(
                                            onClick = { viewModel.cancelDownload() },
                                            modifier = Modifier.size(20.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Cancel Download",
                                                tint = Color.Red,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = downloadingModelName ?: "",
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val progress = downloadProgress ?: 0f
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth().height(4.dp),
                                        color = electricBlue,
                                        trackColor = Color(0xFF334155)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "${(progress * 100).toInt()}% completed",
                                        fontSize = 10.sp,
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (availableModels.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No models in Sandbox storage.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8),
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { launchModelPickerWithPermission() },
                                        colors = ButtonDefaults.buttonColors(containerColor = electricBlue),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Build,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Sideload Model", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }
                        } else {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = "SELECT ACTIVE ENGRAM:",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
                                )
                                Column(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.heightIn(max = 120.dp)
                                ) {
                                    availableModels.forEach { model ->
                                        val isCurrent = model == selectedModelName
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(
                                                    if (isCurrent) electricBlue.copy(alpha = 0.15f)
                                                    else Color.Transparent
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isCurrent) electricBlue else Color.Transparent,
                                                    shape = RoundedCornerShape(6.dp)
                                                )
                                                .clickable { viewModel.selectModel(model) }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Info,
                                                contentDescription = null,
                                                tint = if (isCurrent) electricBlue else Color(0xFF64748B),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = model,
                                                fontSize = 11.sp,
                                                color = if (isCurrent) Color.White else Color(0xFF94A3B8),
                                                fontFamily = FontFamily.Monospace,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isCurrent) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Active",
                                                    tint = Color(0xFF60A5FA),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "📡 PRESET ENGRAM DOWNLOADS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        val presets = listOf(
                            Triple("Gemma 2B Q2_K (GGUF - RichardErkhov Mirror)", "https://huggingface.co/RichardErkhov/google_-_gemma-2b-gguf/resolve/main/gemma-2b.Q2_K.gguf", "gemma-2b.Q2_K.gguf"),
                            Triple("Phi-3 Mini 3.8B Q4 (GGUF - Official)", "https://huggingface.co/microsoft/Phi-3-mini-4k-instruct-gguf/resolve/main/Phi-3-mini-4k-instruct-q4.gguf", "Phi-3-mini-4k-instruct-q4.gguf"),
                            Triple("Qwen 1.5 0.5B Chat Q4 (GGUF - Official)", "https://huggingface.co/Qwen/Qwen1.5-0.5B-Chat-GGUF/resolve/main/qwen1.5-0_5b-chat-q4_k_m.gguf", "qwen1.5-0_5b-chat-q4_k_m.gguf"),
                            Triple("Gemma 2B IT (Official)", "https://storage.googleapis.com/mediapipe-models/llm/gemma-2b-it-cpu-int4.bin", "gemma-2b-it-cpu-int4.bin")
                        )
                        
                        presets.forEach { (name, url, filename) ->
                            val alreadyInstalled = availableModels.contains(filename)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(
                                        width = 1.dp,
                                        color = if (alreadyInstalled) Color.Transparent else Color(0xFF334155),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (alreadyInstalled) securityEmerald else Color.White
                                    )
                                    Text(
                                        text = if (alreadyInstalled) "Installed & Ready" else "Tap to auto-pull down",
                                        fontSize = 9.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                                if (alreadyInstalled) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Installed",
                                        tint = securityEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                } else {
                                    IconButton(
                                        onClick = { viewModel.downloadModel(url, filename) },
                                        enabled = downloadingModelName == null,
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDropDown,
                                            contentDescription = "Download model",
                                            tint = if (downloadingModelName == null) electricBlue else Color(0xFF64748B),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "🔗 PULL CUSTOM ENGRAM LINK",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF64748B),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        
                        OutlinedTextField(
                            value = customModelUrl,
                            onValueChange = { customModelUrl = it },
                            label = { Text("Model Download URL (.bin / .gguf)", fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = electricBlue,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = electricBlue,
                                unfocusedLabelColor = Color(0xFF64748B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customModelFilename,
                            onValueChange = { customModelFilename = it },
                            label = { Text("Local Filename (e.g. model.gguf or model.bin)", fontSize = 10.sp) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = electricBlue,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = electricBlue,
                                unfocusedLabelColor = Color(0xFF64748B),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (customModelUrl.isNotBlank() && customModelFilename.isNotBlank()) {
                                    val nameTrim = customModelFilename.trim()
                                    val urlTrim = customModelUrl.trim()
                                    
                                    val hasGgufExt = nameTrim.endsWith(".gguf", ignoreCase = true) || urlTrim.contains(".gguf", ignoreCase = true)
                                    val hasBinExt = nameTrim.endsWith(".bin", ignoreCase = true) || urlTrim.contains(".bin", ignoreCase = true)
                                    
                                    val cleanedFilename = when {
                                        hasGgufExt -> if (nameTrim.endsWith(".gguf", ignoreCase = true)) nameTrim else "$nameTrim.gguf"
                                        hasBinExt -> if (nameTrim.endsWith(".bin", ignoreCase = true)) nameTrim else "$nameTrim.bin"
                                        else -> if (nameTrim.endsWith(".bin", ignoreCase = true)) nameTrim else "$nameTrim.bin"
                                    }
                                    
                                    viewModel.downloadModel(urlTrim, cleanedFilename)
                                    customModelUrl = ""
                                    customModelFilename = ""
                                }
                            },
                            enabled = downloadingModelName == null && customModelUrl.isNotBlank() && customModelFilename.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = electricBlue),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pull Down Custom Model", fontSize = 11.sp, color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Active engine state info badge
                        val engineBadgeText = when (val s = llmStatus) {
                            is LlmStatus.Ready -> {
                                if (s.modelName.lowercase(java.util.Locale.getDefault()).endsWith(".gguf")) {
                                    "Local GGUF Sandbox"
                                } else {
                                    "Offline MediaPipe Active"
                                }
                            }
                            is LlmStatus.Loading -> "Switching/Loading..."
                            is LlmStatus.Error -> "State Error / Standby"
                            is LlmStatus.FallbackActive -> "State: Sandboxed Companion"
                            LlmStatus.Uninitialized -> "Uninitialized"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF1E293B))
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (llmStatus is LlmStatus.Ready) securityEmerald else Color(0xFFFBBF24)
                                    )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = engineBadgeText,
                                fontSize = 10.sp,
                                color = Color(0xFFE2E8F0),
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(
                            onClick = { coroutineScope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("drawer_toggle_btn")
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                        }
                    },
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (isOnlineMode) "Connected AI" else "Offline AI",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                // Dynamic isolated notification tag
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isOnlineMode) electricBlue.copy(alpha = 0.15f)
                                            else securityEmerald.copy(alpha = 0.15f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isOnlineMode) Icons.Default.Share else Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = if (isOnlineMode) Color(0xFF60A5FA) else securityEmerald,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isOnlineMode) "CONNECTED" else "ISOLATED",
                                        color = if (isOnlineMode) Color(0xFF60A5FA) else securityEmerald,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            
                            // LLM State subtitle
                            val statusText = if (isOnlineMode) {
                                if (webSearchEnabled) "Gemini 3.5 Flash (Grounded Search Active)"
                                else "Gemini 3.5 Flash (Online Assistant)"
                            } else {
                                when (llmStatus) {
                                    is LlmStatus.Ready -> "MediaPipe Ready"
                                    is LlmStatus.Loading -> "Importing model binary..."
                                    is LlmStatus.Error -> "Hardware Fallback active"
                                    is LlmStatus.FallbackActive -> "Offline Llama / Local Sandbox"
                                    LlmStatus.Uninitialized -> "Initializing Local Stack..."
                                }
                            }
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleOnlineMode() },
                            modifier = Modifier.testTag("toggle_online_btn")
                        ) {
                            Icon(
                                imageVector = if (isOnlineMode) Icons.Default.Share else Icons.Default.Lock,
                                contentDescription = "Toggle Online/Offline Mode",
                                tint = if (isOnlineMode) Color(0xFF60A5FA) else Color(0xFF94A3B8)
                            )
                        }

                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Local Settings",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = darkSanctuaryBg
                    )
                )
            },
            containerColor = darkSanctuaryBg
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .drawBehind {
                        // Ambient blue glow behind input area
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(electricBlue.copy(alpha = 0.08f), Color.Transparent),
                                radius = 400.dp.toPx()
                            ),
                            center = center.copy(y = size.height)
                        )
                    }
            ) {
                if (messages.isEmpty()) {
                    // Elevated, gorgeous empty state welcoming the user
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isOnlineMode) Color(0xFF3B82F6).copy(alpha = 0.12f)
                                    else securityEmerald.copy(alpha = 0.12f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOnlineMode) Icons.Default.Share else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isOnlineMode) Color(0xFF60A5FA) else securityEmerald,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(18.dp))
                        
                        Text(
                            text = if (isOnlineMode) "Connected Hybrid Active" else "Offline AI Sandbox Active",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = if (isOnlineMode) "Running Gemini 3.5 Flash. Real-time DuckDuckGo lookup is trained directly into model conversation history dynamically. Full access."
                                   else "All processing remains strictly offline. Running Llama / Local Sandbox. Tap the top cloud switch to connect live.",
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.widthIn(max = 290.dp)
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        // Fast switch button on screen
                        Button(
                            onClick = { viewModel.toggleOnlineMode() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isOnlineMode) securityEmerald else Color(0xFF2563EB)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (isOnlineMode) Icons.Default.Lock else Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isOnlineMode) "Switch to Local Llama/Sandbox" else "Switch to Live Grounding Search",
                                fontSize = 12.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Small guide shortcuts
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CardOptionItem(
                                title = if (isOnlineMode) "🌐 Live Search" else "🔒 Local Vault",
                                subtitle = if (isOnlineMode) "How does web grounding help train my AI?" else "How is this 100% private?",
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isOnlineMode) {
                                    viewModel.onUserInputChange("Show me how real-time website search grounding updates the Gemini Flash model with live current news.")
                                } else {
                                    viewModel.onUserInputChange("How does this app guarantee 100% user privacy offline?")
                                }
                            }

                            CardOptionItem(
                                title = "📦 Sideload Model",
                                subtitle = "How do I load Llama?",
                                modifier = Modifier.weight(1f)
                            ) {
                                viewModel.onUserInputChange("How do I download and import a quantized Llama/Sandbox model file into local sandbox storage?")
                            }
                        }
                    }
                } else {
                    val listState = rememberLazyListState()
                    LaunchedEffect(messages.size) {
                        if (messages.isNotEmpty()) {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .padding(bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(messages) { message ->
                            ChatBubble(
                                message = message,
                                userBubbleBg = activeBubbleBg,
                                modelBubbleBg = cardSurfaceBg,
                                emerald = securityEmerald
                            )
                        }
                        
                        // Staggered writing pulsing bubble for typing
                        if (isGenerating) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(cardSurfaceBg)
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Computing...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF94A3B8)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(12.dp),
                                            strokeWidth = 2.dp,
                                            color = electricBlue
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Beautiful input footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, darkSanctuaryBg),
                                startY = 0f
                            )
                        )
                        .padding(16.dp)
                ) {
                    val controller = LocalSoftwareKeyboardController.current
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(cardSurfaceBg)
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(18.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { launchModelPickerWithPermission() },
                            modifier = Modifier.testTag("sideload_model_prompt_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Build,
                                contentDescription = "Sideload model from folder",
                                tint = Color(0xFF64748B)
                            )
                        }

                        TextField(
                            value = currentUserInput,
                            onValueChange = { viewModel.onUserInputChange(it) },
                            placeholder = {
                                Text(
                                    text = if (isOnlineMode) "Ask online + live web grounding search..." else "Ask local model isolated...",
                                    color = Color(0xFF64748B),
                                    fontSize = 13.sp
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            maxLines = 4,
                            singleLine = false,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_input_text_field"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            keyboardActions = KeyboardActions(onDone = { controller?.hide() })
                        )

                        IconButton(
                            onClick = {
                                if (currentUserInput.trim().isNotEmpty() && !isGenerating) {
                                    viewModel.sendMessage()
                                }
                            },
                            modifier = Modifier
                                .testTag("send_msg_btn")
                                .background(
                                    if (currentUserInput.trim().isEmpty() || isGenerating) Color(0xFF334155)
                                    else electricBlue,
                                    CircleShape
                                )
                                .size(36.dp),
                            enabled = currentUserInput.trim().isNotEmpty() && !isGenerating
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            containerColor = Color(0xFF1E293B),
            title = {
                Text(
                    text = "🛠️ Local AI Settings",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Toggle between high-privacy local execution or real-time internet searches with Gemini Flash.",
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Hybrid Configuration Header
                    Text(
                        text = "INTELLIGENCE ENGINE COGNITION",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = electricBlue,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .clickable { viewModel.toggleOnlineMode() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🌐 Connected Online Mode",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Uses Gemini 3.5 Flash for grounded replies",
                                color = Color(0xFF64748B),
                                fontSize = 10.sp
                            )
                        }
                        Switch(
                            checked = isOnlineMode,
                            onCheckedChange = { viewModel.toggleOnlineMode() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = Color(0xFF2563EB)
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    if (isOnlineMode) {
                        Text(
                            text = "GEMINI FLASH API CONFIGURATION",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = electricBlue,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { viewModel.updateApiKey(it) },
                            label = { Text("Gemini Flash API Key", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = electricBlue,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = electricBlue,
                                unfocusedLabelColor = Color(0xFF64748B)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("api_key_text_field")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = systemPrompt,
                            onValueChange = { viewModel.updateSystemPrompt(it) },
                            label = { Text("AI System Persona (Prompt)", fontSize = 11.sp) },
                            textStyle = androidx.compose.ui.text.TextStyle(
                                color = Color.White,
                                fontSize = 11.sp
                            ),
                            maxLines = 4,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = electricBlue,
                                unfocusedBorderColor = Color(0xFF334155),
                                focusedLabelColor = electricBlue,
                                unfocusedLabelColor = Color(0xFF64748B)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .clickable { viewModel.toggleWebSearch() }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "🔍 Live Background Web Grounding",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Searches DuckDuckGo in real-time to train the AI",
                                    color = Color(0xFF64748B),
                                    fontSize = 10.sp
                                )
                            }
                            Switch(
                                checked = webSearchEnabled,
                                onCheckedChange = { viewModel.toggleWebSearch() },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = Color(0xFF10B981)
                                )
                            )
                        }
                    } else {
                        Text(
                            text = "OFFLINE COGNITIVE SANDBOX",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = electricBlue,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )

                        when (val current = llmStatus) {
                            is LlmStatus.Ready -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF0F172A))
                                        .padding(8.dp)
                                ) {
                                    Text(
                                        text = "🟢 Active Model Binary",
                                        color = securityEmerald,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = current.modelName,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            is LlmStatus.Error -> {
                                Text(
                                    text = "⚠️ Status Error:\n${current.message}",
                                    color = Color(0xFFF87171),
                                    fontSize = 11.sp
                                )
                            }
                            is LlmStatus.FallbackActive -> {
                                Text(
                                    text = "💡 Llama / Local Sandbox Active\n\nNo physical .bin loaded yet. Running fully isolated local rule execution.",
                                    color = Color(0xFFFBBF24),
                                    fontSize = 11.sp
                                )
                            }
                            LlmStatus.Loading -> {
                                Text(
                                    text = "⏳ Sideloading model file into app container directory...",
                                    color = Color.White,
                                    fontSize = 11.sp
                                )
                            }
                            LlmStatus.Uninitialized -> {
                                Text(
                                    text = "Initializing local AI components...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = {
                                launchModelPickerWithPermission()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = electricBlue),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_import_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sideload Model (.bin)", fontSize = 12.sp, color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedButton(
                            onClick = {
                                viewModel.purgeCache()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                            border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_purge_btn"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Purge Import Cache", fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSettingsDialog = false },
                    modifier = Modifier.testTag("dialog_close_btn")
                ) {
                    Text("OK", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun CardOptionItem(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        ),
        modifier = modifier
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                lineHeight = 12.sp
            )
        }
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    userBubbleBg: Color,
    modelBubbleBg: Color,
    emerald: Color
) {
    val isUser = message.role == "user"
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isUser) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 295.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Icon(
                    imageVector = if (isUser) Icons.Default.Person else Icons.Default.Build,
                    contentDescription = null,
                    tint = if (isUser) Color(0xFF60A5FA) else Color(0xFFA78BFA),
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (isUser) "Me" else "Local LLM",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF64748B)
                )
            }
            
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .background(if (isUser) userBubbleBg else modelBubbleBg)
                    .border(
                        1.dp,
                        if (isUser) Color.Transparent else Color(0xFF334155),
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (isUser) 16.dp else 4.dp,
                            bottomEnd = if (isUser) 4.dp else 16.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Column {
                    Text(
                        text = message.content,
                        color = Color.White,
                        fontSize = 13.sp,
                        lineHeight = 17.sp
                    )
                    
                    // Show performance diagnostic card details for model responses
                    if (!isUser && message.inferenceTimeMs > 0L) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF0F172A))
                                .border(0.5.dp, Color(0xFF334155), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Inference time",
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "${message.inferenceTimeMs}ms",
                                    fontSize = 9.sp,
                                    color = Color(0xFFE2E8F0),
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Token speed",
                                    tint = emerald,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = String.format("%.1f t/s", message.tokensPerSecond),
                                    fontSize = 9.sp,
                                    color = emerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = message.engineType,
                                fontSize = 8.sp,
                                color = Color(0xFF64748B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
