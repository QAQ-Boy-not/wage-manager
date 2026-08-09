// MainActivity.kt - App 入口（M2 完整版）
//
// 职责：
// 1. 从 Application 拿 repository（手写 DI）
// 2. 创建 HomeViewModel
// 3. 设置 Compose 主题 + 收集状态
// 4. 监听一次性事件，转成 Toast（M2 加了更多事件类型）
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

                // 一次性事件：登记/编辑/标记/撤销/删除 全部 Toast
                LaunchedEffect(Unit) {
                    viewModel.eventFlow.collect { event ->
                        val msg = when (event) {
                            is HomeEvent.RegisterSuccess ->
                                getString(R.string.toast_register_success, event.workerName, MoneyUtils.formatCent(event.wageCent))
                            is HomeEvent.EditSuccess ->
                                getString(R.string.toast_edit_success, event.workerName, MoneyUtils.formatCent(event.wageCent))
                            is HomeEvent.MarkPaidSuccess ->
                                getString(R.string.toast_mark_paid_success)
                            is HomeEvent.RevokeSuccess ->
                                getString(R.string.toast_revoke_success)
                            is HomeEvent.DeleteSuccess ->
                                getString(R.string.toast_delete_success)
                            is HomeEvent.OperationFailed ->
                                getString(R.string.toast_operation_failed, event.message)
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
                        // 登记 / 编辑
                        onManualRegisterClick = viewModel::onManualRegisterClick,
                        onRegisterSubmit = viewModel::onRegisterSubmit,
                        onRegisterDismiss = viewModel::onRegisterDismiss,
                        onWorkerNameChange = viewModel::onWorkerNameChange,
                        onWorkerNameFocusLost = viewModel::onWorkerNameFocusLost,
                        onWageInputChange = viewModel::onWageInputChange,
                        onUseExistingWorker = viewModel::onUseExistingWorker,
                        onRenameAndCreate = viewModel::onRenameAndCreate,
                        onDuplicateDialogDismiss = viewModel::onDuplicateDialogDismiss,
                        // Tab / 卡片操作
                        onTabChange = viewModel::onTabChange,
                        onMarkPaidClick = viewModel::onMarkPaidClick,
                        onActionMenuShow = viewModel::onActionMenuShow,
                        onActionSelected = viewModel::onActionSelected,
                        onActionMenuDismiss = viewModel::onActionMenuDismiss,
                        onEditClick = viewModel::onEditClick,
                        // 二次确认
                        onPendingConfirmAccept = viewModel::onPendingConfirmAccept,
                        onPendingConfirmDismiss = viewModel::onPendingConfirmDismiss
                    )
                )
            }
        }
    }
}