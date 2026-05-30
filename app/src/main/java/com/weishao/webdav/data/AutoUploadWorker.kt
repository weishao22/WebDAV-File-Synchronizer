package com.weishao.webdav.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.weishao.webdav.data.db.AppDatabase
import com.weishao.webdav.data.db.SyncedFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class AutoUploadWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = coroutineScope {
        LogManager.log("自动上传工作开始")
        val dataStore = WebDavDataStore(applicationContext)
        val config = dataStore.configFlow.first()
        val db = AppDatabase.getDatabase(applicationContext)

        if (!config.autoUploadEnabled || config.syncConfigs.isEmpty() || config.url.isEmpty()) {
            LogManager.log("自动上传未开启或配置不全")
            return@coroutineScope Result.success()
        }

        val repository = WebDavRepository(config)
        try {
            val semaphore = Semaphore(config.uploadConcurrency)
            val uploadCount = AtomicInteger(0)

            config.syncConfigs.forEach { sync ->
                val localDir = File(sync.localPath)
                if (localDir.exists() && localDir.isDirectory) {
                    uploadDirectoryRecursive(localDir, sync.remotePath, repository, semaphore, uploadCount, db)
                } else {
                    LogManager.log("本地目录不存在: ${sync.localPath}")
                }
            }
            LogManager.log("自动上传完成，上传了 ${uploadCount.get()} 个文件")
            return@coroutineScope Result.success()
        } catch (e: Exception) {
            LogManager.log("自动上传出错: ${e.message}")
            return@coroutineScope Result.retry()
        }
    }

    private suspend fun uploadDirectoryRecursive(
        localDir: File,
        remoteRelativePath: String,
        repo: WebDavRepository,
        semaphore: Semaphore,
        uploadCount: AtomicInteger,
        db: AppDatabase
    ): Unit = coroutineScope {
        val files = localDir.listFiles() ?: return@coroutineScope
        
        val remoteFiles = try {
            repo.listFiles(remoteRelativePath).associateBy { it.name }
        } catch (e: Exception) {
            emptyMap()
        }

        if (remoteRelativePath.isNotEmpty() && remoteFiles.isEmpty()) {
            try {
                repo.createDirectory(remoteRelativePath)
            } catch (e: Exception) {
                LogManager.log("创建目录失败: $remoteRelativePath")
            }
        }

        files.forEach { file ->
            val remotePath = if (remoteRelativePath.isEmpty()) file.name else "${remoteRelativePath.trimEnd('/')}/${file.name}"
            if (file.isDirectory) {
                uploadDirectoryRecursive(file, remotePath, repo, semaphore, uploadCount, db)
            } else {
                launch {
                    semaphore.withPermit {
                        try {
                            val syncedFile = db.syncedFileDao().getSyncedFile(remotePath)
                            val needsUpload = if (syncedFile != null) {
                                syncedFile.size != file.length() || syncedFile.lastModified != file.lastModified()
                            } else {
                                !remoteFiles.containsKey(file.name)
                            }

                            if (needsUpload) {
                                repo.uploadFile(file, remotePath)
                                db.syncedFileDao().insert(SyncedFile(remotePath, file.absolutePath, file.length(), file.lastModified()))
                                uploadCount.incrementAndGet()
                            }
                        } catch (e: Exception) {
                            LogManager.log("上传失败: ${file.name} 原因: ${e.message}")
                        }
                    }
                }
            }
        }
    }
}
