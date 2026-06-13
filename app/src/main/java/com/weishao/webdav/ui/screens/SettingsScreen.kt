package com.weishao.webdav.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.weishao.webdav.data.SyncConfig
import com.weishao.webdav.data.WebDavConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    config: WebDavConfig,
    onConfigChange: (WebDavConfig) -> Unit,
    onBack: () -> Unit
) {
    var autoUploadEnabled by remember { mutableStateOf(config.autoUploadEnabled) }
    var uploadConcurrency by remember { mutableFloatStateOf(config.uploadConcurrency.toFloat()) }
    var syncConfigs by remember { mutableStateOf(config.syncConfigs) }
    var downloadPath by remember { mutableStateOf(config.downloadPath) }
    
    var showAddDialog by remember { mutableStateOf(false) }
    var localPath by remember { mutableStateOf("") }
    var remotePath by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = {
                        onConfigChange(config.copy(
                            autoUploadEnabled = autoUploadEnabled,
                            uploadConcurrency = uploadConcurrency.toInt(),
                            syncConfigs = syncConfigs,
                            downloadPath = downloadPath
                        ))
                        onBack()
                    }) {
                        Text("保存")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("开启自动上传", modifier = Modifier.weight(1f))
                Switch(checked = autoUploadEnabled, onCheckedChange = { autoUploadEnabled = it })
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("上传并发数: ${uploadConcurrency.toInt()}")
            Slider(
                value = uploadConcurrency,
                onValueChange = { uploadConcurrency = it },
                valueRange = 1f..10f,
                steps = 8
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("下载保存路径", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = downloadPath,
                onValueChange = { downloadPath = it },
                label = { Text("下载路径 (留空使用默认下载目录)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("同步目录配置", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加")
                }
            }
            
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(syncConfigs) { sync ->
                    ListItem(
                        headlineContent = { Text("本地: ${sync.localPath}") },
                        supportingContent = { Text("远程: ${sync.remotePath}") },
                        trailingContent = {
                            IconButton(onClick = {
                                syncConfigs = syncConfigs.filter { it != sync }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加同步路径") },
            text = {
                Column {
                    TextField(value = localPath, onValueChange = { localPath = it }, label = { Text("本地路径") })
                    TextField(value = remotePath, onValueChange = { remotePath = it }, label = { Text("远程路径") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (localPath.isNotEmpty()) {
                        syncConfigs = syncConfigs + SyncConfig(localPath, remotePath)
                        showAddDialog = false
                        localPath = ""
                        remotePath = ""
                    }
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}
