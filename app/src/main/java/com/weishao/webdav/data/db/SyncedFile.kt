package com.weishao.webdav.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "synced_files")
data class SyncedFile(
    @PrimaryKey val remotePath: String,
    val localPath: String,
    val size: Long,
    val lastModified: Long
)
