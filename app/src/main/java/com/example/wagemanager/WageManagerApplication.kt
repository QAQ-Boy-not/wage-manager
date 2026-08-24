// WageManagerApplication.kt - App 入口 Application
//
// 手写 DI 容器：避免引 Hilt 带来的额外学习成本。
// 所有单例在这里 lazy 创建，Activity / ViewModel 通过 application as WageManagerApplication 拿。
//
// 设计要点：
// 1. Application 是 Context 最先可用的地方，Room.databaseBuilder 必须用 applicationContext
//    避免内存泄漏（Activity Context 持有数据库会让 Activity 无法被 GC）
// 2. repository 依赖 database，单 database 已经够用
// 3. M3.1：强制 Locale.CHINA，避免 Compose DatePicker weekday 表头 fallback 到英文/数字

package com.example.wagemanager

import android.app.Application
import com.example.wagemanager.data.AppDatabase
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.util.BackupManager
import java.util.Locale

class WageManagerApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 强制 java.util.Locale 默认值为中文，影响 java.time.DateTimeFormatter 等 API
        Locale.setDefault(Locale.CHINA)
        // M4.5：启动时自动备份 db 到 filesDir/backup/wage_manager.db
        // 解决：升级安装 / 调试重装 / 进程被杀后的恢复
        // 不解决：卸载（私有目录随 app 删除）→ M6 CSV 导出到 Downloads 解决
        BackupManager.backupToPrivate(this)
    }

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val wageRepository: WageRepository by lazy {
        WageRepository(database)
    }
}
