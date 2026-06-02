package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 1. Initialize complete offline database, engine, and repository
        val database = ChatDatabase.getDatabase(applicationContext)
        val llmEngine = OfflineLlmEngine(applicationContext)
        val repository = ChatRepository(database.chatDao(), llmEngine)
        
        // 2. Instantiate stateful viewmodel
        val viewModel = ViewModelProvider(
            this,
            ChatViewModel.Factory(repository)
        )[ChatViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    ChatScreen(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
