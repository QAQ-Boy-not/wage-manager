// WorkerDetailScreen.kt - 工人详情页 UI（V1.2）
//
// 布局（自上而下）：
//   1. 顶部导航：← 返回 + 工人姓名
//   2. 工人信息卡：姓名 + 首次登记日期 + 统计
//   3. 未付账单组（红色标题）
//   4. 已付账单组（绿色标题）
//   5. 右下角 FAB [+] 添加账单

package com.example.wagemanager.ui.worker

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
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        factory = WorkerDetailViewModel.factory(repository, workerId)
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                            .padding(horizontal = 20.dp)
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
                        Spacer(modifier = Modifier.height(12.dp))

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
                                    BillCard(bill = bill)
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
                                    BillCard(bill = bill)
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

    // ===== 添加账单 BottomSheet =====
    if (state.isRegisterSheetVisible) {
        RegisterBillSheet(
            repository = repository,
            onDismiss = viewModel::onAddBillDismiss
        )
    }
}

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
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = if (name.isEmpty()) "👷" else "👤 $name",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(R.color.wage_text_primary)
            )
            if (firstWorkDate != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.detail_first_work_date,
                        DateRules.formatChineseDate(firstWorkDate)
                    ),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.stats_total_label, totalCount),
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = MoneyUtils.formatCent(totalCent) + " 元",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = colorResource(R.color.wage_text_primary)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.stats_unpaid_count, unpaidCount) + " / " + MoneyUtils.formatCent(unpaidCent) + " 元",
                    fontSize = 16.sp,
                    color = colorResource(R.color.wage_unpaid_red)
                )
                Text(
                    text = stringResource(R.string.stats_paid_count, paidCount) + " / " + MoneyUtils.formatCent(paidCent) + " 元",
                    fontSize = 16.sp,
                    color = colorResource(R.color.wage_paid_green)
                )
            }
        }
    }
}

@Composable
private fun BillGroupHeader(title: String, color: Color) {
    Text(
        text = title,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    )
}

@Composable
private fun BillCard(bill: BillItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.wage_card_background)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = DateRules.formatChineseDate(bill.workDate),
                fontSize = 16.sp,
                color = colorResource(R.color.wage_text_primary)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = MoneyUtils.formatCent(bill.wageCent) + " 元",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = colorResource(
                    if (bill.isPaid) R.color.wage_paid_green
                    else R.color.wage_unpaid_red
                )
            )
            if (bill.isPaid && bill.paidTime != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(
                        R.string.detail_paid_at,
                        PaymentRules.formatPaidTime(bill.paidTime)
                    ),
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }
        }
    }
}