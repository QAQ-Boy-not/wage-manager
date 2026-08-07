// HomeScreen.kt - 首页 UI
//
// 布局（自上而下）：
//   1. 今日日期（大标题）
//   2. 手动登记按钮（蓝色大按钮）
//   3. 统计栏：合计 / 未付 / 已付
//   4. 工资记录列表（LazyColumn）
//   5. 空列表提示
//   6. RegisterBottomSheet（按状态显示）
//
// 设计要点：
// - 回调统一打包成 HomeScreenCallbacks（11 个回调太多，单个 data class 更清晰）
// - 汇总数字调用 WageCalculator 已在 ViewModel 里算好，这里只读不重算
// - 大字体、大按钮、高对比度（老年用户友好）

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R
import com.example.wagemanager.ui.components.BigButton
import com.example.wagemanager.util.DateRules
import com.example.wagemanager.util.MoneyUtils

/**
 * HomeScreen 的所有回调（11 个，用 data class 打包避免参数列表过长）
 */
data class HomeScreenCallbacks(
    val onManualRegisterClick: () -> Unit,
    val onWorkerNameChange: (String) -> Unit,
    val onWorkerNameFocusLost: () -> Unit,
    val onWageInputChange: (String) -> Unit,
    val onRegisterSubmit: () -> Unit,
    val onRegisterDismiss: () -> Unit,
    val onReuseWorker: (String) -> Unit,
    val onCreateNewWorker: () -> Unit,
    val onConfirmCreateNewWorker: () -> Unit,
    val onDuplicateDialogDismiss: () -> Unit,
    val onConfirmNewWorkerDialogDismiss: () -> Unit
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
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(20.dp))

            // ===== 手动登记按钮 =====
            BigButton(
                text = stringResource(R.string.action_manual_register),
                backgroundColor = MaterialTheme.colorScheme.primary,
                onClick = callbacks.onManualRegisterClick
            )
            Spacer(modifier = Modifier.height(20.dp))

            // ===== 统计栏 =====
            HomeStats(
                totalCent = state.totalCent,
                unpaidCount = state.unpaidCount,
                paidCount = state.paidCount,
                recordCount = state.recordCount
            )
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            // ===== 工资记录列表 =====
            if (state.records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Text(
                        text = stringResource(R.string.empty_records_hint),
                        fontSize = 20.sp,
                        color = Color.Gray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.records, key = { it.recordId }) { item ->
                        WageListItem(item = item)
                    }
                }
            }
        }
    }

    // ===== 登记 BottomSheet =====
    RegisterBottomSheet(
        state = state.registerForm,
        onWorkerNameChange = callbacks.onWorkerNameChange,
        onWorkerNameFocusLost = callbacks.onWorkerNameFocusLost,
        onWageInputChange = callbacks.onWageInputChange,
        onSubmit = callbacks.onRegisterSubmit,
        onDismiss = callbacks.onRegisterDismiss,
        onReuseWorker = callbacks.onReuseWorker,
        onCreateNewWorker = callbacks.onCreateNewWorker,
        onConfirmCreateNewWorker = callbacks.onConfirmCreateNewWorker,
        onDuplicateDialogDismiss = callbacks.onDuplicateDialogDismiss,
        onConfirmNewWorkerDialogDismiss = callbacks.onConfirmNewWorkerDialogDismiss
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

/**
 * 工资记录列表项（M1 不做点击交互，仅展示）
 */
@Composable
private fun WageListItem(item: HomeWageItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = colorResource(R.color.wage_card_background)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "👤 ${item.workerName}",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(180.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${MoneyUtils.formatCent(item.wageCent)} 元",
                fontSize = 22.sp,
                color = colorResource(
                    if (item.isPaid) R.color.wage_paid_green else R.color.wage_unpaid_red
                ),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
