// AppNavigation.kt - 简单路由（V1.2 工人模型）
//
// 设计要点：
// 1. 不用 androidx.navigation 库：项目小，3 个 Screen（列表/详情/添加）手写管理足矣
// 2. 用 sealed interface Screen 表达当前路由（workerId 通过 data class 携带）
// 3. mutableStateOf 持有当前 Screen；切换 = 重组到对应 Composable

package com.example.wagemanager.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.ui.worker.WorkerDetailScreen
import com.example.wagemanager.ui.worker.WorkerListScreen

/**
 * 屏幕路由。
 * - WorkerList：首页（工人列表）
 * - WorkerDetail：详情页（单工人的所有账单）
 */
sealed interface Screen {
    data object WorkerList : Screen
    data class WorkerDetail(val workerId: String) : Screen
}

@Composable
fun WageManagerApp(repository: WageRepository) {
    var currentScreen: Screen by remember { mutableStateOf<Screen>(Screen.WorkerList) }

    when (val s = currentScreen) {
        Screen.WorkerList -> WorkerListScreen(
            repository = repository,
            onWorkerClick = { workerId ->
                currentScreen = Screen.WorkerDetail(workerId)
            }
        )
        is Screen.WorkerDetail -> WorkerDetailScreen(
            repository = repository,
            workerId = s.workerId,
            onBack = { currentScreen = Screen.WorkerList }
        )
    }
}