package com.weishao.webdav.data

import kotlinx.serialization.Serializable

@Serializable
data class WebDavConfig(
    val url: String = "",
    val username: String = "",
    val password: String = "",
    val syncConfigs: List<SyncConfig> = emptyList(),
    val autoUploadEnabled: Boolean = false,
    val uploadConcurrency: Int = 3
)
