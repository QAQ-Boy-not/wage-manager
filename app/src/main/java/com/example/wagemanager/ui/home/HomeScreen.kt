// HomeScreen.kt - 首页 UI（M2 完整版）
//
// 布局（自上而下）：
//   1. 今日日期（大标题）
//   2. 手动登记按钮（蓝色大按钮）
//   3. 统计栏：合计 / 未付 / 已付
//   4. 未付 / 已付 双标签
//   5. 当前 tab 的工资记录列表（LazyColumn）
//   6. 空列表提示
//   7. RegisterBottomSheet（按状态显示）
//   8. RecordActionMenuDialog（长按已付项弹）
//   9. ConfirmActionDialog（标记 / 撤销 / 删除前确认）

package com.example.wagemanager.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R
import com.example.wagemanager.ui.components.BigButton
import com.example.wagemanager.util.DateRules
import com.example.wagemanager.util.MoneyUtils

data class HomeScreenCallbacks(
    val onManualRegisterClick: () -> Unit,
    val onTabChange: (Boolean) -> Unit,
    val onMarkPaidClick: (Long) -> Unit,
    val onActionMenuShow: (Long) -> Unit,
    val onActionSelected: (Long, ActionOption) -> Unit,
    val onActionMenuDismiss: () -> Unit,
    val onEditClick: (Long) -> Unit,
    val onPendingConfirmAccept: () -> Unit,
    val onPendingConfirmDismiss: () -> Unit,
    val onWorkerNameChange: (String) -> Unit,
    val onWorkerNameFocusLost: () -> Unit,
    val onWageInputChange: (String) -> Unit,
    val onRegisterSubmit: () -> Unit,
    val onRegisterDismiss: () -> Unit,
    val onUseExistingWorker: (String) -> Unit,
    val onRenameAndCreate: () -> Unit,
    val onDuplicateDialogDismiss: () -> Unit
)

@Composable
fun HomeScreen(
    state: HomeUiState,
    callbacks: HomeScreenCallbacks
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            // ===== 顶部：今日日期 =====
            Text(
                text = DateRules.formatChineseDate(state.workDate),
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ===== 手动登记按钮 =====
            BigButton(
                text = stringResource(R.string.action_manual_register),
                backgroundColor = MaterialTheme.colorScheme.primary,
                onClick = callbacks.onManualRegisterClick
            )
            Spacer(modifier = Modifier.height(16.dp))

            // ===== 统计栏 =====
            HomeStats(
                totalCent = state.totalCent,
                unpaidCount = state.unpaidCount,
                paidCount = state.paidCount,
                recordCount = state.recordCount
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ===== 双标签 =====
            HomeTabs(
                isPaidTab = state.isPaidTab,
                unpaidCount = state.unpaidCount,
                paidCount = state.paidCount,
                onTabChange = callbacks.onTabChange
            )
            Spacer(modifier = Modifier.height(12.dp))

            // ===== 当前 tab 的记录列表 =====
            val visibleRecords = if (state.isPaidTab) {
                state.records.filter { it.isPaid }
            } else {
                state.records.filter { !it.isPaid }
            }

            if (visibleRecords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = stringResource(
                            if (state.isPaidTab) R.string.empty_paid_hint
                            else R.string.empty_unpaid_hint
                        ),
                        fontSize = 20.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleRecords, key = { it.recordId }) { item ->
                        WageCard(
                            item = item,
                            onMarkPaidClick = { callbacks.onMarkPaidClick(item.recordId) },
                            onEditClick = { callbacks.onEditClick(item.recordId) },
                            onLongClick = { callbacks.onActionMenuShow(item.recordId) }
                        )
                    }
                }
            }
        }
    }

    // ===== 登记 / 编辑 BottomSheet =====
    RegisterBottomSheet(
        state = state.registerForm,
        onWorkerNameChange = callbacks.onWorkerNameChange,
        onWorkerNameFocusLost = callbacks.onWorkerNameFocusLost,
        onWageInputChange = callbacks.onWageInputChange,
        onSubmit = callbacks.onRegisterSubmit,
        onDismiss = callbacks.onRegisterDismiss,
        onUseExistingWorker = callbacks.onUseExistingWorker,
        onRenameAndCreate = callbacks.onRenameAndCreate,
        onDuplicateDialogDismiss = callbacks.onDuplicateDialogDismiss
    )

    // ===== 长按操作菜单 =====
    val menuRecordId = state.actionMenuRecordId
    if (menuRecordId != null) {
        val menuItem = state.records.firstOrNull { it.recordId == menuRecordId }
        if (menuItem != null) {
            RecordActionMenuDialog(
                recordId = menuRecordId,
                workerName = menuItem.workerName,
                onSelected = callbacks.onActionSelected,
                onDismiss = callbacks.onActionMenuDismiss
            )
        }
    }

    // ===== 二次确认对话框 =====
    val pending = state.pendingConfirmAction
    if (pending != null) {
        val (title, message, confirmLabel, destructive) = when (pending) {
            is PendingConfirmAction.MarkPaid -> {
                val name = state.records.firstOrNull { it.recordId == pending.recordId }?.workerName ?: ""
                ConfirmDialogTexts(
                    title = stringResource(R.string.dialog_mark_paid_title),
                    message = stringResource(R.string.dialog_mark_paid_message, name, formatAmount(state, pending.recordId)),
                    confirmLabel = stringResource(R.string.action_mark_paid),
                    destructive = false
                )
            }
            is PendingConfirmAction.RevokePayment -> {
                val name = state.records.firstOrNull { it.recordId == pending.recordId }?.workerName ?: ""
                ConfirmDialogTexts(
                    title = stringResource(R.string.dialog_revoke_title),
                    message = stringResource(R.string.dialog_revoke_message, name),
                    confirmLabel = stringResource(R.string.action_revoke_payment),
                    destructive = false
                )
            }
            is PendingConfirmAction.DeleteRecord -> {
                val name = state.records.firstOrNull { it.recordId == pending.recordId }?.workerName ?: ""
                ConfirmDialogTexts(
                    title = stringResource(R.string.dialog_delete_title),
                    message = stringResource(R.string.dialog_delete_message, name, formatAmount(state, pending.recordId)),
                    confirmLabel = stringResource(R.string.action_delete_record),
                    destructive = true
                )
            }
        }
        ConfirmActionDialog(
            title = title,
            message = message,
            confirmLabel = confirmLabel,
            isDestructive = destructive,
            onConfirm = callbacks.onPendingConfirmAccept,
            onDismiss = callbacks.onPendingConfirmDismiss
        )
    }
}

private data class ConfirmDialogTexts(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val destructive: Boolean
)

private fun formatAmount(state: HomeUiState, recordId: Long): String {
    return MoneyUtils.formatCent(
        state.records.firstOrNull { it.recordId == recordId }?.wageCent ?: 0L
    )
}

/**
 * 统计栏：今日合计 / 未付条数 / 已付条数
 */
@Composable
private fun HomeStats(
    totalCent: Long,
    unpaidCount: Int,
    paidCount: Int,
    recordCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colorResource(R.color.wage_card_background),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_total, MoneyUtils.formatCent(totalCent)),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.wage_text_primary)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.stats_unpaid, unpaidCount),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.wage_unpaid_red)
            )
            Text(
                text = stringResource(R.string.stats_paid, paidCount),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.wage_paid_green)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.stats_records, recordCount),
            fontSize = 16.sp,
            color = Color.Gray
        )
    }
}