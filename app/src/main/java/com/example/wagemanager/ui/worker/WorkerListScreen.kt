// WorkerListScreen.kt - 首页 UI（V1.2 工人列表）
//
// 布局（自上而下）：
//   1. 顶部：今日日期（大标题 28sp 居中）
//   2. 今日汇总栏（红色 / 绿色 22sp）
//   3. 工人卡片列表（LazyColumn）
//      - 空状态：「还没有工人，点击 [+] 添加第一笔账单」
//   4. 右下角 FAB [+] 添加账单

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun WorkerListScreen(
    repository: WageRepository,
    onWorkerClick: (String) -> Unit
) {
    val viewModel: WorkerListViewModel = viewModel(
        factory = WorkerListViewModel.factory(repository)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(Unit) {
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
                // ===== 顶部日期 =====
                Text(
                    text = DateRules.formatChineseDate(state.workDate),
                    style = MaterialTheme.typography.headlineLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ===== 今日汇总 =====
                TodaySummary(
                    unpaidCount = state.totalUnpaidCount,
                    unpaidCent = state.totalUnpaidCent,
                    paidCount = state.totalPaidCount,
                    paidCent = state.totalPaidCent
                )
                Spacer(modifier = Modifier.height(16.dp))

                // ===== 工人列表 =====
                if (state.workers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "👷",
                                fontSize = 56.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = stringResource(R.string.empty_workers_hint),
                                fontSize = 20.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = stringResource(R.string.empty_workers_action_hint),
                                fontSize = 16.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(state.workers, key = { it.workerId }) { worker ->
                            WorkerCard(
                                item = worker,
                                onClick = { onWorkerClick(worker.workerId) }
                            )
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

    // ===== 添加账单 BottomSheet =====
    if (state.isRegisterSheetVisible) {
        RegisterBillSheet(
            repository = repository,
            onDismiss = viewModel::onAddBillDismiss
        )
    }
}

/**
 * 今日汇总栏
 */
@Composable
private fun TodaySummary(
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = stringResource(R.string.stats_unpaid_count, unpaidCount),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.wage_unpaid_red)
                )
                Text(
                    text = MoneyUtils.formatCent(unpaidCent) + " 元",
                    fontSize = 18.sp,
                    color = colorResource(R.color.wage_unpaid_red)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.stats_paid_count, paidCount),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.wage_paid_green)
                )
                Text(
                    text = MoneyUtils.formatCent(paidCent) + " 元",
                    fontSize = 18.sp,
                    color = colorResource(R.color.wage_paid_green)
                )
            }
        }
    }
}

/**
 * 工人卡片
 */
@Composable
private fun WorkerCard(
    item: WorkerSummaryItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
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
            Text(
                text = "👤 ${item.workerName}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.wage_text_primary)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.worker_card_unpaid,
                        item.unpaidCount,
                        MoneyUtils.formatCent(item.unpaidCent)
                    ),
                    fontSize = 18.sp,
                    color = colorResource(R.color.wage_unpaid_red),
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(
                        R.string.worker_card_paid,
                        item.paidCount,
                        MoneyUtils.formatCent(item.paidCent)
                    ),
                    fontSize = 18.sp,
                    color = colorResource(R.color.wage_paid_green),
                    fontWeight = FontWeight.Bold
                )
            }
            if (item.latestWorkDate != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.worker_card_latest,
                        DateRules.formatChineseDate(item.latestWorkDate)
                    ),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}