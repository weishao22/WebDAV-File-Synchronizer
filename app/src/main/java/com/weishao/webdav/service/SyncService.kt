package com.weishao.webdav.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.weishao.webdav.MainActivity
import com.weishao.webdav.data.*
import com.weishao.webdav.data.db.AppDatabase
import com.weishao.webdav.data.db.SyncedFile
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class SyncService : Service() {
    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sync_service_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WebDavSync::WakeLock")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification("正在同步文件...")
        startForeground(NOTIFICATION_ID, notification)

        wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max

        serviceScope.launch {
            try {
                performSync()
            } finally {
                stopForeground(true)
                stopSelf()
                wakeLock?.let {
                    if (it.isHeld) it.release()
                }
            }
        }

        return START_NOT_STICKY
    }

    private suspend fun performSync() {
        LogManager.log("开始手动同步")
        val dataStore = WebDavDataStore(applicationContext)
        val config = dataStore.configFlow.first()
        val db = AppDatabase.getDatabase(applicationContext)

        if (config.syncConfigs.isEmpty() || config.url.isEmpty()) {
            LogManager.log("同步配置不全")
            return
        }

        val repository = WebDavRepository(config)
        val semaphore = Semaphore(config.uploadConcurrency)
        val uploadCount = AtomicInteger(0)

        coroutineScope {
            config.syncConfigs.forEach { sync ->
                val localDir = File(sync.localPath)
                if (localDir.exists() && localDir.isDirectory) {
                    uploadDirectoryRecursive(localDir, sync.remotePath, repository, semaphore, uploadCount, db)
                } else {
                    LogManager.log("本地目录不存在: ${sync.localPath}")
                }
            }
        }
        LogManager.log("同步完成，上传了 ${uploadCount.get()} 个文件")
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "文件同步服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(content: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WebDAV同步")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
