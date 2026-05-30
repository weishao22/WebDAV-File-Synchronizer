package com.weishao.webdav.data

import at.bitfire.dav4jvm.DavResource
import at.bitfire.dav4jvm.property.DisplayName
import at.bitfire.dav4jvm.property.GetContentLength
import at.bitfire.dav4jvm.property.GetLastModified
import at.bitfire.dav4jvm.property.ResourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class WebDavRepository(private val config: WebDavConfig) {
    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .authenticator { _, response ->
            if (response.request.header("Authorization") != null) {
                null
            } else {
                val credential = Credentials.basic(config.username, config.password)
                response.request.newBuilder()
                    .header("Authorization", credential)
                    .build()
            }
        }
        .build()

    fun buildUrl(path: String, isDirectory: Boolean): HttpUrl {
        val baseUrl = config.url.trimEnd('/') + "/"
        val builder = baseUrl.toHttpUrl().newBuilder()
        
        path.split('/').filter { it.isNotEmpty() }.forEach {
            builder.addPathSegment(it)
        }
        
        if (isDirectory && path.isNotEmpty()) {
            builder.addPathSegment("") 
        }
        return builder.build()
    }

    private fun getRelativePath(base: HttpUrl, href: HttpUrl): String {
        val baseSegments = base.pathSegments.filter { it.isNotEmpty() }
        val hrefSegments = href.pathSegments.filter { it.isNotEmpty() }
        
        var i = 0
        while (i < baseSegments.size && i < hrefSegments.size && baseSegments[i] == hrefSegments[i]) {
            i++
        }
        return hrefSegments.drop(i).joinToString("/")
    }

    suspend fun listFiles(path: String): List<FileItem> = withContext(Dispatchers.IO) {
        val fullUrl = buildUrl(path, true)
        val davResource = DavResource(client, fullUrl)
        val files = mutableListOf<FileItem>()
        val baseUrl = config.url.trimEnd('/').toHttpUrl()

        suspendCancellableCoroutine<Unit> { continuation ->
            try {
                davResource.propfind(1, DisplayName.NAME, GetContentLength.NAME, GetLastModified.NAME, ResourceType.NAME) { response, _ ->
                    val properties = response.properties
                    val isDir = properties.filterIsInstance<ResourceType>().any { it.types.contains(ResourceType.COLLECTION) }
                    
                    val relPath = getRelativePath(baseUrl, response.href)
                    val requestPath = path.trim('/')
                    
                    if (relPath != requestPath && relPath.isNotEmpty()) {
                        val name = properties.filterIsInstance<DisplayName>().firstOrNull()?.displayName
                            ?: response.href.pathSegments.lastOrNull { it.isNotEmpty() }
                            ?: "unknown"
                            
                        files.add(
                            FileItem(
                                name = name,
                                path = relPath,
                                isDirectory = isDir,
                                size = properties.filterIsInstance<GetContentLength>().firstOrNull()?.contentLength ?: 0L,
                                lastModified = properties.filterIsInstance<GetLastModified>().firstOrNull()?.lastModified ?: 0L
                            )
                        )
                    }
                }
                continuation.resume(Unit)
            } catch (e: Exception) {
                if (e is at.bitfire.dav4jvm.exception.NotFoundException) {
                    continuation.resume(Unit) 
                } else {
                    LogManager.log("列表失败: ${e.message}")
                    continuation.resumeWithException(e)
                }
            }
        }
        files
    }

    suspend fun uploadFile(localFile: File, remotePath: String) = withContext(Dispatchers.IO) {
        val fullUrl = buildUrl(remotePath, false)
        val davResource = DavResource(client, fullUrl)
        
        LogManager.log("开始上传: ${localFile.name} -> $remotePath")
        suspendCancellableCoroutine<Unit> { continuation ->
            davResource.put(localFile.asRequestBody()) { response ->
                if (response.isSuccessful) {
                    LogManager.log("上传成功: ${localFile.name}")
                    continuation.resume(Unit)
                } else {
                    LogManager.log("上传失败: ${localFile.name} 代码: ${response.code}")
                    continuation.resumeWithException(Exception("Upload failed: ${response.code}"))
                }
            }
        }
    }

    suspend fun downloadFile(remotePath: String, localFile: File) = withContext(Dispatchers.IO) {
        val fullUrl = buildUrl(remotePath, false)
        val davResource = DavResource(client, fullUrl)

        suspendCancellableCoroutine<Unit> { continuation ->
            davResource.get("*/*", null) { response ->
                if (response.isSuccessful) {
                    try {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(localFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        continuation.resume(Unit)
                    } catch (e: Exception) {
                        continuation.resumeWithException(e)
                    }
                } else {
                    continuation.resumeWithException(Exception("Download failed: ${response.code}"))
                }
            }
        }
    }

    suspend fun delete(remotePath: String) = withContext(Dispatchers.IO) {
        val fullUrl = buildUrl(remotePath, false)
        val davResource = DavResource(client, fullUrl)
        
        suspendCancellableCoroutine<Unit> { continuation ->
            davResource.delete { response ->
                if (response.isSuccessful) continuation.resume(Unit)
                else continuation.resumeWithException(Exception("Delete failed: ${response.code}"))
            }
        }
    }

    suspend fun move(srcPath: String, destPath: String) = withContext(Dispatchers.IO) {
        val srcUrl = buildUrl(srcPath, false)
        val destUrl = buildUrl(destPath, false)
        val davResource = DavResource(client, srcUrl)
        
        suspendCancellableCoroutine<Unit> { continuation ->
            davResource.move(destUrl, false) { response ->
                if (response.isSuccessful) continuation.resume(Unit)
                else continuation.resumeWithException(Exception("Move failed: ${response.code}"))
            }
        }
    }

    suspend fun createDirectory(path: String) = withContext(Dispatchers.IO) {
        val pathParts = path.trim('/').split('/').filter { it.isNotEmpty() }
        var currentPath = ""
        
        for (part in pathParts) {
            currentPath = if (currentPath.isEmpty()) part else "$currentPath/$part"
            
            if (!exists(currentPath + "/")) {
                val fullUrl = buildUrl(currentPath, true)
                val davResource = DavResource(client, fullUrl)
                
                suspendCancellableCoroutine<Unit> { continuation ->
                    davResource.mkCol(null) { response ->
                        if (response.isSuccessful || response.code == 405) {
                            continuation.resume(Unit)
                        } else {
                            LogManager.log("创建目录失败: $currentPath 代码: ${response.code}")
                            continuation.resumeWithException(Exception("创建目录失败 (${response.code}): $currentPath"))
                        }
                    }
                }
            }
        }
    }

    suspend fun exists(remotePath: String): Boolean = withContext(Dispatchers.IO) {
        val isDir = remotePath.endsWith("/")
        val fullUrl = buildUrl(remotePath, isDir)
        val davResource = DavResource(client, fullUrl)
        
        try {
            var found = false
            davResource.propfind(0, ResourceType.NAME) { _, _ ->
                found = true
            }
            found
        } catch (e: Exception) {
            false
        }
    }
}
