package com.weishao.webdav.ui

import android.app.Application
import android.content.Intent
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.weishao.webdav.data.*
import com.weishao.webdav.data.db.AppDatabase
import com.weishao.webdav.data.db.SyncedFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class WebDavViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = WebDavDataStore(application)
    private val workManager = WorkManager.getInstance(application)
    private val db = AppDatabase.getDatabase(application)
    
    private val _config = MutableStateFlow(WebDavConfig())
    val config: StateFlow<WebDavConfig> = _config

    init {
        LogManager.init(application)
        viewModelScope.launch {
            dataStore.configFlow.collectLatest { 
                _config.value = it
                repository = WebDavRepository(it)
                if (it.url.isNotEmpty()) {
                    loadFiles()
                    scheduleAutoUpload(it)
                }
            }
        }
    }

    private fun scheduleAutoUpload(config: WebDavConfig) {
        if (config.autoUploadEnabled && config.syncConfigs.isNotEmpty()) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<AutoUploadWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                "auto_upload",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        } else {
            workManager.cancelUniqueWork("auto_upload")
        }
    }

    fun syncNow() {
        val context = getApplication<Application>()
        val intent = Intent(context, com.weishao.webdav.service.SyncService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        
        viewModelScope.launch {
            _error.value = "已启动后台同步服务..."
            delay(3000)
            if (_error.value == "已启动后台同步服务...") {
                _error.value = null
            }
        }
    }

    fun loadFiles(path: String = _currentPath.value) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val repo = repository ?: WebDavRepository(_config.value)
                val result = repo.listFiles(path)
                _files.value = result
                _currentPath.value = path
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files

    private val _currentPath = MutableStateFlow("")
    val currentPath: StateFlow<String> = _currentPath

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isPreviewLoading = MutableStateFlow(false)
    val isPreviewLoading: StateFlow<Boolean> = _isPreviewLoading

    private val _remoteUrl = MutableStateFlow<String?>(null)
    val remoteUrl: StateFlow<String?> = _remoteUrl

    private val _authHeader = MutableStateFlow<String?>(null)
    val authHeader: StateFlow<String?> = _authHeader

    private val _searchTerm = MutableStateFlow("")
    val searchTerm: StateFlow<String> = _searchTerm

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _fileContent = MutableStateFlow<String?>(null)
    val fileContent: StateFlow<String?> = _fileContent

    private val _tempFile = MutableStateFlow<File?>(null)
    val tempFile: StateFlow<File?> = _tempFile

    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage

    private val _downloads = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloads: StateFlow<List<DownloadItem>> = _downloads

    private val downloadIdCounter = AtomicLong(0)
    private val downloadJobs = ConcurrentHashMap<Long, Job>()
    private val downloadCalls = ConcurrentHashMap<Long, okhttp3.Call>()

    private var repository: WebDavRepository? = null

    fun updateConfig(newConfig: WebDavConfig) {
        viewModelScope.launch {
            dataStore.saveConfig(newConfig)
        }
    }

    fun logout() {
        viewModelScope.launch {
            val current = _config.value
            val newConfig = current.copy(
                url = "",
                username = "",
                password = ""
            )
            dataStore.saveConfig(newConfig)
        }
    }

    fun viewFile(fileItem: FileItem) {
        viewModelScope.launch {
            _isPreviewLoading.value = true
            _fileContent.value = null
            _tempFile.value = null
            _remoteUrl.value = null
            _authHeader.value = null
            
            try {
                val context = getApplication<Application>()
                val repo = repository ?: WebDavRepository(_config.value)
                val ext = fileItem.name.substringAfterLast('.').lowercase()
                
                if (ext in listOf("txt", "log", "json", "md")) {
                    val temp = File(context.cacheDir, fileItem.name)
                    repo.downloadFile(fileItem.path, temp)
                    _fileContent.value = temp.readText()
                    _tempFile.value = temp
                } else {
                    _remoteUrl.value = repo.buildUrl(fileItem.path, false).toString()
                    _authHeader.value = okhttp3.Credentials.basic(_config.value.username, _config.value.password)
                }
            } catch (e: Exception) {
                _error.value = "查看失败: ${e.message}"
            } finally {
                _isPreviewLoading.value = false
            }
        }
    }

    fun closeFileView() {
        _fileContent.value = null
        _tempFile.value?.delete()
        _tempFile.value = null
        _remoteUrl.value = null
        _authHeader.value = null
    }

    fun uploadFile(localFile: File) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val repo = repository ?: WebDavRepository(_config.value)
                val remotePath = if (_currentPath.value.isEmpty()) localFile.name else "${_currentPath.value}/${localFile.name}"
                repo.uploadFile(localFile, remotePath)
                db.syncedFileDao().insert(SyncedFile(remotePath, localFile.absolutePath, localFile.length(), localFile.lastModified()))
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteFile(fileItem: FileItem) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val repo = repository ?: WebDavRepository(_config.value)
                repo.delete(fileItem.path)
                db.syncedFileDao().deleteByPrefix(fileItem.path)
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    fun renameFile(fileItem: FileItem, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val repo = repository ?: WebDavRepository(_config.value)
                val parentPath = fileItem.path.substringBeforeLast('/', "")
                val destPath = if (parentPath.isEmpty()) newName else "$parentPath/$newName"
                repo.move(fileItem.path, destPath)
                db.syncedFileDao().deleteByPrefix(fileItem.path)
                loadFiles()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadFileFromUri(uri: android.net.Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val context = getApplication<Application>()
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = getFileName(context, uri) ?: "upload_${System.currentTimeMillis()}"
                val tempFile = File(context.cacheDir, fileName)
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                val repo = repository ?: WebDavRepository(_config.value)
                val remotePath = if (_currentPath.value.isEmpty()) fileName else "${_currentPath.value}/$fileName"
                repo.uploadFile(tempFile, remotePath)
                tempFile.delete()
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getFileName(context: android.content.Context, uri: android.net.Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name ?: uri.path?.substringAfterLast('/')
    }

    fun setSearchTerm(term: String) {
        _searchTerm.value = term
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val repo = repository ?: WebDavRepository(_config.value)
                val folderPath = if (_currentPath.value.isEmpty()) name else "${_currentPath.value}/$name"
                repo.createDirectory(folderPath)
                loadFiles()
            } catch (e: Exception) {
                _error.value = "创建文件夹失败: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun downloadToSystem(fileItem: FileItem) {
        val downloadId = downloadIdCounter.incrementAndGet()
        val item = DownloadItem(
            id = downloadId,
            fileName = fileItem.name,
            remotePath = fileItem.path,
            status = DownloadStatus.Pending,
            totalBytes = fileItem.size
        )
        _downloads.value = _downloads.value + item
        startDownload(downloadId)
    }

    fun pauseDownload(downloadId: Long) {
        downloadCalls[downloadId]?.cancel()
        downloadCalls.remove(downloadId)
        downloadJobs[downloadId]?.cancel()
        downloadJobs.remove(downloadId)
        _downloads.value = _downloads.value.map {
            if (it.id == downloadId) it.copy(status = DownloadStatus.Paused) else it
        }
    }

    fun resumeDownload(downloadId: Long) {
        _downloads.value = _downloads.value.map {
            if (it.id == downloadId) it.copy(
                status = DownloadStatus.Pending,
                totalBytes = 0L,
                speed = ""
            ) else it
        }
        startDownload(downloadId)
    }

    fun cancelDownload(downloadId: Long) {
        downloadCalls[downloadId]?.cancel()
        downloadCalls.remove(downloadId)
        downloadJobs[downloadId]?.cancel()
        downloadJobs.remove(downloadId)
        val item = _downloads.value.find { it.id == downloadId } ?: return
        _downloads.value = _downloads.value.map {
            if (it.id == downloadId) it.copy(status = DownloadStatus.Cancelled) else it
        }
        val context = getApplication<Application>()
        val tempFile = File(resolveDownloadDir(context), "${item.fileName}.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    fun removeDownload(downloadId: Long) {
        downloadCalls[downloadId]?.cancel()
        downloadCalls.remove(downloadId)
        downloadJobs[downloadId]?.cancel()
        downloadJobs.remove(downloadId)
        _downloads.value = _downloads.value.filter { it.id != downloadId }
    }

    fun clearDownloads() {
        val activeIds = _downloads.value
            .filter { it.status == DownloadStatus.Downloading || it.status == DownloadStatus.Pending }
            .map { it.id }
            .toSet()
        _downloads.value = _downloads.value.filter { it.id in activeIds }
    }

    fun clearDownloadMessage() {
        _downloadMessage.value = null
    }

    private fun startDownload(downloadId: Long) {
        val item = _downloads.value.find { it.id == downloadId } ?: return
        val job = viewModelScope.launch {
            val repo = repository ?: WebDavRepository(_config.value)
            val context = getApplication<Application>()

            _downloads.value = _downloads.value.map {
                if (it.id == downloadId) it.copy(status = DownloadStatus.Downloading) else it
            }

            val downloadDir = resolveDownloadDir(context)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }

            val tempFile = File(downloadDir, "${item.fileName}.tmp")
            val offset = if (tempFile.exists()) tempFile.length() else 0L

            var lastBytes = offset
            var lastTime = System.currentTimeMillis()

            try {
                repo.downloadFileWithProgress(
                    item.remotePath, tempFile, offset,
                    onProgress = { downloaded, total ->
                    val now = System.currentTimeMillis()
                    val elapsed = (now - lastTime).coerceAtLeast(1)
                    val bytesDelta = downloaded - lastBytes
                    val speedStr = if (elapsed >= 500 || bytesDelta > 0) {
                        lastBytes = downloaded
                        lastTime = now
                        formatSpeed(bytesDelta, elapsed)
                    } else {
                        _downloads.value.find { it.id == downloadId }?.speed ?: ""
                    }

                    _downloads.value = _downloads.value.map {
                        if (it.id == downloadId) it.copy(
                            downloadedBytes = downloaded,
                            totalBytes = total,
                            speed = speedStr
                        ) else it
                    }
                },
                onCallCreated = { call ->
                    downloadCalls[downloadId] = call
                }
            )

                val finalFile = File(downloadDir, item.fileName)
                if (finalFile.exists()) {
                    finalFile.delete()
                }
                tempFile.renameTo(finalFile)

                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(finalFile.absolutePath),
                    null
                ) { _, _ -> }

                _downloads.value = _downloads.value.map {
                    if (it.id == downloadId) it.copy(
                        status = DownloadStatus.Completed,
                        speed = ""
                    ) else it
                }
                _downloadMessage.value = "下载完成: ${item.fileName}"
                downloadJobs.remove(downloadId)
                downloadCalls.remove(downloadId)
            } catch (e: CancellationException) {
                downloadJobs.remove(downloadId)
                downloadCalls.remove(downloadId)
                throw e
            } catch (e: Exception) {
                downloadJobs.remove(downloadId)
                downloadCalls.remove(downloadId)
                val current = _downloads.value.find { it.id == downloadId }
                if (current?.status != DownloadStatus.Paused && current?.status != DownloadStatus.Cancelled) {
                    _downloads.value = _downloads.value.map {
                        if (it.id == downloadId) it.copy(
                            status = DownloadStatus.Failed,
                            errorMessage = e.message,
                            speed = ""
                        ) else it
                    }
                    _downloadMessage.value = "下载失败: ${e.message}"
                }
            }
        }
        downloadJobs[downloadId] = job
    }

    private fun formatSpeed(bytesDelta: Long, elapsedMs: Long): String {
        val bytesPerSec = (bytesDelta * 1000.0 / elapsedMs).toLong()
        return when {
            bytesPerSec >= 1_000_000 -> "%.1f MB/s".format(bytesPerSec / 1_000_000.0)
            bytesPerSec >= 1_000 -> "%.1f KB/s".format(bytesPerSec / 1_000.0)
            else -> "$bytesPerSec B/s"
        }
    }

    private fun resolveDownloadDir(context: android.content.Context): File {
        val configPath = _config.value.downloadPath.trim()
        return if (configPath.isEmpty()) {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        } else if (configPath.startsWith("/")) {
            File(configPath)
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), configPath)
        }
    }
}
