// WorkerDetailDialog.kt - 长按工人卡片弹出的累计统计 Dialog（V1.3）
//
// 设计要点：
// 1. 用 Dialog（不是 BottomSheet）—— 高级查看功能，跟主流程不冲突
// 2. 不显示 UUID（V1.3 强制结论）
// 3. 显示：首次登记日期 / 累计账单数 / 未付+已付统计

package com.example.wagemanager.ui.worker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wagemanager.R
import com.example.wagemanager.util.DateRules
import com.example.wagemanager.util.MoneyUtils
import java.time.LocalDate

/**
 * 工人详情 Dialog 数据
 */
data class WorkerDetailInfo(
    val workerId: String,
    val workerName: String,
    val firstWorkDate: LocalDate?,
    val totalCount: Int,
    val totalCent: Long,
    val unpaidCount: Int,
    val unpaidCent: Long,
    val paidCount: Int,
    val paidCent: Long
)

@Composable
fun WorkerDetailDialog(
    info: WorkerDetailInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "👤 ${info.workerName}",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (info.firstWorkDate != null) {
                    Text(
                        text = stringResource(
                            R.string.detail_first_work_date,
                            DateRules.formatChineseDate(info.firstWorkDate)
                        ),
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = stringResource(R.string.detail_total_label, info.totalCount) + " 笔 / " + MoneyUtils.formatCent(info.totalCent) + " 元",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.wage_text_primary)
                )
                Spacer(modifier = Modifier.height(12.dp))

                // 未付（分行）
                Text(
                    text = "🔴 " + stringResource(R.string.stats_unpaid_count, info.unpaidCount) + " / " + MoneyUtils.formatCent(info.unpaidCent) + " 元",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.wage_unpaid_red)
                )
                Spacer(modifier = Modifier.height(4.dp))

                // 已付
                Text(
                    text = "🟢 " + stringResource(R.string.stats_paid_count, info.paidCount) + " / " + MoneyUtils.formatCent(info.paidCent) + " 元",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = colorResource(R.color.wage_paid_green)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.action_close),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}