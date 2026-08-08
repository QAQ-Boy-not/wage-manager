// WageManagerApplication.kt - App 入口 Application
//
// 手写 DI 容器：避免引 Hilt 带来的额外学习成本。
// 所有单例在这里 lazy 创建，Activity / ViewModel 通过 application as WageManagerApplication 拿。
//
// 设计要点：
// 1. Application 是 Context 最先可用的地方，Room.databaseBuilder 必须用 applicationContext
//    避免内存泄漏（Activity Context 持有数据库会让 Activity 无法被 GC）
// 2. repository 依赖 database，单 database 已经够用

package com.example.wagemanager

import android.app.Application
import com.example.wagemanager.data.AppDatabase
import com.example.wagemanager.data.WageRepository

class WageManagerApplication : Application() {

    val database: AppDatabase by lazy {
        AppDatabase.getInstance(this)
    }

    val wageRepository: WageRepository by lazy {
        WageRepository(database)
    }
}
