// WorkerDetailScreen.kt - 工人详情页 UI（V1.2 + M1.1 状态流转）
//
// 布局（自上而下）：
//   1. 顶部导航：← 返回 + 工人姓名
//   2. 工人信息卡：姓名 + 首次登记日期 + 汇总（分行显示未付/已付）
//   3. 未付账单组（红色标题）
//      - 每张账单卡片：日期 + 金额 + [✅ 标记已付] 按钮
//      - 长按弹操作菜单 [✏️ 编辑] [🗑️ 删除]
//   4. 已付账单组（绿色标题）
//      - 每张账单卡片：日期 + 金额 + 支付时间
//      - 长按弹操作菜单 [↩️ 撤销付款] [🗑️ 删除]
//   5. 右下角 FAB [+] 添加账单

package com.example.wagemanager.ui.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wagemanager.R
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.util.DateRules
import com.example.wagemanager.util.MoneyUtils
import com.example.wagemanager.util.PaymentRules
import java.time.LocalDate

@Composable
fun WorkerDetailScreen(
    repository: WageRepository,
    workerId: String,
    onBack: () -> Unit
) {
    val viewModel: WorkerDetailViewModel = viewModel(
        key = "WorkerDetailVM-$workerId",  // 不同 workerId 创建不同 VM 实例（M2.1 Bug10 修复）
        factory = WorkerDetailViewModel.factory(repository, workerId)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 一次性事件：操作失败 Toast
    LaunchedEffect(workerId) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is WorkerDetailEvent.OperationFailed ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ===== 顶部导航 =====
                DetailTopBar(
                    workerName = state.workerName,
                    onBack = onBack
                )

                if (state.isWorkerNotFound) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.worker_not_found),
                            fontSize = 20.sp,
                            color = Color.Gray
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        // ===== 工人信息卡 =====
                        WorkerInfoCard(
                            name = state.workerName,
                            firstWorkDate = state.firstWorkDate,
                            totalCount = state.totalCount,
                            totalCent = state.totalCent,
                            unpaidCount = state.unpaidBills.size,
                            unpaidCent = state.unpaidCent,
                            paidCount = state.paidBills.size,
                            paidCent = state.paidCent
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        // ===== 账单列表 =====
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (state.unpaidBills.isNotEmpty()) {
                                item {
                                    BillGroupHeader(
                                        title = stringResource(
                                            R.string.detail_group_unpaid,
                                            state.unpaidBills.size,
                                            MoneyUtils.formatCent(state.unpaidCent)
                                        ),
                                        color = colorResource(R.color.wage_unpaid_red)
                                    )
                                }
                                items(state.unpaidBills, key = { it.recordId }) { bill ->
                                    BillCard(
                                        bill = bill,
                                        workerName = state.workerName,
                                        onMarkPaidClick = {
                                            viewModel.setPendingAction(
                                                PendingConfirmAction.MarkPaid(
                                                    recordId = bill.recordId,
                                                    workerName = state.workerName,
                                                    wageCent = bill.wageCent
                                                )
                                            )
                                        },
                                        onRevokeClick = null,
                                        onMenuClick = {
                                            viewModel.onActionMenuShow(bill.recordId)
                                        }
                                    )
                                }
                            }

                            if (state.paidBills.isNotEmpty()) {
                                item {
                                    BillGroupHeader(
                                        title = stringResource(
                                            R.string.detail_group_paid,
                                            state.paidBills.size,
                                            MoneyUtils.formatCent(state.paidCent)
                                        ),
                                        color = colorResource(R.color.wage_paid_green)
                                    )
                                }
                                items(state.paidBills, key = { it.recordId }) { bill ->
                                    BillCard(
                                        bill = bill,
                                        workerName = state.workerName,
                                        onMarkPaidClick = null,
                                        onRevokeClick = {
                                            viewModel.setPendingAction(
                                                PendingConfirmAction.RevokePayment(
                                                    recordId = bill.recordId,
                                                    workerName = state.workerName
                                                )
                                            )
                                        },
                                        onMenuClick = {
                                            viewModel.onActionMenuShow(bill.recordId)
                                        }
                                    )
                                }
                            }

                            if (state.unpaidBills.isEmpty() && state.paidBills.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.detail_empty_bills),
                                            fontSize = 18.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ===== FAB =====
        FloatingActionButton(
            onClick = viewModel::onAddBillClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(72.dp),
            containerColor = colorResource(R.color.wage_action_blue),
            contentColor = Color.White,
            shape = CircleShape
        ) {
            Text(
                text = "➕",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }

    // ===== 添加 / 编辑账单 BottomSheet（Bug 9 + Bug14 修复）=====
    if (state.control.isRegisterSheetVisible) {
        val editingBill = state.control.editingBillId?.let { id ->
            state.findBillById(id)
        }
        // Bug 14：传 workerId 让 RegisterBillSheet 走 addBillToWorker 路径
        // 跳过同名检查（工人已经确定，不该触发"重复名称"）
        RegisterBillSheet(
            repository = repository,
            workerId = workerId,  // ← 详情页上下文传入
            initialName = state.workerName.takeIf { it.isNotEmpty() },
            editingBill = editingBill,
            editingBillId = state.control.editingBillId,
            onDismiss = viewModel::onAddBillDismiss
        )
    }

    // ===== 操作菜单（长按账单） =====
    val menuRecordId = state.control.actionMenuRecordId
    if (menuRecordId != null) {
        val menuBill = state.findBillById(menuRecordId)
        if (menuBill != null) {
            BillActionMenuDialog(
                bill = menuBill,
                onEdit = { viewModel.onActionMenuEdit(menuBill.recordId) },
                onMarkPaid = {
                    viewModel.setPendingAction(
                        PendingConfirmAction.MarkPaid(
                            menuBill.recordId, state.workerName, menuBill.wageCent
                        )
                    )
                },
                onRevoke = {
                    viewModel.setPendingAction(
                        PendingConfirmAction.RevokePayment(
                            menuBill.recordId, state.workerName
                        )
                    )
                },
                onDelete = {
                    viewModel.setPendingAction(
                        PendingConfirmAction.DeleteRecord(
                            menuBill.recordId, state.workerName, menuBill.wageCent
                        )
                    )
                },
                onDismiss = viewModel::onActionMenuDismiss
            )
        }
    }

    // ===== 二次确认对话框 =====
    val pending = state.control.pendingConfirmAction
    if (pending != null) {
        ConfirmActionDialog(
            action = pending,
            onConfirm = viewModel::onPendingConfirmAccept,
            onDismiss = viewModel::onPendingConfirmDismiss
        )
    }
}

// ===================== 子组件 =====================

@Composable
private fun DetailTopBar(workerName: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onBack) {
            Text(
                text = "← 返回",
                fontSize = 18.sp,
                color = colorResource(R.color.wage_action_blue)
            )
        }
        Spacer(modifier = Modifier.size(8.dp))
        Text(
            text = if (workerName.isEmpty()) "👷" else "👤 $workerName",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.wage_text_primary)
        )
    }
}

@Composable
private fun WorkerInfoCard(
    name: String,
    firstWorkDate: LocalDate?,
    totalCount: Int,
    totalCent: Long,
    unpaidCount: Int,
    unpaidCent: Long,
    paidCount: Int,
    paidCent: Long
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.wage_card_background)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = if (name.isEmpty()) "👷" else "👤 $name",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.wage_text_primary)
            )
            if (firstWorkDate != null) {
                Text(
                    text = stringResource(
                        R.string.detail_first_work_date,
                        DateRules.formatChineseDate(firstWorkDate)
                    ),
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
            Text(
                text = stringResource(R.string.stats_total_label, totalCount) + " / " + MoneyUtils.formatCent(totalCent) + " 元",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.wage_text_primary)
            )
            Text(
                text = "🔴 " + stringResource(R.string.stats_unpaid_count, unpaidCount) + " / " + MoneyUtils.formatCent(unpaidCent) + " 元",
                fontSize = 15.sp,
                color = colorResource(R.color.wage_unpaid_red)
            )
            Text(
                text = "🟢 " + stringResource(R.string.stats_paid_count, paidCount) + " / " + MoneyUtils.formatCent(paidCent) + " 元",
                fontSize = 15.sp,
                color = colorResource(R.color.wage_paid_green)
            )
        }
    }
}

@Composable
private fun BillGroupHeader(title: String, color: Color) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    )
}

/**
 * 账单卡片（M2.1 修复长按 + Bug12 紧凑布局）
 * - 未付账单：右上角 [✅ 标记已付] 按钮
 * - 已付账单：右上角 [↩️ 撤销] 按钮
 * - 右上角 "..." 图标按钮 → 弹操作菜单 [✏️ 编辑] [🗑️ 删除]（替代长按）
 */
@Composable
private fun BillCard(
    bill: BillItem,
    workerName: String,
    onMarkPaidClick: (() -> Unit)?,
    onRevokeClick: (() -> Unit)?,
    onMenuClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.wage_card_background)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateRules.formatChineseDate(bill.workDate),
                    fontSize = 14.sp,
                    color = colorResource(R.color.wage_text_primary)
                )
                if (!bill.worksiteName.isNullOrBlank()) {
                    Text(
                        text = "📍 ${bill.worksiteName}",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
                if (!bill.notes.isNullOrBlank()) {
                    Text(
                        text = bill.notes,
                        fontSize = 13.sp,
                        color = colorResource(R.color.wage_text_primary)
                    )
                }
                Text(
                    text = MoneyUtils.formatCent(bill.wageCent) + " 元",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(
                        if (bill.isPaid) R.color.wage_paid_green
                        else R.color.wage_unpaid_red
                    )
                )
                if (bill.isPaid && bill.paidTime != null) {
                    Text(
                        text = stringResource(
                            R.string.detail_paid_at,
                            PaymentRules.formatPaidTime(bill.paidTime)
                        ),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            // 右侧：操作按钮 + "..." 菜单按钮（垂直布局）
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 标记已付 / 撤销 按钮
                when {
                    !bill.isPaid && onMarkPaidClick != null -> {
                        ActionButton(
                            text = stringResource(R.string.action_mark_paid),
                            bgColor = colorResource(R.color.wage_paid_green),
                            onClick = onMarkPaidClick
                        )
                    }
                    bill.isPaid && onRevokeClick != null -> {
                        ActionButton(
                            text = stringResource(R.string.action_revoke_short),
                            bgColor = colorResource(R.color.wage_unpaid_red),
                            onClick = onRevokeClick
                        )
                    }
                }

                // "..." 菜单按钮（替代长按，老年用户可见）
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onMenuClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⋮",
                        fontSize = 24.sp,
                        color = colorResource(R.color.wage_disabled_gray)
                    )
                }
            }
        }
    }
}

/**
 * 操作按钮（标记已付 / 撤销）—— 通用样式
 */
@Composable
private fun ActionButton(text: String, bgColor: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(color = bgColor, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * 操作菜单（长按账单弹出）
 *
 * 未付账单：[✏️ 编辑] [🗑️ 删除记录]
 * 已付账单：[↩️ 撤销付款] [🗑️ 删除记录]
 */
@Composable
private fun BillActionMenuDialog(
    bill: BillItem,
    onEdit: () -> Unit,
    onMarkPaid: () -> Unit,
    onRevoke: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.dialog_action_menu_title, bill.workDate.toString()),
                fontSize = 22.sp
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_action_menu_message),
                fontSize = 18.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    when {
                        !bill.isPaid -> onMarkPaid()
                        else -> onRevoke()
                    }
                }
            ) {
                Text(
                    text = stringResource(
                        if (!bill.isPaid) R.string.action_mark_paid
                        else R.string.action_revoke_payment
                    ),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.wage_paid_green)
                )
            }
        },
        dismissButton = {
            Column {
                if (!bill.isPaid) {
                    TextButton(onClick = onEdit) {
                        Text(
                            text = stringResource(R.string.action_edit_bill),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = colorResource(R.color.wage_action_blue)
                        )
                    }
                }
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.action_delete_record),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

/**
 * 二次确认对话框（标记已付 / 撤销 / 删除）
 */
@Composable
private fun ConfirmActionDialog(
    action: PendingConfirmAction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val (title, message, confirmLabel, destructive) = when (action) {
        is PendingConfirmAction.MarkPaid -> {
            ConfirmDialogTexts(
                title = stringResource(R.string.dialog_mark_paid_title),
                message = stringResource(
                    R.string.dialog_mark_paid_message,
                    action.workerName,
                    MoneyUtils.formatCent(action.wageCent)
                ),
                confirmLabel = stringResource(R.string.action_mark_paid),
                destructive = false
            )
        }
        is PendingConfirmAction.RevokePayment -> {
            ConfirmDialogTexts(
                title = stringResource(R.string.dialog_revoke_title),
                message = stringResource(R.string.dialog_revoke_message, action.workerName),
                confirmLabel = stringResource(R.string.action_revoke_payment),
                destructive = false
            )
        }
        is PendingConfirmAction.DeleteRecord -> {
            ConfirmDialogTexts(
                title = stringResource(R.string.dialog_delete_title),
                message = stringResource(
                    R.string.dialog_delete_message,
                    action.workerName,
                    MoneyUtils.formatCent(action.wageCent)
                ),
                confirmLabel = stringResource(R.string.action_delete_record),
                destructive = true
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = title, fontSize = 22.sp) },
        text = { Text(text = message, fontSize = 18.sp) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = confirmLabel,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (destructive) MaterialTheme.colorScheme.error
                    else colorResource(R.color.wage_action_blue)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), fontSize = 20.sp)
            }
        }
    )
}

private data class ConfirmDialogTexts(
    val title: String,
    val message: String,
    val confirmLabel: String,
    val destructive: Boolean
)