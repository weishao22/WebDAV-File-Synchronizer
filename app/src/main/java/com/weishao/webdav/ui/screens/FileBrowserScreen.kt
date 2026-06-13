package com.weishao.webdav.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.weishao.webdav.data.FileItem
import com.weishao.webdav.ui.WebDavViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    viewModel: WebDavViewModel,
    onLogout: () -> Unit,
    onSettings: () -> Unit,
    onViewLogs: () -> Unit,
    onViewDownloads: () -> Unit
) {
    val files by viewModel.files.collectAsState()
    val currentPath by viewModel.currentPath.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isPreviewLoading by viewModel.isPreviewLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val fileContent by viewModel.fileContent.collectAsState()
    val tempFile by viewModel.tempFile.collectAsState()
    val remoteUrl by viewModel.remoteUrl.collectAsState()
    val authHeader by viewModel.authHeader.collectAsState()
    val searchTerm by viewModel.searchTerm.collectAsState()
    val downloadMessage by viewModel.downloadMessage.collectAsState()

    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var selectedFileForView by remember { mutableStateOf<FileItem?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(downloadMessage) {
        downloadMessage?.let {
            scope.launch { snackbarHostState.showSnackbar(it) }
            viewModel.clearDownloadMessage()
        }
    }

    val filteredFiles = remember(files, searchTerm) {
        if (searchTerm.isEmpty()) files else files.filter { it.name.contains(searchTerm, ignoreCase = true) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadFileFromUri(it) }
    }

    if (selectedFileForView != null) {
        FileViewDialog(
            fileName = selectedFileForView!!.name,
            content = fileContent,
            tempFile = tempFile,
            remoteUrl = remoteUrl,
            authHeader = authHeader,
            isPreviewLoading = isPreviewLoading,
            onDismiss = {
                viewModel.closeFileView()
                selectedFileForView = null
            },
            onDownload = {
                selectedFileForView?.let { viewModel.downloadToSystem(it) }
            }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("新建文件夹") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("文件夹名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotEmpty()) {
                        viewModel.createFolder(newName)
                    }
                    showCreateFolderDialog = false
                    newName = ""
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showRenameDialog != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("重命名") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("新名称") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showRenameDialog?.let { viewModel.renameFile(it, newName) }
                    showRenameDialog = null
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showSearchDialog) {
        var searchText by remember { mutableStateOf(searchTerm) }
        AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("搜索文件") },
            text = {
                TextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("输入文件名关键词...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setSearchTerm(searchText)
                    showSearchDialog = false
                }) {
                    Text("搜索")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val displayTitle = if (currentPath.isEmpty()) "/" else currentPath.substringAfterLast('/')
                    Text(
                        text = displayTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (currentPath.isNotEmpty()) {
                        IconButton(onClick = {
                            val parent = currentPath.substringBeforeLast('/', "")
                            viewModel.loadFiles(parent)
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (searchTerm.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchTerm("") }) {
                            Icon(Icons.Default.SearchOff, contentDescription = "清除搜索")
                        }
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("搜索文件") },
                                onClick = {
                                    showMenu = false
                                    showSearchDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.Search, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("立即同步") },
                                onClick = {
                                    showMenu = false
                                    viewModel.syncNow()
                                },
                                leadingIcon = { Icon(Icons.Default.Sync, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                onClick = {
                                    showMenu = false
                                    viewModel.loadFiles()
                                },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("新建文件夹") },
                                onClick = {
                                    showMenu = false
                                    showCreateFolderDialog = true
                                },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("查看日志") },
                                onClick = {
                                    showMenu = false
                                    onViewLogs()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ListAlt, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("下载列表") },
                                onClick = {
                                    showMenu = false
                                    onViewDownloads()
                                },
                                leadingIcon = { Icon(Icons.Default.Download, null) }
                            )
                            DropdownMenuItem(
                                text = { Text("设置") },
                                onClick = {
                                    showMenu = false
                                    onSettings()
                                },
                                leadingIcon = { Icon(Icons.Default.Settings, null) }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("退出登录") },
                                onClick = {
                                    showMenu = false
                                    onLogout()
                                },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { launcher.launch("*/*") }) {
                Icon(Icons.Default.Add, contentDescription = "上传文件")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (error != null) {
                Text(text = "$error", modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn {
                    items(filteredFiles) { file ->
                        FileRow(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    viewModel.loadFiles(file.path)
                                } else {
                                    selectedFileForView = file
                                    viewModel.viewFile(file)
                                }
                            },
                            onDelete = { viewModel.deleteFile(file) },
                            onRename = {
                                showRenameDialog = file
                                newName = file.name
                            },
                            onDownload = {
                                viewModel.downloadToSystem(file)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FileRow(
    file: FileItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onDownload: () -> Unit
) {
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = { Text(if (file.isDirectory) "文件夹" else "${file.size} 字节") },
        leadingContent = {
            Icon(
                imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.FilePresent,
                contentDescription = null
            )
        },
        trailingContent = {
            Row {
                if (!file.isDirectory) {
                    IconButton(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = "下载")
                    }
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "重命名")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除")
                }
            }
        },
        modifier = Modifier.clickable { onClick() }
    )
}
