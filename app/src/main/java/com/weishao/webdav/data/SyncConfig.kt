package com.weishao.webdav.data

import kotlinx.serialization.Serializable

@Serializable
data class SyncConfig(
    val localPath: String,
    val remotePath: String
)
