// MainActivity.kt - App 入口（V1.2 工人模型）
//
// 职责：
// 1. 从 Application 拿 repository（手写 DI）
// 2. 设置 Compose 主题 + WageManagerApp 路由

package com.example.wagemanager

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.wagemanager.ui.WageManagerApp
import com.example.wagemanager.ui.theme.WageManagerTheme

class MainActivity : ComponentActivity() {

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