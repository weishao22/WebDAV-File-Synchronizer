package com.weishao.webdav.data.db

import androidx.room.*

@Dao
interface SyncedFileDao {
    @Query("SELECT * FROM synced_files WHERE remotePath = :remotePath")
    suspend fun getSyncedFile(remotePath: String): SyncedFile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(file: SyncedFile)

    @Query("DELETE FROM synced_files WHERE remotePath LIKE :prefix || '%'")
    suspend fun deleteByPrefix(prefix: String)
}
