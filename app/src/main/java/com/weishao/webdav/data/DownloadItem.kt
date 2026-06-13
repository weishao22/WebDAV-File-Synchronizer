package com.weishao.webdav.data

enum class DownloadStatus {
    Pending,
    Downloading,
    Paused,
    Completed,
    Failed,
    Cancelled
}

data class DownloadItem(
    val id: Long,
    val fileName: String,
    val remotePath: String,
    val status: DownloadStatus,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val speed: String = "",
    val errorMessage: String? = null
) {
    val progress: Float
        get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes) else 0f

    val progressPercent: Int
        get() = (progress * 100).toInt()
}
