package com.weishao.webdav.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FileViewDialog(
    fileName: String,
    content: String?,
    tempFile: File?,
    remoteUrl: String?,
    authHeader: String?,
    isPreviewLoading: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(fileName) },
        text = {
            Box(
                modifier = Modifier
                    .sizeIn(maxHeight = 400.dp, maxWidth = 300.dp)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (isPreviewLoading) {
                    CircularProgressIndicator()
                } else if (content != null) {
                    Text(
                        text = content,
                        modifier = Modifier.verticalScroll(rememberScrollState())
                    )
                } else if (remoteUrl != null) {
                    val ext = fileName.substringAfterLast('.').lowercase()
                    if (ext in listOf("jpg", "jpeg", "png", "gif", "webp", "bmp")) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val request = remember(remoteUrl, authHeader) {
                            coil.request.ImageRequest.Builder(context)
                                .data(remoteUrl)
                                .setHeader("Authorization", authHeader ?: "")
                                .build()
                        }
                        coil.compose.AsyncImage(
                            model = request,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else if (ext in listOf("mp3", "wav", "ogg", "m4a", "flac")) {
                        AudioPlayer(remoteUrl, authHeader)
                    } else {
                        Text("不支持预览此文件类型")
                    }
                } else if (tempFile != null) {
                    Text("文件已下载到临时目录")
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onDownload) {
                    Text("下载")
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        }
    )
}

@Composable
fun AudioPlayer(url: String, authHeader: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaPlayer = remember { 
        android.media.MediaPlayer().apply {
            val headers = mutableMapOf<String, String>()
            authHeader?.let { headers["Authorization"] = it }
            setDataSource(context, android.net.Uri.parse(url), headers)
            prepareAsync()
        }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var isPrepared by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        mediaPlayer.setOnPreparedListener {
            isPrepared = true
        }
        onDispose {
            mediaPlayer.release()
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("音频流媒体")
        if (!isPrepared) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        }
        Row {
            Button(
                onClick = {
                    if (isPlaying) {
                        mediaPlayer.pause()
                    } else {
                        mediaPlayer.start()
                    }
                    isPlaying = !isPlaying
                },
                enabled = isPrepared
            ) {
                Text(if (isPlaying) "暂停" else "播放")
            }
        }
    }
}
