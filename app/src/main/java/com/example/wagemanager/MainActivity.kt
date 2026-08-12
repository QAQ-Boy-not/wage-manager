// MainActivity.kt - App 入口（V1.2 工人模型）
//
// 职责：
// 1. 从 Application 拿 repository（手写 DI）
// 2. 设置 Compose 主题 + WageManagerApp 路由
// 3. M3.1：attachBaseContext 强制中文 Configuration，影响 Compose 内部 locale-aware widget

package com.example.wagemanager

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.wagemanager.ui.WageManagerApp
import com.example.wagemanager.ui.theme.WageManagerTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        // 强制 Configuration 的 locale 为中文，让 Compose 内部 locale-aware widget
        // （如 DatePicker weekday 表头）正确渲染中文（一二三四五六日）
        val config = Configuration(newBase.resources.configuration)
        config.setLocale(Locale.CHINA)
        val updatedContext = newBase.createConfigurationContext(config)
        super.attachBaseContext(updatedContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as WageManagerApplication).wageRepository

        setContent {
            WageManagerTheme {
                WageManagerApp(repository)
            }
        }
    }
}