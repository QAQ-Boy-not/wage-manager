// MainActivity.kt - App 入口
//
// 职责：
// 1. 从 Application 拿 repository（手写 DI）
// 2. 创建 HomeViewModel
// 3. 设置 Compose 主题 + 收集状态
// 4. 监听一次性事件，转成 Toast
// 5. 触发 ViewModel.onScreenResume 处理跨午夜

package com.example.wagemanager

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wagemanager.ui.home.HomeScreen
import com.example.wagemanager.ui.home.HomeScreenCallbacks
import com.example.wagemanager.ui.home.HomeViewModel
import com.example.wagemanager.ui.home.HomeEvent
import com.example.wagemanager.ui.theme.WageManagerTheme
import com.example.wagemanager.util.MoneyUtils

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val repository = (application as WageManagerApplication).wageRepository

        setContent {
            WageManagerTheme {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.factory(repository)
                )
                val state by viewModel.uiState.collectAsStateWithLifecycle()

                // 进入屏幕时刷新日期（处理跨午夜）
                LaunchedEffect(Unit) {
                    viewModel.onScreenResume()
                }

                // 一次性事件：登记成功 / 失败 Toast
                LaunchedEffect(Unit) {
                    viewModel.eventFlow.collect { event ->
                        val msg = when (event) {
                            is HomeEvent.RegisterSuccess ->
                                getString(R.string.toast_register_success, event.workerName, MoneyUtils.formatCent(event.wageCent))
                            is HomeEvent.RegisterFailed ->
                                getString(R.string.toast_register_failed, event.message)
                            is HomeEvent.WageInputError ->
                                getString(
                                    when (event.error) {
                                        MoneyUtils.WageError.REQUIRED -> R.string.error_wage_required
                                        MoneyUtils.WageError.INVALID_FORMAT -> R.string.error_wage_invalid_format
                                        MoneyUtils.WageError.MUST_BE_POSITIVE -> R.string.error_wage_must_be_positive
                                        MoneyUtils.WageError.OUT_OF_RANGE -> R.string.error_wage_out_of_range
                                        else -> R.string.error_wage_invalid_format
                                    }
                                )
                            HomeEvent.NameRequired ->
                                getString(R.string.error_name_required)
                        }
                        Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                HomeScreen(
                    state = state,
                    callbacks = HomeScreenCallbacks(
                        onManualRegisterClick = viewModel::onManualRegisterClick,
                        onWorkerNameChange = viewModel::onWorkerNameChange,
                        onWorkerNameFocusLost = viewModel::onWorkerNameFocusLost,
                        onWageInputChange = viewModel::onWageInputChange,
                        onRegisterSubmit = viewModel::onRegisterSubmit,
                        onRegisterDismiss = viewModel::onRegisterDismiss,
                        onReuseWorker = viewModel::onReuseWorker,
                        onCreateNewWorker = viewModel::onCreateDuplicateWorkerClick,
                        onConfirmCreateNewWorker = viewModel::onCreateDuplicateWorkerConfirm,
                        onDuplicateDialogDismiss = viewModel::onDuplicateDialogDismiss,
                        onConfirmNewWorkerDialogDismiss = viewModel::onConfirmNewWorkerDialogDismiss
                    )
                )
            }
        }
    }
}
