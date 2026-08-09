// WorkerListScreen.kt - 首页 UI（V1.3 按工人聚合）
//
// 布局（自上而下）：
//   1. 顶部：今日日期 + 右上角 [⚙️ 管理] 按钮
//   2. 汇总：未付 X 笔 / ¥XX，已付 X 笔 / ¥XX
//   3. Tab：[🔴 未付（X）] [🟢 已付（Y）]
//   4. 工人聚合卡片列表（按工人）：
//      - 姓名 / 应付金额 / 笔数 / 订单缩略
//      - 点击 → 展开该工人的今日订单列表
//      - 长按 → 弹 WorkerDetailDialog（累计统计）
//   5. FAB ➕ 批量添加账单（V1.3 新行为）

package com.example.wagemanager.ui.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import com.example.wagemanager.ui.components.BigButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.wagemanager.R
import com.example.wagemanager.data.WageRepository
import com.example.wagemanager.util.DateRules
import com.example.wagemanager.util.MoneyUtils
import com.example.wagemanager.util.PaymentRules
import com.example.wagemanager.data.Worksite
import com.example.wagemanager.data.Worker

@Composable
fun WorkerListScreen(
    repository: WageRepository,
    onWorkerClick: (String) -> Unit,
    onManageClick: () -> Unit
) {
    val viewModel: WorkerListViewModel = viewModel(
        factory = WorkerListViewModel.factory(repository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var longPressGroup by remember { mutableStateOf<WorkerGroup?>(null) }

    LaunchedEffect(Unit) {
        viewModel.onScreenResume()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                // ===== 顶部：日期 + 管理按钮 =====
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateRules.formatChineseDate(state.workDate),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    TextButton(onClick = onManageClick) {
                        Text(
                            text = "⚙️ 管理",
                            fontSize = 18.sp,
                            color = colorResource(R.color.wage_action_blue)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))

                // ===== 汇总（分行显示未付/已付，M1.1 反馈 1 修复） =====
                HomeStats(
                    unpaidCount = state.totalUnpaidCount,
                    unpaidCent = state.totalUnpaidCent,
                    paidCount = state.totalPaidCount,
                    paidCent = state.totalPaidCent
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ===== Tab 切换 =====
                HomeTabs(
                    isPaidTab = state.isPaidTab,
                    unpaidCount = state.totalUnpaidCount,
                    paidCount = state.totalPaidCount,
                    onTabChange = viewModel::onTabChange
                )
                Spacer(modifier = Modifier.height(12.dp))

                // ===== Bug 3：未付 tab 显示"全部标记已付"按钮 =====
                if (!state.isPaidTab && state.totalUnpaidCount > 0) {
                    BigButton(
                        text = stringResource(
                            R.string.action_mark_all_paid,
                            state.totalUnpaidCount,
                            MoneyUtils.formatCent(state.totalUnpaidCent)
                        ),
                        backgroundColor = colorResource(R.color.wage_paid_green),
                        onClick = viewModel::onMarkAllPaidClick
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // ===== 工人聚合列表 =====
                val visibleGroups = state.visibleGroups()
                if (visibleGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 32.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = if (state.isPaidTab) "🟢" else "🔴", fontSize = 48.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (state.isPaidTab) "今日还没有已付订单" else "今日还没有未付订单",
                                fontSize = 18.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "点击右下角 ➕ 批量添加",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleGroups, key = { it.workerId }) { group ->
                            WorkerGroupCard(
                                group = group,
                                isPaidTab = state.isPaidTab,
                                onWorkerClick = onWorkerClick,
                                onMenuClick = { longPressGroup = group }
                            )
                        }
                    }
                }
            }
        }

        // ===== FAB ➕ 批量添加 =====
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
            Text(text = "➕", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        }
    }

    // ===== 批量添加 BottomSheet =====
    if (state.isBatchSheetVisible) {
        BatchAddBillSheet(
            repository = repository,
            workDate = state.workDate,
            onDismiss = viewModel::onAddBillDismiss
        )
    }

    // ===== Bug 3：全部标记已付 二次确认 =====
    if (state.isMarkAllPaidConfirmVisible) {
        AlertDialog(
            onDismissRequest = viewModel::onMarkAllPaidDismiss,
            title = {
                Text(
                    text = stringResource(R.string.dialog_mark_all_paid_title),
                    fontSize = 22.sp
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.dialog_mark_all_paid_message,
                        state.totalUnpaidCount,
                        MoneyUtils.formatCent(state.totalUnpaidCent)
                    ),
                    fontSize = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::onMarkAllPaidConfirm) {
                    Text(
                        text = stringResource(R.string.action_mark_paid),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.wage_paid_green)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onMarkAllPaidDismiss) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        fontSize = 20.sp
                    )
                }
            }
        )
    }

    // ===== 长按工人弹 WorkerDetailDialog =====
    longPressGroup?.let { group ->
        WorkerDetailInfo(
            workerId = group.workerId,
            workerName = group.workerName,
            firstWorkDate = null,
            totalCount = group.totalCount,
            totalCent = group.totalCent,
            unpaidCount = group.unpaidCount,
            unpaidCent = group.unpaidCent,
            paidCount = group.paidCount,
            paidCent = group.paidCent
        ).let { info ->
            WorkerDetailDialog(
                info = info,
                onDismiss = { longPressGroup = null }
            )
        }
    }
}

/**
 * 工人聚合卡片（V1.3 + M2.1 修复长按）
 *
 * - 未付 tab：显示未付金额 + 未付笔数 + 未付订单缩略
 * - 已付 tab：显示已付金额 + 已付笔数 + 已付订单缩略
 * - 点击 → 进入工人详情（看所有订单）
 * - 右上角 "..." 图标按钮 → 弹 WorkerDetailDialog（累计统计）
 *
 * M2.1 决策：避免长按。改用可见的"..."图标按钮。
 */
@Composable
private fun WorkerGroupCard(
    group: WorkerGroup,
    isPaidTab: Boolean,
    onWorkerClick: (String) -> Unit,
    onMenuClick: () -> Unit
) {
    val visibleBills = if (isPaidTab) group.paidBills else group.unpaidBills
    val amount = if (isPaidTab) group.paidCent else group.unpaidCent
    val count = if (isPaidTab) group.paidCount else group.unpaidCount
    val amountColor = if (isPaidTab) R.color.wage_paid_green else R.color.wage_unpaid_red

    // Bug 1 修复：闭包陷阱防护
    val currentOnClick by rememberUpdatedState { onWorkerClick(group.workerId) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { currentOnClick() }
                )
            },
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.wage_card_background)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 顶部：姓名 + "..." 菜单按钮（Bug 5：替代长按）
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👤 ${group.workerName}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                // M2.1：右上角 "..." 图标按钮（替代长按，老年用户能看见）
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clickable(onClick = onMenuClick),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⋮",
                        fontSize = 28.sp,
                        color = colorResource(R.color.wage_disabled_gray)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isPaidTab) "🟢 已付 $count 笔" else "🔴 应付 $count 笔",
                fontSize = 16.sp,
                color = Color.Gray
            )
            Text(
                text = MoneyUtils.formatCent(amount) + " 元",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(amountColor)
            )

            // 订单缩略（前 3 笔）
            if (visibleBills.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                visibleBills.take(3).forEach { bill ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = bill.notes ?: (bill.worksiteName ?: ""),
                            fontSize = 14.sp,
                            color = colorResource(R.color.wage_text_primary),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = MoneyUtils.formatCent(bill.wageCent) + " 元",
                            fontSize = 14.sp,
                            color = colorResource(amountColor),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                if (visibleBills.size > 3) {
                    Text(
                        text = "...还有 ${visibleBills.size - 3} 笔",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeStats(
    unpaidCount: Int,
    unpaidCent: Long,
    paidCount: Int,
    paidCent: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = colorResource(R.color.wage_card_background),
                shape = RoundedCornerShape(12.dp)
            )
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.stats_unpaid_count, unpaidCount) + " / " + MoneyUtils.formatCent(unpaidCent) + " 元",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.wage_unpaid_red)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.stats_paid_count, paidCount) + " / " + MoneyUtils.formatCent(paidCent) + " 元",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colorResource(R.color.wage_paid_green)
        )
    }
}

@Composable
private fun HomeTabs(
    isPaidTab: Boolean,
    unpaidCount: Int,
    paidCount: Int,
    onTabChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabButton(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.tab_unpaid, unpaidCount),
            isSelected = !isPaidTab,
            selectedColor = colorResource(R.color.wage_unpaid_red),
            onClick = { onTabChange(false) }
        )
        TabButton(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.tab_paid, paidCount),
            isSelected = isPaidTab,
            selectedColor = colorResource(R.color.wage_paid_green),
            onClick = { onTabChange(true) }
        )
    }
}

@Composable
private fun TabButton(
    modifier: Modifier,
    label: String,
    isSelected: Boolean,
    selectedColor: Color,
    onClick: () -> Unit
) {
    val background = if (isSelected) selectedColor else Color.White
    val textColor = if (isSelected) Color.White else colorResource(R.color.wage_disabled_gray)
    val borderModifier = if (!isSelected) {
        Modifier.background(
            color = Color.White,
            shape = RoundedCornerShape(8.dp)
        ).background(
            color = Color.Transparent,
            shape = RoundedCornerShape(8.dp)
        )
    } else Modifier

    Row(
        modifier = modifier
            .height(56.dp)
            .background(color = background, shape = RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}