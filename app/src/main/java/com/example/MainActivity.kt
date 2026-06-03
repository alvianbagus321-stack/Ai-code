package com.example

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.database.ChatDatabase
import com.example.data.model.OfflineLlmEngine
import com.example.data.repository.ChatRepository
import com.example.ui.chat.ChatScreen
import com.example.ui.chat.ChatViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Handle gracefully, ignoring for now as they are checked specifically when used
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val requiredPermissions = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requiredPermissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        permissionLauncher.launch(requiredPermissions.toTypedArray())

        // 1. Initialize complete offline database, engine, and repository
        val database = ChatDatabase.getDatabase(applicationContext)
        val llmEngine = OfflineLlmEngine(applicationContext)
        val repository = ChatRepository(database.chatDao(), llmEngine)
        
        // 2. Instantiate stateful viewmodel
        val viewModel = ViewModelProvider(
            this,
            ChatViewModel.Factory(repository)
        )[ChatViewModel::class.java]

        // 3. Process any incoming deep-linked session data
        intent?.data?.let { handleIncomingUri(it, viewModel) }

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val showFilePermissionDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                val showBatteryPermissionDialog = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                val context = androidx.compose.ui.platform.LocalContext.current
                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current

                androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                showFilePermissionDialog.value = !android.os.Environment.isExternalStorageManager()
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val pm = getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
                                showBatteryPermissionDialog.value = !pm.isIgnoringBatteryOptimizations(packageName)
                            }
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                if (showFilePermissionDialog.value) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { /* forced */ },
                        title = { androidx.compose.material3.Text("Izin Penyimpanan Diperlukan") },
                        text = { androidx.compose.material3.Text("Untuk menyimpan dan memuat model AI secara lokal, izinkan akses All Files Access (Manage External Storage).") },
                        confirmButton = {
                            androidx.compose.material3.Button(onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                                intent.addCategory("android.intent.category.DEFAULT")
                                intent.data = android.net.Uri.parse("package:$packageName")
                                try {
                                    startActivity(intent)
                                } catch (e: Exception) {
                                    val fallbackIntent = android.content.Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    startActivity(fallbackIntent)
                                }
                            }) { androidx.compose.material3.Text("Buka Pengaturan") }
                        }
                    )
                }

                if (!showFilePermissionDialog.value && showBatteryPermissionDialog.value) {
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { /* forced */ },
                        title = { androidx.compose.material3.Text("Abaikan Optimasi Baterai") },
                        text = { androidx.compose.material3.Text("Agar AI bisa berjalan lancar sebagai proses latar belakang saat layarnya mati, abaikan optimasi baterai untuk aplikasi ini.") },
                        confirmButton = {
                            androidx.compose.material3.Button(onClick = {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                                intent.data = android.net.Uri.parse("package:$packageName")
                                startActivity(intent)
                            }) { androidx.compose.material3.Text("Buka Pengaturan") }
                        }
                    )
                }

                ChatScreen(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val database = ChatDatabase.getDatabase(applicationContext)
        val llmEngine = OfflineLlmEngine(applicationContext)
        val repository = ChatRepository(database.chatDao(), llmEngine)
        val viewModel = ViewModelProvider(this, ChatViewModel.Factory(repository))[ChatViewModel::class.java]
        intent.data?.let { handleIncomingUri(it, viewModel) }
    }

    private fun handleIncomingUri(uri: android.net.Uri, viewModel: ChatViewModel) {
        val base64Data = uri.getQueryParameter("data")
        if (!base64Data.isNullOrBlank()) {
            try {
                val decodedBytes = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
                val jsonStr = String(decodedBytes, Charsets.UTF_8)
                viewModel.importSharedSessionFromJson(jsonStr)
            } catch (e: Exception) {
                viewModel.logEvent("Failed parsing deep-linked shared chat: ${e.message}")
            }
        }
    }
}
