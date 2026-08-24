// BackupManager.kt - 数据库自动备份（M4.5）
//
// 设计要点：
// 1. App 启动时自动备份 db 到 filesDir/backup/wage_manager.db
// 2. 覆盖最近一份备份（保留最新）
// 3. 解决场景：升级安装、调试重装、App 进程被系统杀死后恢复
// 4. 不解决场景：卸载（私有目录随 app 删除）→ M6 CSV 导出到 Downloads 解决
//
// 实现：纯 Kotlin + java.io，不依赖 Android SDK 特有 API

package com.example.wagemanager.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * 数据库备份管理器（M4.5）
 *
 * 备份策略：
 * - 启动时自动备份到 filesDir/backup/wage_manager.db
 * - 每次启动覆盖最新一份备份
 * - 不保留历史版本（节省空间）
 */
object BackupManager {

    private const val TAG = "BackupManager"
    private const val BACKUP_DIR = "backup"
    private const val BACKUP_FILE = "wage_manager.db"

    /**
     * 备份 db 到私有目录
     *
     * @param context Application Context
     * @return true 成功；false 失败（不影响 App 启动）
     */
    fun backupToPrivate(context: Context): Boolean {
        return try {
            val dbFile = context.getDatabasePath("wage_manager.db")
            if (!dbFile.exists()) {
                Log.w(TAG, "db file not found: ${dbFile.absolutePath}")
                return false
            }

            val backupDir = File(context.filesDir, BACKUP_DIR)
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                Log.e(TAG, "failed to create backup dir: ${backupDir.absolutePath}")
                return false
            }

            val backupFile = File(backupDir, BACKUP_FILE)

            // 复制 db 到 backup
            FileInputStream(dbFile).use { input ->
                FileOutputStream(backupFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "backup ok: ${backupFile.absolutePath} (${backupFile.length()} bytes)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "backup failed", e)
            false
        }
    }

    /**
     * 检查是否有备份文件（M7 用）
     */
    fun hasBackup(context: Context): Boolean {
        val backupFile = File(File(context.filesDir, BACKUP_DIR), BACKUP_FILE)
        return backupFile.exists() && backupFile.length() > 0
    }

    /**
     * 获取备份文件路径（M7 用）
     */
    fun getBackupFile(context: Context): File {
        return File(File(context.filesDir, BACKUP_DIR), BACKUP_FILE)
    }

    /**
     * 从备份文件恢复 db（M7 用）
     * - 会覆盖当前 db 文件
     * - 调用方需先关闭 Room（避免文件锁冲突）
     */
    fun restoreFromPrivate(context: Context): Boolean {
        return try {
            val backupFile = getBackupFile(context)
            if (!backupFile.exists()) {
                Log.w(TAG, "backup file not found")
                return false
            }

            val dbFile = context.getDatabasePath("wage_manager.db")
            FileInputStream(backupFile).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }

            Log.i(TAG, "restore ok")
            true
        } catch (e: Exception) {
            Log.e(TAG, "restore failed", e)
            false
        }
    }
}