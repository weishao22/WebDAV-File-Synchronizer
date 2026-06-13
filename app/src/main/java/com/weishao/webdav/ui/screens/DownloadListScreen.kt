package com.weishao.webdav.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.weishao.webdav.data.DownloadItem
import com.weishao.webdav.data.DownloadStatus
import com.weishao.webdav.ui.WebDavViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadListScreen(
    viewModel: WebDavViewModel,
    onBack: () -> Unit
) {
    val downloads by viewModel.downloads.collectAsState()
    val hasCompleted = downloads.any {
        it.status == DownloadStatus.Completed || it.status == DownloadStatus.Failed || it.status == DownloadStatus.Cancelled
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("下载列表") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (hasCompleted) {
                        IconButton(onClick = { viewModel.clearDownloads() }) {
                            Icon(Icons.Default.ClearAll, contentDescription = "清空已完成")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (downloads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无下载任务", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(downloads, key = { it.id }) { item ->
                    DownloadRow(
                        item = item,
                        onPause = { viewModel.pauseDownload(item.id) },
                        onResume = { viewModel.resumeDownload(item.id) },
                        onCancel = { viewModel.cancelDownload(item.id) },
                        onRemove = { viewModel.removeDownload(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DownloadRow(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRemove: () -> Unit
) {
    ListItem(
        headlineContent = { Text(item.fileName) },
        supportingContent = {
            Column {
                when (item.status) {
                    DownloadStatus.Pending -> Text("等待中")
                    DownloadStatus.Downloading -> {
                        if (item.totalBytes > 0) {
                            Column {
                                Text("下载中 ${item.progressPercent}%  ${item.speed}")
                                LinearProgressIndicator(
                                    progress = { item.progress },
                                    modifier = Modifier.fillMaxWidth(0.6f)
                                )
                                Text(
                                    "${formatSize(item.downloadedBytes)} / ${formatSize(item.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        } else {
                            Text("下载中  ${item.speed}")
                        }
                    }
                    DownloadStatus.Paused -> {
                        if (item.totalBytes > 0) {
                            Text("已暂停 ${item.progressPercent}%")
                        } else {
                            Text("已暂停")
                        }
                    }
                    DownloadStatus.Completed -> Text("已完成", color = Color(0xFF4CAF50))
                    DownloadStatus.Failed -> Text("失败: ${item.errorMessage ?: "未知错误"}", color = MaterialTheme.colorScheme.error)
                    DownloadStatus.Cancelled -> Text("已取消", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        leadingContent = {
            when (item.status) {
                DownloadStatus.Pending -> Icon(
                    Icons.Default.HourglassEmpty, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DownloadStatus.Downloading -> CircularProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.size(32.dp),
                    strokeWidth = 3.dp
                )
                DownloadStatus.Paused -> Icon(
                    Icons.Default.PauseCircle, contentDescription = null,
                    tint = Color(0xFFFFA726)
                )
                DownloadStatus.Completed -> Icon(
                    Icons.Default.CheckCircle, contentDescription = null,
                    tint = Color(0xFF4CAF50)
                )
                DownloadStatus.Failed -> Icon(
                    Icons.Default.Error, contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                DownloadStatus.Cancelled -> Icon(
                    Icons.Default.Cancel, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Row {
                when (item.status) {
                    DownloadStatus.Pending, DownloadStatus.Downloading -> {
                        IconButton(onClick = onPause) {
                            Icon(Icons.Default.Pause, contentDescription = "暂停")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    }
                    DownloadStatus.Paused -> {
                        IconButton(onClick = onResume) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "继续")
                        }
                        IconButton(onClick = onCancel) {
                            Icon(Icons.Default.Close, contentDescription = "取消")
                        }
                    }
                    DownloadStatus.Completed, DownloadStatus.Failed, DownloadStatus.Cancelled -> {
                        IconButton(onClick = onRemove) {
                            Icon(Icons.Default.Delete, contentDescription = "移除")
                        }
                    }
                }
            }
        }
    )
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}
