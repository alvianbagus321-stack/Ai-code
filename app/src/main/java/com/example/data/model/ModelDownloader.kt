package com.example.data.model

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ModelDownloader {
    private val TAG = "ModelDownloader"
    private val _downloadingModelName = MutableStateFlow<String?>(null)
    val downloadingModelName: StateFlow<String?> = _downloadingModelName.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Float?>(null)
    val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()
    
    private val _downloadError = MutableStateFlow<String?>(null)
    val downloadError: StateFlow<String?> = _downloadError.asStateFlow()

    private val downloadScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var downloadJob: Job? = null
    
    private var downloadId: Long = -1L
    private var receiverRegistered = false
    private var maxProgressAchieved = 0f

    private val onDownloadComplete = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
            if (downloadId == id) {
                checkDownloadStatus(context)
            }
        }
    }

    fun restoreDownloadState(context: Context) {
        val prefs = context.getSharedPreferences("model_download_prefs", Context.MODE_PRIVATE)
        val savedId = prefs.getLong("download_id", -1L)
        val savedName = prefs.getString("downloading_model_name", null)

        if (savedId != -1L && savedName != null) {
            downloadId = savedId
            _downloadingModelName.value = savedName
            _downloadProgress.value = prefs.getFloat("download_progress", 0f)
            maxProgressAchieved = _downloadProgress.value ?: 0f

            if (!receiverRegistered) {
                try {
                    context.applicationContext.registerReceiver(
                        onDownloadComplete,
                        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                        Context.RECEIVER_EXPORTED
                    )
                    receiverRegistered = true
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register receiver during restore", e)
                }
            }

            checkDownloadStatus(context)
        }
    }

    fun startDownload(context: Context, modelUrl: String, displayName: String) {
        if (_downloadingModelName.value != null) return

        _downloadingModelName.value = displayName
        maxProgressAchieved = 0f
        _downloadProgress.value = 0f
        _downloadError.value = null

        try {
            val request = DownloadManager.Request(Uri.parse(modelUrl)).apply {
                setTitle("Downloading $displayName")
                setDescription("Please wait while the AI model is downloading...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, displayName)
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = downloadManager.enqueue(request)

            context.getSharedPreferences("model_download_prefs", Context.MODE_PRIVATE).edit()
                .putLong("download_id", downloadId)
                .putString("downloading_model_name", displayName)
                .putFloat("download_progress", 0f)
                .apply()

            if (!receiverRegistered) {
                context.applicationContext.registerReceiver(
                    onDownloadComplete,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
                receiverRegistered = true
            }

            startPollingProgress(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error starting download", e)
            _downloadError.value = e.message ?: "Failed to start download"
            resetState(context)
        }
    }

    private fun startPollingProgress(context: Context) {
        downloadJob?.cancel()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadJob = downloadScope.launch {
            var querying = true
            while (querying) {
                delay(1000)
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                if (cursor != null && cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    if (statusIndex >= 0) {
                        val status = cursor.getInt(statusIndex)
                        
                        val bytesDownloadedIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                        val bytesTotalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                        
                        if (bytesDownloadedIndex >= 0 && bytesTotalIndex >= 0) {
                            val bytesDownloaded = cursor.getLong(bytesDownloadedIndex)
                            val bytesTotal = cursor.getLong(bytesTotalIndex)
                            
                            if (bytesTotal > 0) {
                                val progressVal = bytesDownloaded.toFloat() / bytesTotal.toFloat()
                                if (progressVal > maxProgressAchieved) {
                                    maxProgressAchieved = progressVal
                                    _downloadProgress.value = progressVal

                                    context.getSharedPreferences("model_download_prefs", Context.MODE_PRIVATE).edit()
                                        .putFloat("download_progress", progressVal)
                                        .apply()
                                }
                            }
                        }

                        if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) {
                            querying = false
                            checkDownloadStatus(context)
                        }
                    }
                } else {
                    querying = false
                }
                cursor?.close()
            }
        }
    }

    private fun checkDownloadStatus(context: Context) {
        downloadJob?.cancel()
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        var isFinished = false
        if (cursor != null && cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (statusIndex >= 0) {
                val status = cursor.getInt(statusIndex)
                if (status == DownloadManager.STATUS_SUCCESSFUL) {
                    isFinished = true
                    val uriIndex = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                    if (uriIndex >= 0) {
                        val fileUriString = cursor.getString(uriIndex)
                        if (fileUriString != null) {
                            processDownloadedFile(context, Uri.parse(fileUriString), _downloadingModelName.value ?: "unknown.bin")
                        }
                    }
                } else if (status == DownloadManager.STATUS_FAILED) {
                    isFinished = true
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)
                    val reason = if (reasonIndex >= 0) cursor.getInt(reasonIndex).toString() else "Unknown Error"
                    _downloadError.value = "Download failed (Reason: $reason). Please check your connection and storage space."
                    resetState(context)
                } else {
                    startPollingProgress(context)
                }
            }
        } else {
            resetState(context)
        }
        cursor?.close()
        
        if (isFinished && receiverRegistered) {
            try {
                context.applicationContext.unregisterReceiver(onDownloadComplete)
                receiverRegistered = false
            } catch (e: Exception) {}
        }
    }

    private fun processDownloadedFile(context: Context, sourceUri: Uri, displayName: String) {
        downloadScope.launch {
            try {
                val modelsDir = File(context.filesDir, "local_llm_models").apply { if (!exists()) mkdirs() }
                val destination = File(modelsDir, displayName)
                if (destination.exists()) destination.delete()

                val downloadedFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), displayName)
                if (downloadedFile.exists()) {
                    downloadedFile.copyTo(destination, overwrite = true)
                    downloadedFile.delete()
                } else {
                    context.contentResolver.openInputStream(sourceUri)?.use { input ->
                        FileOutputStream(destination).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
                
                // Cleanup original download
                val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                downloadManager.remove(downloadId)
                
                Log.d(TAG, "Download and copy completed: $displayName")
                
                val intent = android.content.Intent("MODEL_DOWNLOAD_COMPLETE")
                intent.putExtra("MODEL_NAME", displayName)
                context.sendBroadcast(intent)
                
                resetState(context)
            } catch (e: Exception) {
                Log.e(TAG, "File copy error", e)
                _downloadError.value = "Failed to process downloaded file: ${e.message}"
                resetState(context)
            }
        }
    }

    fun cancel(context: Context) {
        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
        }
        downloadJob?.cancel()
        resetState(context)
    }
    
    private fun resetState(context: Context? = null) {
        _downloadingModelName.value = null
        _downloadProgress.value = null
        downloadId = -1L
        context?.let {
            val prefs = it.getSharedPreferences("model_download_prefs", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        }
    }
}
