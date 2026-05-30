package com.weishao.webdav.ui

import android.app.Application
import android.content.Intent
import android.os.Build
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
import java.util.concurrent.TimeUnit

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
        val context = getApplication<Application>()
        val url = repository?.buildUrl(fileItem.path, false).toString()
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setAllowedNetworkTypes(android.app.DownloadManager.Request.NETWORK_WIFI or android.app.DownloadManager.Request.NETWORK_MOBILE)
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setTitle(fileItem.name)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, fileItem.name)
            .addRequestHeader("Authorization", okhttp3.Credentials.basic(_config.value.username, _config.value.password))

        val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        manager.enqueue(request)
    }
}
